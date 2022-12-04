package com.github.sunpj.sharest.services.collection.builder

import cats.data.EitherT
import cats.implicits.catsSyntaxApplicativeId
import com.github.sunpj.sharest.services.collection.{Collection, Filter, Paging, RestAPIError, Sort}
import com.github.sunpj.sharest.services.collection.CollectionService.{Res, UnitRes}
import com.github.sunpj.sharest.services.collection.builder.CollectionActionAccessRules.{ActionAccessRule, disabled, secured}
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

case class CollectionActionAccessRules(
  create: ActionAccessRule,
  get: ActionAccessRule,
  update: ActionAccessRule,
  delete: ActionAccessRule,
  fetch: ActionAccessRule
)

object CollectionActionAccessRules {
  type ActionAccessRule = Option[RequestHeader => Res[RequestHeader]]
  val disabled: ActionAccessRule = None
  def enabled()(implicit e: ExecutionContext): ActionAccessRule = Some(_.pure[Res])
  def secured(f: RequestHeader => Future[Boolean])(implicit e: ExecutionContext): ActionAccessRule = Some(r =>
    for {
      allowed <- EitherT.right(f(r))
      h <- if (allowed) r.pure[Res] else EitherT.leftT[Future, RequestHeader](RestAPIError.forbidden)
    } yield h
  )

  def allEnabled(implicit e: ExecutionContext) = CollectionActionAccessRules(
    enabled(),
    enabled(),
    enabled(),
    enabled(),
    enabled()
  )
}

trait CollectionAccessBuilder[T] {

  protected val rules: CollectionActionAccessRules

  protected def withRules(newRules: CollectionActionAccessRules): T

  def disableCreate(): T = withRules(rules.copy(create = disabled))

  def disableGet(): T = withRules(rules.copy(get = disabled))

  def disableUpdate(): T = withRules(rules.copy(update = disabled))

  def disableDelete(): T = withRules(rules.copy(delete = disabled))

  def disableFetch(): T = withRules(rules.copy(fetch = disabled))
}
case class PlainCollectionAccessBuilder(private val collection: Collection, protected override val rules: CollectionActionAccessRules)(implicit ec: ExecutionContext)
  extends CollectionAccessBuilder[PlainCollectionAccessBuilder] {

  def disableAllMethods() = withRules(
    rules.copy(disabled, disabled, disabled, disabled, disabled)
  )

  def toCollection: Collection = new Collection {
    override def create(model: JsValue)(implicit r: RequestHeader): UnitRes =
      rules.create.fold(EitherT.leftT[Future, Unit](RestAPIError.forbidden)) { f =>
        f(r).flatMap(collection.create(model)(_))
      }

    override def get(id: String)(implicit r: RequestHeader): Res[Option[JsValue]] =
      rules.get.fold(EitherT.leftT[Future, Option[JsValue]](RestAPIError.forbidden)) { f =>
        f(r).flatMap(collection.get(id)(_))
      }

    override def update(id: String, model: JsValue)(implicit r: RequestHeader): UnitRes =
      rules.update.fold(EitherT.leftT[Future, Unit](RestAPIError.forbidden)) { f =>
        f(r).flatMap(collection.update(id, model)(_))
      }

    override def delete(id: String)(implicit r: RequestHeader): UnitRes =
      rules.delete.fold(EitherT.leftT[Future, Unit](RestAPIError.forbidden)) { f =>
        f(r).flatMap(collection.delete(id)(_))
      }

    override def fetch(filter: Option[Filter], sort: Option[Sort], paging: Option[Paging])(implicit r: RequestHeader): Res[(Seq[JsValue], Long)] =
      rules.fetch.fold(EitherT.leftT[Future, (Seq[JsValue], Long)](RestAPIError.forbidden)) { f =>
        f(r).flatMap(collection.fetch(filter, sort, paging)(_))
      }
  }

  override protected def withRules(newRules: CollectionActionAccessRules): PlainCollectionAccessBuilder =
    copy(rules = newRules)

}


case class SecuredCollectionAccessBuilder[I](
  private val identityExtractor: IdentityExtractor[I],
  private val collection: Collection,
  protected override val rules: CollectionActionAccessRules
)(implicit ec: ExecutionContext) extends CollectionAccessBuilder[SecuredCollectionAccessBuilder[I]] {

  private def securedAction(predicate: Option[I] => Future[Boolean]): ActionAccessRule =
    secured(r => identityExtractor(r).flatMap(predicate))

  def withSecuredGet(predicate: Option[I] => Future[Boolean]): SecuredCollectionAccessBuilder[I] =
    withRules(rules.copy(get = securedAction(predicate)))
  def withSecuredUpdate(predicate: Option[I] => Future[Boolean]): SecuredCollectionAccessBuilder[I] =
    withRules(rules.copy(update = securedAction(predicate)))
  def withSecuredFetch(predicate: Option[I] => Future[Boolean]): SecuredCollectionAccessBuilder[I] =
    withRules(rules.copy(fetch = securedAction(predicate)))
  def withSecuredCreate(predicate: Option[I] => Future[Boolean]): SecuredCollectionAccessBuilder[I] =
    withRules(rules.copy(create = securedAction(predicate)))
  def withSecuredDelete(predicate: Option[I] => Future[Boolean]): SecuredCollectionAccessBuilder[I] =
    withRules(rules.copy(delete = securedAction(predicate)))

  override protected def withRules(newRules: CollectionActionAccessRules): SecuredCollectionAccessBuilder[I] =
    copy(rules = newRules)

}
