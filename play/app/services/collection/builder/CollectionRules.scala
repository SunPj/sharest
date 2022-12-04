package com.github.sunpj.sharest.services.collection.builder

import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId, toFunctorOps}
import play.api.mvc.RequestHeader
import com.github.sunpj.sharest.services.collection.{Paging, Sort}
import com.github.sunpj.sharest.services.collection.builder.source.SourceTypes

import scala.concurrent.{ExecutionContext, Future}

trait CollectionRules[S <: SourceTypes] {
  def get: GetRules[S]
  def fetch: FetchRules[S]
  def update: UpdateRules[S]
  def delete: DeleteRules[S]
  def create: CreateRules[S]
}

case class PlainCollectionRules[S <: SourceTypes](
    get: GetRules[S], fetch: FetchRules[S], update: UpdateRules[S], delete: DeleteRules[S], create: CreateRules[S]
) extends CollectionRules[S]

object PlainCollectionRules {
  def empty[S <: SourceTypes](): PlainCollectionRules[S] = PlainCollectionRules(
    PlainGetRules[S](), FetchRules[S](), PlainUpdateRules[S](), PlainDeleteRules[S](), CreateRules[S]()
  )
}

case class SecuredCollectionRules[S <: SourceTypes, I](
  get: SecuredGetRules[S, I], fetch: FetchRules[S], update: UpdateRules[S], delete: SecuredDeleteRules[S, I], create: CreateRules[S]
) extends CollectionRules[S]

object SecuredCollectionRules {
  def empty[S <: SourceTypes, I](identityExtractor: IdentityExtractor[I]): SecuredCollectionRules[S, I] = SecuredCollectionRules(
    SecuredGetRules[S, I](None, None, identityExtractor),
    FetchRules[S](),
    SecuredUpdateRules[S, I](None, identityExtractor),
    SecuredDeleteRules[S, I](identityExtractor, None),
    CreateRules[S]()
  )
}

trait GetRules[S <: SourceTypes] extends AllowedFields[S] with FilterField[S] {
  type T <: GetRules[S]
}

