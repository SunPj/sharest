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
    PlainGetRules[S](), PlainFetchRules[S](), PlainUpdateRules[S](), PlainDeleteRules[S](), PlainCreateRules[S]()
  )
}

case class SecuredCollectionRules[S <: SourceTypes, I](
  get: SecuredGetRules[S, I], fetch: SecuredFetchRules[S, I], update: SecuredUpdateRules[S, I], delete: SecuredDeleteRules[S, I], create: SecuredCreateRules[S, I]
) extends CollectionRules[S]

object SecuredCollectionRules {
  def empty[S <: SourceTypes, I](identityExtractor: IdentityExtractor[I]): SecuredCollectionRules[S, I] = SecuredCollectionRules(
    SecuredGetRules[S, I](None, None, identityExtractor),
    SecuredFetchRules[S, I](identityExtractor),
    SecuredUpdateRules[S, I](None, identityExtractor),
    SecuredDeleteRules[S, I](identityExtractor, None),
    SecuredCreateRules[S, I](None, identityExtractor)
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

trait FetchRules[S <: SourceTypes] extends AllowedFields[S] with FilterField[S] {
  override type T <: FetchRules[S]

  private[builder] val sortAllowed: Option[RequestHeader => Sort => Future[Boolean]]

  private[builder] val pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]]

  private[builder] val filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]]

  protected def updateSortAllowed(
    sortAllowed: Option[RequestHeader => Sort => Future[Boolean]]
  ): T

  protected def updatePagingAllowed(
    pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]]
  ): T

  protected def updateFilterAllowed(
    filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]]
  ): T

  def withAllowedSortF(f: RequestHeader => Sort => Future[Boolean]): T =
    updateSortAllowed(f.some)

  def withAllowedSortR(f: RequestHeader => Sort => Boolean)(implicit ec: ExecutionContext): T =
    withAllowedSortF(f.map(_.map(_.pure[Future])))

  def withAllowedSort(f: Sort => Boolean)(implicit ec: ExecutionContext): T = withAllowedSortR(_ => f)

  def withAllowedPagingF(f: RequestHeader => Paging => Future[Boolean]): T =
    updatePagingAllowed(f.some)

  def withAllowedPagingR(f: RequestHeader => Paging => Boolean)(implicit ec: ExecutionContext): T =
    withAllowedPagingF(f.map(_.map(_.pure[Future])))

  def withAllowedPaging(f: Paging => Boolean)(implicit ec: ExecutionContext): T = withAllowedPagingR(_ => f)

  def withAllowedFilterF(f: RequestHeader => (String, String) => Future[Boolean]): T =
    updateFilterAllowed(f.some)

  def withAllowedFilterR(f: RequestHeader => (String, String) => Boolean)(implicit ec: ExecutionContext): T =
    withAllowedFilterF(r => (k, v) => f(r)(k, v).pure[Future])

  def withAllowedFilter(f: (String, String) => Boolean)(implicit ec: ExecutionContext): T = withAllowedFilterR(_ => f)

}

case class PlainFetchRules[S <: SourceTypes](
  override val filter: Option[RequestHeader => Future[S#Filter]] = None,
  override val allowedFields: Option[RequestHeader => Future[S#Fields]] = None,
  override val sortAllowed: Option[RequestHeader => Sort => Future[Boolean]] = None,
  override val pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]] = None,
  override val filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]] = None,
) extends FetchRules[S] {
  override type T = PlainFetchRules[S]

  override protected def updateSortAllowed(sortAllowed: Option[RequestHeader => Sort => Future[Boolean]]): PlainFetchRules[S] =
    copy(sortAllowed = sortAllowed)

  override protected def updatePagingAllowed(pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]]): PlainFetchRules[S] =
    copy(pagingAllowed = pagingAllowed)

  override protected def updateFilterAllowed(filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]]): PlainFetchRules[S] =
    copy(filterAllowed = filterAllowed)

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): PlainFetchRules[S] =
    copy(allowedFields = f)

  override protected def updateFilter(f: Option[RequestHeader => Future[S#Filter]]): PlainFetchRules[S] =
    copy(filter = f)
}

case class SecuredFetchRules[S <: SourceTypes, I](
  override val ie: IdentityExtractor[I],
  override val filter: Option[RequestHeader => Future[S#Filter]] = None,
  override val allowedFields: Option[RequestHeader => Future[S#Fields]] = None,
  override val sortAllowed: Option[RequestHeader => Sort => Future[Boolean]] = None,
  override val pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]] = None,
  override val filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]] = None,
) extends FetchRules[S] with SecuredAllowedFields[S, I] with SecuredFilterField[S, I] {
  override type T = SecuredFetchRules[S, I]

  override protected def updateSortAllowed(sortAllowed: Option[RequestHeader => Sort => Future[Boolean]]): SecuredFetchRules[S, I] =
    copy(sortAllowed = sortAllowed)

  override protected def updatePagingAllowed(pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]]): SecuredFetchRules[S, I] =
    copy(pagingAllowed = pagingAllowed)

  override protected def updateFilterAllowed(filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]]): SecuredFetchRules[S, I] =
    copy(filterAllowed = filterAllowed)

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): SecuredFetchRules[S, I] =
    copy(allowedFields = f)

  override protected def updateFilter(f: Option[RequestHeader => Future[S#Filter]]): SecuredFetchRules[S, I] =
    copy(filter = f)

  def withAllowedFilterSecuredF(f: Option[I] => RequestHeader => (String, String) => Future[Boolean])(implicit ec: ExecutionContext): T =
    withAllowedFilterF(r => (k, v) => ie(r).flatMap(i => f(i)(r)(k, v)))

  def withAllowedFilterSecured(f: Option[I] => RequestHeader => (String, String) => Boolean)(implicit ec: ExecutionContext): T =
    withAllowedFilterSecuredF(i => r => (k, v) => f(i)(r)(k, v).pure[Future])

  def withAllowedPagingSecuredF(f: Option[I] => RequestHeader => Paging => Future[Boolean])(implicit ec: ExecutionContext): T =
    withAllowedPagingF(r => p => ie(r).flatMap(i => f(i)(r)(p)))

  def withAllowedPagingSecured(f: Option[I] => RequestHeader => Paging => Boolean)(implicit ec: ExecutionContext): T =
    withAllowedPagingSecuredF(i => r => p => f(i)(r)(p).pure[Future])

  def withAllowedSortSecuredF(f: Option[I] => RequestHeader => Sort => Future[Boolean])(implicit ec: ExecutionContext): T =
    withAllowedSortF(r => p => ie(r).flatMap(i => f(i)(r)(p)))

  def withAllowedSortSecured(f: Option[I] => RequestHeader => Sort => Boolean)(implicit ec: ExecutionContext): T =
    withAllowedSortSecuredF(i => r => p => f(i)(r)(p).pure[Future])

}

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


trait CreateRules[S <: SourceTypes] extends AllowedFields[S] {
  override type T <: CreateRules[S]
}

case class PlainCreateRules[S <: SourceTypes](
  override val allowedFields: Option[RequestHeader => Future[S#Fields]] = None
) extends CreateRules[S] {
  override type T = PlainCreateRules[S]

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): T = copy(f)
}

case class SecuredCreateRules[S <: SourceTypes, I](
  override val allowedFields: Option[RequestHeader => Future[S#Fields]] = None,
  override val ie: IdentityExtractor[I],
) extends CreateRules[S] with SecuredAllowedFields[S, I] {
  override type T = SecuredCreateRules[S, I]

  override protected def updateAllowedFields(f: Option[RequestHeader => Future[S#Fields]]): T = copy(allowedFields = f)
}