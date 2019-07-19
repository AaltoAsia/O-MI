package database.influxDB

import java.sql.Timestamp


import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try
import scala.language.postfixOps

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging,LoggingAdapter}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling._
import akka.util.Timeout

import database.{DB, SingleStores, SingleStoresMaintainer}
import http.OmiConfigExtension
import types.OmiTypes._
import types.Path
import types.Path._
import types.odf._

class InfluxDBImplementation
(
  protected val settings: OmiConfigExtension
  )(
    implicit val system: ActorSystem,
    protected val singleStores: SingleStores
  ) extends DB with InfluxDBClient {
    require( settings.influx.nonEmpty)
  import InfluxDBClient._
  import InfluxDBImplementation._

  log.info(s"Write address of InfluxDB instance $writeAddress")
  log.info(s"Read address of InfluxDB instance $readAddress")

  import system.dispatcher // execution context for futures
  def log: LoggingAdapter = Logging(system, this)
  implicit val odfJsonFormatter: InfluxDBJsonProtocol.InfluxDBJsonODFFormat = new InfluxDBJsonProtocol.InfluxDBJsonODFFormat()

  def initialize(): Unit = {
    val initialisation = httpResponseToStrict(sendQueries(Vector( ShowDBs )),log).flatMap {
      entity: HttpEntity.Strict =>
        val ent = entity.copy(contentType = `application/json`)

        implicit val showDatabaseFormat: InfluxDBJsonProtocol.InfluxDBJsonShowDatabasesFormat = new InfluxDBJsonProtocol.InfluxDBJsonShowDatabasesFormat()
        Unmarshal(ent).to[Seq[String]].map {
          databases: Seq[String] =>
            log.debug(s"Found following databases: ${databases.mkString(", ")}")
            if (databases.contains(config.databaseName)) {
              //Everything okay
              log.info(s"Database ${config.databaseName} found from InfluxDB at address ${config.address}")
              Future.successful(())
            } else {
              //Create or error
              log.warning(s"Database ${config.databaseName} not found from InfluxDB at address ${config.address}")
              log.warning(s"Creating database ${config.databaseName} to InfluxDB in address ${config.address}")
              sendQueries(Vector(CreateDB(config.databaseName))).flatMap {
                case response@HttpResponse(status, headers, _entity, protocol) if status.isSuccess =>
                  log.info(s"Database ${config.databaseName} created seccessfully to InfluxDB at address ${config.address}")
                  Future.successful(())
                case response@HttpResponse(status, headers, _entity, protocol) if status.isFailure =>
                  _entity.toStrict(10.seconds).flatMap { stricted =>
                    Unmarshal(stricted).to[String].map {
                      str =>
                        log.error(s"Database ${config.databaseName} could not be created to InfluxDB at address ${config.address}")
                        log.warning(s"""InfluxQuery returned $status with:\n $str""")
                        throw new Exception(str)
                    }
                  }
              }

            }
        }
    }

    Await.result(initialisation, 1 minutes)
  }

  initialize()
  val dbmaintainer: ActorRef = system.actorOf(SingleStoresMaintainer.props(singleStores, settings))


  def writeMany(infoItems: Seq[InfoItem]): Future[OmiReturn] = {
    writeManyNewTypes(infoItems)
  }

  def writeManyNewTypes(data: Seq[InfoItem]): Future[OmiReturn] = {
    val response = Future{
      data.flatMap { ii: InfoItem => infoItemToWriteFormat(ii) }
    }.flatMap{
      valuesAsMeasurements: Seq[Measurement] => sendMeasurements(valuesAsMeasurements)
    }
    

    response.failed.foreach {
      t: Throwable =>
        log.error(s"Failed to communicate to InfluxDB: $t")
    }
    response.flatMap {
      case HttpResponse(status, headers, entity, protocol) if status.isSuccess =>
        log.debug("Successful write to InfluxDB")
        Future.successful(OmiReturn(status.value))
      case HttpResponse(status, headers, entity, protocol) if status.isFailure =>
        Unmarshal(entity).to[String].map {
          str =>
            log.warning(s"Write returned $status with:\n $str")
            OmiReturn(status.value, Some(str))
        }
    }
  }

  def getNBetween(
                   nodes: Iterable[Node],
                   begin: Option[Timestamp],
                   end: Option[Timestamp],
                   newest: Option[Int],
                   oldest: Option[Int],
                   maxLevels: Option[Int]
                 )(implicit timeout: Timeout): Future[Option[ODF]] = {
    val iODF = ImmutableODF(nodes)
    getNBetweenNewTypes(iODF, begin, end, newest, oldest, maxLevels)
  }


  /* GetNBetween
   * SELECT * FROM PATH WHERE time < end AND time > begin ORDER BY time DESC LIMIt newest
   *
   */
  def getNBetweenNewTypes(
                           requestODF: ImmutableODF,
                           beginO: Option[Timestamp],
                           endO: Option[Timestamp],
                           newestO: Option[Int],
                           oldestO: Option[Int],
                           maxLevels: Option[Int]
                         ): Future[Option[ImmutableODF]] = {
    if (oldestO.nonEmpty) {
      Future.failed(new Exception("Oldest attribute is not allowed with InfluxDB."))
    } else {
      for {
        cachedODF <- singleStores.getHierarchyTree()
        requestedODF: ODF = cachedODF.selectSubTree(requestODF.getLeafPaths,maxLevels)
        requestedIIs: Seq[InfoItem] = requestedODF.getInfoItems.toSeq
        res: Option[ODF] <- (beginO, endO, newestO) match {
          case (None, None, None) => singleStores.readValues(requestedIIs.map(_.path))
            .mapTo[Seq[(Path, Value[Any])]]
            .map(pathToValue => Some(ImmutableODF(
              pathToValue.map {
                case (path: Path, value: Value[Any]) => InfoItem(path.last, path, values = Vector(value))
              })
              .union(requestedODF)))
          case (bO, eO, nO) => {

            if (requestedIIs.nonEmpty) {
              val queries = createNBetweenInfoItemsQueries( requestedIIs,bO,eO,nO)
              read(queries, requestedODF)
            } else Future.successful(Some(requestedODF))
          }

        }
        response = res.map(_.toImmutable)
      } yield response

    }
  }
  private def read(queries: Seq[InfluxQuery], requestedODF: ODF): Future[Option[ImmutableODF]] = {
    val responseF: Future[HttpResponse] = sendQueries(queries)
    //httpHandler(request)
    val formatedResponse = httpResponseToStrict(responseF,log).flatMap {
      entity: HttpEntity.Strict =>
        val ent = entity.copy(contentType = `application/json`)

        Unmarshal(ent).to[ImmutableODF].map {
          odf: ImmutableODF =>
            log.debug(s"Influx O-DF:\n$odf")
            if (odf.getPaths.size < 2 && requestedODF.getPaths.size < 2) None
            else Some(requestedODF.union(odf).toImmutable)
        }
    }
    formatedResponse.failed.foreach {
      t: Throwable =>
        log.error(s"Failed to communicate to InfluxDB: $t")
        log.warning(t.getStackTrace.mkString("\n"))
    }
    formatedResponse
  }


  def remove(path: Path)(implicit timeout: Timeout): Future[Seq[Int]] = {
    for {
      cachedODF <- singleStores.getHierarchyTree()
      removedIIs: Seq[InfoItem] = cachedODF.selectSubTree(Set(path)).getInfoItems.toSeq
      queries = removedIIs.map {
        ii: InfoItem =>
          val mName = pathToMeasurementName(ii.path)
          DropMeasurement(mName)
      }
      response: HttpResponse <- sendQueries(queries)
      res <- response match {
        case HttpResponse(status, headers, entity, protocol) if status.isSuccess => {
          Future.successful(Seq(status.intValue))
        }
        case HttpResponse(status, headers, entity, protocol) if status.isFailure =>
          Unmarshal(entity).to[String].map {
            str =>
              log.warning(s"Remove returned $status with:\n $str")
              throw new Exception(str)
          }
      }
    } yield res
  }

}

  case class Measurement(measurement: String, value: String, time: Timestamp){
    def formatStr: String = s"$measurement value=$value ${time.getTime}"
  }

