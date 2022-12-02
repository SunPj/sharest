package com.github.sunpj.shaytan.services.collection.builder

import play.api.mvc.RequestHeader
import com.github.sunpj.shaytan.services.collection.{Paging, Sort}
import com.github.sunpj.shaytan.services.collection.builder.source.SourceTypes

import scala.concurrent.Future

case class CollectionRules[S <: SourceTypes](
    get: GetRules[S], fetch: FetchRules[S], update: UpdateRules[S], delete: DeleteRules[S], create: CreateRules[S]
)

object CollectionRules {
  def empty[S <: SourceTypes](): CollectionRules[S] = CollectionRules(
    GetRules[S](), FetchRules[S](), UpdateRules[S](), DeleteRules[S](), CreateRules[S]()
  )
}

case class GetRules[S <: SourceTypes](
  fieldAllowed: Option[RequestHeader => Future[S#Fields]] = None,
  customFilter: Option[RequestHeader => Future[S#Filter]] = None
)

case class FetchRules[S <: SourceTypes](
  fieldAllowed: Option[RequestHeader => Future[S#Fields]] = None,
  customFilter: Option[RequestHeader => Future[S#Filter]] = None,
  sortAllowed: Option[RequestHeader => Sort => Future[Boolean]] = None,
  pagingAllowed: Option[RequestHeader => Paging => Future[Boolean]] = None,
  filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]] = None
)

case class UpdateRules[S <: SourceTypes](
  allowedFields: Option[RequestHeader => Future[S#Fields]] = None
)

case class DeleteRules[S <: SourceTypes](
  filter: Option[RequestHeader => Future[S#Filter]] = None
)

case class CreateRules[S <: SourceTypes](
  fieldAllowed: Option[RequestHeader => Future[S#Fields]] = None
)
