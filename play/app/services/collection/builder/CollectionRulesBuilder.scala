package com.github.sunpj.sharest.services.collection.builder

import com.github.sunpj.sharest.services.collection.builder.source.anorm.{AnormTableSource, StringTypes}
import com.github.sunpj.sharest.services.collection.Collection
import com.github.sunpj.sharest.services.collection.builder.source.{Source, SourceTypes}
import play.api.db.Database
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

case class SimpleCollectionRulesBuilder[S <: SourceTypes] private[builder](
  private val source: Source[S],
  private val rules: PlainCollectionRules[S]
) {

  private def withUpdatedRules(rules: PlainCollectionRules[S]): SimpleCollectionRulesBuilder[S] =
    copy(source, rules)

  def withGetRules(f: GetRules[S] => GetRules[S]): SimpleCollectionRulesBuilder[S] =
    withUpdatedRules(rules.copy(get = f(rules.get)))

  def withCreateRules(f: CreateRules[S] => CreateRules[S]): SimpleCollectionRulesBuilder[S] =
    withUpdatedRules(rules.copy(create = f(rules.create)))

  def withUpdateRules(f: UpdateRules[S] => UpdateRules[S]): SimpleCollectionRulesBuilder[S] =
    withUpdatedRules(rules.copy(update = f(rules.update)))

  def withDeleteRules(f: DeleteRules[S] => DeleteRules[S]): SimpleCollectionRulesBuilder[S] =
    withUpdatedRules(rules.copy(delete = f(rules.delete)))

  def withFetchRules(f: FetchRules[S] => FetchRules[S]): SimpleCollectionRulesBuilder[S] =
    withUpdatedRules(rules.copy(fetch = f(rules.fetch)))

  def toCollection: Collection = source.toCollection(rules)

  def withAccessSettings(implicit ec: ExecutionContext): PlainCollectionAccessBuilder =
    PlainCollectionAccessBuilder(toCollection, CollectionActionAccessRules.allEnabled)

  def secured[I](identityExtractor: IdentityExtractor[I]): SecuredCollectionRulesBuilder[S, I] =
    SecuredCollectionRulesBuilder(source, SecuredCollectionRules.empty(identityExtractor), identityExtractor)

}

trait IdentityExtractor[I] {
  def apply(r: RequestHeader): Future[Option[I]]
}

case class SecuredCollectionRulesBuilder[S <: SourceTypes, I] private[builder](
  private val source: Source[S],
  private val rules: SecuredCollectionRules[S, I],
  private val identityExtractor: IdentityExtractor[I]
) {

  private def withUpdatedRules(rules: SecuredCollectionRules[S, I]): SecuredCollectionRulesBuilder[S, I] =
    copy(source, rules, identityExtractor)

  private def secure[R](f: (RequestHeader, Option[I]) => Future[R])(implicit e: ExecutionContext): RequestHeader => Future[R] = h => {
    identityExtractor(h).flatMap(i => f(h, i))
  }

  def withGetRules(f: SecuredGetRules[S, I] => SecuredGetRules[S, I]): SecuredCollectionRulesBuilder[S, I] =
    withUpdatedRules(rules.copy(get = f(rules.get)))

  def withCreateRules(f: SecuredCreateRules[S, I] => SecuredCreateRules[S, I]): SecuredCollectionRulesBuilder[S, I] =
    withUpdatedRules(rules.copy(create = f(rules.create)))

  def withUpdateRules(f: SecuredUpdateRules[S, I] => SecuredUpdateRules[S, I]): SecuredCollectionRulesBuilder[S, I] =
    withUpdatedRules(rules.copy(update = f(rules.update)))

  def withDeleteRules(f: SecuredDeleteRules[S, I] => SecuredDeleteRules[S, I]): SecuredCollectionRulesBuilder[S, I] =
    withUpdatedRules(rules.copy(delete = f(rules.delete)))

  def withFetchRules(f: SecuredFetchRules[S, I] => SecuredFetchRules[S, I]): SecuredCollectionRulesBuilder[S, I] =
    withUpdatedRules(rules.copy(fetch = f(rules.fetch)))

  def toCollection: Collection = source.toCollection(rules)

  def withSecuredAccessSettings(implicit ec: ExecutionContext): SecuredCollectionAccessBuilder[I] =
    SecuredCollectionAccessBuilder(identityExtractor, toCollection, CollectionActionAccessRules.allEnabled)

}

object CollectionRulesBuilder {
  def fromSource[S <: SourceTypes](source: Source[S]): SimpleCollectionRulesBuilder[S] = SimpleCollectionRulesBuilder(source, PlainCollectionRules.empty[S]())

  def fromAnormTable(tableName: String, db: Database)(implicit ec: ExecutionContext): SimpleCollectionRulesBuilder[StringTypes.type] =
    fromSource(new AnormTableSource(tableName, db))

}
