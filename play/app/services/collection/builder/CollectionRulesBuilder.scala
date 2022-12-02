package com.github.sunpj.shaytan.services.collection.builder

import com.github.sunpj.shaytan.services.collection.builder.source.anorm.{AnormTableSource, StringTypes}
import com.github.sunpj.shaytan.services.collection.{Collection, Paging, Sort}
import com.github.sunpj.shaytan.services.collection.builder.source.{Source, SourceTypes}
import play.api.db.Database
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

trait CollectionRulesBuilder[S <: SourceTypes, T] {
  protected def rules: CollectionRules[S]

  protected def withUpdatedRules(newRules: CollectionRules[S]): T

  def withGetRules(newRules: GetRules[S]): T =
    withUpdatedRules(rules.copy(get = newRules))

  def withCreateRules(newRules: CreateRules[S]): T =
    withUpdatedRules(rules.copy(create = newRules))

  def withUpdateRules(newRules: UpdateRules[S]): T =
    withUpdatedRules(rules.copy(update = newRules))

  def withDeleteRules(newRules: DeleteRules[S]): T =
    withUpdatedRules(rules.copy(delete = newRules))

  def withFetchRules(newRules: FetchRules[S]): T =
    withUpdatedRules(rules.copy(fetch = newRules))

}

case class SimpleCollectionRulesBuilder[S <: SourceTypes] private[builder](
  private val source: Source[S],
  override val rules: CollectionRules[S]
) extends CollectionRulesBuilder[S, SimpleCollectionRulesBuilder[S]] {

  override protected def withUpdatedRules(rules: CollectionRules[S]): SimpleCollectionRulesBuilder[S] =
    copy(source, rules)

  def toCollection: Collection = source.toCollection(rules)

  def withAccessSettings(implicit ec: ExecutionContext): PlainCollectionAccessBuilder =
    PlainCollectionAccessBuilder(toCollection, CollectionActionAccessRules.allEnabled)

  def secured[I](identityExtractor: RequestHeader => Future[Option[I]]): SecuredCollectionRulesBuilder[S, I] =
    SecuredCollectionRulesBuilder(source, rules, identityExtractor)

}

case class SecuredCollectionRulesBuilder[S <: SourceTypes, I] private[builder](
  private val source: Source[S],
  override protected val rules: CollectionRules[S],
  private val identityExtractor: RequestHeader => Future[Option[I]]
) extends CollectionRulesBuilder[S, SecuredCollectionRulesBuilder[S, I]] {

  override protected def withUpdatedRules(rules: CollectionRules[S]): SecuredCollectionRulesBuilder[S, I] =
    copy(source, rules, identityExtractor)

  private def secure[R](f: (RequestHeader, Option[I]) => Future[R])(implicit e: ExecutionContext): RequestHeader => Future[R] = h => {
    identityExtractor(h).flatMap(i => f(h, i))
  }

  def withGetRules(newRules: SecuredGetRules[S, I])(implicit ec: ExecutionContext): this.type = {
      withUpdatedRules(
        rules.copy(
          get = GetRules(
            newRules.fieldAllowed.map(secure),
            newRules.customFilter.map(secure)
          )
      )
      )
    this
  }

  def withCreateRules(newRules: SecuredCreateRules[S, I])(implicit ec: ExecutionContext): this.type = {
    withUpdatedRules(
      rules.copy(
        create = CreateRules(
          newRules.fieldAllowed.map(secure),
        )
      )
    )
    this
  }


  def withUpdateRules(newRules: SecuredUpdateRules[S, I])(implicit ec: ExecutionContext): this.type = {
    withUpdatedRules(
      rules.copy(
        update = UpdateRules(
          newRules.allowedFields.map(secure)
        )
      )
    )
    this
  }

  def withDeleteRules(newRules: SecuredDeleteRules[S, I])(implicit ec: ExecutionContext): this.type = {
    withUpdatedRules(
      rules.copy(
        delete = DeleteRules(
          newRules.filter.map(secure)
        )
      )
    )
    this
  }

  def withFetchRules(newRules: SecuredFetchRules[S, I])(implicit ec: ExecutionContext): this.type = {
    withUpdatedRules(
      rules.copy(
        fetch = FetchRules(
          newRules.fieldAllowed.map(secure),
          newRules.customFilter.map(secure),
          newRules.sortAllowed,
          newRules.pagingAllowed,
          newRules.filterAllowed
        )
      )
    )
    this
  }

  def toCollection: Collection = source.toCollection(rules)

  def withSecuredAccessSettings(implicit ec: ExecutionContext): SecuredCollectionAccessBuilder[I] =
    SecuredCollectionAccessBuilder(identityExtractor, toCollection, CollectionActionAccessRules.allEnabled)

}

case class SecuredGetRules[S <: SourceTypes, I](
  fieldAllowed: Option[(RequestHeader, Option[I]) => Future[S#Fields]] = None,
  customFilter: Option[(RequestHeader, Option[I]) => Future[S#Filter]] = None
)

case class SecuredFetchRules[S <: SourceTypes, I](
  fieldAllowed: Option[(RequestHeader, Option[I]) => Future[S#Fields]] = None,
  customFilter: Option[(RequestHeader, Option[I]) => Future[S#Filter]] = None,
  sortAllowed: Option[RequestHeader => Sort => Future[Boolean]] = None,
  pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]] = None,
  filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]] = None
)

case class SecuredUpdateRules[S <: SourceTypes, I](
  allowedFields: Option[(RequestHeader, Option[I]) => Future[S#Fields]] = None
)

case class SecuredDeleteRules[S <: SourceTypes, I](
  filter: Option[(RequestHeader, Option[I]) => Future[S#Filter]] = None
)

case class SecuredCreateRules[S <: SourceTypes, I](
  fieldAllowed: Option[(RequestHeader, Option[I]) => Future[S#Fields]] = None
)

object CollectionRulesBuilder {
  def fromSource[S <: SourceTypes](source: Source[S]) = new SimpleCollectionRulesBuilder(source, CollectionRules.empty[S]())

  def fromAnormTable(tableName: String, db: Database)(implicit ec: ExecutionContext): SimpleCollectionRulesBuilder[StringTypes.type] =
    fromSource(new AnormTableSource(tableName, db))

}
