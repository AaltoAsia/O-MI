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

import scala.annotation.tailrec
import scala.collection.immutable.HashMap
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
import parsing.xmlGen.xmlTypes.{MetaData, QlmID}
import spray.json._
import types.OdfTypes._
import types.Path
import types.OmiTypes.OmiReturn
import Warp10JsonProtocol.Warp10JsonFormat
//serializer and deserializer for warp10 json formats
object Warp10JsonProtocol extends DefaultJsonProtocol {

  implicit object Warp10JsonFormat extends RootJsonFormat[Seq[(OdfObject,OdfObject)]] {
    //def warp10MetaData(path: Path) = Some(MetaData(OdfInfoItem(path / "type",OdfTreeCollection(OdfValue("ISO 6709","xs:String",new Timestamp(1470230717254L)))).asInfoItemType))

    def getObject(path: Path): OdfObject = {
      val hTree = SingleStores.hierarchyStore execute GetTree()
      hTree.get(path.init) match {
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
                         in: (OdfTreeCollection[OdfValue], OdfTreeCollection[Option[OdfValue]])): (OdfTreeCollection[OdfInfoItem], OdfTreeCollection[OdfInfoItem]) = {
      val infoItemPath = path

      val locations = {
        val locs = in._2.flatten.sortBy(_.timestamp.getTime)
        if(locs.isEmpty) None
        else Some(locs)
      }
      val locationInfoItem = locations.map( locValues => OdfInfoItem(Path(infoItemPath.init)  / "location", locValues))

      val result = OdfInfoItem(infoItemPath, in._1.sortBy(_.timestamp.getTime()))//, metaData = metaDatas)

      (OdfTreeCollection(result), OdfTreeCollection(locationInfoItem).flatten)

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
        case (Some(latilong), el) => Some(OdfValue(s"$latilong${el.getOrElse("")}/","xs:String", timestamp))
        case (_, Some(el)) => Some(OdfValue(s"$el/","xs:String", timestamp))
        case _ => None
      }

    }

    def createOdfValue(
                                value: JsValue,
                                _timestamp: BigDecimal,
                                lat: Option[BigDecimal],
                                lon: Option[BigDecimal],
                                elev: Option[BigDecimal],
                                typeVal: HashMap[String, String]): (OdfValue, Option[OdfValue]) = {

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
    def parseObjects(in: Seq[JsObject]): Seq[(OdfObject, OdfObject)] = in match {
       case jsObjs: Seq[JsObject] => {
         val idPathValuesTuple = jsObjs.map { jsobj =>
           val path = fromField[Option[String]](jsobj, "c")
           val labels = fromField[Option[JsObject]](jsobj,"l")
           val vals = fromField[JsArray](jsobj,"v")
           val id = fromField[Option[String]](jsobj,"i")

          //edit this to add support for different kinds of labels
          val typeVal: HashMap[String, String] = labels match {
            case Some(obj) => fromField[Option[String]](obj, "type") match {
              case Some(typev) => HashMap("type" -> typev)
              case None => HashMap.empty
            }
            case None => HashMap.empty
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
              val parentObj = getObject(path) //TODO what happens if not in hierarchystore
              val (infoItems, locations) = createInfoItems(path, _infoItems.unzip)

              (parentObj.copy(infoItems=infoItems), parentObj.copy(infoItems=locations))
            }
            case _ => throw new DeserializationException("No Path found when deserializing")
          }

          case (Some(id), ii) => {

            val path = Path(ii.collectFirst{ case (_, Some(p),_) => p}
              .getOrElse(throw new DeserializationException("Was not able to match id to path while deserializing"))
              .replaceAll("\\.", "/"))

            val parentObj = getObject(path) //TODO what happens if not in hierarchystore
            //val infoItems = createInfoItems(path, infoItems)

            val (infoItems, locations) = createInfoItems(
              path,
              ii.foldLeft(Vector[(OdfValue, Option[OdfValue])]())((col ,next ) => col ++ next._3).unzip)

            Seq((parentObj.copy(infoItems = infoItems), parentObj.copy(infoItems = locations)))

          }
          case _ => throw new DeserializationException("Unknown format")
        }(collection.breakOut).flatten

        infoIs
      }
    }

    def read(v: JsValue): Seq[(OdfObject, OdfObject)] = v match {
      case JsArray(Vector(JsArray(in: Vector[JsObject]))) => parseObjects(in) //sometimes a array of arrays?
      case JsArray(in: Vector[JsObject]) => parseObjects(in)
      case _ => throw new DeserializationException("Unknown format")
    }
    def write(o: Seq[(OdfObject, OdfObject)]): JsValue = ??? //not in use



  }

}

class Warp10Wrapper( settings: Warp10ConfigExtension )(implicit system: ActorSystem = ActorSystem()) extends DB {
  import Warp10JsonProtocol.Warp10JsonFormat._
  type Warp10Token = String