case class PlainGetRules[S <: SourceTypes](
  fieldAllowed: Option[RequestHeader => Future[S#Fields]] = None,
  customFilter: Option[RequestHeader => Future[S#Filter]] = None
) extends GetRules[S] {

  override type T = PlainGetRules[S]

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): PlainGetRules[S] = copy(fieldAllowed = f)

  override protected def updateFilter(f: Option[RequestHeader => Future[S#Filter]]): PlainGetRules[S] = copy(customFilter = f)
}

case class SecuredGetRules[S <: SourceTypes, I](
  fieldAllowed: Option[RequestHeader => Future[S#Fields]] = None,
  customFilter: Option[RequestHeader => Future[S#Filter]] = None,
  override val ie: IdentityExtractor[I],
) extends GetRules[S] with SecuredAllowedFields[S, I] with SecuredFilterField[S, I] {

  override type T = SecuredGetRules[S, I]

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): SecuredGetRules[S, I] = copy(fieldAllowed = f)

  override protected def updateFilter(f: Option[RequestHeader => Future[S#Filter]]): SecuredGetRules[S, I] = copy(customFilter = f)
}

case class FetchRules[S <: SourceTypes](
  fieldAllowed: Option[RequestHeader => Future[S#Fields]] = None,
  customFilter: Option[RequestHeader => Future[S#Filter]] = None,
  sortAllowed: Option[RequestHeader => Sort => Future[Boolean]] = None,
  pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]] = None,
  filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]] = None
)

trait AllowedFields[S <: SourceTypes]{
  type T <: AllowedFields[S]
  private[builder] val allowedFields: Option[RequestHeader => Future[S#Fields]] = None

  protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): T

  def withAllowedFieldsF(f: RequestHeader => Future[S#Fields]): T =
    updateAllowedFields(f.some)

  def withAllowedFields(f: RequestHeader => S#Fields)(implicit ec: ExecutionContext): T =
    withAllowedFieldsF(f.map(_.pure[Future]))

  def withAllowedFields(fields: Future[S#Fields]): T = withAllowedFieldsF(_ => fields)

  def withAllowedFields(fields: S#Fields)(implicit ec: ExecutionContext): T = withAllowedFields(fields.pure[Future])

}

trait SecuredAllowedFields[S <: SourceTypes, I] extends AllowedFields[S] {
  protected val ie: IdentityExtractor[I]

  def withAllowedFieldsSecuredF(f: Option[I] => RequestHeader => Future[S#Fields])(implicit ec: ExecutionContext): T =
    withAllowedFieldsF(r => ie(r).flatMap(f(_)(r)))

  def withAllowedFieldsSecured(f: Option[I] => RequestHeader => S#Fields)(implicit ec: ExecutionContext): T =
    withAllowedFieldsSecuredF(f(_).map(_.pure[Future]))

  def withAllowedFieldsSecured(fields: Future[S#Fields])(implicit ec: ExecutionContext): T = withAllowedFieldsSecuredF(_ => _ => fields)

  def withAllowedFieldsSecured(fields: S#Fields)(implicit ec: ExecutionContext): T = withAllowedFieldsSecured(fields.pure[Future])
}

trait FilterField[S <: SourceTypes]{
  type T <: FilterField[S]
  private[builder] val filter: Option[RequestHeader => Future[S#Filter]] = None

  protected def updateFilter(f: Option[RequestHeader => Future[S#Filter]]): T

  def withFilterF(f: RequestHeader => Future[S#Filter]): T =
    updateFilter(f.some)

  def withFilter(f: RequestHeader => S#Filter)(implicit ec: ExecutionContext): T =
    withFilterF(f.map(_.pure[Future]))

  def withFilter(filter: Future[S#Filter]): T = withFilterF(_ => filter)

  def withFilter(fields: S#Filter)(implicit ec: ExecutionContext): T = withFilter(fields.pure[Future])

}

trait SecuredFilterField[S <: SourceTypes, I] extends FilterField[S] {
  protected val ie: IdentityExtractor[I]

  def withFilterSecuredF(f: Option[I] => RequestHeader => Future[S#Filter])(implicit ec: ExecutionContext): T =
    withFilterF(r => ie(r).flatMap(f(_)(r)))

  def withFilterSecured(f: Option[I] => RequestHeader => S#Filter)(implicit ec: ExecutionContext): T =
    withFilterSecuredF(f(_).map(_.pure[Future]))

  def withFilterSecured(fields: Future[S#Filter])(implicit ec: ExecutionContext): T = withFilterSecuredF(_ => _ => fields)

  def withFilterSecured(fields: S#Filter)(implicit ec: ExecutionContext): T = withFilterSecured(fields.pure[Future])
}

trait UpdateRules[S <: SourceTypes] extends AllowedFields[S] {
  type T <: UpdateRules[S]
}

case class PlainUpdateRules[S <: SourceTypes](
  override val allowedFields: Option[RequestHeader => Future[S#Fields]] = None
) extends UpdateRules[S] {
  override type T = PlainUpdateRules[S]

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): T = copy(f)
}

case class SecuredUpdateRules[S <: SourceTypes, I](
  override val allowedFields: Option[RequestHeader => Future[S#Fields]] = None,
  override val ie: IdentityExtractor[I],
) extends UpdateRules[S] with SecuredAllowedFields[S, I] {
  override type T = SecuredUpdateRules[S, I]

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): T = copy(allowedFields = f)
}

trait DeleteRules[S <: SourceTypes] extends FilterField[S] {
  type T <: DeleteRules[S]
}

case class PlainDeleteRules[S <: SourceTypes](
  override val filter: Option[RequestHeader => Future[S#Filter]] = None
) extends DeleteRules[S] {

  override type T = PlainDeleteRules[S]
  override protected def updateFilter(newFilter: Option[RequestHeader => Future[S#Filter]]): T = copy(newFilter)
}

case class SecuredDeleteRules[S <: SourceTypes, I](
  override val ie: IdentityExtractor[I],
  override val filter: Option[RequestHeader => Future[S#Filter]] = None
) extends DeleteRules[S] with SecuredFilterField[S, I] {

  override type T = SecuredDeleteRules[S, I]
  override protected def updateFilter(newFilter: Option[RequestHeader => Future[S#Filter]]): SecuredDeleteRules[S, I] = copy(filter = newFilter)
}


case class CreateRules[S <: SourceTypes](
  fieldAllowed: Option[RequestHeader => Future[S#Fields]] = None
)
