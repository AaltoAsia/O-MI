/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 + Copyright (c) 2015 Aalto University.                                       +
 +                                                                            +
 + Licensed under the 4-clause BSD (the "License");                           +
 + you may not use this file except in compliance with the License.           +
 + You may obtain a copy of the License at top most directory of project.     +
 +                                                                            +
 + Unless required by applicable law or agreed to in writing, software        +
 + distributed under the License is distributed on an "AS IS" BASIS,          +
 + WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   +
 + See the License for the specific language governing permissions and        +
 + limitations under the License.                                             +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package parsing

import java.io.{File, InputStream}
import java.sql.Timestamp
import java.util.Date

import org.json4s._
import org.json4s.native.JsonMethods.{parse => jparse}
import types.OmiTypes.{CallRequest, Callback, CancelRequest, DeleteRequest, OmiParseResult, OmiRequest, OmiResult, OmiReturn, PollRequest, RawCallback, ReadRequest, RequestID, ResponseRequest, SenderInformation, SubscriptionRequest, UserInfo, WriteRequest}
import types.{ODFParserError, OMIParserError, ParseError, Path}
import types.odf._

import scala.collection.immutable._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}




case class RequestMeta(
                        ttl: Duration = 10 seconds,
                        callback: Option[Callback] = None,
                        userInfo: UserInfo = UserInfo(),
                        senderInfo: Option[SenderInformation] = None,
                        ttlLimit: Option[Timestamp] = None,
                        requestToken: Option[Long] = None,
                        renderRequestID: Boolean = false
                      )



class JSONParser {
  val test =
    """
    {
      "omiEnvelope": {
        "version": "1.0",
        "ttl": 10,
        "response": {
          "result": {
            "msgformat": "odf",
            "return": {
              "returnCode": "200"
            },
            "msg": {
              "Objects": {
                "Object": {
                  "id": "OMI-Service",
                  "Object": {
                    "id": "Settings",
                    "InfoItem": {
                      "name": "num-latest-values-stored",
                      "description": {
                        "text": "\n                "
                      },
                      "values": [
                        {
                          "type": "xs:int",
                          "dateTime": "2019-06-04T13:37:59.311+03:00",
                          "unixTime": 1559644679,
                          "value": 50
                        },
                        {
                          "type": "xs:int",
                          "dateTime": "2019-06-04T13:35:26.196+03:00",
                          "unixTime": 1559644526,
                          "value": 50
                        }
                      ]
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    """
  val test2 =
    """{
   "Objects":{
      "Object":{
         "id":"OMI-Service",
         "Object":{
            "id":"Settings",
            "InfoItem":{
               "name":"num-latest-values-stored",
               "description":{
                  "text":"\n                "
               },
               "values":[
                  {
                     "type":"xs:int",
                     "dateTime":"2019-06-04T13:37:59.311+03:00",
                     "unixTime":1559644679,
                     "content":50
                  },
                  {
                     "type":"xs:int",
                     "dateTime":"2019-06-04T13:35:26.196+03:00",
                     "unixTime":1559644526,
                     "content":50
                  }
               ]
            }
         }
      }
   }
}"""

  val test3 =
    """{
      "Object":{
         "id":"OMI-Service",
         "Object":{
            "id":"Settings",
            "InfoItem":{
               "name":"num-latest-values-stored",
               "description":{
                  "text":"\n                "
               },
               "values":[
                  {
                     "type":"xs:int",
                     "dateTime":"2019-06-04T13:37:59.311+03:00",
                     "unixTime":1559644679,
                     "content":50
                  },
                  {
                     "type":"xs:int",
                     "dateTime":"2019-06-04T13:35:26.196+03:00",
                     "unixTime":1559644526,
                     "content":50
                  }
               ]
            }
         }
      }
   }
"""


  def parse(in:String): OmiParseResult= parse(StringInput(in))
  def parse(in: java.io.Reader): OmiParseResult = parse(ReaderInput(in))
  def parse(in: InputStream): OmiParseResult = parse(StreamInput(in))
  def parse(in: File): OmiParseResult = parse(FileInput(in))