  val locationRegex = """([+-]\d\d\.\d*)?([+-]\d\d\d\.\d*)?(?:([+-]\d*)CRSWGS_84)?\/""".r

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
   val locationsSeparated = requests.groupBy(_.path.last == "location")
   val locations: Map[Path, OdfNode] = locationsSeparated
     .get(true)
     .toSeq
     .flatten
     .groupBy(p => Path(p.path.init))
     .mapValues(_.head)
   val requestWithoutLocations = locationsSeparated.get(false).toSeq.flatten
   oldest match {
     case Some(a) => Future.failed( new Exception("Oldest is not supported, since 29.6.2016"))
     case None =>
       val selector = nodesToReadPathSelector(requestWithoutLocations)
       val contentFuture : Future[String] = (begin, end, newest) match {
         case (None, None, None) =>
           Future.successful( warpReadNBeforeMsg(selector, 1, None)(readToken) )
         case (None, endTime, Some(sticks)) =>
           Future.successful( warpReadNBeforeMsg(selector,sticks, end)(readToken) )
         case (startTime, endTime, None) =>
           Future.successful( warpReadBetweenMsg(selector,begin, end)(readToken) )
         case (startTime, endTime, sticks) =>
           Future.failed( new Exception(s"Unsupported combination ($startTime, $endTime, $sticks), since 29.6.2016"))
       }
       contentFuture.flatMap{
         content =>
           read( content, locations)
        }
    }
 }
 private def read(content : String, requiredLocations: Map[Path,OdfNode]) = {
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
            Unmarshal(ent).to[Seq[(OdfObject, OdfObject)]].map{
              case infos if infos.isEmpty=>
                None
              case (infos) =>
                Some(infos.collect{
                  case (info, location) if requiredLocations.contains(Path(info.path)) => info combine location
                  case (info, _ ) => info
                }.map(createAncestors).foldLeft(OdfObjects())( _ union _ ))
            }
        }
 }

  private def findClosestLocation(path: Path, locs: Map[Path,Seq[OdfInfoItem]]): Option[OdfInfoItem] = {
     @tailrec def inner(iPath: Path):Option[Seq[OdfInfoItem]] = {
       if(iPath.length <= 1)
         None
       else{
         locs.get(iPath / "location") match {
           case res @ Some(_) => res
           case _ => inner(iPath.init)
         }
       }
     }
     inner(path.init).flatMap(_.headOption)//should only contain 1 location per path
   }

 def writeMany(infos: Seq[OdfInfoItem]): Future[OmiReturn] ={
   val hTree = SingleStores.hierarchyStore execute GetTree()
   val grouped = infos.groupBy(_.path.lastOption.exists(_ == "location"))

   val locations = grouped.get(true).map(_.groupBy(_.path))
   val newInfos = grouped.get(false).toSeq.flatten // Option[Seq[InfoItem]] -> Seq[Seq[InfoItem]] -> Seq[InfoItem]

   val data = newInfos.flatMap( ii =>
     ii.values.map(value =>
       (
         ii.path,
         value,
         for{
           locs <- locations
           closest <- findClosestLocation(ii.path, locs)
           matched <- closest.values.find(_.timestamp.getTime == value.timestamp.getTime)
           res = matched.value.toString
         } yield res
         )
     )
   )

   val content = data.map{
    case (path, odfValue, location) =>
    toWriteFormat(path,odfValue, location)
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
  private def warp10MetaData(path: Path) = Some(MetaData(OdfInfoItem(path / "type",OdfTreeCollection(OdfValue("ISO 6709","xs:String",new Timestamp(1470230717254L),HashMap.empty))).asInfoItemType))
  private def toWriteFormat( path: Path, odfValue : OdfValue, location: Option[String]) : String = {
   def handleString(in: Any): String = {
     val str = URLEncoder.encode(in.toString, "UTF-8")

     s"'$str'"
   }
   val unixEpochTime = odfValue.timestamp.getTime * 1000
   val pathJS = path.mkString(".")
   val typeValue = odfValue.typeValue
   val labels = s"{ type=$typeValue }"

   val matchResult = location.flatMap(loc => locationRegex.findFirstMatchIn(loc))
   val loc = matchResult match {
     case Some(res) => {
       SingleStores.hierarchyStore execute Union(createAncestors(OdfInfoItem(Path(path.init) / "location", metaData = warp10MetaData(path))))
       (Option(res.group(1)),Option(res.group(2)),Option(res.group(3))) match {
         case (None, None, Some(elev)) => s"/${elev.toLong}"
         case (Some(lat), Some(lon), None) => s"${lat.toDouble}:${lon.toDouble}/"
         case (Some(lat), Some(lon), Some(elev)) => s"${lat.toDouble}:${lon.toDouble}/${elev.toLong}"
         case _ => {
           log.warning(s"Invalid format for location${location}")
           "/"
         }
       }
     }
     case none => {
       "/"
     }
   }

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

   val result = s"$unixEpochTime/$loc $pathJS$labels $value\n"
   //log.debug(s"sent message: $result")
   result
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

 def currentEpoch = new Timestamp( new Date().getTime ).getTime * 1000
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
