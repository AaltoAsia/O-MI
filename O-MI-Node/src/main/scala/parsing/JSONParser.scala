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

import org.json4s._
import org.json4s.native.JsonMethods.{parse => jparse}
import types.{ODFParserError, Path}
import types.odf._

import scala.collection.immutable.HashMap

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
  val test2 = """"Objects": {
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
              }"""

  //def parse(in:String) = parse(StringInput(in))
  //def parse(in:Reader) = parse(ReaderInput(in))
  //def parse(in: InputStream) = parse(StreamInput(in))
  //def parse(in: File) = parse(FileInput(in))




  def parse(in: JsonInput): ODF = {
    handleASTRoot(jparse(in))
  }


  private def handleASTRoot(in: JValue) = {
    in match {
      case JObject(asd) =>
    }
    ???
  }
  private def parseOmiEnvelope() = {

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

  private def parseObjects(in: JObject) = {
    val path = Path("Objects")
    val fields: Map[String, JValue] = in.obj.toMap
    val version = fields.get("version").map(parseStringAttribute)
    val prefix: Map[String,String] = Map.empty ++
        fields       //Map[String,JValue]
          .get("prefix") //Option[JValue]
          .map(parseStringAttribute)//Option[String]
          .map(p => "prefix" -> p) //Option[(String,String)] add to map as "list" of tuples
    val attributes: Map[String,String] = prefix

    val objects: Option[Seq[Node]] = fields.get("object").map{
      case obj: JObject => parseObject(path, obj)
      case JArray(arr: List[JObject]) => arr.flatMap(parseObject(path,_))
      case other => throw ODFParserError(s"'object' must be either object or array of objects")
    }

    val nodes: Seq[Node] = Objects(version, attributes) :: objects.toList.flatten
    ImmutableODF(nodes)

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
  private def parseID(in: JValue): Vector[QlmID] = {

    def parseArrayValue(in: JValue): QlmID = {
      in match {
        case JString(s) => QlmID(s)
        case obj: JObject => parseQlmID(obj)
        case other => throw ODFParserError(s"Invalid id type inside id array")
      }
    }

    in match {
      case JString(s) => Vector(QlmID(s))
      case obj: JObject => Vector(parseQlmID(obj))
      case JArray(arr: List[JValue]) => arr.map(parseArrayValue).toVector
      case other => throw ODFParserError(s"Id must be one of: String, Array Object.")
    }

  }

  private def parseAttributes(in:JValue): Map[String,String] = {
    in match {
      case jobj: JObject =>
        jobj.obj.toMap.mapValues(parseStringAttribute)
      case other => throw ODFParserError("Invalid type for attributes object: JSON object expected")
    }
  }

  private def parseObject(parentPath: Path, jval: JValue): Seq[Node] = {

    def _parseObject(in: JObject): Seq[Node] = {
      val fields: Map[String, JValue] = in.obj.toMap

      val id: Vector[QlmID] = fields.get("id").map(parseID).getOrElse(throw new ODFParserError("Id missing"))
      val path: Path = parentPath./(id.head) // id must not be empty id is required
      val oType: Option[String] = fields.get("type").map(parseStringAttribute)
      val attributes: Map[String, String] = fields.get("attributes").map(parseAttributes).getOrElse(Map.empty)
      val objects: Seq[Node] = fields.get("Object").map(parseObject(path,_)).toSeq.flatten
      val infoitems: Seq[InfoItem] = fields.get("InfoItem").map(parseInfoItems(parentPath,_)).toSeq.flatten
      val description: Set[Description] = fields.get("description").map(parseDescription).getOrElse(Set.empty)
      val thisObject: Object = Object(id,path,oType,description,attributes)

       Seq(thisObject) ++  objects ++ infoitems
    }

    jval match {
      case in: JObject => _parseObject(in)
      case JArray(arr: List[JObject]) => arr.flatMap(_parseObject(_))
      case other => throw ODFParserError("Invalid JSON type for ODF Object")
    }
  }
  private def parseInfoItems(parentPath: Path, jval: JValue): Seq[InfoItem] = {
    def parseInfoItem(in: JObject): InfoItem = {
     val fields: Map[String,JValue]  = in.obj.toMap

      val name: String = fields.get("name").map(parseStringAttribute)
        .getOrElse(throw ODFParserError("Name missing from InfoItem"))
      val path: Path = parentPath./(name)
      val altName: Vector[QlmID] = fields.get("altname").map(parseID).toVector.flatten
      val typev: Option[String] = fields.get("type").map(parseStringAttribute)
      val attributes: Map[String, String] = fields.get("attributes").map(parseAttributes).getOrElse(Map.empty)
      val values: Vector[Value[Any]] = fields.get("values").map(parseValues).toVector.flatten
      val metaData: Option[MetaData] = fields.get("MetaData").map(parseMetaData)
      val description: Set[Description] = fields.get("description").map(parseDescription).getOrElse(Set.empty)

      InfoItem(name,path,typev,altName,description,values,metaData,attributes)
    }

    jval match {
      case obj: JObject => Seq(parseInfoItem(obj))
      case JArray(arr: List[JObject]) => arr.map(parseInfoItem(_))
      case other => throw ODFParserError("Invalid JSON type for ODF InfoItem")
    }
  }

  private def parseValues(jval: JValue): Vector[Value[Any]] = {
    def parseValue(in: JObject): Value[Any] = {
      ???
    }

    jval match {
      case obj: JObject => Vector(parseValue(obj))
      case JArray(arr: List[JObject]) => arr.map(parseValue).toVector
      case other => throw ODFParserError("Invalid JSON type for ODF Value")
    }
  }

  private def parseDescription(jval: JValue): Set[Description] = {
    ???
  }

  private def parseMetaData(jval: JValue): MetaData = {
    ???
  }

}

/*
  //Element name strings
  //private val omiEnvelope = "omiEnvelope"
  //private val callback = "callback"
  //private val msgformat = "msgformat"
  //private val targetType = "targetType"
  //private val nodeList = "nodeList"
  //private val requestId = "requestId"
  //private val msg = "msg"
  //private val version = "version"
  //private val authorization = "authorization"
  //private val read = "read"
  //private val write = "write"
  //private val response = "response"
  //private val cancel = "cancel"
  //private val call = "call"
  //private val delete = "delete"
  //private val interval = "interval"
  //private val oldest = "oldest"
  //private val begin = "begin"
  //private val end = "end"
  //private val newest = "newest"
  //private val all = "all"
  //private val maxlevels = "maxlevels"
  //private val result = "result"
  //private val returnCode = "returnCode"
  //private val description = "description"














  //val testParser: JsonParser.Parser => String = (p: JsonParser.Parser) => {
  //  def parse: String = p.nextToken match {
  //    case FieldStart("test") => p.nextToken match {
  //      case StringVal(test) => test
  //      case _ => p.fail("expected int")
  //    }
  //    case End => p.fail("test not found")
  //    case _ => parse
  //  }

  //  parse
  //}
  //val testR: String = JsonParser.parse(json,testParser)
  private def parseVersion(p: JsonParser.Parser): String = {
    p.nextToken match {
      case OpenObj => p.nextToken match {
        case FieldStart("omiEnvelope") => p.nextToken match {
          case OpenObj => p.nextToken match {
            case FieldStart("version") => p.nextToken match {
              case StringVal(version) => version
              case _ => p.fail("Version must be string")
            }
            case _ => p.fail("Object omiEnvelope must contain version key")
          }
          case _ => p.fail("Invalid O-MI structure")
        }
        case _ => p.fail("O-MI must start with omiEnvelope ")
      }
      case _ => p.fail("Invalid O-MI structure")
    }
  }
  private def parseTtl(p:JsonParser.Parser): Double = {
    p.nextToken match {
      case FieldStart("ttl") => p.nextToken match {
        case IntVal(ttl) => ttl.toDouble ////ERROR CHECK
        case _ => p.fail("ttl must be integer")
      }
      case _ => p.fail("Object omienvelope must contain ttl key")
    }
  }

  val omiParser = (p:JsonParser.Parser) => {

    def parse:Int = p.nextToken match {
      case FieldStart("ttl") => p.nextToken match {
        case IntVal(number) => number.toInt
        case _ => p.fail("expexted Int")
      }
      case FieldStart("version") => ???

      case End => p.fail("element not found")
      case other => {
        println(other)
        parse
      }
    }
    val version: Int = parse
    version
  }
  val parseObjects = (p: JsonParser.Parser) => {
    var version: Option[String] = None
    var attributes: Map[String,String] = HashMap.empty
    var nodes: List[Node] = Nil
    def parse: ODF = p.nextToken match {
      case FieldStart("Object") => nodes = parseObject(p); parse
      case FieldStart("version") => version = parseStringAttribute(p); parse
      case FieldStart("prefix") => attributes + "prefix" -> parseStringAttribute(p); parse
      //case FieldStart("attributes") => ???   //TODO improve schema
      case CloseObj => ImmutableODF(Objects(version,attributes)::nodes)
      case End => p.fail("Invalid JSON")
      case other => parse //p.fail(s"Unexpected parse event $other") //TODO REMOVE??
    }
    parse
  }

  def parseAttributes(p: JsonParser.Parser): Map[String,String] = {
    ???
  }
  def parseStringAttribute(p:JsonParser.Parser): Option[String] = {
    p.nextToken match {
      case StringVal(value) => Some(value)
      case other => p.fail(s"expected: String found: $other")
    }
  }

  def parseObject(p:JsonParser.Parser): List[Node] = {
    var ids: Vector[QlmID] = Vector.empty
    var path: Path = Path.empty
    var typeAttribute: Option[String] = None
    var descriptions: Set[Description] = Set.empty
    var attributes: Map[String,String] = HashMap.empty
    var nodes: List[Node] = Nil

    def parse: List[Node] = p.nextToken match {
      case FieldStart("id") => ids = parseId(p); parse // what if ID is after ObjecT?!?!?!?
      case FieldStart("type") => typeAttribute = parseStringAttribute(p); parse
      case FieldStart("Object") => nodes = parseObject(p) ::: nodes; parse
      case FieldStart("InfoItem") => nodes = parseInfoItem(p) ::: nodes; parse
      case FieldStart("description") => descriptions = parseDescription(p); parse
      case FieldStart("attributes") => attributes = parseAttributes(p); parse //TODO unify schema
      case CloseObj => ???
      case End => p.fail("Invalid JSON")

    }

  }

  def parseInfoItem(p: JsonParser.Parser): List[Node] = {
    ???
  }

  def parseDescription(p: JsonParser.Parser): Set[Description] = {
    ???
  }

  def parseId(p:JsonParser.Parser): Vector[QlmID] = {
    p.nextToken match {
      case test => ???
    }
  }

  val res = JsonParser.parse(test,parseObjects) //omiParser)
  //JsonParser
  println(res)
*/
