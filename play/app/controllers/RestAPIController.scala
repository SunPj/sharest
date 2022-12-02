package com.github.sunpj.shaytan.controllers

import com.github.sunpj.shaytan.services.collection.{Asc, CollectionService, Desc, EitherTAction, FetchResult, Filter, Paging, Sort, SortOrder}
import play.api.libs.json.{JsString, JsValue, Json, JsonValidationError, Reads, Writes}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Result}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

/**
 * Web API for collections CRUD methods
 */
class RestAPIController @Inject() (
    collectionService: CollectionService,
    eitherTAction: EitherTAction,
    cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  import RestAPIController._

  /**
    * Returns collection item by given id in JSON
    *
    * @param collectionName name of a collection to retrieve the item from
    * @param id             id of a collection item which needs to be returned in json
    * @return [[NotFound]] if there is no collection by given name, or collection item
    *         with given id not found
    *         [[BadRequest]] if given collection item's id can't be parsed
    *         [[MethodNotAllowed]] if GET method is not allowed for given collection
    *         [[Forbidden]] if authenticated user can't call this method
    */
  def get(collectionName: String, id: String): Action[AnyContent] =
    eitherTAction.handleEitherT { implicit r =>
      collectionService.get(collectionName, id).map(_.fold[Result](NotFound)(Ok(_)))
    }

  /**
    * Deletes collection item by given id
    *
    * @param collectionName name of a collection to delete the item from
    * @param id             id of a collection item which needs to be deleted
    * @return [[NotFound]] if there is no registered collection by given name, or collection item
    *         with given id not found
    *         [[BadRequest]] if given collection item's id can't be parsed
    *         [[MethodNotAllowed]] if DELETE method is not allowed for given collection
    *         [[Forbidden]] if authenticated user can't call this method
    */
  def delete(collectionName: String, id: String): Action[AnyContent] =
    eitherTAction.handleEitherT { implicit r =>
      collectionService.delete(collectionName, id).map(_ => Ok)
    }

  /**
    * Fetches a list of collection items
    *
    * @param collectionName name of a collection to fetch the list of items from
    * @return [[NotFound]] if there is no registered collection with given name
    *         [[BadRequest]] if filter or sort params are not correct
    *         [[MethodNotAllowed]] if fetch method is not allowed for given collection
    *         [[Forbidden]] if authenticated user can't call this method
    */
  def fetch(collectionName: String, filter: Option[Filter], sort: Option[Sort], page: Option[Paging], render: Int = 0): Action[AnyContent] =
    eitherTAction.handleEitherT { implicit r =>
      collectionService
        .fetch(collectionName, filter, sort, page, render)
        .map(r => Ok(Json.toJson(r)))
    }

  /**
    * Creates a new collection item
    *
    * @param collectionName name of a collection to create a new item in
    * @return [[NotFound]] if there is no registered collection by given name
    *         [[BadRequest]] if HTTP request body can't be parsed to collection item
    *         [[MethodNotAllowed]] if create method is not allowed for given collection
    *         [[Forbidden]] if authenticated user can't call this method
    */
  def create(collectionName: String): Action[JsValue] =
    eitherTAction.handleEitherT(parse.json) { implicit r =>
      collectionService.create(collectionName, r.body).map(_ => Ok)
    }

  /**
    * Updates collection item
    *
    * @param collectionName name of a collection to create a new item in
    * @param id             id of a collection item which needs to be updated
    * @return [[NotFound]] if there is no registered collection by given name, or collection item
    *         with given id not found
    *         [[BadRequest]] if HTTP request body can't be parsed to collection item
    *         [[MethodNotAllowed]] if update method is not allowed for given collection
    *         [[Forbidden]] if authenticated user can't call this method
    */
  def update(collectionName: String, id: String): Action[JsValue] =
    eitherTAction.handleEitherT(parse.json) { implicit r =>
      collectionService.update(collectionName, id, r.body).map(_ => Ok)
    }

}

case class FetchParams(filter: Option[Filter], sort: Option[Sort], page: Option[Paging], render: Int = 0)

object RestAPIController {
  implicit val sortOrderReads: Reads[SortOrder] =
    Json
      .reads[JsString]
      .collect(JsonValidationError("Incorrect sort order, valid values are desc and asc")) {
        case JsString("desc") => Desc
        case JsString("asc") => Asc
      }
  implicit val sortReads: Reads[Sort] = Json.reads
  implicit val filterReads: Reads[Filter] = Json.reads
  implicit val pagingReads: Reads[Paging] = Json.reads
  implicit val fetchParamsReads: Reads[FetchParams] = Json.reads
  implicit val fetchResultWrites: Writes[FetchResult] = Json.writes
}
