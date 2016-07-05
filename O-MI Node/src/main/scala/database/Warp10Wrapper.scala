/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package database

import java.net.URLEncoder
import java.sql.Timestamp
import java.text.DecimalFormat
import java.util.Date

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

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
import parsing.xmlGen.xmlTypes.QlmID
import spray.json._
import types.OdfTypes._
import types.Path
import types.OmiTypes.OmiReturn
import Warp10JsonProtocol.Warp10JsonFormat
//serializer and deserializer for warp10 json formats
object Warp10JsonProtocol extends DefaultJsonProtocol {

  implicit object Warp10JsonFormat extends RootJsonFormat[Seq[OdfObject]] {
    val hTree = SingleStores.hierarchyStore execute GetTree()
    def getObject(path: Path): OdfObject = {
      hTree.get(path) match {
        case Some(obj: OdfObject) => obj.copy(infoItems = OdfTreeCollection.empty,objects = OdfTreeCollection.empty)
        case _ => {
          val id = OdfTreeCollection(QlmID(path.lastOption.getOrElse(
            throw new DeserializationException(s"found invalid path for Object: $path"))))

          OdfObject(id,path)
        }
      }
    }
    def createInfoItems(
                         path: Path,
                         in: (OdfTreeCollection[OdfValue], OdfTreeCollection[Option[OdfValue]])): OdfTreeCollection[OdfInfoItem] = {
      val first = OdfInfoItem(path / "values",in._1.sortBy(_.timestamp.getTime()))
      val second = OdfInfoItem(path / "locations", in._2.flatten.sortBy(_.timestamp.getTime()))

      if(second.values.isEmpty)
        OdfTreeCollection(first)
      else
        OdfTreeCollection(first, second)
    }

    //formatters
    val longitudeFormatter = new DecimalFormat()
    val latitudeFormatter = new DecimalFormat()
    val elevationFormatter = new DecimalFormat()
    val coordinateFormat = "CRSWGS_84"

    longitudeFormatter.setPositivePrefix("+")
    longitudeFormatter.setMaximumFractionDigits(Int.MaxValue)
    longitudeFormatter.setMinimumIntegerDigits(3)
    longitudeFormatter.setMaximumIntegerDigits(3)

    latitudeFormatter.setPositivePrefix("+")
    latitudeFormatter.setMaximumFractionDigits(Int.MaxValue)
    latitudeFormatter.setMinimumIntegerDigits(2)
    latitudeFormatter.setMaximumIntegerDigits(2)

    elevationFormatter.setPositivePrefix("+")
    elevationFormatter.setPositiveSuffix(coordinateFormat)
    elevationFormatter.setNegativeSuffix(coordinateFormat)
    elevationFormatter.setGroupingUsed(false)
    /*
    private def createOdfValue(value: JsValue,_timestamp: BigDecimal, typeVal: Map[String, String]): OdfValue = {
      val timestamp = new Timestamp((_timestamp/1000).toLong)
      value match {
        case JsString(v) => {
          typeVal.get("type") match {
            case Some(dataType) => OdfValue(v, dataType, timestamp, typeVal - "type")
            case None => OdfValue(v, "xs:string", timestamp, typeVal)
          }
        }
        case JsBoolean(v) => OdfValue(v, timestamp, typeVal - "type")
        case JsNumber(n) => {
          if (n.ulp == 1) //if no decimal separator, parse to long
            OdfValue(n.toLong, timestamp, typeVal - "type")
          else
            OdfValue(n.toDouble, timestamp, typeVal - "type")
        }
        case _ => throw new DeserializationException("Invalid type, could not cast into string, boolean, or number")
      }
    }

    private def parseObjects(in: Seq[JsObject]) = in match {
      case jsObjs: Seq[JsObject] => {
        val idPathValuesTuple = jsObjs.map { jsobj =>
          val path = fromField[Option[String]](jsobj, "c")
          val labels = fromField[Option[JsObject]](jsobj,"l")
          val vals = fromField[JsArray](jsobj,"v")
          val id = fromField[Option[String]](jsobj,"i")

          //edit this to add support for different kinds of labels
          val typeVal: Map[String, String] = labels match {
            case Some(obj) => fromField[Option[String]](obj, "type") match {
              case Some(typev) => Map("type" -> typev)
              case None => Map.empty
            }
            case None => Map.empty
          }
          //typevalues are strings because of format "LAT:LON/ELEV VALUE"
          val values: OdfTreeCollection[OdfValue] = vals match {
            case JsArray(valueVectors: Vector[JsArray]) => valueVectors.collect{
              case JsArray(Vector(JsNumber(timestamp), value: JsValue)) =>{
                createOdfValue(value,timestamp, typeVal)
              }

              case JsArray(Vector(JsNumber(timestamp), JsNumber(elev), value: JsValue)) =>
                createOdfValue(value, timestamp, typeVal + ("elev" -> elev.toString()))

              case JsArray(Vector(JsNumber(timestamp), JsNumber(lat), JsNumber(lon), value: JsValue)) =>
                createOdfValue(value, timestamp, typeVal ++ Map("lat" -> lat.toString(), "lon" -> lon.toString()))

              case JsArray(Vector(JsNumber(timestamp), JsNumber(lat), JsNumber(lon), JsNumber(elev), value: JsValue)) =>
                createOdfValue(value, timestamp, typeVal ++ Map("lat" -> lat.toString(), "lon" -> lon.toString(), "elev" -> elev.toString()))
    }
          }

          (id, path , values)

        }
        val infoIs:Seq[OdfInfoItem] = idPathValuesTuple.groupBy(_._1).collect{
          case (None , ii) => ii.map{
            case (_, Some(_path), c) => OdfInfoItem(Path(_path.replaceAll("\\.", "/")), c.sortBy(_.timestamp.getTime()))
            case _ => throw new DeserializationException("No Path found when deserializing")
          }

          case (Some(id), ii) => {
            val _path = ii.collectFirst{ case (_, Some(p),_) => p}
              .getOrElse(throw new DeserializationException("Was not able to match id to path while deserializing"))

            val _values = ii.foldLeft(OdfTreeCollection[OdfValue])((col ,next ) => col ++ next._3)

            Seq(OdfInfoItem(Path(_path.replaceAll("\\.", "/")), _values.sortBy(_.timestamp.getTime())))
          }
          case _ => throw new DeserializationException("Unknown format")
        }(collection.breakOut).flatten

        infoIs
      }
    }

    def read(v: JsValue): Seq[OdfInfoItem] = v match {
      case JsArray(Vector(JsArray(in: Vector[JsObject]))) => parseObjects(in)
      case JsArray(in: Vector[JsObject]) => parseObjects(in)
      case _ => throw new DeserializationException("Unknown format")
    }
    */
    def createLocationValue(
                             timestamp: Timestamp,
                             latitude:Option[BigDecimal],
                             longitude:Option[BigDecimal],
                             elevation: Option[BigDecimal]): Option[OdfValue] = {
      val latlon = for {
        lat <- latitude
        lon <- longitude
        res = latitudeFormatter.format(lat)+longitudeFormatter.format(lon)
      } yield res

      val elev = elevation.map(e=> elevationFormatter.format(e))

      (latlon, elev) match {
        case (Some(latilong), el) => Some(OdfValue(s"$latilong${el.getOrElse("")}/","ISO 6709", timestamp))
        case (_, Some(el)) => Some(OdfValue(s"$el/","ISO 6709", timestamp))
        case _ => None
      }

    }

    def createOdfValue(
                                value: JsValue,
                                _timestamp: BigDecimal,
                                lat: Option[BigDecimal],
                                lon: Option[BigDecimal],
                                elev: Option[BigDecimal],
                                typeVal: Map[String, String]): (OdfValue, Option[OdfValue]) = {
      val timestamp = new Timestamp((_timestamp/1000).toLong)
      val warp10Value = value match {
        case JsString(v) => {
          typeVal.get("type") match {
            case Some(dataType) => OdfValue(v, dataType, timestamp, typeVal - "type")
            case None => OdfValue(v, "xs:string", timestamp, typeVal)
          }
        }
        case JsBoolean(v) => OdfValue(v, timestamp, typeVal - "type")
        case JsNumber(n) => {
          if (n.ulp == 1) //if no decimal separator, parse to long
            OdfValue(n.toLong, timestamp, typeVal - "type")
          else
            OdfValue(n.toDouble, timestamp, typeVal - "type")
        }
        case _ => throw new DeserializationException("Invalid type, could not cast into string, boolean, or number")
      }

      (warp10Value, createLocationValue(timestamp, lat,lon,elev))
    }
    def parseObjects(in: Seq[JsObject]): Seq[OdfObject] = in match {
       case jsObjs: Seq[JsObject] => {
         val idPathValuesTuple = jsObjs.map { jsobj =>
           val path = fromField[Option[String]](jsobj, "c")
           val labels = fromField[Option[JsObject]](jsobj,"l")
           val vals = fromField[JsArray](jsobj,"v")
           val id = fromField[Option[String]](jsobj,"i")

          //edit this to add support for different kinds of labels
          val typeVal: Map[String, String] = labels match {
            case Some(obj) => fromField[Option[String]](obj, "type") match {
              case Some(typev) => Map("type" -> typev)
              case None => Map.empty
            }
            case None => Map.empty
          }

           //parse JsonArray to matching different length arrays contain location, elevation, both or neither
          val values: Vector[(OdfValue, Option[OdfValue])] = vals match {
            case JsArray(valueVectors: Vector[JsArray]) => valueVectors.collect{
              case JsArray(Vector(JsNumber(timestamp), value: JsValue)) =>{
                createOdfValue(value, timestamp, None, None, None, typeVal)
              }

              case JsArray(Vector(JsNumber(timestamp), JsNumber(elev), value: JsValue)) =>
                createOdfValue(value, timestamp, None, None, Some(elev), typeVal)

              case JsArray(Vector(JsNumber(timestamp), JsNumber(lat), JsNumber(lon), value: JsValue)) =>
                createOdfValue(value, timestamp, Some(lat), Some(lon), None,  typeVal)

              case JsArray(Vector(JsNumber(timestamp), JsNumber(lat), JsNumber(lon), JsNumber(elev), value: JsValue)) =>
                createOdfValue(value, timestamp, Some(lat), Some(lon), Some(elev), typeVal)
            }
          }

          (id, path , values)

        }
        val infoIs = idPathValuesTuple.groupBy(_._1).collect {
          case (None , ii) => ii.map{
            case (_, Some(_path), _infoItems ) => {
              val path = Path(_path.replaceAll("\\.", "/"))
              val parentObj = getObject(path)
              val infoItems = createInfoItems(path, _infoItems.unzip)
              parentObj.copy(infoItems=infoItems)
            }
            case _ => throw new DeserializationException("No Path found when deserializing")
          }

          case (Some(id), ii) => {

            val path = Path(ii.collectFirst{ case (_, Some(p),_) => p}
              .getOrElse(throw new DeserializationException("Was not able to match id to path while deserializing"))
              .replaceAll("\\.", "/"))

            val parentObj = getObject(path)
            //val infoItems = createInfoItems(path, infoItems)

            val infoItems = createInfoItems(
              path,
              ii.foldLeft(Vector[(OdfValue, Option[OdfValue])]())((col ,next ) => col ++ next._3).unzip)

            Seq(parentObj.copy(infoItems = infoItems))

            //Seq(OdfInfoItem(Path(_path.replaceAll("\\.", "/")), _values.sortBy(_.timestamp.getTime())))
          }
          case _ => throw new DeserializationException("Unknown format")
        }(collection.breakOut).flatten

        infoIs
      }
    }

    def read(v: JsValue): Seq[OdfObject] = v match {
      case JsArray(Vector(JsArray(in: Vector[JsObject]))) => parseObjects(in) //sometimes a array of arrays?
      case JsArray(in: Vector[JsObject]) => parseObjects(in)
      case _ => throw new DeserializationException("Unknown format")
    }
    def write(o: Seq[OdfObject]): JsValue = ??? //not in use



  }

}

