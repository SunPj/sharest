package com.github.sunpj.sharest.services.collection

import cats.data.EitherT
import com.github.sunpj.sharest.services.collection.CollectionService.{Res, UnitRes}
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

// delegates CRUD methods for specific collection by given collection name
class CollectionService @Inject()(supportedCollections: SupportedCollections)(implicit ec: ExecutionContext) {

  import supportedCollections.collections
  private val collectionsMap = collections.toMap

  private def getCollection(collectionName: String): Res[Collection] =
    EitherT.fromOption(collectionsMap.get(collectionName), CollectionNotFoundRestAPIError)

  def update(collectionName: String, id: String, updateModel: JsValue)(implicit r: RequestHeader): UnitRes =
    getCollection(collectionName).flatMap(_.update(id, updateModel))

  def create(collectionName: String, model: JsValue)(implicit r: RequestHeader): UnitRes =
    getCollection(collectionName).flatMap(_.create(model))

  def fetch(collectionName: String, filter: Option[Filter], sort: Option[Sort], page: Option[Paging], render: Int)(implicit r: RequestHeader): Res[FetchResult] =
    getCollection(collectionName)
      .flatMap(_.fetch(filter, sort, page))
      .map {
        case (items, total) => FetchResult(items, render + 1, total)
      }

  def delete(collectionName: String, id: String)(implicit r: RequestHeader): UnitRes =
    getCollection(collectionName).flatMap(_.delete(id))

  def get(collectionName: String, id: String)(implicit r: RequestHeader): Res[Option[JsValue]] =
    getCollection(collectionName).flatMap(_.get(id))

}

object CollectionService {
  type Res[T] = EitherT[Future, RestAPIError, T]
  type UnitRes = EitherT[Future, RestAPIError, Unit]
}

sealed trait RestAPIError extends Throwable
case object CollectionNotFoundRestAPIError extends RestAPIError
case class CollectionItemNotFoundRestAPIError(id: String, name: String) extends RestAPIError
case object CollectionIdParseRestAPIError extends RestAPIError
case object NoHandlerRestAPIError extends RestAPIError
case object Forbidden extends RestAPIError
case object BadRequest extends RestAPIError
case class SystemError(e: Throwable) extends RestAPIError

object RestAPIError {
  def noHandlerError: RestAPIError = NoHandlerRestAPIError
  def systemError(e: Throwable): RestAPIError = new SystemError(e)
  def systemError(message: String): RestAPIError = new SystemError(new RuntimeException(message))
  def systemError(message: String, e: Throwable): RestAPIError = new SystemError(new RuntimeException(message, e))
  def forbidden: RestAPIError = Forbidden
  def badRequest: RestAPIError = BadRequest
}

case class SupportedCollections(collections: (String, Collection)*)
