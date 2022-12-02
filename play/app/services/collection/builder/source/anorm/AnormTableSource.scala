package com.github.sunpj.sharest.services.collection.builder.source.anorm

import anorm._
import cats.data.{EitherT, OptionT}
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxApplicativeId, catsSyntaxEitherId, catsSyntaxOptionId, none, toFunctorOps, toTraverseOps}
import com.github.sunpj.sharest.services.collection.RestAPIError
import com.github.sunpj.sharest.services.collection.builder.source.SourceTypes
import play.api.db.Database
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString, JsValue, Json}
import play.api.mvc.RequestHeader
import com.github.sunpj.sharest.services.collection.CollectionService.{Res, UnitRes}
import com.github.sunpj.sharest.services.collection.{Collection, Filter, Paging, Sort}
import com.github.sunpj.sharest.services.collection.builder.{CollectionRules, CreateRules, DeleteRules, FetchRules, GetRules, UpdateRules}
import com.github.sunpj.sharest.services.collection.builder.source.Source

import java.math.BigInteger
import java.sql.{Date, Timestamp}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object StringTypes extends SourceTypes {
  type Field = String
  type Fields = Set[Field]
  type Filter = String
}

class AnormTableSource(tableName: String, db: Database)(implicit e: ExecutionContext) extends Source[StringTypes.type] {

  @inline private def className(that: Any): String =
    if (that == (null: Any)) "<null>" else that.getClass.getName

