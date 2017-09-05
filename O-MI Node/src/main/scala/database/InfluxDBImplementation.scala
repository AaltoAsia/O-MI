package database
package influxdb

import java.net.URLEncoder
import java.sql.Timestamp
import java.text.DecimalFormat
import java.util.Date

import scala.math.Numeric
import scala.annotation.tailrec
import scala.collection.immutable.HashMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import akka.actor.ActorSystem
import akka.event.slf4j.Logger
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling._
import akka.stream.{ActorMaterializer, Materializer}
import spray.json._

import database._

import types.odf._
import types.OmiTypes._
import types.OdfTypes._
import types.Path
import types.Path._

object InfluxDBJsonProtocol extends DefaultJsonProtocol {

    class InfluxDBJsonFormat(implicit singleStores: SingleStores) extends RootJsonFormat[ImmutableODF] {
      // Members declared in spray.json.JsonReader
      def read(json: spray.json.JsValue): types.odf.ImmutableODF ={
        val iis: Seq[InfoItem] = json match{
          case obj: JsObject =>
            obj.getFields("results").collect{
              case results: JsArray  =>
                results.elements.collect{
                  case statementObj: JsObject =>
                    statementObj.getFields("statement_id").headOption match{
                      case Some( id: JsNumber ) =>
                        //log.debug( s"Parsing JSON result for statement $id" )
                    }
                    statementObj.getFields("series").collect{ 
                      case series: JsArray =>
                        series.elements.collect{
                          case serie: JsObject =>
                            serie.getFields( "name", "columns", "values") match{
                              case Seq(JsString( measurementName), JsArray(columns), JsArray( values )) =>
                                Some(serieToInfoItem( serie )) 
                              case seq: Seq[JsValue] =>
                                None
                            }
                      }.flatten
                    }.flatten
                }.flatten
            }.flatten
        }
        ImmutableODF(iis.toVector)
      }

      def serieToInfoItem( serie: JsObject ): InfoItem ={
        serie.getFields( "name", "columns", "values") match{
          case Seq( JsString( measurementName), JsArray(Vector(JsString("time"),JsString("value"))), JsArray( values )) =>
            val path = measurementNameToPath(measurementName)
            InfoItem( path.last, path, values = values.collect{
              case JsArray( Seq(JsString(timestampStr), JsNumber( number )) ) =>
                val timestamp: Timestamp = Timestamp.valueOf(timestampStr)
                Value( number, timestamp)
              case JsArray( Seq(JsString(timestampStr), JsBoolean( bool ))) =>
                val timestamp: Timestamp = Timestamp.valueOf(timestampStr)
                Value( bool, timestamp)
              case JsArray( Seq(JsString(timestampStr), JsString( str ))) =>
                val timestamp: Timestamp = Timestamp.valueOf(timestampStr)
                Value( str, timestamp)
            })
        }
      }

      // Members declared in spray.json.JsonWriter
      def write(obj: types.odf.ImmutableODF): spray.json.JsValue = ???
      def measurementNameToPath( measurementName: String ): Path = ???
    }
}

/* GetNBetween
 * SELECT * FROM PATH WHERE time < end AND time > begin ORDER BY time DESC LIMIt newest
 *
 */
trait OdfInfluxDBImplementation extends DB {
  final class AcceptHeader(format: String) extends ModeledCustomHeader[AcceptHeader] {
    override def renderInRequests = true
    override def renderInResponses = false
    override val companion = AcceptHeader
    override def value: String = format
  }
  object AcceptHeader extends ModeledCustomHeaderCompanion[AcceptHeader] {
    override val name = "Accept"
    override def parse(value: String) = Try(new AcceptHeader(value))
  }

  protected val singleStores: SingleStores   
  protected val databaseName: String = ??? //Get from config
  protected val userName: String = ??? //Get from config
  protected val userPassword: String = ??? //Get from config
  protected val writeAddress: Uri = ??? //Get from config
  protected val readAddress: Uri = ??? //Get from config

  implicit val system: ActorSystem
 import system.dispatcher // execution context for futures
 val httpExt = Http(system)
 implicit val mat: Materializer = ActorMaterializer()
 def log = system.log
  def infoItemToWriteFormat( ii: InfoItem ): Seq[String] = {
        val measurement: String = pathToMeasurementName( ii.path)
        ii.values.map{
          value: Value[Any] => 
            val valueStr: String= value.value match {
              case str: String => s""""$str""""
              case num: Numeric[Any] => s"$num" //XXX: may cause issues...
              case any: Any => s""""${any.toString}""""
            }
            s"$measurement value=$valueStr ${value.timestamp.getTime}"
        }
  }

