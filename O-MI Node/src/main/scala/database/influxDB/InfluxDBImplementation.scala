package database
package influxDB

import java.net.URLEncoder
import java.sql.Timestamp
import java.text.DecimalFormat
import java.util.Date

import scala.math.Numeric
import scala.annotation.tailrec
import scala.collection.immutable.HashMap
import scala.concurrent.{Await, Future}
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

import http.OmiConfigExtension
import types.odf._
import types.OmiTypes._
import types.OdfTypes._
import types.Path
import types.Path._

object InfluxDBJsonProtocol extends DefaultJsonProtocol {

    def getSeries(json: spray.json.JsValue)= json match{
      case obj: JsObject =>
        obj.getFields("results").collect{
          case results: JsArray  =>
            results.elements.collect{
              case statementObj: JsObject =>
                statementObj.getFields("statement_id").headOption match{
                  case Some( id: JsNumber ) =>
                    //log.warning( s"Parsing JSON result for statement $id" )
                }
                statementObj.getFields("series").collect{ 
                  case series: JsArray =>
                    series.elements                      
                }.flatten
            }.flatten
        }.flatten
    }

    def measurementNameToPath( measurementName: String ): Path = Path( measurementName.replace("\\=","=").replace("\\ "," ").replace("\\,",",") )
    class InfluxDBJsonShowDatabasesFormat() extends RootJsonFormat[Seq[String]] {
      def read(json: spray.json.JsValue): Seq[String] ={
        val series: Seq[JsValue] = getSeries(json)
        //println( s"Got ${series.lenght} from show databases" ) 
        val names : Seq[String]= series.collect{
          case serie: JsObject =>

            serie.getFields( "name", "columns", "values") match{
              case Seq(JsString("databases"), JsArray(Seq(JsString("name"))), JsArray( values )) =>
                values.collect{
                  case JsArray(Seq(JsString(dbName))) => dbName
                }.toVector

              case seq: Seq[JsValue] => Vector.empty
            }
        }.flatten
        names
      }
      def write(obj: Seq[String]): spray.json.JsValue = ???
    }
    class InfluxDBJsonShowMeasurementsFormat() extends RootJsonFormat[Seq[Path]] {
      def read(json: spray.json.JsValue): Seq[Path]={
        val names: Seq[Path] = getSeries(json).collect{
          case serie: JsObject =>
            serie.getFields( "name", "columns", "values") match{
              case Seq(JsString("measurements"), JsArray(Seq(JsString("name"))), JsArray( values )) =>
                values.collect{
                  case JsArray(Seq(JsString(strPath))) =>
                    val path = measurementNameToPath(strPath)
                    path
                }.toVector

                  case seq: Seq[JsValue] => Vector.empty
            }
        }.flatten
        names
      }
      def write(obj: Seq[Path]): spray.json.JsValue = ???
    }
    class InfluxDBJsonODFFormat() extends RootJsonFormat[ImmutableODF] {
      // Members declared in spray.json.JsonReader
      def read(json: spray.json.JsValue): types.odf.ImmutableODF ={
        println( s"Got following json: $json")
        val series = getSeries(json)
        println( s"Found ${series.length} series")
        val iis: Seq[InfoItem] = series.collect{
          case serie: JsObject =>
            serie.getFields( "name", "columns", "values") match{
              case Seq(JsString( measurementName), JsArray(columns), JsArray( values )) =>
                Some(serieToInfoItem( serie )) 
              case seq: Seq[JsValue] =>
                None
            }
        }.flatten
        println( s"Found ${iis.length} series")
        ImmutableODF(iis.toVector)
      }

      def serieToInfoItem( serie: JsObject ): InfoItem ={
        serie.getFields( "name", "columns", "values") match{
          case Seq( JsString( measurementName), JsArray(Vector(JsString("time"),JsString("value"))), JsArray( values )) =>
            val path = measurementNameToPath(measurementName)
            InfoItem( path.last, path, values = values.collect{
              case JsArray( Seq(JsString(timestampStr), JsNumber( number )) ) =>
                val timestamp: Timestamp = Timestamp.valueOf(timestampStr.replace("T"," ").replace("Z"," "))
                Value( number, timestamp)
              case JsArray( Seq(JsString(timestampStr), JsBoolean( bool ))) =>
                val timestamp: Timestamp = Timestamp.valueOf(timestampStr.replace("T"," ").replace("Z"," "))
                Value( bool, timestamp)
              case JsArray( Seq(JsString(timestampStr), JsString( str ))) =>
                val timestamp: Timestamp = Timestamp.valueOf(timestampStr.replace("T"," ").replace("Z"," "))
                Value( str.replace("\\\"","\""), timestamp)
            })
        }
      }

