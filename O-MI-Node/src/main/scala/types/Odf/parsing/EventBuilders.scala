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
  private class FailedEventBuilder(val previous: Option[EventBuilder[_]], val msg: String, implicit val receiveTime: Timestamp)  extends EventBuilder[ParseError]{
    def parse( event: ParseEvent ): EventBuilder[ParseError] = {
      this
    }
    final def isComplete: Boolean = false
    def build: ParseError = ODFParserError(msg)
  } 

  private class ODFEventBuilder(  val previous: Option[EventBuilder[_]], implicit val receiveTime: Timestamp)  extends EventBuilder[ODF]{
    private var position: Int = 0
    private var odf: MutableODF = MutableODF()
    private var complete: Boolean = false 
    final def isComplete: Boolean = previous.isEmpty && complete
    def build: ODF = odf.immutable
    def addSubNode( node: Node): ODFEventBuilder = {
      odf = odf.add(node)
      this
    }
    def addSubNodes( nodes: Iterable[Node] ) ={
      odf = odf.addNodes(nodes)
      this
    }
    def parse( event: ParseEvent ): EventBuilder[_] = {
      event match{
        case startElement: StartElement if startElement.localName == "Objects" =>
          if( position == 0){
            val version = startElement.attributes.get("version")
            val attributes = startElement.attributes.filter( _._1 != "version")
            odf.add( Objects(version, attributes))
            this
          } else 
            new FailedEventBuilder(Some(this),"Objects is defined twice", receiveTime)

        case startElement: StartElement if startElement.localName == "Object" =>
          new ObjectEventBuilder(Path("Objects"),Some(this),receiveTime).parse(event)
        case endElement: EndElement if endElement.localName == "Objects" =>
          previous match {
            case Some( state: ValueEventBuilder ) =>
              state.addODF(odf)
            case None =>
              complete = true
              this
          }
      }
    }
  }

  private class ObjectEventBuilder( val parentPath:Path,  val previous: Option[EventBuilder[_]], implicit  val receiveTime: Timestamp)  extends EventBuilder[Object]{
    private var subNodes: List[Node] = List.empty
    private var mainId = ""
    private var path: Path = parentPath
    private var typeAttribute: Option[String] = None
    private var descriptions: List[Description] = List.empty
    private var ids: List[QlmID] = List.empty
    private var position: Int = 0
    private var complete: Boolean = false 
    final def isComplete: Boolean = previous.isEmpty && complete
    def build: Object = Object(
              ids.toVector,
              path,
              typeAttribute,
              Description.unionReduce(descriptions.toSet)
            )
    def addSubNode( node: Node): ObjectEventBuilder = {
      subNodes = node:: subNodes
      this
    }
    def addSubNodes( nodes: List[Node]): ObjectEventBuilder = {
      subNodes = nodes ++ subNodes
      this
    }

    def addId( id: QlmID ): ObjectEventBuilder  ={
      ids = id:: ids
      if( mainId.isEmpty ){
        mainId = id.id
        path = parentPath / mainId
      }
      this
    } 
    def addDescription( desc: Description): ObjectEventBuilder ={
      descriptions = desc:: descriptions
      this
    } 
    def parse( event: ParseEvent ): EventBuilder[_] = {
      event match{
        case startElement: StartElement if startElement.localName == "Object" =>
          if( position == 0){
            typeAttribute = startElement.attributes.get("type")
            position = 1
            this
          } else {
            if(mainId.nonEmpty){
              position = 4
              new ObjectEventBuilder( path, Some(this), receiveTime).parse(event)
            } else new FailedEventBuilder(Some(this),"No id defined for Object before child Object", receiveTime)
          }
        case startElement: StartElement if startElement.localName == "id" =>
          if( position <= 1){
            position = 1
            new IdEventBuilder(Some(this),receiveTime).parse(event)
          } else {
            new FailedEventBuilder(Some(this),"id in wrong position, after description, InfoItem or Objects", receiveTime)
          }
        case startElement: StartElement if startElement.localName == "description" =>
          if( position <= 2){
            if(mainId.nonEmpty){
              position = 2
              new DescriptionEventBuilder(Some(this),receiveTime).parse(event)
            } else new FailedEventBuilder(Some(this),"No id defined for Object before description", receiveTime)
          } else {
            new FailedEventBuilder(Some(this),"description in wrong position, after InfoItem or Objects", receiveTime)
          }
        case startElement: StartElement if startElement.localName == "InfoItem" =>
          if( position <= 3){
            if(mainId.nonEmpty){
              position = 3
              new InfoItemEventBuilder( path, Some(this),receiveTime).parse(event)
            } else new FailedEventBuilder(Some(this),"No id defined for Object before InfoItem", receiveTime)
          } else {
            new FailedEventBuilder(Some(this),"InfoItem in wrong position, after Objects", receiveTime)
          }
        case endElement: EndElement if endElement.localName == "Object" =>
          if( mainId.nonEmpty ){

            val obj = build
            previous match{
              case Some(state: ObjectEventBuilder ) => state.addSubNodes(obj:: subNodes)
              case Some(state: ODFEventBuilder ) => state.addSubNodes(obj:: subNodes)
              case None =>
                complete = true
                this
            }
          } else new FailedEventBuilder(Some(this),"No id defined for Object before end tag", receiveTime)
        case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
          new FailedEventBuilder( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in Object",receiveTime)
        case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
          new FailedEventBuilder( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in Object",receiveTime)

      }
    }
    val allowedElements = Set("Object","InfoItem","description","id")

  }

  private class MetaDataEventBuilder(val infoItemPath: Path,  val previous: Option[InfoItemEventBuilder], implicit  val receiveTime: Timestamp)  extends EventBuilder[MetaData]{
    val allowedElements = Set("MetaData","InfoItem")
    private var position: Int = 0
    private var infoItems: List[InfoItem] = List.empty
    private var path = infoItemPath / "MetaData"
    private var complete: Boolean = false 
    final def isComplete: Boolean = previous.isEmpty && complete
    def build: MetaData = MetaData( infoItems.toVector)

    def addInfoItem( ii: InfoItem): MetaDataEventBuilder = {
      infoItems = ii:: infoItems
      this
    }
    def parse( event: ParseEvent ): EventBuilder[_] = {
      event match{
        case startElement: StartElement if startElement.localName == "MetaData" =>
          position = 1
          this
        case startElement: StartElement if startElement.localName == "MetaData" =>
          new InfoItemEventBuilder(path,Some(this),receiveTime).parse(event)
        case endElement: EndElement if endElement.localName == "MetaData" =>
          previous match{
            case Some(state: InfoItemEventBuilder) => state.addMetaData( build )
            case Some(state: EventBuilder[_]) => new FailedEventBuilder(Some(this), "MetaData after something else than InfoItem", receiveTime)
            case None =>
              complete = true
              this
          }
        case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
          new FailedEventBuilder( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in MetaData",receiveTime)
        case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
          new FailedEventBuilder( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in MetaData",receiveTime)
      }
    }
  }

  private class InfoItemEventBuilder( val objectPath: Path,  val previous: Option[EventBuilder[_]], implicit  val receiveTime: Timestamp)  extends EventBuilder[InfoItem]{
    val allowedElements = Set("name","InfoItem","description","altName", "MetaData","value")
    private var position: Int = 0
    private var descriptions: List[Description] = List.empty
    private var values: List[Value[_]] = List.empty
    private var names: List[QlmID] = List.empty
    private var nameAttribute: String =""
    private var typeAttribute: Option[String] = None
    private var path: Path = objectPath
    private var metaData: Option[MetaData] = None
    private var complete: Boolean = false 
    final def isComplete: Boolean = previous.isEmpty && complete
    def build: InfoItem = InfoItem(
      nameAttribute,
      path,
      typeAttribute,
      names.toVector,
      Description.unionReduce(descriptions.toSet),
      values.toVector,
      metaData
    )
    def addMetaData( metaD: MetaData ): InfoItemEventBuilder ={
      metaData = Some(metaD)
      this
    }
    def addDescription( desc: Description): InfoItemEventBuilder ={
      descriptions = desc:: descriptions
      this
    } 
    def addValue( value: Value[_]): InfoItemEventBuilder ={
      values = value:: values
      this
    } 
    def addName( name: QlmID ): InfoItemEventBuilder  ={
      names = name:: names
      this
    } 
    def parse( event: ParseEvent ): EventBuilder[_] = {
      event match{
        case startElement: StartElement if startElement.localName == "name" || startElement.localName == "altName" =>
          if( position == 0 )
            new IdEventBuilder(Some(this),receiveTime).parse(event)
          else
            new FailedEventBuilder(Some(this),"No names after descriptions", receiveTime)
        case startElement: StartElement if startElement.localName == "description" =>
          if( position <= 1 ){
            position = 1
            new DescriptionEventBuilder(Some(this),receiveTime).parse(event)
          } else
            new FailedEventBuilder(Some(this),"No descriptions after MetaData", receiveTime)
        case startElement: StartElement if startElement.localName == "MetaData" =>
          if( position <= 2 ){
            position = 2
            new MetaDataEventBuilder(path,Some(this),receiveTime).parse(event)
          } else
            new FailedEventBuilder(Some(this),"No MetaData after value", receiveTime)
        case startElement: StartElement if startElement.localName == "value" =>
          position = 3
          new ValueEventBuilder(Some(this),receiveTime).parse(event)
        case startElement: StartElement if startElement.localName == "InfoItem" =>
          nameAttribute = startElement.attributes.get("name").getOrElse("")
          typeAttribute = startElement.attributes.get("type")
          path = objectPath / nameAttribute
          this
        case endElement: EndElement if endElement.localName == "InfoItem" =>
          val ii = build
          previous match{
            case Some(state:ObjectEventBuilder) => state.addSubNode(ii)
            case Some(state:MetaDataEventBuilder) => state.addInfoItem(ii)
            case None =>
              complete = true
              this
          }
        case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
          new FailedEventBuilder( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in InfoItem",receiveTime)
        case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
          new FailedEventBuilder( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in InfoItem",receiveTime)
      }
    }
  }

  private class ValueEventBuilder( val  previous: Option[EventBuilder[_]], implicit val  receiveTime: Timestamp)  extends EventBuilder[Value[_]]{
    val allowedElements = Set("Objects","value")
    private var valueO: Option[Value[_]] = None 
    private var complete: Boolean = false 
    final def isComplete: Boolean = previous.isEmpty && complete
    def build = valueO.getOrElse{ throw ODFParserError("Build non existing value.") }
    def addODF( odf: ODF ): ValueEventBuilder ={
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
    def parse( event: ParseEvent ): EventBuilder[_] = {
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
              new ODFEventBuilder(Some(this),receiveTime)
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
                  case Some(iiEventBuilder: InfoItemEventBuilder) =>
                    iiEventBuilder.addValue( value )
                  case Some(other: EventBuilder[_]) =>
                    new FailedEventBuilder(Some(this),"Value state should be only after InfoItem state",receiveTime)
                  case None =>
                    complete = true
                    this
                }
              case None =>
                new FailedEventBuilder(Some(this),"Nonexisting value", receiveTime)
            }
          //ODFValue parsing
          case startElement: StartElement if startElement.localName == "Objects" =>
            new FailedEventBuilder(Some(this),"Objects inside value without proper type given for value", receiveTime)
          case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
            new FailedEventBuilder( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in value",receiveTime)
          case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
            new FailedEventBuilder( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in value",receiveTime)
        }
    }
  }

  private class DescriptionEventBuilder( val  previous: Option[EventBuilder[_]], implicit val  receiveTime: Timestamp)  extends EventBuilder[Description] {
    val allowedElements = Set("description")
    private var lang: Option[String] = None 
    private var text: String = ""
    private var complete: Boolean = false 
    final def isComplete: Boolean = previous.isEmpty && complete
    def build: Description = Description( text, lang)
    def parse( event: ParseEvent ): EventBuilder[_] = {
        event match {
          case startElement: StartElement if startElement.localName == "description" =>
            lang = startElement.attributes.get("lang")
            this
          case content: TextEvent => 
            text = text + content.text
            this

          case endElement: EndElement if endElement.localName == "description" =>
            previous match{
              case Some(state: InfoItemEventBuilder) =>state.addDescription( build ) 
              case Some(state: ObjectEventBuilder )=> state.addDescription( build ) 
              case Some(state: EventBuilder[_]) => new FailedEventBuilder(Some(this),"Description state after wrong state. Previous should be InfoItem or Object",receiveTime)
              case None =>
                complete = true
                this
            }
          case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
            new FailedEventBuilder( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in description",receiveTime)
          case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
            new FailedEventBuilder( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in description",receiveTime)
        }
    }
  }

  private class IdEventBuilder(  val previous: Option[EventBuilder[_]], implicit  val receiveTime: Timestamp)  extends EventBuilder[QlmID] {
    val allowedElements = Set("id","name","altName")
    private var tagType: Option[String] = None
    private var idType: Option[String] = None
    private var startDate: Option[Timestamp] = None
    private var endDate: Option[Timestamp] = None
    private var complete: Boolean = false 
    private var id: String = ""
    final def isComplete: Boolean = previous.isEmpty && complete
    def build: QlmID = QlmID(id,idType,tagType,startDate,endDate)
    def parse( event: ParseEvent ): EventBuilder[_] = {
        event match {
          case startElement: StartElement if startElement.localName == "id" || startElement.localName == "name" || startElement.localName == "altName"  =>
            tagType = startElement.attributes.get("tagType")
            idType = startElement.attributes.get("idType")
            startDate = startElement.attributes.get("startDate").map{
              str => dateTimeStrToTimestamp(str)
            }
            endDate = startElement.attributes.get("startDate").map{
              str => dateTimeStrToTimestamp(str)
            }
            this
          case content: TextEvent => 
            id = id+ content.text
            this

          case endElement: EndElement if endElement.localName == "id" || endElement.localName == "name" || endElement.localName == "altName"  =>
            previous match {
              case Some(state: InfoItemEventBuilder) =>state.addName( build) 
              case Some(state: ObjectEventBuilder )=> state.addId( build ) 
              case Some(state: EventBuilder[_]) => new FailedEventBuilder(Some(this),"Id state after wrong state. Previous should be InfoItem or Object",receiveTime)
              case None =>
                complete = true
                this
            }
          case startElement: StartElement if !allowedElements.contains(startElement.localName) =>
            new FailedEventBuilder( Some(this), s"Possibly unknown ${startElement.localName} element is not allowed in name, altName or id",receiveTime)
          case endElement: EndElement if !allowedElements.contains(endElement.localName) =>
            new FailedEventBuilder( Some(this), s"Possibly unknown ${endElement.localName} element is not allowed in name, altName or id",receiveTime)
        }
    }
  }
  