  def initialize(): Unit = {
  }
  def writeMany(data: Seq[OdfInfoItem]): Future[OmiReturn] ={
    val iis = data.map{ 
      oii: OdfInfoItem => 
      OldTypeConverter.convertOdfInfoItem(oii)
    }
    writeManyNewTypes(iis)
  }
  def writeManyNewTypes(data: Seq[InfoItem]): Future[OmiReturn] = {
    val valuesAsString = data.flatMap{ case ii: InfoItem => infoItemToWriteFormat(ii) }.mkString("\n")
    val request = RequestBuilding.Post(writeAddress, valuesAsString)//.withHeaders()
    val response = httpExt.singleRequest(request)

    response.onFailure{
      case t : Throwable =>
        log.debug(request.toString)
        log.error(t, "Failed to communicate to InfluxDB")
    }
    response.flatMap{
      case HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
        Future.successful( OmiReturn( status.value) )
      case HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
        Unmarshal(entity).to[String].map{
          str =>
            log.debug(s"$status with:\n $str")
            OmiReturn( status.value, Some(str))
        }
    }

  }
  def getNBetween(
    requests: Iterable[OdfNode],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]): Future[Option[OdfObjects]]={
      val iODF = OldTypeConverter.convertOdfObjects(requests.map( _.createAncestors ).fold(OdfObjects())( _ union _ ))
      getNBetweenNewTypes( iODF, begin, end, newest, oldest).map{
        case result: Option[ImmutableODF] =>
          result.map{
            resultODF: ImmutableODF  => 
              NewTypeConverter.convertODF( resultODF )
          }
      }
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
    oldestO: Option[Int]
  ): Future[Option[ImmutableODF]] = {
    if( oldestO.nonEmpty ){
      Future.failed( new Exception("Oldest attribute is not allowed with InfluxDB."))
    } else {
      lazy val filteringClause ={
        val whereClause = ( beginO, endO ) match{
          case (Some( begin), Some(end)) => s" WHERE time >= $begin AND time <= $end "
          case (None, Some(end)) => s"WHERE time <= $end "
          case (Some( begin), None) => s"WHERE time >= $begin "
          case (None, None) => ""
        }
        val limitClause = s" LIMIT ${newestO.getOrElse(1)} "
        whereClause + "ORDER BY time DESC" + limitClause
      }
      val cachedODF = OldTypeConverter.convertOdfObjects(singleStores.hierarchyStore execute GetTree())
      val requestedIIs = cachedODF.intersection(requestODF).getInfoItems
      //XXX: What about Objects/ read all?
      val iiQueries = getNBetweenInfoItemsQueryString(requestedIIs, filteringClause)
      read( "q=" + iiQueries )

    }
  }
   private def read(content : String) = {
     val request = RequestBuilding.Post(readAddress, content).withHeaders(AcceptHeader("application/json"))
     val responseF : Future[HttpResponse] = httpExt.singleRequest(request)//httpHandler(request)
     val formatedResponse = responseF.flatMap{
       case response @ HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
         entity.toStrict(10.seconds)
       case response @ HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
         entity.toStrict(10.seconds).flatMap{ stricted => Unmarshal(stricted).to[String].map{
           str =>
             log.debug(s"$status with:\n $str")
             throw new Exception( str)
         }}
     }.flatMap{
       case entity : HttpEntity.Strict =>
         val ent = entity.copy(contentType =`application/json`)
         //TODO: Parse JSON to ImmutableODF
         ???
     }
     formatedResponse.onFailure{
       case t: Throwable =>
         log.error(t,
           "Failed to communicate to InfluxDB.")
         log.debug(t.getStackTrace().mkString("\n"))
     }
     formatedResponse
   }

   def getNBetweenInfoItemsQueryString(
     iis: Iterable[InfoItem],
     filteringClause: String 
   ): String= {
     val iisGroupedByParents = iis.groupBy{ ii => ii.path.getParent } 
     val queries = iis.map{
       case ii: InfoItem =>
         val measurementName = pathToMeasurementName( ii.path )
         val select = s"SELECT value FROM $measurementName " 
         select + filteringClause
     }
     queries.mkString(";\n")
   }
   //TODO: Escape all odd parts
   def pathToMeasurementName(path: Path ): String

}