object InfluxDBImplementation{

  def infoItemToWriteFormat(ii: InfoItem): Seq[Measurement] = {
    val measurement: String = pathToMeasurementName(ii.path).replace(" ", "\\ ")
    ii.values.map {
      value: Value[Any] =>
        val valueStr: String = value.value match {
          case odf: ImmutableODF => throw new Exception("Having O-DF inside value with InfluxDB is not supported.")
          case num @ (_: Double | _: Float | _: Int | _: Long | _:Short ) => num.toString 
          case str: String => 
            Try{
              str.toInt.toString
            }.orElse{
              Try{str.toDouble.toString}
            }.getOrElse{
              s""""${str.replace("\"", "\\\"")}""""
            }
          case bool: Boolean => bool.toString
          case any: Any => s""""${any.toString.replace("\"", "\\\"")}""""
        }
        Measurement(measurement, valueStr, value.timestamp)
    }
  }

  //Escape all odd parts
  def pathToMeasurementName(path: Path): String = path.toString.replace("=", "\\=").replace(",", "\\,")

  def createNBetweenInfoItemsQueries(
    iis: Iterable[InfoItem],
    beginO: Option[Timestamp],
    endO: Option[Timestamp],
    newestO: Option[Int]
  ): Seq[InfluxQuery] = { 
    val whereClause = createWhereClause( beginO,endO)
    val limitClause = createLimitClause(newestO, beginO, endO)
    iis.map {
      ii: InfoItem =>
        val measurementName = pathToMeasurementName(ii.path)
        SelectValue( measurementName, whereClause,Some(DescTimeOrderByClause()),limitClause)
    }.toVector
  }
  def createWhereClause( 
    beginO: Option[Timestamp],
    endO: Option[Timestamp]
  ): Option[WhereClause] ={
    (beginO, endO) match {
      case (Some(begin), Some(end)) => Some(WhereClause( Vector( LowerTimeBoundExpression(begin),UpperTimeBoundExpression(end))))
      case (None, Some(end)) => Some(WhereClause( Vector( UpperTimeBoundExpression(end))))
      case (Some(begin), None) => Some(WhereClause( Vector( LowerTimeBoundExpression(begin))))
      case (None, None) => None
    }
  }
  def createLimitClause( 
    newestO: Option[Int],
    beginO: Option[Timestamp],
    endO: Option[Timestamp]
  ): Option[LimitClause]  ={
    newestO.map {
      n: Int => LimitClause(n)
    }.orElse {
      if (beginO.isEmpty && endO.isEmpty) Some(LimitClause(1)) else None
    }
  }
}
