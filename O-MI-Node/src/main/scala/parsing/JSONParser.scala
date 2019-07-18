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

import java.io.{File, InputStream, Reader}
import java.sql.Timestamp
import java.util.Date

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods.{parse => jparse}
import types.OmiTypes.ReturnCode.ReturnCode
import types.OmiTypes.{CallRequest, Callback, CancelRequest, DeleteRequest, OmiParseResult, OmiResult, OmiReturn, RawCallback, ReadRequest, RequestID, ResponseRequest, SenderInformation, UserInfo, WriteRequest}
import types.{ODFParserError, OMIParserError, Path}
import types.odf._

import scala.collection.immutable.HashMap
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
                      "value": [
                        {
                          "type": "xs:int",
                          "dateTime": "2019-06-04T13:37:59.311+03:00",
                          "unixTime": 1559644679,
                          "content": 50
                        },
                        {
                          "type": "xs:int",
                          "dateTime": "2019-06-04T13:35:26.196+03:00",
                          "unixTime": 1559644526,
                          "content": 50
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


  def parse(in:String): ODF = parse(StringInput(in))
  //def parse(in:Reader) = parse(ReaderInput(in))
  //def parse(in: InputStream) = parse(StreamInput(in))
  //def parse(in: File) = parse(FileInput(in))




  def parse(in: JsonInput): ODF = {
    handleASTRoot(jparse(in))
  }


  private def handleASTRoot(in: JValue) = {
    in match {
      case obj: JObject => ???
    }
    ???
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
      case other => throw OMIParserError("invalid json format for TTL; number expected")
    }
  }

  def parseIntegerValue(in: JValue): Int = {
    in match {
      case JInt(num) => if(num.isValidInt) num.intValue() else throw ODFParserError("Integer value too long")
      case JString(num) => Try(num.toInt).getOrElse(throw ODFParserError("Could not convert string value to integeter value"))
      case _ => throw ODFParserError("invalid json type when parsing integer value")
    }

  }
  def parseLongValue(in: JValue): Long = {
    in match {
      case JInt(num) => if(num.isValidLong) num.longValue() else throw ODFParserError("Integer value too long")
      case JString(num) => Try(num.toLong).getOrElse(throw ODFParserError("Could not convert string value to integeter value"))
      case _ => throw ODFParserError("invalid json type when parsing integer value")
    }

  }

  def parseOmiEnvelope(jval: JValue): OmiParseResult = {
    def parseEnvelope(in: JObject) = {
      val fields = in.obj.toMap
      val version = fields.get("version").map(parseStringAttribute)
        .getOrElse(throw OMIParserError("version mandatory in omiEnvelope"))
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
      val test: Option[Option[OmiParseResult]] = List(read,write,response,cancel,call,delete).find(_.isDefined)
      ???
    }


    jval match {
      case obj: JObject => parseEnvelope(obj)
      case other => ???
    }

  }
  private def parseNodeList(in: JValue) = ???

  private def parseRequestID(in: JValue): OdfCollection[RequestID] = {
    in match {
      case JInt(num) =>Vector(Try(num.longValue())
        .getOrElse(throw OMIParserError("RequestID only integer value supported currently")))
      case JString(str) => Vector(Try(str.toLong).getOrElse(throw OMIParserError("currently only integer requestID supported")))
      case JArray(arr) => arr.flatMap(parseRequestID).toVector
      case other => throw OMIParserError("Invalid RequestID found, currently only integer values supported")
    }
  }
  private def parseMsg(in: JValue): ODF = {
    in match {
      case obj: JObject =>obj.obj.toMap.get("Objects").map(parseObjects).getOrElse(ImmutableODF())
      case others => ImmutableODF() //empty odf
    }
  }

  private def parseRead(jval: JValue, meta: RequestMeta) : Try[ReadRequest] = {
    def _parseRead(in: JObject) = {
      val fields = in.obj.toMap

      val callback = fields.get("callback").map(parseStringAttribute).map(RawCallback)
      val msgformat = fields.get("msgformat").map(parseStringAttribute)
      val targetType = fields.get("targetType").map(parseStringAttribute) // node or device
      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
      val requestId: OdfCollection[RequestID] = fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty)
      val msg: ODF = fields.get("msg").map(parseMsg).getOrElse(ImmutableODF())

      //val interval = fields.get("interval")
      val oldest: Option[Int] = fields.get("oldest").map(parseIntegerValue)
      val begin: Option[Timestamp] = fields.get("begin").map(parseDateTime)
      val end: Option[Timestamp] = fields.get("end").map(parseDateTime)
      val newest: Option[Int] = fields.get("newest").map(parseIntegerValue)

      //val all = fields.get("all")
      //val maxlevels = fields.get("maxlevels")

      ReadRequest(msg,begin,end,newest,oldest,callback,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,requestId)

    }

    jval match {
      case obj: JObject => Try(_parseRead(obj))
      case other => throw OMIParserError("Invalid JSON type for O-MI read")
      case other => ???
    }

  }
  private def parseWrite(jval: JValue, meta: RequestMeta): Try[WriteRequest] = {

    def _parseWrite(in: JObject): WriteRequest = {
      val fields = in.obj.toMap

      val callback = fields.get("callback").map(parseStringAttribute).map(RawCallback)
      val msgformat = fields.get("msgformat").map(parseStringAttribute)
      val targetType = fields.get("targetType").map(parseStringAttribute) // node or device
      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
      val requestId: OdfCollection[RequestID] = fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty)
      val msg: ODF = fields.get("msg").map(parseMsg).getOrElse(ImmutableODF())

      WriteRequest(msg, callback, meta.ttl, meta.userInfo, meta.senderInfo, meta.ttlLimit,meta.requestToken)
    }

    jval match {
      case obj: JObject => Try(_parseWrite(obj))
      case other => throw OMIParserError("Invalid JSON type for O-MI write")
      case other => ???
    }

  }

  private def parseReturn(jval: JValue): OmiReturn = {
    def _parseReturn(in: JObject) = {
      val fields = in.obj.toMap

      val attributes = fields.get("attributes").map(parseAttributes).getOrElse(Map.empty)
      val returnCode: ReturnCode = fields.get("attributes")
        .map(parseStringAttribute)
        .filter(_.matches("""^2[0-9]{2}|4[0-9]{2}|5[0-9]{2}|6[0-9]{2}$""")) //only allow 2xx 4xx 5xx 6xx return codes
        .getOrElse(throw OMIParserError("returnCode missing or invalid format"))
      val description = fields.get("description").map(parseStringAttribute)

      OmiReturn(returnCode,description,attributes)

    }

    jval match {
      case obj: JObject => _parseReturn(obj)
      case _ => throw OMIParserError("invalid JSON type for O-MI return")
    }
  }
  private def parseResult(jval: JValue): OdfCollection[OmiResult] = {
    def _parseResult(in: JObject) = {
      val fields = in.obj.toMap
      val msgformat = fields.get("msgformat").map(parseStringAttribute)
      val targetType = fields.get("targetType").map(parseStringAttribute) // node or device
      val returnV = fields.get("return").map(parseReturn).getOrElse(throw OMIParserError("return missing from result"))
      val requestId: OdfCollection[RequestID] = fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty)
      val msg: Option[ODF] = fields.get("msg").map(parseMsg)
      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
      val omiEnvelope = fields.get("omiEnvelope").map(parseOmiEnvelope)

      OmiResult(returnV,requestId,msg)
    }

    jval match {
      case obj: JObject => Vector(_parseResult(obj))
      case JArray(arr:List[JObject]) => arr.map(_parseResult).toVector
      case other => throw OMIParserError("Invalid JSON type for O-MI result")
    }
  }
  private def parseResponse(jval: JValue, meta: RequestMeta): Try[ResponseRequest] = {
    def _parseResponse(in: JObject) = {
      val fields = in.obj.toMap
      val results = fields.get("result").map(parseResult).getOrElse(throw OMIParserError("result mandatory in response"))
      ResponseRequest(results,meta.ttl,meta.callback,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken,meta.renderRequestID)
    }
    jval match {
      case obj: JObject => Try(_parseResponse(obj))
      case other => throw OMIParserError("Invalid JSON type for O-MI Response")
    }

  }
  private def parseCancel(jval: JValue, meta: RequestMeta) : Try[CancelRequest] = {
    def _parseCancel(in: JObject): CancelRequest = {
      val fields = in.obj.toMap

      val requestId: OdfCollection[RequestID] = fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty)
      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???

      CancelRequest(requestId,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)
    }
    jval match {
      case obj: JObject => Try(_parseCancel(obj))
      case other => ???
      case other => throw OMIParserError("Invalid JSON type for O-MI Cancel")
    }

  }
  private def parseCall(jval: JValue, meta: RequestMeta) : Try[CallRequest] = {
    def _parseCall(in:JObject): CallRequest = {
      val fields = in.obj.toMap

      val callback = fields.get("callback").map(parseStringAttribute).map(RawCallback)
      val msgformat = fields.get("msgformat").map(parseStringAttribute)
      val targetType = fields.get("targetType").map(parseStringAttribute) // node or device
      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
      val requestId: OdfCollection[RequestID] = fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty)
      val msg: ODF = fields.get("msg").map(parseMsg).getOrElse(ImmutableODF())

      CallRequest(msg,callback,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)
    }
    jval match {
      case obj: JObject => Try(_parseCall(obj))
      case other => throw OMIParserError("Invalid JSON type for O-MI Call")
    }

  }
  private def parseDelete(jval: JValue, meta: RequestMeta) : Try[DeleteRequest] = {
    def _parseDelete(in: JObject): DeleteRequest = {
      val fields = in.obj.toMap

      val callback = fields.get("callback").map(parseStringAttribute).map(RawCallback)
      val msgformat = fields.get("msgformat").map(parseStringAttribute)
      val targetType = fields.get("targetType").map(parseStringAttribute) // node or device
      //val nodeList = fields.get("nodeList").map(parseNodelist) //nodelist not supported ???
      val requestId: OdfCollection[RequestID] = fields.get("requestID").map(parseRequestID).getOrElse(OdfCollection.empty)
      val msg: ODF = fields.get("msg").map(parseMsg).getOrElse(ImmutableODF())

      DeleteRequest(msg,callback,meta.ttl,meta.userInfo,meta.senderInfo,meta.ttlLimit,meta.requestToken)
    }
    jval match {
      case obj: JObject => Try(_parseDelete(obj))
      case other => ???
      case other => throw OMIParserError("Invalid JSON type for O-MI Delete")
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

  def parseObjects(jval: JValue):Try[ODF] = {
    def _parseObjects(in: JObject): ODF = {
      val path = Path("Objects")
      val fields: Map[String, JValue] = in.obj.toMap
      val version = fields.get("version").map(parseStringAttribute)
      val prefix: Map[String,String] = Map.empty ++
        fields       //Map[String,JValue]
          .get("prefix") //Option[JValue]
          .map(parseStringAttribute)//Option[String]
          .map(p => "prefix" -> p) //Option[(String,String)] add to map as "list" of tuples
      val attributes: Map[String,String] = prefix

      val objects: Option[Seq[Node]] = fields.get("Object").map{
        case obj: JObject => parseObject(path, obj)
        case JArray(arr: List[JObject]) => arr.flatMap(parseObject(path,_))
        case other => throw ODFParserError(s"'object' must be either object or array of objects")
      }

      val nodes: Seq[Node] = Objects(version, attributes) +: (objects.toList.flatten)
      ImmutableODF(nodes)

    }


    jval match {
      case obj: JObject => Try(_parseObjects(obj))
      case _ => throw ODFParserError(s"Invalid type for ODF Objects JSON Object expected")
    }

  }
  private def parseQlmID(in: JObject) = {
    val fields: Map[String,JValue] = in.obj.toMap

    val id = fields.get("id").map(parseStringAttribute).getOrElse(throw ODFParserError("missing id value in id object"))
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
      case other => throw ODFParserError(s"Id must be one of: String, Array Object.")
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

    def _parseObject(in: JObject): Seq[Node] = {
      val fields: Map[String, JValue] = in.obj.toMap

      val id: Vector[QlmID] = fields.get("id").map(parseID).getOrElse(throw new ODFParserError("Id missing"))
      val path: Path = parentPath./(id.head) // id must not be empty id is required
      val oType: Option[String] = fields.get("type").map(parseStringAttribute)
      val attributes: Map[String, String] = fields.get("attributes").map(parseAttributes).getOrElse(Map.empty)
      val objects: Seq[Node] = fields.get("Object").map(parseObject(path,_)).toSeq.flatten
      val infoitems: Seq[InfoItem] = fields.get("InfoItem").map(parseInfoItems(path,_)).toSeq.flatten
      val description: Set[Description] = fields.get("description").map(parseDescriptions).getOrElse(Set.empty)
      val thisObject: Object = Object(id,path,oType,description,attributes)

       Seq(thisObject) ++  objects ++ infoitems
    }

    jval match {
      case in: JObject => Try(_parseObject(in))
      case JArray(arr: List[JObject]) => Try(arr.flatMap(_parseObject(_)))
      case other => throw ODFParserError("Invalid JSON type for ODF Object")
    }
  }
  private def parseInfoItems(parentPath: Path, jval: JValue): Try[Seq[InfoItem]] = {
    def parseInfoItem(in: JObject): InfoItem = {
      val fields: Map[String,JValue]  = in.obj.toMap

      val name: String = fields.get("name").map(parseStringAttribute)
        .getOrElse(throw ODFParserError("Name missing from InfoItem"))
      val path: Path = parentPath./(name)
      val typev: Option[String] = fields.get("type").map(parseStringAttribute)
      for{
        attributes: Map[String, String] <- fields.get("attributes").map(parseAttributes).getOrElse(Success(Map.empty))
        values: Vector[Value[Any]]      <- fields.get("values").map(parseValues).getOrElse(Success(Vector.empty))
        altName: Vector[QlmID]          <- fields.get("altname").map(parseID).getOrElse(Success(Vector.empty))
        metaData: Option[MetaData]      <- fields.get("MetaData").map(parseMetaData(path,_).map(Some(_))).getOrElse(Success(None))
        description: Set[Description]   <- fields.get("description").map(parseDescriptions).getOrElse(Success(Set.empty))
      } yield InfoItem(name,path,typev,altName,description,values,metaData,attributes)

    }

    jval match {
      case obj: JObject => Try(Seq(parseInfoItem(obj)))
      case JArray(arr: List[JObject]) => Try(arr.map(parseInfoItem(_)))
      case other => throw ODFParserError("Invalid JSON type for ODF InfoItem")
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
      val value = fields.get("value").map{
        case JString(s) => s
        case JDouble(num) => num
        case JInt(num) =>
          if(num.isValidLong)
            num.longValue()
          else num
        case JBool(b) => b
        case obj: JObject => parseObjects(obj)
        case other => throw ODFParserError("Invalid JSON type for ODF Value")
      }
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
            throw ODFParserError("unixTime too big")
        }
        case Some(JDouble(num)) => {
          new Timestamp( (num*1000).toLong)

        }
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

    parseInfoItems(path,jval).map(ii => MetaData(ii.toVector))

  }

}