  def parse(in: JsonInput): OmiParseResult = {
    val ast = Try(jparse(in))
    ast.fold(t => Left(Vector(OMIParserError(t.getMessage))),handleASTRoot(_))

  }


  private def handleASTRoot(in: JValue) = {
    def parseRoot(obj: JObject) = {
      val fields = obj.obj.toMap
      fields.get("omiEnvelope").map(parseOmiEnvelope).getOrElse(Right(Seq.empty[OmiRequest]))
    }
    in match {
      case obj: JObject => parseRoot(obj)
      case oth => Left(Seq(OMIParserError("Could not find root object for omiEnvelope")))
    }
  }

  private def parseTTL(in: JValue): Duration = {
    in match {
      case JDouble(ttl) => ttl match {
        case -1.0 => Duration.Inf
        case 0.0 => Duration.Inf
        case w if w > 0 => w.seconds
        case _ => throw OMIParserError("Negative interval, other than -1 isn't allowed")
      }
      case JInt(ttl) => ttl.longValue match {
        case -1 => Duration.Inf
        case 0.0 => Duration.Inf
        case w if w > 0 => w.seconds
        case _ => throw OMIParserError("Negative interval, other than -1 isn't allowed")
      }
      case JString(ttl) => Try(parseTTL(JDouble(ttl.toDouble)))
        .getOrElse(throw OMIParserError("Invalid format for TTL; number expected"))
      case other => throw OMIParserError("Invalid json format for TTL; number expected")
    }
  }

  private def parseInterval(in: JValue): Try[Duration] = {
    def inner(inn: JValue) = {
      inn match {
        case JDouble(interval) => interval match {
          case -1.0 => -1.seconds
          case -2.0 => -2.seconds
          case w if w > 0 => w.seconds
          case _ => throw OMIParserError("Invalid interval, only positive or -1 and -2 are allowed")
        }
        case JInt(interval) => interval.longValue match {
          case -1 => -1.seconds
          case -2 => -2.seconds
          case w if w > 0 => w.seconds
          case _ => throw OMIParserError("Invalid interval, only positive or -1 and -2 are allowed")
        }
        case other => throw OMIParserError("Invalid json format for interval; number expected")
      }
    }
      Try(inner(in))
  }

  def parseIntegerValue(in: JValue): Int = {
    in match {
      case JInt(num) => if(num.isValidInt) num.intValue() else throw ODFParserError("Integer value too long")
      case JString(num) => Try(num.toInt).getOrElse(throw ODFParserError("Could not convert string value to integeter value"))
      case _ => throw ODFParserError("Invalid json type when parsing integer value")
    }

  }
  def parseLongValue(in: JValue): Long = {
    in match {
      case JInt(num) => if(num.isValidLong) num.longValue() else throw ODFParserError("Integer value too long")
      case JString(num) => Try(num.toLong).getOrElse(throw ODFParserError("Could not convert string value to integeter value"))
      case _ => throw ODFParserError("Invalid json type when parsing integer value")
    }

  }