class Warp10Wrapper( settings: Warp10ConfigExtension )(implicit system: ActorSystem = ActorSystem()) extends DB {
  import Warp10JsonProtocol.Warp10JsonFormat._
  type Warp10Token = String
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
  final class Warp10TokenHeader(token: Warp10Token) extends ModeledCustomHeader[Warp10TokenHeader] {
    override def renderInRequests = true
    override def renderInResponses = false
    override val companion = Warp10TokenHeader
    override def value: String = token
  }
  object Warp10TokenHeader extends ModeledCustomHeaderCompanion[Warp10TokenHeader] {
    override val name = "x-warp10-token"
    override def parse(value: String) = Try(new Warp10TokenHeader(value))
  }

 def warpAddress : String = settings.warp10Address 
 def writeAddress : Uri = Uri( warpAddress + "update")
 def readAddress : Uri = Uri( warpAddress + "exec")
 implicit val readToken : Warp10Token = settings.warp10ReadToken
 implicit val writeToken : Warp10Token = settings.warp10WriteToken
 
 import system.dispatcher // execution context for futures
 val httpExt = Http(system)
 implicit val mat: Materializer = ActorMaterializer()
 def log = system.log
 def getNBetween(
    requests: Iterable[OdfNode],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]
  ): Future[Option[OdfObjects]] = {
    oldest match {
      case Some(a) => Future.failed( new Exception("Oldest is not supported, since 29.6.2016"))
      case None =>
        val selector = nodesToReadPathSelector(requests)
        val contentFuture : Future[String] = (begin, end, newest) match {
          case (None, None, None) =>
            Future.successful( warpReadNBeforeMsg(selector, 1, None)(readToken) )
          case (None, endTime, Some(sticks)) => 
            Future.successful( warpReadNBeforeMsg(selector,sticks, end)(readToken) )
          case (startTime, endTime, None) => 
            Future.successful( warpReadBetweenMsg(selector,begin, end)(readToken) )
          case (startTime, endTime, sticks) => 
             Future.failed( new Exception(s"Unsupported compination ($startTime, $endTime, $sticks), since 29.6.2016"))
        } 
        contentFuture.flatMap{
          content => 
            read( content)
        }
    }
 }
 private def read(content : String ) = { 
        val request = RequestBuilding.Post(readAddress, content).withHeaders(AcceptHeader("application/json"))
        val responseF : Future[HttpResponse] = httpExt.singleRequest(request)//httpHandler(request)
        responseF.onFailure{
          case t : Throwable => 
            log.error(t, "Failed to communicate to Warp 10.")
        }
        responseF.flatMap{
          case response @ HttpResponse( status, headers, entity, protocol ) if status.isSuccess =>
            entity.toStrict(10.seconds)
          case response @ HttpResponse( status, headers, entity, protocol ) if status.isFailure =>
            Unmarshal(entity).to[String].map{ 
              str => 
                log.debug(s"$status with:\n $str")
                throw new Exception( str)
            }
        }.flatMap{
          case entity : HttpEntity.Strict =>
            //Ugly fix, for wrong/missing content type.
            val ent = entity.copy(contentType = `application/json`) 
            Unmarshal(ent).to[Seq[OdfObject]].map{
              case infos if infos.isEmpty=> 
                None
              case infos => 
                Some(infos.map(createAncestors).foldLeft(OdfObjects())( _ union _ ))
            }
        }
 }

 def writeMany(data: Seq[(Path, OdfValue)]): Future[OmiReturn] ={
   val content = data.map{
    case (path, odfValue) =>
    toWriteFormat(path,odfValue)
   }.mkString("")
   val request = RequestBuilding.Post(writeAddress, content).withHeaders(Warp10TokenHeader(writeToken))

   val response = httpExt.singleRequest(request)//httpHandler(request)
    response.onFailure{
      case t : Throwable => 
        log.debug(request.toString)
        log.error(t, "Failed to communicate to Warp 10.")
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
  def remove(path: Path): Future[Int] = ???

 private def toWriteFormat( path: Path, odfValue : OdfValue ) : String = {
   def handleString(in: Any): String = {
     val str = URLEncoder.encode(in.toString, "UTF-8") //might cause problems if encoded twice

     s"'$str'"
   }
   val unixEpochTime = odfValue.timestamp.getTime * 1000
   val pathJS = path.mkString(".") 
   val typeValue = odfValue.typeValue
   val labels = s"{ type=$typeValue }"
   val latlon = for {
     lat <- odfValue.attributes.get("lat")
     lon <- odfValue.attributes.get("lon")
     res = lat + ":" + lon
   } yield res
   val elevation = odfValue.attributes.getOrElse("elev", "")

   val value =
     if( odfValue.isNumeral )
       odfValue.value
     else {
       val resString = odfValue.typeValue match {
         case "xs:boolean" => odfValue.value match {
           case b: Boolean => if(b) "T" else "F"
           case default: Any => handleString(default)
         }
         case _ => handleString(odfValue.value)
       }

       s"$resString"
     }

   s"$unixEpochTime/${latlon.getOrElse("")}/$elevation $pathJS$labels $value\n"
 }
 
 private def nodesToReadPathSelector( nodes : Iterable[OdfNode] ) = {
   val paths = nodes.map{ 
     case obj : OdfObject => obj.path.mkString(".") + ".*"
     case objs : OdfObjects => objs.path.mkString(".") + ".*" 
     case info : OdfInfoItem => info.path.mkString(".") 
   }
   "~(" + paths.mkString("|") + ")"
 }
 
 
 private def warpReadNBeforeMsg(
   pathSelector: String,
   sticks: Int,
   start: Option[Timestamp]
 )(
   implicit readToken: Warp10Token
 ): String = {
   val epoch = start.map{ ts => (ts.getTime * 1000).toString }.getOrElse("NOW")
   s"""[
   '$readToken'
   '$pathSelector'
   {}
   $epoch
   -$sticks
   ] FETCH"""
 }

 def currentEpoch = new Timestamp( new Date().getTime ).getTime *1000
 private def warpReadBetweenMsg(
   pathSelector: String,
   begin: Option[Timestamp],
   end: Option[Timestamp]
   )(
     implicit readToken: Warp10Token
   ): String = {
   val startEpoch=  end.map{
    time => time.getTime * 1000
   }.getOrElse( currentEpoch )
   val timespan =  begin.map{
    time => startEpoch - time.getTime * 1000 
   }.getOrElse( startEpoch )
   s"""[
   '$readToken'
   '$pathSelector'
   {}
   $startEpoch
   $timespan
   ] FETCH"""
 }
}
