package com.github.sunpj.sharest.services.collection

import cats.data.EitherT
import play.api.Logging
import play.api.mvc.Results.{BadRequest, InternalServerError, MethodNotAllowed, NotFound, Forbidden => ForbiddenResult}
import play.api.mvc.{Action, ActionBuilderImpl, AnyContent, BodyParser, BodyParsers, Request, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EitherTAction @Inject() (parser: BodyParsers.Default)(implicit ec: ExecutionContext)
  extends ActionBuilderImpl(parser) with Logging {

  private def defaultRecoverStrategy: PartialFunction[RestAPIError, Result] = {
    case CollectionNotFoundRestAPIError =>
      NotFound("collection not found")
    case CollectionItemNotFoundRestAPIError(id, name) =>
      NotFound(s"No item with id = $id found in collection = $name")
    case CollectionIdParseRestAPIError =>
      BadRequest
    case NoHandlerRestAPIError =>
      MethodNotAllowed
    case Forbidden =>
      ForbiddenResult
    case SystemError(e) =>
      logger.logger.error("RestAPI error", e)
      InternalServerError
  }

  def handleEitherT(f: Request[AnyContent] => EitherT[Future, RestAPIError, Result]): Action[AnyContent] =
    async(f.andThen { r =>
      r.valueOr(defaultRecoverStrategy)
    })

  def handleEitherT[A](bodyParser: BodyParser[A])(block: Request[A] => EitherT[Future, RestAPIError, Result]): Action[A] =
    async[A](bodyParser)(block.andThen { r =>
      r.valueOr(defaultRecoverStrategy)
    })

}