  def parseOmiEnvelope(jval: JValue): OmiParseResult = {
    def parseEnvelope(in: JObject): OmiParseResult = {
      val fields = in.obj.toMap
      val version = fields.get("version").map(parseStringAttribute)
        .getOrElse(throw OMIParserError("Version mandatory in omiEnvelope"))
      val ttl = fields.get("ttl").map(parseTTL).getOrElse(throw OMIParserError("TTL not found"))
      val attributes = fields.get("attributes").map(parseAttributes)
      val authorization = fields.get("authorization").map(parseStringAttribute)

      val meta = RequestMeta(ttl)

      val read = fields.get("read").map(parseRead(_, meta))
      val write = fields.get("write").map(parseWrite(_, meta))
      val response = fields.get("response").map(parseResponse(_, meta))
      val cancel = fields.get("cancel").map(parseCancel(_, meta))
      val call = fields.get("call").map(parseCall(_, meta))
      val delete = fields.get("delete").map(parseDelete(_, meta))
      val (succ, fail) = List(read,write,response,cancel,call,delete).flatten.partition(_.isSuccess)


      if(fail.isEmpty)
        if(succ.isEmpty)
          Left(Seq(OMIParserError(s"Request missing")))
        else
          Right(succ.map(_.get))
      else Left(fail.map(_.failed.map[ParseError]{
        case pe: ParseError => pe
        case oth => OMIParserError(s"unknown error while parsing: ${oth.getMessage}")
      }.get))

      //val test: Option[Option[OmiParseResult]] = List(read,write,response,cancel,call,delete).find(_.isDefined)

    }


    jval match {
      case obj: JObject => parseEnvelope(obj)
      case other => Left(Seq(OMIParserError(s"Invalid json type for omiOmiEnvelope")))
    }

  }
  private def parseNodeList(in: JValue) = ???

  private def parseRequestID(in: JValue): OdfCollection[RequestID] = {
    in match {
      case JInt(num) =>Vector(Try(num.longValue())
        .getOrElse(throw OMIParserError("RequestID only integer value supported currently")))
      case JString(str) => Vector(Try(str.toLong).getOrElse(throw OMIParserError("Currently only integer requestID supported")))
      case JArray(arr) => arr.flatMap(parseRequestID).toVector
      case other => throw OMIParserError("Invalid RequestID found, currently only integer values supported")
    }
  }
  private def parseMsg(in: JValue): Try[ODF] = {
    in match {
      case obj: JObject =>obj.obj.toMap.get("Objects").map(parseObjects).getOrElse(Success(ImmutableODF()))
      case others => Success(ImmutableODF()) //empty odf
    }
  }

  private def parseRead(jval: JValue, meta: RequestMeta) : Try[OmiRequest] = {

    def _parseRead(in: JObject): Try[OmiRequest] = {
      val fields = in.obj.toMap

      val requestID: Option[OdfCollection[RequestID]] = fields.get("requestID").map(parseRequestID)
      val requestInterval = fields.get("interval").map(parseInterval)

      (requestID, requestInterval) match {
        case (Some(id),_) =>
          for {
            callback                    <- Try(fields.get("callback").map(parseStringAttribute).map(RawCallback))
          } yield PollRequest(callback,id,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)
        case (_, Some(intval)) =>
          for {
            callback                    <- Try(fields.get("callback").map(parseStringAttribute).map(RawCallback))
            interval                    <- intval
            msg                         <- fields.get("msg").map(parseMsg).getOrElse(Success(ImmutableODF()))
            newest: Option[Int]         <- Try(fields.get("newest").map(parseIntegerValue))
            oldest: Option[Int]         <- Try(fields.get("oldest").map(parseIntegerValue))
            _                           <- (newest, oldest) match {
              case (Some(n), Some(o)) => Failure(OMIParserError("Invalid Read request, Can not query oldest and newest values at same time."))
              case _ => Success(Unit)}
          } yield SubscriptionRequest(interval,msg,newest,oldest,callback,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)
        case _ =>
          for {
            msg                         <- fields.get("msg").map(parseMsg).getOrElse(Success(ImmutableODF()))
            begin: Option[Timestamp]    <- Try(fields.get("begin").map(parseDateTime))
            end: Option[Timestamp]      <- Try(fields.get("end").map(parseDateTime))
            newest: Option[Int]         <- Try(fields.get("newest").map(parseIntegerValue))
            oldest: Option[Int]         <- Try(fields.get("oldest").map(parseIntegerValue))
            _                           <- (newest, oldest) match {
              case (Some(n), Some(o)) => Failure(OMIParserError("Invalid Read request, Can not query oldest and newest values at same time."))
              case _ => Success(Unit)}
            maxLevels                   <- Try(fields.get("maxlevels").map(parseIntegerValue))
            callback <- Try(fields.get("callback").map(parseStringAttribute).map(RawCallback))

          } yield ReadRequest(msg, begin, end, newest, oldest, maxLevels, callback, meta.ttl, meta.userInfo, meta.senderInfo, meta.ttlLimit, meta.requestToken)

      }
      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
      //val all = fields.get("all")



    }

    jval match {
      case obj: JObject => _parseRead(obj)
      case other => Failure(OMIParserError("Invalid JSON type for O-MI read"))
    }

  }
  private def parseWrite(jval: JValue, meta: RequestMeta): Try[WriteRequest] = {

    def _parseWrite(in: JObject): Try[WriteRequest] = {
      val fields = in.obj.toMap

      for {
        callback                            <- Try(fields.get("callback").map(parseStringAttribute).map(RawCallback))
        msgformat                           <- Try(fields.get("msgformat").map(parseStringAttribute))
        targetType                          <- Try(fields.get("targetType").map(parseStringAttribute)) // node or device
        //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
        msg: ODF                            <- fields.get("msg").map(parseMsg).getOrElse(Success(ImmutableODF()))
      } yield WriteRequest(msg, callback, meta.ttl, meta.userInfo, meta.senderInfo, meta.ttlLimit,meta.requestToken)
    }

    jval match {
      case obj: JObject => _parseWrite(obj)
      case other => Failure(OMIParserError("Invalid JSON type for O-MI write"))
    }

  }

