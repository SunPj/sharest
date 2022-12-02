package com.github.sunpj.shaytan.services.collection

import cats.data.EitherT
import com.github.sunpj.shaytan.services.collection.CollectionService.Res
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

trait FetchAction {
  def apply(filter: Option[Filter], sort: Option[Sort], page: Option[Paging], render: Int = 0)(implicit r: RequestHeader, ec: ExecutionContext): Res[FetchResult]
}

object UnsupportedFetchAction extends FetchAction {
  def apply(filter: Option[Filter], sort: Option[Sort], page: Option[Paging], render: Int = 0)(implicit r: RequestHeader, ec: ExecutionContext): Res[FetchResult] =
    EitherT.leftT[Future, FetchResult](RestAPIError.noHandlerError)

}

case class FetchResult(items: Seq[JsValue], count: Int, total: Long)

case class Filter(params: Seq[(String, String)])
case class Paging(limit: Int, offset: Int)
case class Sort(column: String, order: SortOrder)

sealed trait SortOrder
case object Desc extends SortOrder
case object Asc extends SortOrder
