/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
+    Copyright (c) 2019 Aalto University.                                        +
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
package types
package odf
package parser

import java.sql.Timestamp
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.util.{Try, Failure, Success}

import akka.NotUsed
import akka.util._
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl._

import types._
import odf._
import types.OmiTypes.parser.{ResultEventBuilder,OdfRequestEventBuilder}
import utils._


class ODFEventBuilder( 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends EventBuilder[ImmutableODF]{
  private var position: Int = 0
  private var odf: MutableODF = MutableODF()
  private var complete: Boolean = false 
  final def isComplete: Boolean = previous.isEmpty && complete
  def build: ImmutableODF = odf.toImmutable
  def addSubNodes( nodes: Iterable[Node] ) : ODFEventBuilder={
    odf = odf.addNodes(nodes)
    this
  }
  def parse( event: ParseEvent ): EventBuilder[_] = {
    event match{
      case startElement: StartElement if startElement.localName == "Objects" =>
        if( position == 0){
          val correctNS = startElement.namespace.forall{
            str: String =>
              str.startsWith("http://www.opengroup.org/xsd/odf/")
          }
          if( !correctNS )
            throw ODFParserError("Wrong namespace url.")
          val version = startElement.attributes.get("version")
        val attributes = startElement.attributes.filter{ case (key:String,_)=> key != "version"}
        odf.add( Objects(version, attributes))
        this
        } else 
          throw ODFParserError("Objects is defined twice")

      case startElement: StartElement if startElement.localName == "Object" =>
        new ObjectEventBuilder(Path("Objects"),Some(this),receiveTime).parse(event)
      case endElement: EndElement if endElement.localName == "Objects" =>
        previous match {
          case Some( state: ValueEventBuilder ) =>
            state.addODF(odf)
          case Some( state: OdfRequestEventBuilder[_] ) =>
            state.addODF(odf)
          case Some( state: ResultEventBuilder ) =>
            state.addODF(odf)
          case None =>
            complete = true
            this
        }
          case event: ParseEvent =>
            unexpectedEventHandle( " before Objects element.", event, this)
    }
  }
}

class ObjectEventBuilder(
  val parentPath: Path,
  val previous: Option[EventBuilder[_]], 
  implicit  val receiveTime: Timestamp = currentTimestamp
)  extends EventBuilder[Object]{
  private var subNodes: List[Node] = List.empty
  private var mainId = ""
  private var path: Path = parentPath
  private var typeAttribute: Option[String] = None
  private var descriptions: List[Description] = List.empty
  private var ids: List[QlmID] = List.empty
  private var complete: Boolean = false 
  private var attributes: Map[String,String] = Map.empty
  final def isComplete: Boolean = previous.isEmpty && complete
  def build: Object = Object(
    ids.toVector,
    path,
    typeAttribute,
    Description.unionReduce(descriptions.toSet),
    attributes
  )
    def addSubNode( node: Node): ObjectEventBuilder = {
      subNodes = node :: subNodes
      this
    }
    def addSubNodes( nodes: List[Node]): ObjectEventBuilder = {
      subNodes = nodes ++ subNodes
      this
    }

    def addId( id: QlmID ): ObjectEventBuilder  ={
      ids = id :: ids
      if( mainId.isEmpty ){
        mainId = id.id
        path = parentPath / mainId
      }
      this
    } 
    def addDescription( desc: Description): ObjectEventBuilder ={
      descriptions = desc :: descriptions
      this
    } 
    object Position extends Enumeration {
      type Position = Value
      val OpenTag, Ids, Descriptions, InfoItems, Objects, CloseTag = Value 
    }
    import Position._
    private var position: Position = OpenTag
    def parse( event: ParseEvent ): EventBuilder[_] = {
      position match {
        case OpenTag =>
          event match { 
            case startElement: StartElement if startElement.localName == "Object" =>
              typeAttribute = startElement.attributes.get("type")
              attributes = startElement.attributes -("type")
              position = Ids
              this
            case event: ParseEvent =>
              unexpectedEventHandle( s"before expected Object element inside parent $parentPath.", event, this)
          }
            case Ids => 
              event match { 
                case startElement: StartElement if startElement.localName == "id" =>
                  new IdEventBuilder(Some(this),receiveTime).parse(event)
                case event: ParseEvent if mainId.isEmpty =>
                  unexpectedEventHandle( s"before at least one Id element in Object inside parent $parentPath.", event, this)
                case startElement: StartElement if startElement.localName == "description" =>
                  position = Descriptions
                  parse(event)
                case startElement: StartElement if startElement.localName == "InfoItem" =>
                  position = InfoItems
                  parse(event)
                case startElement: StartElement if startElement.localName == "Object" =>
                  position = Objects
                  parse(event)
                case endElement: EndElement if endElement.localName == "Object" =>
                  position = CloseTag
                  parse(event)
                case event: ParseEvent =>
                  unexpectedEventHandle( s"after id in Object with id $mainId.", event, this)
              }
                case Descriptions => 
                  event match { 
                    case startElement: StartElement if startElement.localName == "description" =>
                      new DescriptionEventBuilder(Some(this),receiveTime).parse(event)
                    case startElement: StartElement if startElement.localName == "InfoItem" =>
                      position = InfoItems
                      parse(event)
                    case startElement: StartElement if startElement.localName == "Object" =>
                      position = Objects
                      parse(event)
                    case endElement: EndElement if endElement.localName == "Object" =>
                      position = CloseTag
                      parse(event)
                    case event: ParseEvent =>
                      unexpectedEventHandle( s"after description inside Object with id $mainId.", event, this)
                  }
                    case InfoItems => 
                      event match { 
                        case startElement: StartElement if startElement.localName == "InfoItem" =>
                          new InfoItemEventBuilder( path, Some(this),receiveTime).parse(event)
                        case startElement: StartElement if startElement.localName == "Object" =>
                          position = Objects
                          parse(event)
                        case endElement: EndElement if endElement.localName == "Object" =>
                          position = CloseTag
                          parse(event)
                        case event: ParseEvent =>
                          unexpectedEventHandle( s"after InfoItem inside Object with id $mainId.", event, this)
                      }
                        case Objects => 
                          event match { 
                            case startElement: StartElement if startElement.localName == "Object" =>
                              new ObjectEventBuilder( path, Some(this), receiveTime).parse(event)
                            case endElement: EndElement if endElement.localName == "Object" =>
                              position = CloseTag
                              parse(event)
                            case event: ParseEvent =>
                              unexpectedEventHandle( s"after Object inside Object with id $mainId.", event, this)
                          }
                            case CloseTag => 
                              event match { 
                                case event: ParseEvent if complete =>
                                  unexpectedEventHandle( s"after complete Object with id $mainId.", event, this)
                                case endElement: EndElement if endElement.localName == "Object" =>
                                  if( mainId.nonEmpty ){

                                    val obj = build
                                    previous match{
                                      case Some(state: ObjectEventBuilder ) => state.addSubNodes(obj :: subNodes)
                                      case Some(state: ODFEventBuilder ) => state.addSubNodes(obj :: subNodes)
                                      case None =>
                                        complete = true
                                        this
                                    }
                                  } else throw ODFParserError("No ids found for Object")
                                      case event: ParseEvent =>
                                        unexpectedEventHandle( "after Object end.", event, this)
                              }
      }
    }

}

class MetaDataEventBuilder(
  val infoItemPath: Path,
  val previous: Option[InfoItemEventBuilder], 
  implicit  val receiveTime: Timestamp = currentTimestamp
) extends EventBuilder[MetaData]{
  private var infoItems: List[InfoItem] = List.empty
  private var path = infoItemPath / "MetaData"
  private var complete: Boolean = false 
  final def isComplete: Boolean = previous.isEmpty && complete
  def build: MetaData = MetaData( infoItems.toVector)

  def addInfoItem( ii: InfoItem): MetaDataEventBuilder = {
    infoItems = ii :: infoItems
    this
  }
  object Position extends Enumeration {
    type Position = Value
    val OpenTag, InfoItems, CloseTag = Value 
  }
  import Position._
  private var position: Position = OpenTag
  def parse( event: ParseEvent ): EventBuilder[_] = {
    position match{
      case OpenTag =>
        event match{
          case startElement: StartElement if startElement.localName == "MetaData" =>
            position = InfoItems
            this
          case event: ParseEvent =>
            unexpectedEventHandle( s"before expected MetaData element in InfoItem ${infoItemPath.toString}.", event, this)
        }
          case InfoItems =>
            event match{
              case startElement: StartElement if startElement.localName == "InfoItem" =>
                new InfoItemEventBuilder(path,Some(this),receiveTime).parse(event)
              case endElement: EndElement if endElement.localName == "MetaData" =>
                position = CloseTag
                parse(event)
              case event: ParseEvent =>
                unexpectedEventHandle( s"when expected InfoItem elements in MetaData in InfoItem ${infoItemPath.toString}.", event, this)
            }
              case CloseTag =>
                event match{
                  case event: ParseEvent if complete =>
                    unexpectedEventHandle( s"after complete MetaData in InfoItem ${infoItemPath.toString}.", event, this)
                  case endElement: EndElement if endElement.localName == "MetaData" =>
                    position = CloseTag
                    complete = true
                    previous match{
                      case Some(state: InfoItemEventBuilder) => state.addMetaData( build )
                      case Some(state: EventBuilder[_]) => throw ODFParserError("MetaData after something else than InfoItem")
                      case None =>
                        this
                    }
                      case event: ParseEvent =>
                        unexpectedEventHandle( s"before expected closing of MetaData element in InfoItem ${infoItemPath.toString}.", event, this)
                }
    }
  }
}

class InfoItemEventBuilder(
  val objectPath: Path, 
  val previous: Option[EventBuilder[_]],
  implicit val receiveTime: Timestamp = currentTimestamp
) extends EventBuilder[InfoItem]{
  private var descriptions: List[Description] = List.empty
  private var values: List[Value[_]] = List.empty
  private var names: List[QlmID] = List.empty
  private var nameAttribute: String =""
  private var typeAttribute: Option[String] = None
  private var path: Path = objectPath
  private var metaData: Option[MetaData] = None
  private var attributes: Map[String,String] = Map.empty
  private var complete: Boolean = false 
  final def isComplete: Boolean = previous.isEmpty && complete
  def build: InfoItem = {
    InfoItem(
      nameAttribute,
      path,
      typeAttribute,
      names.toVector,
      Description.unionReduce(descriptions.toSet),
      values.toVector.sortBy(_.timestamp.getTime),
      metaData,
      attributes
    )
  }
  def addMetaData( metaD: MetaData ): InfoItemEventBuilder ={
    metaData = Some(metaD)
    this
  }
  def addDescription( desc: Description): InfoItemEventBuilder ={
    descriptions = desc :: descriptions
    this
  } 
  def addValue( value: Value[_]): InfoItemEventBuilder ={
    values = value :: values
    this
  } 
  def addName( name: QlmID ): InfoItemEventBuilder  ={
    names = name :: names
    this
  } 
  object Position extends Enumeration {
    type Position = Value
    val OpenTag, Names, Descriptions, MetaData, Values, CloseTag = Value 
  }
  import Position.{OpenTag, Names, Descriptions, MetaData, Values, CloseTag, Position }
  private var position: Position = OpenTag
  def parse( event: ParseEvent ): EventBuilder[_] = {
    position match{
      case OpenTag =>
        event match {
          case startElement: StartElement if startElement.localName == "InfoItem" =>
            nameAttribute = startElement.attributes.get("name").getOrElse("")
            typeAttribute = startElement.attributes.get("type")
            path = objectPath / nameAttribute
            position = Names
            attributes = startElement.attributes -("name","type")
            this
          case event: ParseEvent =>
            unexpectedEventHandle( s"before expected InfoItem element under O-DF path ${objectPath}.", event, this)
        }
          case Names =>
            event match {
              case startElement: StartElement if startElement.localName == "name" || startElement.localName == "altName" =>
                new IdEventBuilder(Some(this),receiveTime).parse(event)
              case startElement: StartElement if startElement.localName == "description" =>
                position = Descriptions
                parse(event)
              case startElement: StartElement if startElement.localName == "MetaData" =>
                position = MetaData
                parse(event)
              case startElement: StartElement if startElement.localName == "value" =>
                position = Values
                parse(event)
              case endElement: EndElement if endElement.localName == "InfoItem" =>
                position = CloseTag
                parse(event)
              case event: ParseEvent =>
                unexpectedEventHandle( s"after additional names in InfoItem with name: $nameAttribute.", event, this)
            }
              case Descriptions =>
                event match {
                  case startElement: StartElement if startElement.localName == "description" =>
                    position = Descriptions
                    new DescriptionEventBuilder(Some(this),receiveTime).parse(event)
                  case startElement: StartElement if startElement.localName == "MetaData" =>
                    position = MetaData
                    parse(event)
                  case startElement: StartElement if startElement.localName == "value" =>
                    position = Values
                    parse(event)
                  case endElement: EndElement if endElement.localName == "InfoItem" =>
                    position = CloseTag
                    parse(event)
                  case event: ParseEvent =>
                    unexpectedEventHandle( s"after descriptions in InfoItem with name: $nameAttribute.", event, this)
                }
                  case MetaData =>
                    event match {
                      case startElement: StartElement if startElement.localName == "MetaData" =>
                        position = MetaData
                        new MetaDataEventBuilder(path,Some(this),receiveTime).parse(event)
                      case startElement: StartElement if startElement.localName == "value" =>
                        position = Values
                        parse(event)
                      case endElement: EndElement if endElement.localName == "InfoItem" =>
                        position = CloseTag
                        parse(event)
                      case event: ParseEvent =>
                        unexpectedEventHandle( s"after MetaData in InfoItem with name: $nameAttribute.", event, this)
                    }
                      case Values =>
                        event match {
                          case startElement: StartElement if startElement.localName == "value" =>
                            position = Values
                            new ValueEventBuilder(Some(this),receiveTime).parse(event)
                          case endElement: EndElement if endElement.localName == "InfoItem" =>
                            position = CloseTag
                            parse(event)
                          case event: ParseEvent =>
                            unexpectedEventHandle( s"after values in InfoItem with name: $nameAttribute.", event, this)
                        }
                          case CloseTag =>
                            event match {
                              case event: ParseEvent if complete =>
                                unexpectedEventHandle( s"after complete InfoItem with name: $nameAttribute.", event, this)
                              case endElement: EndElement if endElement.localName == "InfoItem" =>
                                val ii = build
                                complete = true
                                previous match{
                                  case Some(state:ObjectEventBuilder) => state.addSubNode(ii)
                                  case Some(state:MetaDataEventBuilder) => state.addInfoItem(ii)
                                  case None =>
                                    this
                                }
                                  case event: ParseEvent =>
                                    unexpectedEventHandle( s"before closing InfoItem with name: $nameAttribute.", event, this)
                            }
    }
  }
}

class ValueEventBuilder(
  val  previous: Option[EventBuilder[_]],
  implicit val  receiveTime: Timestamp = currentTimestamp)
extends EventBuilder[Value[_]]{
  private var timestamp: Timestamp = receiveTime
  private var valueStr: String = "" 
  private var typeAttribute: String = "xs:string" 
  private var odf: Option[ODF] = None
  private var complete: Boolean = false 
  final def isComplete: Boolean = previous.isEmpty && complete
  def build: Value[_] = odf.map{
    o_df: ODF => ODFValue(o_df,timestamp)
  }.getOrElse(Value.applyFromString( valueStr, typeAttribute, timestamp))
  def addODF( _odf: ODF ): ValueEventBuilder ={
    odf = Some(_odf)
    this
  }
  object Position extends Enumeration {
    type Position = Value
    val OpenTag, Content, CloseTag = Value 
  }
  import Position._
  private var position: Position = OpenTag
  def parse( event: ParseEvent ): EventBuilder[_] = {
    position match{
      case OpenTag =>
        event match {
          case startElement: StartElement if startElement.localName == "value" =>
            typeAttribute = startElement.attributes.get("type").getOrElse(typeAttribute)
            val dateTime = Try{
              startElement.attributes.get("dateTime").map{
                str => dateTimeStrToTimestamp(str)
              }
            } match {
              case Failure(t) =>
                throw ODFParserError("Invalid dateTime for value.")
              case Success(ts) => ts
            }
            val unixTime = Try{
              startElement.attributes.get("unixTime").map{
                str => 
                  new Timestamp((str.toDouble * 1000.0).toLong)
              }
            }.getOrElse{
              throw ODFParserError("Invalid unixTime for value.")
            }
            timestamp = solveTimestamp(dateTime,unixTime,receiveTime)
            position = Content
            this
                    case event: ParseEvent =>
                      unexpectedEventHandle( "before expected value element.", event, this)
        }
                    case Content =>
                      event match {
                        case endElement: EndElement if endElement.localName == "value" =>
                          position = CloseTag
                          parse(event)
                        case startElement: StartElement if startElement.localName == "Objects" && typeAttribute == "odf" =>
                          position = CloseTag
                          new ODFEventBuilder(Some(this),receiveTime)
                        case startElement: StartElement if startElement.localName == "Objects" =>
                          throw ODFParserError(s"Objects inside value without correct type attribute. Found attribute $typeAttribute.")
                        case content: TextEvent if typeAttribute == "odf" => 
                          throw ODFParserError(s"Expected Objects inside value because of type attribute $typeAttribute.")
                        case content: TextEvent => 
                          valueStr = valueStr + content.text
                          position = CloseTag
                          this
                        case event: ParseEvent =>
                          unexpectedEventHandle( "when expected text content.", event, this)
                      }
                        case CloseTag =>
                          event match {
                            case event: ParseEvent if complete =>
                              unexpectedEventHandle( s"after complete value.", event, this)
                            case content: TextEvent => 
                              valueStr = valueStr+ content.text
                              position = CloseTag
                              this
                            case content: TextEvent if typeAttribute == "odf" => 
                              throw ODFParserError(s"Textafter Objects inside value.")
                            case endElement: EndElement if endElement.localName == "value" =>
                              complete = true
                              previous match {
                                case Some(state: InfoItemEventBuilder) => 
                                  state.addValue( build)
                                case Some(state: EventBuilder[_]) => throw ODFParserError("Value state after wrong state. Previous should be InfoItem.")
                                case None =>
                                  this
                              }
                                case event: ParseEvent =>
                                  unexpectedEventHandle( s"before expected closing of Value.", event, this)
                          }
    }
  }
}

class DescriptionEventBuilder(
  val  previous: Option[EventBuilder[_]], 
  implicit val  receiveTime: Timestamp = currentTimestamp
) extends EventBuilder[Description] {
  private var lang: Option[String] = None 
  private var text: String = ""
  private var complete: Boolean = false 
  final def isComplete: Boolean = previous.isEmpty && complete
  def build: Description = Description( text, lang)
  object Position extends Enumeration {
    type Position = Value
    val OpenTag, Content, CloseTag = Value 
  }
  import Position._
  private var position: Position = OpenTag
  def parse( event: ParseEvent ): EventBuilder[_] = {
    position match{
      case OpenTag =>
        event match {
          case startElement: StartElement if startElement.localName == "description" =>
            lang = startElement.attributes.get("lang")
            position = Content
            this
          case event: ParseEvent =>
            unexpectedEventHandle( "before expected description element.", event, this)
        }
          case Content =>
            event match {
              case content: TextEvent => 
                text = text + content.text
                this
              case endElement: EndElement if endElement.localName == "description" =>
                position = CloseTag
                this.parse(event)
              case event: ParseEvent =>
                unexpectedEventHandle( "when expected text content.", event, this)
            }
              case CloseTag =>
                event match {
                  case event: ParseEvent if complete =>
                    unexpectedEventHandle( s"after complete description.", event, this)
                  case content: TextEvent => 
                    text = text + content.text
                    this
                  case endElement: EndElement if endElement.localName == "description" =>
                    complete = true
                    previous match {
                      case Some(state: InfoItemEventBuilder) => 
                        state.addDescription(build)
                      case Some(state: ObjectEventBuilder ) =>  
                        state.addDescription(build)
                      case Some(state: EventBuilder[_]) => throw ODFParserError("Description state after wrong state. Previous should be InfoItem or Object")
                      case None =>
                        this
                    }
                      case event: ParseEvent =>
                        unexpectedEventHandle( s"before expected closing of description.", event, this)
                }
    }
  }
}

class IdEventBuilder( 
  val previous: Option[EventBuilder[_]], 
  implicit  val receiveTime: Timestamp = currentTimestamp
) extends EventBuilder[QlmID] {
  private var openingTag: String = "id"
  private var tagType: Option[String] = None
  private var idType: Option[String] = None
  private var startDate: Option[Timestamp] = None
  private var endDate: Option[Timestamp] = None
  private var complete: Boolean = false 
  private var id: String = ""
  private var attributes: Map[String,String] = Map.empty
  final def isComplete: Boolean = previous.isEmpty && complete
  def build: QlmID = QlmID(id,idType,tagType,startDate,endDate,attributes)
  object Position extends Enumeration {
    type Position = Value
    val OpenTag, Content, CloseTag = Value 
  }
  import Position._
  private var position: Position = OpenTag
  def parse( event: ParseEvent ): EventBuilder[_] = {
    position match{
      case OpenTag =>
        event match {
          case startElement: StartElement if startElement.localName == "id" || startElement.localName == "name" || startElement.localName == "altName"  =>
            position = Content
            openingTag = startElement.localName
            tagType = startElement.attributes.get("tagType")
            idType = startElement.attributes.get("idType")
            startDate = startElement.attributes.get("startDate").map{
              str => 
                Try{
                  dateTimeStrToTimestamp(str)
                  }.getOrElse{
                    throw ODFParserError(s"Invalid startDate attribute for ${startElement.localName} element.")
                  }
            }
            endDate = startElement.attributes.get("endDate").map{
              str => 
                Try{
                  dateTimeStrToTimestamp(str)
                  }.getOrElse{
                    throw ODFParserError(s"Invalid endDate attribute for ${startElement.localName} element.")
                  }
            }

            attributes = startElement.attributes -("tagType","idType","startDate","endDate")
            this
          case event: ParseEvent =>
            unexpectedEventHandle( "before expected id, name or altName element.", event, this)
        }
      case Content =>
        event match {
          case content: TextEvent => 
            id = id+ content.text
            position = CloseTag
            this
          case event: ParseEvent =>
            unexpectedEventHandle( "when expected text content.", event, this)
        }
      case CloseTag =>
        event match {
          case event: ParseEvent if complete =>
            unexpectedEventHandle( s"after complete $openingTag element.", event, this)
          case content: TextEvent => 
            id = id+ content.text
            this
          case endElement: EndElement if endElement.localName == openingTag =>
            previous match {
              case Some(state: InfoItemEventBuilder) => 
                if( openingTag == "name" || openingTag == "altName" )
                  state.addName( build ) 
                else
                  throw ODFParserError("id element should not be used for InfoItem")
              case Some(state: ObjectEventBuilder ) =>  
                if( openingTag == "id" )
                  state.addId( build ) 
                else
                  throw ODFParserError("name or altName element should not be used for Object")
              case Some(state: EventBuilder[_]) => throw ODFParserError("Id state after wrong state. Previous should be InfoItem or Object")
              case None =>
                complete = true
                this
            }
              case event: ParseEvent =>
                unexpectedEventHandle( s"before expected closing of $openingTag", event, this)
        }
    }
  }
}