  private def parseReturn(jval: JValue): Try[OmiReturn] = {
    def _parseReturn(in: JObject): Try[OmiReturn] = {
      val fields = in.obj.toMap

      for {
        attributes                      <- fields.get("attributes").map(parseAttributes).getOrElse(Success(Map.empty[String,String]))
        returnCode                      <- Try(fields.get("returnCode")
          .map[String](parseStringAttribute)
          .filter(_.matches("""^2[0-9]{2}|4[0-9]{2}|5[0-9]{2}|6[0-9]{2}$""")) //only allow 2xx 4xx 5xx 6xx return codes
          .getOrElse(throw OMIParserError("ReturnCode missing or invalid format")))
        description                     <- Try(fields.get("description").map(parseStringAttribute))

      } yield OmiReturn(returnCode, description, attributes)

    }

    jval match {
      case obj: JObject => _parseReturn(obj)
      case _ => Failure(OMIParserError("Invalid JSON type for O-MI return"))
    }
  }
  private def parseResult(jval: JValue): Try[OdfCollection[OmiResult]]= {
    def _parseResult(in: JObject):Try[OmiResult] = {
      val fields = in.obj.toMap

      for {
        //msgformat: Option[String]   <- Try(fields.get("msgformat").map(parseStringAttribute))
        //targetType: Option[String]  <- Try(fields.get("targetType").map(parseStringAttribute)) // node or device
        returnV: OmiReturn          <- fields.get("return").map(parseReturn).getOrElse( Failure(OMIParserError("Return missing from result")))
        requestId                   <- Try(fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty))
        msg                         <- fields.get("msg").map(m => parseMsg(m).map(Some(_))).getOrElse(Success(None))
        //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
        //omiEnvelope <- fields.get("omiEnvelope").map(parseOmiEnvelope) // omiEnvelope
      } yield OmiResult(returnV,requestId,msg)
    }

