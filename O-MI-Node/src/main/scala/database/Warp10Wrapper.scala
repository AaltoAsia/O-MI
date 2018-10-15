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
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import akka.actor.ActorSystem
import akka.event.slf4j.Logger
import akka.pattern.ask
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling._
import akka.stream.{ActorMaterializer, Materializer}
import spray.json._
import http.OmiConfigExtension
import types.OdfTypes._
import types.{ParseError, Path, Warp10ParseError}
import types.OmiTypes.{OmiReturn, Returns}
import Warp10JsonProtocol.Warp10JsonFormat
import akka.util.Timeout
import types.OdfTypes.OdfQlmID
import types.odf.{ImmutableODF, InfoItem, MetaData, NewTypeConverter, Node, ODF, Object, Objects, OldTypeConverter, Value}

import scala.concurrent.ExecutionContext.Implicits.global
//serializer and deserializer for warp10 json formats
object Warp10JsonProtocol extends DefaultJsonProtocol {

  class Warp10JsonFormat(implicit singleStores: SingleStores) extends RootJsonFormat[Seq[(OdfObject,OdfObject)]] {
    //def warp10MetaData(path: Path) = Some(MetaData(OdfInfoItem(path / "type",OdfTreeCollection(OdfValue("ISO 6709","xs:String",new Timestamp(1470230717254L)))).asInfoItemType))

    def getObject(path: Path) : Future[OdfObject] = {
      for{
        hTree <- singleStores.getHierarchyTree()
        res = hTree.get(path.init) match {
          case Some(obj: Object) => NewTypeConverter.convertObject(obj)
          case Some(obj: OdfObject) => obj.copy(infoItems = OdfTreeCollection.empty,objects = OdfTreeCollection.empty)
          case _ => {
            val id =
              OdfTreeCollection(OdfQlmID(path.lastOption.getOrElse(throw new DeserializationException(s"Found invalid path for Object: $path"))))
            OdfObject(id,path)
        }
      }
    } yield res
    }

    def createInfoItems(
                         path: Path,
                         in: (OdfTreeCollection[OdfValue[Any]], OdfTreeCollection[Option[OdfValue[Any]]])): (OdfTreeCollection[OdfInfoItem], OdfTreeCollection[OdfInfoItem]) = {
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
                             elevation: Option[BigDecimal]): Option[OdfValue[Any]] = {
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
                                typeVal: HashMap[String, String]): (OdfValue[Any], Option[OdfValue[Any]]) = {

      val timestamp = new Timestamp((_timestamp/1000).toLong)
      val warp10Value = value match {
        case JsString(v) => {
          typeVal.get("type") match {
            case Some(dataType) => OdfValue(v, dataType, timestamp, typeVal - "type")
            case None => OdfValue(v, "xs:string", timestamp, typeVal)
          }
        }
        case JsBoolean(v) => OdfBooleanValue(v, timestamp)
        case JsNumber(n) => {
          if (n.ulp == 1) //if no decimal separator, parse to long
            OdfValue(n.toLong, timestamp)
          else
            OdfValue(n.toDouble, timestamp)
        }
        case _ => throw new DeserializationException("Invalid type, could not cast into string, boolean, or number")
      }

      (warp10Value, createLocationValue(timestamp, lat,lon,elev))
    }
    def parseObjects(in: Seq[JsObject])(implicit singleStores: SingleStores): Future[Seq[(OdfObject, OdfObject)]] = in match {
       case jsObjs: Seq[JsObject] => {
         val idPathValuesTuple: Seq[(Option[String], Option[String], Vector[(OdfValue[Any], Option[OdfValue[Any]])])] = jsObjs.map { jsobj =>
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
          val values: Vector[(OdfValue[Any], Option[OdfValue[Any]])] = vals match {
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
        val infoIs: Seq[Future[(OdfObject, OdfObject)]] = idPathValuesTuple.groupBy(_._1).collect {
          case (None , ii) => ii.map{
            case (_, Some(_path), _infoItems ) => {
              val path = Path(_path)
              val parentObjF: Future[OdfObject] = getObject(path) //TODO what happens if not in hierarchystore
              val (infoItems, locations) = createInfoItems(path, _infoItems.unzip)
              parentObjF.map(parentObj => (parentObj.copy(infoItems=infoItems), parentObj.copy(infoItems=locations)))
            }
            case _ => throw new DeserializationException("No Path found when deserializing")
          }

          case (Some(id), ii) => {

            val path = Path(ii.collectFirst{ case (_, Some(p),_) => p}
              .getOrElse(throw new DeserializationException("Was not able to match id to path while deserializing")))

            val parentObjF = getObject(path) //TODO what happens if not in hierarchystore
            //val infoItems = createInfoItems(path, infoItems)

            val (infoItems, locations) = createInfoItems(
              path,
              ii.foldLeft(Vector[(OdfValue[Any], Option[OdfValue[Any]])]())((col ,next ) => col ++ next._3).unzip)

            Seq(parentObjF.map(parentObj=> (parentObj.copy(infoItems = infoItems), parentObj.copy(infoItems = locations))))

          }
          case _ => throw new DeserializationException("Unknown format")
        }(collection.breakOut).flatten

        Future.sequence(infoIs)
      }
    }

    def read(v: JsValue): Seq[(OdfObject, OdfObject)] = v match {
      case JsArray(Vector(JsArray(in: Vector[JsObject]))) => Await.result(parseObjects(in),30 seconds) //sometimes a array of arrays?
      case JsArray(in: Vector[JsObject]) => Await.result(parseObjects(in), 30 seconds)
      case _ => throw new DeserializationException("Unknown format")
    }
    def write(o: Seq[(OdfObject, OdfObject)]): JsValue = ??? //not in use



  }

}

class Warp10Wrapper( settings: OmiConfigExtension with Warp10ConfigExtension )(implicit val system: ActorSystem, val singleStores: SingleStores) extends DB {
  def initialize(): Unit = Unit

