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
package types
package odf
package parser

import java.sql.Timestamp
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap

import akka.NotUsed
import akka.util._
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl._

import types._
import odf._
import utils._

/** Parser for data in O-DF format */
object ODFStreamParser {
  def parser: Flow[String,ImmutableODF,NotUsed] = Flow[String]
    .map(ByteString(_))
    .via(XmlParsing.parser)
    .via(new ODFParserFlow)
  private class ODFParserFlow extends GraphStage[FlowShape[ParseEvent, ImmutableODF]] {
    val in = Inlet[ParseEvent]("ODParserFlowF.in")
    val out = Outlet[ImmutableODF]("ODFParserFlow.out")
    override val shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var state: State = new ODFState(None,currentTimestamp)


      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val event: ParseEvent = grab(in)
          state = state.parse(event) 
          state match {
            case fail: FailedState =>
              throw ODFParserError(fail.msg)
            case complete: CompleteState =>
              push(out,complete.odf)
            case other: State =>
          }
          pull(in)
        }

        override def onUpstreamFinish(): Unit = {
          completeStage()
        }
      })
    }
  }
}
  private trait State {
    def parse(event: ParseEvent): State
    def previous: Option[State]
  }
  private class FailedState(val previous: Option[State], val msg: String, implicit val receiveTime: Timestamp)  extends State{
    def parse( event: ParseEvent ): State = {
      this
    }
  } 
  private class CompleteState(val odf: ImmutableODF, val previous: Option[State], implicit val receiveTime: Timestamp)  extends State{
    def parse( event: ParseEvent ): State = {
      this
    }
  } 

  private class ODFState(  val previous: Option[State], implicit val receiveTime: Timestamp)  extends State{
    private var position: Int = 0
    private var odf: MutableODF = MutableODF()
    def addSubNode( node: Node) : State = {
      odf = odf.add(node)
      this
    }
    def addSubNodes( nodes: Iterable[Node] ) ={
      odf = odf.addNodes(nodes)
      this
    }
    def parse( event: ParseEvent ): State = {
      event match{
        case startElement: StartElement if startElement.localName == "Objects" =>
          if( position == 0){
            val version = startElement.attributes.get("version")
            val attributes = startElement.attributes.filter( _._1 != "version")
            odf.add( Objects(version, attributes))
            this
          } else 
            new FailedState(Some(this),"Objects is defined twice", receiveTime)

        case startElement: StartElement if startElement.localName == "Object" =>
          new ObjectState(Path("Objects"),Some(this),receiveTime)
        case endElement: EndElement if endElement.localName == "Objects" =>
          previous match {
            case Some( state: ValueState ) =>
              state.addODF(odf)
            case None =>
              new CompleteState(odf.immutable,Some(this),receiveTime)// Complete
          }
      }
    }
  }

  private class ObjectState( val parentPath:Path,  val previous: Option[State], implicit  val receiveTime: Timestamp)  extends State{
    private var subNodes: List[Node] = List.empty
    private var mainId = ""
    private var path: Path = parentPath
    private var typeAttribute: Option[String] = None
    private var descriptions: List[Description] = List.empty
    private var ids: List[QlmID] = List.empty
    private var position: Int = 0
    def addSubNode( node: Node) : State = {
      subNodes = node :: subNodes
      this
    }
    def addSubNodes( nodes: List[Node]) : State = {
      subNodes = nodes ++ subNodes
      this
    }

    def addId( id: QlmID ): State  ={
      ids = id :: ids
      if( mainId.isEmpty ){
        mainId = id.id
        path = parentPath / mainId
      }
      this
    } 
    def addDescription( desc: Description): State ={
      descriptions = desc :: descriptions
      this
    } 
    def parse( event: ParseEvent ): State = {
      event match{
        case startElement: StartElement if startElement.localName == "Object" =>
          if( position == 0){
            typeAttribute = startElement.attributes.get("type")
            position = 1
            this
          } else {
            if(mainId.nonEmpty){
              position = 4
              new ObjectState( path, Some(this), receiveTime)
            } else new FailedState(Some(this),"No id defined for Object before child Object", receiveTime)
          }
        case startElement: StartElement if startElement.localName == "id" =>
          if( position <= 1){
            position = 1
            new IdState(Some(this),receiveTime)
          } else {
            new FailedState(Some(this),"id in wrong position, after description, InfoItem or Objects", receiveTime)
          }
        case startElement: StartElement if startElement.localName == "description" =>
          if( position <= 2){
            if(mainId.nonEmpty){
              position = 2
              new DescriptionState(Some(this),receiveTime)
            } else new FailedState(Some(this),"No id defined for Object before description", receiveTime)
          } else {
            new FailedState(Some(this),"description in wrong position, after InfoItem or Objects", receiveTime)
          }
        case startElement: StartElement if startElement.localName == "InfoItem" =>
          if( position <= 3){
            if(mainId.nonEmpty){
              position = 3
              new InfoItemState( path, Some(this),receiveTime)
            } else new FailedState(Some(this),"No id defined for Object before InfoItem", receiveTime)
          } else {
            new FailedState(Some(this),"InfoItem in wrong position, after Objects", receiveTime)
          }
        case endElement: EndElement if endElement.localName == "Object" =>
          if( mainId.nonEmpty ){

            val obj = Object(
              ids.toVector,
              path,
              typeAttribute,
              Description.unionReduce(descriptions.toSet)
            )
            previous match{
              case Some(state: ObjectState ) => state.addSubNodes(obj :: subNodes)
              case Some(state: ODFState ) => state.addSubNodes(obj :: subNodes)
            }
          } else new FailedState(Some(this),"No id defined for Object before end tag", receiveTime)
        case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
          new FailedState( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in Object",receiveTime)
        case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
          new FailedState( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in Object",receiveTime)

      }
    }
    val allowedElements = Set("Object","InfoItem","description","id")

  }

  private class MetaDataState(val infoItemPath: Path,  val previous: Option[InfoItemState], implicit  val receiveTime: Timestamp)  extends State{
    val allowedElements = Set("MetaData","InfoItem")
    private var position: Int = 0
    private var infoItems: List[InfoItem] = List.empty
    private var path = infoItemPath / "MetaData"
    def addInfoItem( ii: InfoItem) : State = {
      infoItems = ii :: infoItems
      this
    }
    def parse( event: ParseEvent ): State = {
      event match{
        case startElement: StartElement if startElement.localName == "MetaData" =>
          position = 1
          this
        case startElement: StartElement if startElement.localName == "MetaData" =>
          new InfoItemState(path,Some(this),receiveTime)
        case endElement: EndElement if endElement.localName == "MetaData" =>
          previous match{
            case Some(state: InfoItemState) => state.addMetaData( MetaData(infoItems.toVector))
            case Some(state: State) => new FailedState(Some(this), "MetaData after something else than InfoItem", receiveTime)
            case None =>
              new FailedState(Some(this), "No previous elements before MetaData", receiveTime)
          }
        case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
          new FailedState( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in MetaData",receiveTime)
        case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
          new FailedState( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in MetaData",receiveTime)
      }
    }
  }

  private class InfoItemState( val objectPath: Path,  val previous: Option[State], implicit  val receiveTime: Timestamp)  extends State{
    val allowedElements = Set("name","InfoItem","description","altName", "MetaData","value")
    private var position: Int = 0
    private var descriptions: List[Description] = List.empty
    private var values: List[Value[_]] = List.empty
    private var names: List[QlmID] = List.empty
    private var nameAttribute: String =""
    private var typeAttribute: Option[String] = None
    private var path: Path = objectPath
    private var metaData: Option[MetaData] = None
    def addMetaData( metaD: MetaData ) ={
      metaData = Some(metaD)
      this
    }
    def addDescription( desc: Description): State ={
      descriptions = desc :: descriptions
      this
    } 
    def addValue( value: Value[_]): State ={
      values = value :: values
      this
    } 
    def addName( name: QlmID ): State  ={
      names = name :: names
      this
    } 
    def parse( event: ParseEvent ): State = {
      event match{
        case startElement: StartElement if startElement.localName == "name" || startElement.localName == "altName" =>
          if( position == 0 )
            new IdState(Some(this),receiveTime).parse(event)
          else
            new FailedState(Some(this),"No names after descriptions", receiveTime)
        case startElement: StartElement if startElement.localName == "description" =>
          if( position <= 1 ){
            position = 1
            new DescriptionState(Some(this),receiveTime).parse(event)
          } else
            new FailedState(Some(this),"No descriptions after MetaData", receiveTime)
        case startElement: StartElement if startElement.localName == "MetaData" =>
          if( position <= 2 ){
            position = 2
            new MetaDataState(path,Some(this),receiveTime).parse(event)
          } else
            new FailedState(Some(this),"No descriptions after MetaData", receiveTime)
        case startElement: StartElement if startElement.localName == "value" =>
          position = 3
          new ValueState(Some(this),receiveTime).parse(event)
        case startElement: StartElement if startElement.localName == "InfoItem" =>
          nameAttribute = startElement.attributes.get("name").getOrElse("")
          typeAttribute = startElement.attributes.get("type")
          path = objectPath / nameAttribute
          this
        case endElement: EndElement if endElement.localName == "InfoItem" =>
          val ii = InfoItem(
            nameAttribute,
            path,
            typeAttribute,
            names.toVector,
            Description.unionReduce(descriptions.toSet),
            values.toVector,
            metaData
          )
          previous match{
            case Some(state:ObjectState) => state.addSubNode(ii)
            case Some(state:MetaDataState) => state.addInfoItem(ii)
          }
        case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
          new FailedState( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in InfoItem",receiveTime)
        case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
          new FailedState( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in InfoItem",receiveTime)
      }
    }
  }

  private class ValueState( val  previous: Option[State], implicit val  receiveTime: Timestamp)  extends State{
    val allowedElements = Set("Objects","value")
    private var valueO: Option[Value[_]] = None 
    def addODF( odf: ODF ): State ={
      valueO = valueO.map{
        case value: ODFValue =>
          value.copy(odf.immutable)
        case value: Value[_] =>
          ODFValue( odf, value.timestamp)
      }.orElse{
          Some(ODFValue( odf, receiveTime))
      }
      this
    }
    def parse( event: ParseEvent ): State = {
        event match {
          case startElement: StartElement if startElement.localName == "value" =>
            val typeAttribute = startElement.attributes.get("type")
            val dateTime = startElement.attributes.get("dateTime")
            val unixTime = startElement.attributes.get("unixTime")
            val timestamp = solveTimestamp(dateTime,unixTime,receiveTime)
            if( typeAttribute != "odf" ) {//Non O-DF values, TODO: Check other possible types
              valueO = Some( StringPresentedValue("",timestamp,typeAttribute.getOrElse("xs:string")))
              this
            } else{
              valueO = Some( ODFValue( ImmutableODF(), timestamp))
              new ODFState(Some(this),receiveTime)
            }

          case text: TextEvent => 
            valueO = valueO.headOption.map{
              case StringPresentedValue(str,timestamp,typeAttribute) =>
                Value.applyFromString(str + text.text, typeAttribute, timestamp)
            }
            this

          case endElement: EndElement if endElement.localName == "value" =>
            valueO match{
              case Some(value: Value[_])=>
                previous match{
                  case Some(iiState: InfoItemState) =>
                    iiState.addValue( value )
                  case Some(other: State) =>
                    new FailedState(Some(this),"Value state should be only after InfoItem state",receiveTime)
                }
              case None =>
                new FailedState(Some(this),"Nonexisting value", receiveTime)
            }
          //ODFValue parsing
          case startElement: StartElement if startElement.localName == "Objects" =>
            new FailedState(Some(this),"Objects inside value without proper type given for value", receiveTime)
          case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
            new FailedState( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in value",receiveTime)
          case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
            new FailedState( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in value",receiveTime)
        }
    }
  }

  private class DescriptionState( val  previous: Option[State], implicit val  receiveTime: Timestamp)  extends State {
    val allowedElements = Set("description")
    private var descriptionO: Option[Description] = None 
    def parse( event: ParseEvent ): State = {
        event match {
          case startElement: StartElement if startElement.localName == "description" =>
            val lang = startElement.attributes.get("lang")
            descriptionO = Some( Description( "", lang) )
            this
          case content: TextEvent => 
            descriptionO = descriptionO.map{
              case Description( text, lang) => Description( text + content.text, lang)
            }
            this

          case endElement: EndElement if endElement.localName == "description" =>
            descriptionO match{
              case Some(desc: Description) =>
                previous match{
                 case Some(state: InfoItemState) =>state.addDescription( desc ) 
                 case Some(state: ObjectState )=> state.addDescription( desc ) 
                 case Some(state: State) => new FailedState(Some(this),"Description state after wrong state. Previous should be InfoItem or Object",receiveTime)
                }
              case None => previous.getOrElse(new FailedState(Some(this),"Description state after no state. Previous should be InfoItem or Object",receiveTime))
            }
          case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
            new FailedState( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in description",receiveTime)
          case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
            new FailedState( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in description",receiveTime)
        }
    }
  }

  private class IdState(  val previous: Option[State], implicit  val receiveTime: Timestamp)  extends State {
    val allowedElements = Set("id","name","altName")
    private var idO: Option[QlmID] = None 
    def parse( event: ParseEvent ): State = {
        event match {
          case startElement: StartElement if startElement.localName == "id" || startElement.localName == "name" || startElement.localName == "altName"  =>
            val tagType = startElement.attributes.get("tagType")
            val idType = startElement.attributes.get("idType")
            val startDate = startElement.attributes.get("startDate").map{
              str => dateTimeStrToTimestamp(str)
            }
            val endDate = startElement.attributes.get("startDate").map{
              str => dateTimeStrToTimestamp(str)
            }
            idO = Some( QlmID( "", idType,tagType,startDate,endDate) )
            this
          case content: TextEvent => 
            idO = idO.map{
              case id: QlmID => id.copy(id = id.id + content.text)
            }
            this

          case endElement: EndElement if endElement.localName == "id" || endElement.localName == "name" || endElement.localName == "altName"  =>
            idO match{
              case Some(id: QlmID) =>
                previous match{
                 case Some(state: InfoItemState) =>state.addName( id ) 
                 case Some(state: ObjectState )=> state.addId( id ) 
                 case Some(state: State) => new FailedState(Some(this),"Id state after wrong state. Previous should be InfoItem or Object",receiveTime)
                }
              case None => previous.getOrElse(new FailedState(Some(this),"Id state after no state. Previous should be InfoItem or Object",receiveTime))
            }
          case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
            new FailedState( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in name, altName or id",receiveTime)
          case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
            new FailedState( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in name, altName or id",receiveTime)
        }
    }
  }
  