    jval match {
      case obj: JObject => _parseResult(obj).map(t => Vector(t))
      case JArray(arr:List[JObject]) => Try(arr.map(t => _parseResult(t).get).toVector)
      case other => Failure(OMIParserError("Invalid JSON type for O-MI result"))
    }
  }

  private def parseResponse(jval: JValue, meta: RequestMeta): Try[ResponseRequest] = {
    def _parseResponse(in: JObject): Try[ResponseRequest] = {
      val fields = in.obj.toMap

      for {
        results <- fields.get("result").map(parseResult).getOrElse( Failure(OMIParserError("Result mandatory in response")))
      } yield ResponseRequest(results,meta.ttl,meta.callback,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken,meta.renderRequestID)
    }

    jval match {
      case obj: JObject => _parseResponse(obj)
      case other => Failure(OMIParserError("Invalid JSON type for O-MI Response"))
    }
  }

  private def parseCancel(jval: JValue, meta: RequestMeta) : Try[CancelRequest] = {
    def _parseCancel(in: JObject): Try[CancelRequest] = {
      val fields = in.obj.toMap

      for {
        requestId: OdfCollection[RequestID] <- Try(fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty))
      } yield CancelRequest(requestId,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)

      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
    }

    jval match {
      case obj: JObject => _parseCancel(obj)
      case other => Failure(OMIParserError("Invalid JSON type for O-MI Cancel"))
    }
  }

  private def parseCall(jval: JValue, meta: RequestMeta) : Try[CallRequest] = {
    def _parseCall(in:JObject): Try[CallRequest] = {
      val fields = in.obj.toMap

      for {
        callback: Option[RawCallback]       <- Try(fields.get("callback").map(parseStringAttribute).map(RawCallback))
        //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
        msg: ODF                            <- fields.get("msg").map(parseMsg).getOrElse(Success(ImmutableODF()))
      } yield CallRequest(msg,callback,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)

    }

    jval match {
      case obj: JObject => _parseCall(obj)
      case other => Failure(OMIParserError("Invalid JSON type for O-MI Call"))
    }

  }

  private def parseDelete(jval: JValue, meta: RequestMeta) : Try[DeleteRequest] = {
    def _parseDelete(in: JObject): Try[DeleteRequest] = {
      val fields = in.obj.toMap

      for {
        callback <- Try(fields.get("callback").map(parseStringAttribute).map(RawCallback))
        msgformat <- Try(fields.get("msgformat").map(parseStringAttribute))
        targetType <- Try(fields.get("targetType").map(parseStringAttribute)) // node or device
        //val nodeList <- fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
        requestId: OdfCollection[RequestID] <- Try(fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty))
        msg: ODF <- fields.get("msg").map(parseMsg).getOrElse(Success(ImmutableODF()))
      } yield DeleteRequest(msg,callback,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)
    }

    jval match {
      case obj: JObject => _parseDelete(obj)
      case other => Failure(OMIParserError("Invalid JSON type for O-MI Delete"))
    }

  }

  private def parseStringAttribute(in: JValue): String = {
    in match {
      case JString(s) => s
      case other => throw ODFParserError(s"Expected String $other found")
    }
  }

  def parseDateTime(in: JValue):Timestamp = {
    in match {
      case JString(s) => new Timestamp(
        javax.xml.datatype.DatatypeFactory.newInstance()
          .newXMLGregorianCalendar(s)
          .toGregorianCalendar
          .getTimeInMillis
      )
      case other => throw ODFParserError(s"invalid type for date-time, found $other when String expected")
    }
  }

  //private def mapMaybeArray[A,B <: JValue](jval: JValue, f: B => A ): A = {
  // f(jval)
  //}

  def parseObjects(jval: JValue): Try[ODF] = {
    def _parseObjects(in: JObject): Try[ODF] = {
      val path = Path("Objects")
      val fields: Map[String, JValue] = in.obj.toMap
      val version = fields.get("version").map(parseStringAttribute)
      val prefix: Map[String,String] = Map.empty ++
        fields       //Map[String,JValue]
          .get("prefix") //Option[JValue]
          .map(parseStringAttribute)//Option[String]
          .map(p => "prefix" -> p) //Option[(String,String)] add to map as "list" of tuples
      val attributes: Map[String,String] = prefix

      val objects: Try[Seq[Node]] = fields.get("Object").map(parseObject(path,_)).getOrElse(Success(Seq.empty))

      objects.map(objs => ImmutableODF(Objects(version,attributes) +: objs))
    }


    jval match {
      case obj: JObject => _parseObjects(obj)
      case _ => Failure(ODFParserError(s"Invalid type for ODF Objects JSON Object expected"))
    }

  }
  private def parseQlmID(in: JObject) = {
    val fields: Map[String,JValue] = in.obj.toMap

    val id = fields.get("id").map(parseStringAttribute).getOrElse(throw ODFParserError("Missing id value in id object"))
    val idType = fields.get("idType").map(parseStringAttribute)
    val tagType = fields.get("tagType").map(parseStringAttribute)
    val startDate = fields.get("startDate").map(parseDateTime)
    val endDate = fields.get("endDate").map(parseDateTime)

    QlmID(id,idType,tagType,startDate,endDate)

  }
  private def parseID(in: JValue): Try[Vector[QlmID]] = {

    def parseArrayValue(in: JValue): QlmID = {
      in match {
        case JString(s) => QlmID(s)
        case obj: JObject => parseQlmID(obj)
        case other => throw ODFParserError(s"Invalid id type inside id array")
      }
    }

    in match {
      case JString(s) => Try(Vector(QlmID(s)))
      case obj: JObject => Try(Vector(parseQlmID(obj)))
      case JArray(arr: List[JValue]) => Try(arr.map(parseArrayValue).toVector)
      case other => Failure(ODFParserError(s"Id must be one of: String, Array Object."))
    }

  }

  private def parseAttributes(in:JValue): Try[Map[String,String]] = {
    in match {
      case jobj: JObject =>
        Try(jobj.obj.toMap.mapValues(parseStringAttribute))
      case other => Failure(ODFParserError("Invalid type for attributes object: JSON object expected"))
    }
  }

  private def parseObject(parentPath: Path, jval: JValue): Try[Seq[Node]] = {

    def _parseObject(in: JObject): Try[Seq[Node]] = {
      val fields: Map[String, JValue] = in.obj.toMap

      //val id: Vector[QlmID] = fields.get("id").map(parseID).getOrElse(throw new ODFParserError("Id missing"))
      //val path: Path = parentPath./(id.head) // id must not be empty id is required
      //val thisObject: Object = Object(id,path,oType,description,attributes)
      for {
        id                         <- fields.get("id").map(parseID).getOrElse(Failure(ODFParserError("Id missing")))
        path                       <- Try(parentPath./(id.head))
        oType: Option[String]      <- Try(fields.get("type").map(parseStringAttribute))
        attributes                 <- fields.get("attributes").map(parseAttributes).getOrElse(Success(Map.empty[String,String]))
        objects: Seq[Node]         <- fields.get("Object").map(parseObject(path,_)).getOrElse(Success(Seq.empty))
        infoitems: Seq[InfoItem]   <- fields.get("InfoItem").map(parseInfoItems(path,_)).getOrElse(Success(Seq.empty))
        description                <- fields.get("description").map(parseDescriptions).getOrElse(Success(Set.empty[Description]))
        thisObject = Object(id,path,oType,description,attributes)
      } yield Seq(thisObject) ++ objects ++ infoitems
    }

    jval match {
      case in: JObject => _parseObject(in)
      case JArray(arr: List[JObject]) => Try(arr.flatMap(_parseObject(_).get)) //Try(arr.flatMap(_parseObject(_)))
      case other => Failure(ODFParserError("Invalid JSON type for ODF Object"))
    }
  }
  private def parseInfoItems(parentPath: Path, jval: JValue): Try[Seq[InfoItem]] = {
    def parseInfoItem(in: JObject): Try[InfoItem] = {

      val fields: Map[String,JValue]  = in.obj.toMap

      for {
        name: String                    <- Try(fields.get("name").map(parseStringAttribute).getOrElse(throw ODFParserError("Name missing from InfoItem")))
        path: Path = parentPath./(name)
        attributes                      <- fields.get("attributes").map(parseAttributes).getOrElse(Success(Map.empty[String,String]))
        values: Vector[Value[Any]]      <- fields.get("values").map(parseValues).getOrElse(Success(Vector.empty))
        altName: Vector[QlmID]          <- fields.get("altname").map(parseID).getOrElse(Success(Vector.empty))
        metaData: Option[MetaData]      <- fields.get("MetaData").map(parseMetaData(path,_).map(Some(_))).getOrElse(Success(None))
        description                     <- fields.get("description").map(parseDescriptions).getOrElse(Success(Set.empty[Description]))
        typev: Option[String]           <- Try(fields.get("type").map(parseStringAttribute))
      } yield InfoItem(name,path,typev,altName,description,values,metaData,attributes)

    }

    jval match {
      case obj: JObject => parseInfoItem(obj).map(Seq(_))
      case JArray(arr: List[JObject]) => Try(arr.map(parseInfoItem(_).get))
      case other => Failure(ODFParserError("Invalid JSON type for ODF InfoItem"))
    }
  }

  private def parseValues(jval: JValue): Try[Vector[Value[Any]]] = {
    def parseValue(in: JObject): Value[Any] = {
      val parseTime = new Timestamp(new Date().getTime)
      val fields: Map[String,JValue] = in.obj.toMap

      val typev = fields.get("type").map(parseStringAttribute) //TODO
      val dateTime = fields.get("dateTime").map(parseDateTime)
      val unixTime = fields.get("unixTime")
      val timestamp = correctTimeStamp(dateTime, unixTime)
      //val attributes = ??? // TODO not implemented in value type
      val value: Any = fields.get("value").map{
        case JString(s) => s
        case JDouble(num) => num
        case JInt(num) =>
          if(num.isValidLong)
            num.longValue()
          else num
        case JBool(b) => b
        case obj: JObject => parseObjects(obj)
        case other => throw ODFParserError("Invalid JSON type for ODF Value")
      }.getOrElse(throw ODFParserError("Value missing"))

      if(typev.isEmpty)
        Value(value,timestamp)
      else
        Value(value,typev.get,timestamp)
    }

    jval match {
      case obj: JObject => Try(Vector(parseValue(obj)))
      case JArray(arr: List[JObject]) => Try(arr.map(parseValue).toVector)
      case other => throw ODFParserError("Invalid JSON type for ODF Value")
    }
  }
  private def correctTimeStamp(dt: Option[Timestamp], ut: Option[JValue]): Timestamp = {
    dt match {
      case Some(tt) => tt
      case None => ut match {
        case Some(JInt(num)) => {
          if(num.isValidLong)
            new Timestamp(num.longValue() * 1000)
          else
            throw ODFParserError("UnixTime too big")
        }
        case Some(JDouble(num)) => {
          new Timestamp( (num*1000).toLong)

        }
        case Some(other) => throw ODFParserError("Invalid timestamp format")
        case None => new Timestamp(new Date().getTime)
      }
    }
  }

  private def parseDescriptions(jval: JValue): Try[Set[Description]] = {
    def parseDescription(in: JObject): Description = {
      val fields = in.obj.toMap

      val lang = fields.get("lang").map(parseStringAttribute)
      //val attributes = fields.get(attributes).map(parseAttributes).getOrElse(Map.empty)
      val text = fields.get("text").map(parseStringAttribute)
        .getOrElse(throw ODFParserError("Text missing from description"))
      Description(text, lang)

    }
    jval match {
      case obj: JObject => Try(Set(parseDescription(obj)))
      case JArray(arr: List[JObject]) => Try(arr.map(parseDescription).toSet)
      case other => throw ODFParserError("Invalid JSON type for ODF Description")
    }
  }

  private def parseMetaData(parentPath :Path, jval: JValue): Try[MetaData]= {
    val path: Path = parentPath./("MetaData")
    def parseM(in: JObject) = {
      val fields = in.obj.toMap
      fields.get("InfoItem").fold[Try[MetaData]](Success(MetaData(Vector.empty)))( jv => parseInfoItems(path,jv).map(ii => MetaData(ii.toVector)))
    }


    //parseInfoItems(path,jval).map(ii => MetaData(ii.toVector))
    jval match {
      case obj: JObject => parseM(obj)
      case other => Failure(ODFParserError("Invalid JSON type for ODF MetaData"))
    }

  }

}