 val singleStoresMaintainer = system.actorOf(SingleStoresMaintainer.props(singleStores, settings), "SingleStoreMaintainer")
  type Warp10Token = String
  implicit val forwatter : Warp10JsonFormat = new Warp10JsonFormat()(singleStores)

  val locationRegex = """([+-]\d\d(?:\.\d+)?)?([+-]\d\d\d(?:\.\d+)?)?(?:([+-]\d+)CRSWGS_84|CRSWGS_84)?\/""".r

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
  implicit val timeout: Timeout = 30 seconds
 def log = system.log


 def getNBetween(
    requests: Iterable[Node],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]
  )(implicit timeout: Timeout): Future[Option[ODF]] = {
   if((newest.isEmpty || newest.contains(1)) && (oldest.isEmpty && begin.isEmpty && end.isEmpty)){
     def getFromCache(): Seq[Future[Option[ODF]]] = {
       lazy val hTreeF: Future[ImmutableODF] = singleStores.getHierarchyTree()
       val objectData: Seq[Future[Option[ODF]]] = requests.collect{

         case obj:Objects => {
           (singleStores.readAll()
           .map(_.toSeq).map(n => Some(singleStores.buildODFFromValues(n))))
         }

         case obj @ Object(_,path, _, _, _) => {
           hTreeF.flatMap{ hTree =>
             val resultsO =hTree.get(path).collect{case o: OdfObject => o}
             val pathsO = resultsO.map(odfObject=> getLeafs(odfObject).map(_.path))
             val pathValuesF: Future[Option[Seq[(Path, Value[Any])]]] =
               pathsO.map(paths => singleStores.readValues(paths)) match{
                 case Some(f) => f.map(Some(_))
                 case None => Future.successful(None)
               }
             val res: Future[Option[ODF]] =
               pathValuesF.map(pathValuesO =>
                                 pathValuesO.map(pathValues =>
                                                   singleStores.buildODFFromValues(pathValues)))
             res
             //for {
             //  odfObject: OdfObject <- hTree.get(path).coll.collect{
             //    case o: OdfObject => o
             //  }
             //  paths = getLeafs(odfObject).map(_.path)
             //  pathValues <- (singleStores.readValues(paths))
             //  res = singleStores.buildODFFromValues(pathValues)
             //} yield res

             //val paths = getLeafs(obj).map(_.path)
             //val objs = singleStores.latestStore execute LookupSensorDatas(paths)
             //val results = singleStores.buildOdfFromValues(objs)

             //resultsO

           }
         }
       }.toSeq

       // And then get all InfoItems with the same call
       val reqInfoItems = requests collect {case ii: InfoItem => ii}
       val paths = reqInfoItems.map(_.path).toSeq


       val infoItemDataF: Future[Seq[(Path, Value[Any])]] = singleStores.readValues(paths)
       val reqObjs: Future[Option[ODF]] =
         for{
           infoItemData <- infoItemDataF
           foundPaths = (infoItemData.map{ case (path,_) => path }).toSet
           resultOdf = singleStores.buildODFFromValues(infoItemData)
         } yield Some(reqInfoItems.foldLeft(resultOdf){(result,info)=>
           info match {
             case qry @ InfoItem(_,path,_,_,_,_,_,_) if foundPaths contains path =>
               result union ImmutableODF(Seq(qry))
             case _ => result // else discard
           }
         })
       objectData :+ reqObjs
     }
     Future.sequence(getFromCache()).map(odf => odf.flatten.reduceOption(_.union(_)))
   } else {
     val locationsSeparated = requests.groupBy(_.path.last == "location")
     val locations: Map[Path, Node] = locationsSeparated
       .get(true)
       .toSeq
       .flatten
       .groupBy(p => Path(p.path.init))
       .mapValues(_.head)
     val requestWithoutLocations = locationsSeparated.get(false).toSeq.flatten
     oldest match {
       case Some(a) => Future.failed(new Exception("Oldest is not supported, since 29.6.2016"))
       case None =>
         val selector = nodesToReadPathSelector(requestWithoutLocations)
         val contentFuture: Future[String] = (begin, end, newest) match {
           case (None, None, None) =>
             Future.successful(warpReadNBeforeMsg(selector, 1, None)(readToken))
           case (None, endTime, Some(sticks)) =>
             Future.successful(warpReadNBeforeMsg(selector, sticks, end)(readToken))
           case (startTime, endTime, None) =>
             Future.successful(warpReadBetweenMsg(selector, begin, end)(readToken))
           case (startTime, endTime, sticks) =>
             Future.failed(new Exception(s"Unsupported combination ($startTime, $endTime, $sticks), since 29.6.2016"))
         }
         contentFuture.flatMap {
           content =>
             read(content, locations).map{n => n.map(OldTypeConverter.convertOdfObjects(_))}
         }
     }
   }
 }
 private def read(content : String, requiredLocations: Map[Path,Node]): Future[Option[OdfObjects]] = {
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
        formatedResponse.onFailure{
          case t : Throwable =>
            log.error(t, "Failed to communicate to Warp 10.")
            log.debug(t.getStackTrace().mkString("\n"))
        }
        formatedResponse

 }