  implicit val columnToJsValue: Column[JsValue] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, _, _) = meta
    value match {
      case bi: BigInteger => Right(JsNumber(bi.longValue))
      case ts: Timestamp => Right(JsNumber(ts.getTime))
      case s: String => Right(JsString(s))
      case int: Int => Right(JsNumber(int.toLong))
      case long: Long => Right(JsNumber(long))
      case s: Short => Right(JsNumber(s.toLong))
      case b: Byte => Right(JsNumber(b.toLong))
      case bool: Boolean => Right(JsBoolean(bool))
      case date: Date => Right(JsNumber(date.getTime))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to JsValue for column $qualified of table $tableName"))
    }
  }

  private val columnParser: RowParser[TableColumn] = Macro.parser[TableColumn]("column_name", "data_type")

  private val schema: TableSchema = {
    db.withConnection { implicit c =>
      val tableColumns = SQL"SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = $tableName".as(columnParser.*)
      val primaryKeyColumnName =
        SQL"""
          SELECT ccu.column_name, c.data_type
          FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
          JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu ON ccu.constraint_name = tc.constraint_name AND ccu.table_name = tc.table_name
          JOIN INFORMATION_SCHEMA.COLUMNS c ON c.table_name = ccu.table_name AND c.column_name = ccu.column_name
          WHERE
            tc.constraint_type = 'PRIMARY KEY'
            AND tc.table_name = $tableName
            AND ccu.table_name = $tableName
         """.as(columnParser.singleOpt)

      TableSchema(primaryKeyColumnName, tableColumns.toSet)
    }
  }

  @annotation.tailrec
  private def go(c: Option[Cursor], fieldsToRetrieve: Set[String], result: Seq[JsObject]): Seq[JsObject] = c match {
    case Some(cursor) =>
      go(
        cursor.next,
        fieldsToRetrieve,
        JsObject(
          fieldsToRetrieve.map{ key => key -> cursor.row.apply[JsValue](key) }.toSeq
        ) +: result
      )
    case _ =>
      result
  }

  private def namedParameter(column: TableColumn, value: String): Either[RestAPIError, NamedParameter] = column match {
    case TableColumn(_, "integer") =>
      Try(Integer.valueOf(value))
        .toEither
        .map[NamedParameter](column.name -> _)
        .left.map(e => RestAPIError.systemError(s"value '$value' can't be converted to Int", e))
    case TableColumn(_, "boolean") =>
      Try(value.toBoolean)
        .toEither
        .map[NamedParameter](column.name -> _)
        .left.map(e => RestAPIError.systemError(s"value '$value' can't be converted to boolean", e))
    case TableColumn(_, "character varying") =>
      ((column.name -> value): NamedParameter).asRight[RestAPIError]
    case TableColumn(_, "timestamp with time zone") =>
      Try(value.toLong)
        .toEither
        .map(Instant.ofEpochSecond)
        .map[NamedParameter](column.name -> _)
        .left.map(e => RestAPIError.systemError(s"value '$value' can't be converted to Long", e))
    case TableColumn(_, dataType) =>
      RestAPIError.systemError(s"value '$value' can't be converted. Type: $dataType is not supported yet!").asLeft
  }

  private def idParameter(id: String): Either[RestAPIError, NamedParameter] =
    schema
      .primaryKeyColumn
      .toRight(RestAPIError.systemError(s"Primary key column name is not specified for table"))
      .flatMap(namedParameter(_, id))

  private def getPrimaryKeyColumnName: Either[RestAPIError, String] =
    schema
      .primaryKeyColumn
      .toRight(RestAPIError.systemError(s"primary key not found for table = $tableName"))
      .map(_.name)

  private def assembleWhereClause(
    filter: Option[RequestHeader => Future[StringTypes.Filter]],
    methodName: String
  )(implicit r: RequestHeader): Res[String] =
    OptionT(filter.traverse(_ (r))).getOrElse("")
    .attemptT
    .leftMap(e => RestAPIError.systemError(s"building where clause for '${methodName.toUpperCase}' method failed for table $tableName", e))

  private def assembleAllowedFields(
    allowedFields: Option[RequestHeader => Future[StringTypes.Fields]], method: String
  )(implicit r: RequestHeader): Res[Set[String]] =
    OptionT(allowedFields.traverse(_ (r)))
      .fold(schema.columns.map(_.name))(_.intersect(schema.columns.map(_.name)))
      .attemptT
      .leftMap(e => RestAPIError.systemError(s"assembling select fields for '$method' failed for table $tableName", e))

  private def prepareFilterParams(
    filterAllowed: Option[RequestHeader => (String, String) => Future[Boolean]],
    filter: Filter
  )(implicit r: RequestHeader): Res[Seq[(String, NamedParameter)]] = EitherT(
    filter
      .params
      .flatMap {
        case (columnName, columnValue) => schema.columns.find(_.name == columnName).tupleRight(columnValue)
      }
      .traverse(cv =>
        filterAllowed.map(f => f(r)(cv._1.name, cv._2)).getOrElse(Future.successful(true)).tupleRight(cv)
      )
      .map(
        _.collect {
          case (true, c) => c
        }
        .traverse {
          case (column, columnValue) => namedParameter(column, columnValue).tupleLeft(column.name)
        }
      )
  )

  private def sortClause(
    allowed: Option[RequestHeader => Sort => Future[Boolean]],
    sort: Option[Sort]
  )(implicit r: RequestHeader): Res[String] =
    allowedCheck(allowed, sort)
      .subflatMap(s => schema.columns.find(_.name == s.column).tupleLeft(s))
      .map {
        case (s, column) => s"ORDER BY ${column.name} ${s.order}"
      }
      .getOrElse("")
      .attemptT
      .leftMap(e =>
        RestAPIError.systemError(s"sort clause assembling error for table = $tableName, sort = $sort", e)
      )

  private def allowedByDefaultF[T]: RequestHeader => T => Future[Boolean] = _ => _ => Future.successful(true)

  private def allowedCheck[T](
    f: Option[RequestHeader => T => Future[Boolean]],
    model: Option[T]
  )(implicit r: RequestHeader): OptionT[Future, T] = OptionT(
    model.flatTraverse(
      m => f.getOrElse(allowedByDefaultF)(r)(m).map(allowed => if (allowed) m.some else none)
    )
  )

  private def pagingClause(
    allowed: Option[RequestHeader => Paging => Future[Boolean]],
    paging: Option[Paging]
  )(implicit r: RequestHeader): Res[Option[(String, Seq[NamedParameter])]] =
    allowedCheck(allowed, paging)
      .map { p =>
          ("limit {limit} offset {offset}", Seq(NamedParameter("limit", p.limit), NamedParameter("offset", p.offset)))
      }
      .value
      .attemptT
      .leftMap(e =>
        RestAPIError.systemError(s"paging query assembling error for table = $tableName, paging = $paging", e)
      )

  override def toCollection(rules: CollectionRules[StringTypes.type]): Collection = new Collection {
    private val GetRules(allowedFields, getFilter) = rules.get
    private val DeleteRules(deleteFilter) = rules.delete
    private val FetchRules(fetchAllowedFields, fetchFilter, sortAllowed, pagingAllowed, filterAllowed) = rules.fetch
    private val UpdateRules(allowedToUpdateFields) = rules.update
    private val CreateRules(allowedToCreateFields) = rules.create

    override def get(id: String)(implicit r: RequestHeader): Res[Option[JsValue]] = for {
      primaryKeyColumnName <- EitherT.fromEither[Future](getPrimaryKeyColumnName)
      fieldsToRetrieve <- assembleAllowedFields(allowedFields, "get")
      selectClause = fieldsToRetrieve.mkString(", ")
      whereClause <- assembleWhereClause(getFilter, "get")
      idParam <- EitherT.fromEither[Future](idParameter(id))
      rawQuery = s"SELECT $selectClause FROM $tableName WHERE $primaryKeyColumnName = {id} $whereClause"
      item <- EitherT.fromEither[Future](
        db.withConnection { implicit c =>
          SQL(rawQuery).on(idParam).withResult(go(_, fieldsToRetrieve, Seq.empty))
        }
      ).leftMap { e =>
        RestAPIError.systemError(s"Get item SQL query error for table $tableName", e.head)
      }
    } yield item.headOption

    override def fetch(filter: Option[Filter], sort: Option[Sort], paging: Option[Paging])(implicit r: RequestHeader): Res[(Seq[JsValue], Long)] = for {
      fieldsToRetrieve <- assembleAllowedFields(fetchAllowedFields, "fetch")
      selectClause = fieldsToRetrieve.mkString(", ")
      customFilterWhereClause <- assembleWhereClause(fetchFilter, "fetch")
      filterData <- filter.fold(Seq.empty[(String, NamedParameter)].pure[Res])(prepareFilterParams(filterAllowed, _))
      (filterColumnNames, filterParams) = filterData.unzip
      requestFilterWhereClause = filterColumnNames.map(n => s"$n = {$n}").mkString(" AND ")
      pagingData <- pagingClause(pagingAllowed, paging)
      (pagingQuery, pagingParams) = pagingData.getOrElse("", Seq.empty)
      sort <- sortClause(sortAllowed, sort)
      whereConditions = Seq(customFilterWhereClause, requestFilterWhereClause).filter(_.nonEmpty).mkString(" AND ")
      whereClause = if (whereConditions.nonEmpty) s" WHERE $whereConditions" else ""
      rawQuery = s"SELECT $selectClause FROM $tableName $whereClause $sort $pagingQuery"
      items <- EitherT.fromEither[Future](
        db.withConnection { implicit c =>
          SQL(rawQuery).on(filterParams: _*).on(pagingParams: _*).withResult(go(_, fieldsToRetrieve, Seq.empty))
        }
      ).leftMap { e =>
        RestAPIError.systemError(s"Fetch items from '$tableName' table SQL query error", e.head)
      }
      totalQuery = s"SELECT COUNT(*) FROM $tableName ${if (customFilterWhereClause.isEmpty) "" else s"WHERE $customFilterWhereClause"}"
      total <- EitherT.fromEither[Future](
        db.withConnection { implicit c =>
          Try(SQL(totalQuery).as(SqlParser.get[Long](1).single))
            .toEither
            .left
            .map(e => RestAPIError.systemError(s"Fetch a total number of items SQL query error for table = $tableName", e))
        }
      )
    } yield (items, total)

    override def delete(id: String)(implicit r: RequestHeader): UnitRes = for {
      primaryKeyColumnName <- EitherT.fromEither[Future](getPrimaryKeyColumnName)
      idParam <- EitherT.fromEither[Future](idParameter(id))
      whereClause <- assembleWhereClause(deleteFilter, "delete")
      rawQuery = s"DELETE FROM $tableName WHERE $primaryKeyColumnName = {id} $whereClause"
      _ <- EitherT.fromEither[Future](
        db.withConnection { implicit c =>
          val deletedRows = SQL(rawQuery).on(idParam).executeUpdate()
          if (deletedRows > 0)
            ().asRight[RestAPIError]
          else
            RestAPIError.systemError(s"No item has been removed in table = $tableName by id = $id").asLeft
        }
      )
    } yield ()

    private def extractFields(json: JsValue): Res[Seq[(String, String)]] =
      EitherT.fromOption(
        json.asOpt[JsObject].map(
          _.fields.toList.collect{
            case (k, JsString(v)) => k -> v
            case (k, JsNumber(v)) => k -> v.toString
            case (k, JsBoolean(v)) => k -> v.toString
          }
        ),
        RestAPIError.badRequest
      )

    private def extractAllowedFields(
      fields: Seq[(String, String)],
      checkAllowedFunction: Option[RequestHeader => Future[Set[String]]]
    )(implicit r: RequestHeader): Res[Seq[(String, NamedParameter)]] = {
      val fieldsWithColumns = fields
        .flatMap {
          case (k, v) => schema.columns.find(_.name == k).tupleLeft(v) // filter out and keep only ones that match column names
        }

      EitherT(
        OptionT
          .fromOption[Future](checkAllowedFunction)
          .semiflatMap(_(r))
          .map(fieldsAllowed => fieldsWithColumns.filter(f => fieldsAllowed.contains(f._1)))
          .getOrElse(fieldsWithColumns)
          .map {
            _.traverse {
              case (v, column) => namedParameter(column, v).tupleLeft(column.name)
            }
          }
      )
    }

    override def update(id: String, updateModel: JsValue)(implicit r: RequestHeader): UnitRes = for {
      primaryKeyColumnName <- EitherT.fromEither[Future](getPrimaryKeyColumnName)
      idParam <- EitherT.fromEither[Future](idParameter(id))
      updateModelFields <- extractFields(updateModel)
      allowedFieldsWithParams <- extractAllowedFields(updateModelFields, allowedToUpdateFields)
      (fields, namedParams) = allowedFieldsWithParams.unzip
      fieldsSetQuery = fields.map(c => s"$c = {$c}").mkString(", ")
      updateQuery = s"UPDATE $tableName SET $fieldsSetQuery WHERE $primaryKeyColumnName = {id}"
      _ <- EitherT.fromEither[Future](
        db.withConnection { implicit c =>
          val updatedRows = SQL(updateQuery).on(idParam).on(namedParams: _*).executeUpdate()
          if (updatedRows > 0)
            ().asRight[RestAPIError]
          else
            RestAPIError.systemError(s"No item has been updated in table = $tableName by id = $id").asLeft
        }
      )
    } yield ()

    // Rest API Builder for Crud operations
    // rabco
    override def create(model: JsValue)(implicit r: RequestHeader): UnitRes = for {
      createModelFields <- extractFields(model)
      allowedFieldsWithParams <- extractAllowedFields(createModelFields, allowedToCreateFields)
      (fields, namedParams) = allowedFieldsWithParams.unzip
      fieldsListQuery = fields.mkString(", ")
      fieldsValuesQuery = fields.map(c => s"{$c}").mkString(", ")
      query = s"INSERT INTO $tableName($fieldsListQuery) VALUES($fieldsValuesQuery)"
      _ <- EitherT.fromEither[Future](
        db.withConnection { implicit c =>
          val created = SQL(query).on(namedParams: _*).executeUpdate()
          if (created == 1)
            ().asRight[RestAPIError]
          else
            RestAPIError.systemError(s"No item has been inserted in table = $tableName for model = ${Json.stringify(model)}").asLeft
        }
      )
    } yield ()
  }
}

case class TableSchema(primaryKeyColumn: Option[TableColumn], columns: Set[TableColumn])

case class TableColumn(name: String, dataType: String)