      // Members declared in spray.json.JsonWriter
      def write(obj: types.odf.ImmutableODF): spray.json.JsValue = ???
    }
}

class InfluxDBImplementation(
  protected val config: InfluxDBConfigExtension 
)(
  implicit val system: ActorSystem,
  protected val singleStores: SingleStores
) extends DB {
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


  protected val writeAddress: Uri = config.writeAddress //Get from config
  log.info(s"Write address of InfluxDB instance $writeAddress")
  protected val readAddress: Uri = config.queryAddress //Get from config
  log.info(s"Read address of InfluxDB instance $readAddress")

 import system.dispatcher // execution context for futures
 val httpExt = Http(system)
 implicit val mat: Materializer = ActorMaterializer()
 def log = system.log
  def infoItemToWriteFormat( ii: InfoItem ): Seq[String] = {
        val measurement: String = pathToMeasurementName( ii.path)
        ii.values.map{
          value: Value[Any] => 
            val valueStr: String= value.value match {
              case odf: ImmutableODF => throw new Exception("Having O-DF inside value with InfluxDB is not supported.") 
              case str: String => s""""${str.replace("\"","\\\"")}""""
              case num: Numeric[Any] => s"$num" //XXX: may cause issues...
              case bool: Boolean => bool.toString
              case any: Any => s""""${any.toString.replace("\"","\\\"")}""""
            }
            s"$measurement value=$valueStr ${value.timestamp.getTime}"
        }
  }

  def initialize(): Unit = {
    val initialisation = httpResponseToStrict(sendQuery("show databases")).flatMap{
       case entity : HttpEntity.Strict =>
         val ent = entity.copy(contentType =`application/json`)

         implicit val showDatabaseFormat = new InfluxDBJsonProtocol.InfluxDBJsonShowDatabasesFormat()
         Unmarshal(ent).to[Seq[String]].map{
           case databases: Seq[String] =>
             log.debug( s" Found following databases: ${databases.mkString(", ")}")
             if( databases.contains( config.databaseName ) ){
               //Everything okay
               log.info( s"Database ${config.databaseName} found from InfluxDB at address ${config.address}")
                   Future.successful()
             } else {
               //Create or error
               log.warning( s"Database ${config.databaseName} not found from InfluxDB at address ${config.address}")
               log.warning( s"Creating database ${config.databaseName} to InfluxDB in address ${config.address}")
               sendQuery(s"create database ${config.databaseName} ").flatMap{
                 case response @ HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
                    log.info( s"Database ${config.databaseName} created seccessfully to InfluxDB at address ${config.address}")
                   Future.successful()
                 case response @ HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
                   entity.toStrict(10.seconds).flatMap{ stricted => Unmarshal(stricted).to[String].map{
                     str =>
                       log.error( s"Database ${config.databaseName} could not be created to InfluxDB at address ${config.address}")
                       log.warning(s""" Query returned $status with:\n $str""")
                       throw new Exception( str)
                   }}
               }

             }
         }
     }
    
    Await.result( initialisation, 1 minutes)
  }
  initialize()
  def sendQuery( query: String ) : Future[HttpResponse] ={
    val httpEntity = FormData( ("q", query)).toEntity( HttpCharsets.`UTF-8` )
    val request = RequestBuilding.Post(readAddress, httpEntity).withHeaders(AcceptHeader("application/json"))
    val responseF : Future[HttpResponse] = httpExt.singleRequest(request)//httpHandler(request)
    responseF
  }
  def httpResponseToStrict( futureResponse: Future[HttpResponse] ) ={
    futureResponse.flatMap{
       case response @ HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
         entity.toStrict(10.seconds)
       case response @ HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
         entity.toStrict(10.seconds).flatMap{ stricted => Unmarshal(stricted).to[String].map{
           str =>
             log.warning(s""" Query returned $status with:\n $str""")
             throw new Exception( str)
         }}
     }
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
        log.warning(request.toString)
        log.error(t, "Failed to communicate to InfluxDB")
    }
    response.flatMap{
      case HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
        Future.successful( OmiReturn( status.value) )
      case HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
        Unmarshal(entity).to[String].map{
          str =>
            log.warning(s"Write returned $status with:\n $str")
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

  implicit val odfJsonFormatter = new InfluxDBJsonProtocol.InfluxDBJsonODFFormat()
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
      val cachedODF = OldTypeConverter.convertOdfObjects(singleStores.hierarchyStore execute GetTree())
      val requestedODF = cachedODF.select(requestODF)
      val requestedIIs = requestedODF.getInfoItems
      if( beginO.isEmpty && endO.isEmpty && newestO.isEmpty ){
        val pathToValue = singleStores.latestStore execute LookupSensorDatas( requestedIIs.map( _.path ).toVector) 
        val odfWithValues = ImmutableODF(pathToValue.map{
          case ( path: Path, value: OdfValue[Any]) => InfoItem( path.last, path, values = Vector( OldTypeConverter.convertOdfValue(value)))
        })
        Future{ Some(requestedODF.union(odfWithValues).immutable) }
      } else {
        lazy val filteringClause ={
          val whereClause = ( beginO, endO ) match{
            case (Some( begin), Some(end)) => s" WHERE time >= '${begin.toString}' AND time <= '${end.toString}' "
            case (None, Some(end)) => s" WHERE time <= '${end.toString}' "
            case (Some( begin), None) => s" WHERE time >= '${begin.toString}' "
            case (None, None) => ""
          }
          val limitClause = newestO.map{
            n: Int =>
              s" LIMIT $n "
          }.getOrElse{
            if( beginO.isEmpty && endO.isEmpty ) " LIMIT 1 " else ""
          }
          whereClause + " ORDER BY time DESC " + limitClause
        }

        //XXX: What about Objects/ read all?
        if( requestedIIs.nonEmpty ){
          val iiQueries = getNBetweenInfoItemsQueryString(requestedIIs, filteringClause)
          read( iiQueries, requestedODF )
        } else Future{
          Some( requestedODF.immutable )
        }
      }

    }
  }
   private def read(content : String, requestedODF: ODF) = {
    val httpEntity = FormData( ("q", content)).toEntity( HttpCharsets.`UTF-8` )
     val request = RequestBuilding.Post(readAddress, httpEntity).withHeaders(AcceptHeader("application/json"))
     log.debug( s"Sending following request\n${content.toString}")
     val responseF : Future[HttpResponse] = httpExt.singleRequest(request)//httpHandler(request)
     val formatedResponse = responseF.flatMap{
       case response @ HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
         entity.toStrict(10.seconds)
       case response @ HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
         entity.toStrict(10.seconds).flatMap{ stricted => Unmarshal(stricted).to[String].map{
           str =>
             log.warning(s"Read returned $status with:\n $str")
             throw new Exception( str)
         }}
     }.flatMap{
       case entity : HttpEntity.Strict =>
         val ent = entity.copy(contentType =`application/json`)
         //TODO: Parse JSON to ImmutableODF

         Unmarshal(ent).to[ImmutableODF].map{
           case odf: ImmutableODF =>
             log.info( s"Influx O-DF:\n$odf" )
             if( odf.getPaths.length < 2 && requestedODF.getPaths.length < 2) None
             else Some( requestedODF.union(odf).immutable )
         }

     }
     formatedResponse.onFailure{
       case t: Throwable =>
         log.error(t,
           "Failed to communicate to InfluxDB.")
         log.warning(t.getStackTrace().mkString("\n"))
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
         val select = s"""SELECT "value" FROM "$measurementName"""" 
         select + filteringClause
     }
     queries.mkString(";")
   }
   //TODO: Escape all odd parts
   def pathToMeasurementName(path: Path ): String = path.toString.replace("=","\\=").replace(" ","\\ ").replace(",","\\,")
   def remove( path: Path ): Future[Seq[Int]] ={
      val cachedODF = OldTypeConverter.convertOdfObjects(singleStores.hierarchyStore execute GetTree())
      val removedIIs = cachedODF.getSubTreeAsODF(path).getInfoItems
      val query = "q=" + removedIIs.map{
        case ii: InfoItem =>
         val mName = pathToMeasurementName(ii.path)
         s"""DROP MEASUREMENT "$mName""""
      }.mkString(";")

     val request = RequestBuilding.Post(readAddress, query).withHeaders(AcceptHeader("application/json"))
     val responseF : Future[HttpResponse] = httpExt.singleRequest(request)//httpHandler(request)
     responseF.flatMap{
       case HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
         Future{
           singleStores.hierarchyStore execute TreeRemovePath( path)
           removedIIs.map{
              case ii: InfoItem =>1
           }
         }
       case HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
         Unmarshal(entity).to[String].map{
           str =>
             log.warning(s"Remove returned $status with:\n $str")
             throw new Exception( str)
         }
     }
   }

}