  private def findClosestLocation(path: Path, locs: Map[Path,Seq[InfoItem]]): Option[InfoItem] = {
     @tailrec def inner(iPath: Path):Option[Seq[InfoItem]] = {
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

 def writeMany(infos: Seq[InfoItem]): Future[OmiReturn] ={
   // val hTree = singleStores.hierarchyStore execute GetTree()
   val grouped = infos.groupBy(_.path.lastOption.exists(_ == "location"))

   val locations: Option[Map[Path, Seq[InfoItem]]] = grouped.get(true).map(_.groupBy(_.path))
   val newInfos: Seq[InfoItem] = grouped.get(false).toSeq.flatten // Option[Seq[InfoItem]] -> Seq[Seq[InfoItem]] -> Seq[InfoItem]

   val data: Seq[(Path, Value[Any], Option[String])] = newInfos.flatMap(ii =>
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


   val requestContent = Try(data.map{
    case (path: Path, odfValue: Value[Any], location: Option[String]) =>
    toWriteFormat(path,odfValue, location)
   }).map(_.mkString(""))


   val request = requestContent.map(content =>
     RequestBuilding.Post(writeAddress, content).withHeaders(Warp10TokenHeader(writeToken)
     )
   )


   val response = {
     if(request.isFailure) Future.failed(request.failed.get)
     else httpExt.singleRequest(request.get)
   }//httpHandler(request)
   /*response.recover{
     case pe: ParseError => {
       log.warning("invalid location syntax")
       Returns.ParseErrors(Vector(pe))
     }
     case nfe: NumberFormatException => {
       log.warning("failed to parse locations")
       Returns.ParseErrors(Vector(ParseError(nfe.getMessage)))

     }
   }*/

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
  def remove(path: Path)(implicit timeout: Timeout): Future[Seq[Int]] = ???
  private def warp10MetaData(path: Path) = Some(
    MetaData(
      OdfTreeCollection(
        InfoItem(
          "type",
          path / "MetaData" / "type",
          values = OdfTreeCollection(
            Value("ISO 6709","xs:String",new Timestamp(1470230717254L))
          )
        )
      )
    )
  )
  private def toWriteFormat( path: Path, odfValue : Value[Any], location: Option[String]): String = {
    def handleString(in: Any): String = {
      val str = URLEncoder.encode(in.toString, "UTF-8")

      s"'$str'"
    }
    def updateHierarchy = {
      singleStores.updateHierarchyTree(ImmutableODF(Seq(InfoItem("location", path.getParent / "location", metaData = warp10MetaData(path)))))
    }

    val unixEpochTime = odfValue.timestamp.getTime * 1000
    val pathJS = path.toString//
    val typeValue = odfValue.typeAttribute
    val labels = s"{ type=$typeValue }"

    val parsedLoc = location match {
      case Some(loc) =>{
        val matchResult = locationRegex.findFirstMatchIn(loc)
        matchResult match {
          case Some(res) => {
            (Option(res.group(1)),Option(res.group(2)),Option(res.group(3))) match {
              case (None, None, Some(elev)) => {
                updateHierarchy
                s"/${elev.toLong}"
              }
              case (Some(lat), Some(lon), None) => {
                updateHierarchy
                s"${lat.toDouble}:${lon.toDouble}/"
              }
              case (Some(lat), Some(lon), Some(elev)) => {
                updateHierarchy
                s"${lat.toDouble}:${lon.toDouble}/${elev.toLong}"
              }
              case _ => {
                log.warning(s"Invalid format for location: ${location}")
                throw Warp10ParseError(s"Invalid format for location: $location")
              }
            }
          }
          case none => {
            log.warning(s"Invalid format for location: ${location}")
            throw Warp10ParseError(s"Invalid format for location: $location")
          }
        }
      }
      case None => "/"
    }
    /*val matchResult = location.flatMap(loc => locationRegex.findFirstMatchIn(loc))
    val loc = matchResult match {
      case Some(res) => {
        singleStores.hierarchyStore execute Union(createAncestors(OdfInfoItem(Path(path.init) / "location", metaData = warp10MetaData(path))))
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
        log.warning(s"Invalid format for location${location}")
        "/"
      }
    }*/

    val value =
      odfValue.typeAttribute match {
        case "xs:float" | "xs:double" | "xs:short" | "xs:int" | "xs:long" => odfValue.value
        case typeValue =>
          val resString = typeValue match {
            case "xs:boolean" => odfValue.value match {
              case b: Boolean => if(b) "T" else "F"
              case default: Any => handleString(default)
            }
            case _ => handleString(odfValue.value)
          }

          s"$resString"
      }
    s"$unixEpochTime/$parsedLoc $pathJS$labels $value\n"
    //log.debug(s"sent message: $result")

  }

 private def nodesToReadPathSelector( nodes : Iterable[Node] ) = {
   val paths = nodes.map{
     case obj : Object => obj.path.mkString(".") + ".*"
     case objs : Objects => objs.path.mkString(".") + ".*"
     case info : InfoItem => info.path.mkString(".")
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
