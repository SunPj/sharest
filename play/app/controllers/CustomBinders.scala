package com.github.sunpj.shaytan.controllers

import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none, toFunctorOps}
import com.github.sunpj.shaytan.services.collection.{Asc, Desc, Filter, Paging, Sort}
import play.api.mvc.QueryStringBindable

object CustomBinders {
  implicit def PageParamsBindable(implicit intBinder: QueryStringBindable[Int]): QueryStringBindable[Paging] =
    new QueryStringBindable[Paging] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Paging]] = {
        for {
          limit <- intBinder.bind("limit", params)
          offset <- intBinder.bind("offset", params)
        } yield {
          (limit, offset) match {
            case (Right(limit), Right(offset)) =>
              Paging(limit, offset).asRight[String]
            case _ =>
              "Unable to bind an Page params".asLeft
          }
        }
      }

      override def unbind(key: String, paging: Paging): String = Seq(
        intBinder.unbind("limit", paging.limit),
        intBinder.unbind("offset", paging.offset)
      ).mkString("&")

    }

    implicit def sortBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Sort] =
    new QueryStringBindable[Sort] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Sort]] = {
        for {
          column <- stringBinder.bind("sortBy", params)
          order <- stringBinder.bind("sortOrder", params)
        } yield {
          (column, order) match {
            case (Right(column), Right("asc")) =>
              Sort(column, Asc).asRight[String]
            case (Right(column), Right("desc")) =>
              Sort(column, Desc).asRight[String]
            case _ =>
              "Unable to bind an SortParams".asLeft
          }
        }
      }

      override def unbind(key: String, sort: Sort): String = Seq(
        stringBinder.unbind("sortBy", sort.column),
        stringBinder.unbind("sortOrder", sort.toString)
      ).mkString("&")

    }

  implicit def filterBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Filter] =
    new QueryStringBindable[Filter] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Filter]] = {
        val filterParams = params.flatMap {
          case (key, value) => value.headOption.tupleLeft(key)
        }.toSeq

        if (filterParams.isEmpty) none else Filter(filterParams).asRight[String].some
      }

      override def unbind(key: String, filter: Filter): String = {
        filter.params.map {
          case (k, v) => stringBinder.unbind(k, v)
        }.mkString("&")
      }
    }

}
