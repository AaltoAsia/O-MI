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
package parsing

import java.io.File
import java.sql.Timestamp
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource

import scala.collection.JavaConversions.asJavaIterable
import scala.collection.immutable.HashMap
import scala.util.{Failure, Success, Try}
import scala.xml.{NodeSeq, Elem}

import akka.http.scaladsl.model.RemoteAddress
import parsing.xmlGen._
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OdfTypes.{QlmID => OdfQlmID}
import types.OmiTypes.UserInfo
import types._

/** Parser for data in O-DF format*/
object OdfParser extends Parser[OdfParseResult] {
  val schemaName = "odf.xsd"
  protected[this] override def schemaPath = Array[Source](
    new StreamSource(getClass.getClassLoader().getResourceAsStream("odf.xsd"))
  )

  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/
  /**
   * Public method for parsing the xml file into OdfParseResults.
   *
   *  @param file XML formatted file to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(file: File): OdfParseResult = {
    val parsed = Try(
      XMLParser.loadFile(file)
    )
    parseTry(parsed)

  }

  /**
   * Public method for parsing the xml string into OdfParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(xml_msg: String): OdfParseResult = {
    val parsed = Try(
      XMLParser.loadString(xml_msg)
    )

    parseTry(parsed)
  }

  private def parseTry(parsed: Try[Elem]): OdfParseResult = {
    parsed match {
      case Success(root) => parse(root)
      case Failure(f) => Left(Iterable(ScalaXMLError(f.getMessage)))
    }
  }

  /**
   * Public method for parsing the xml structure into OdfParseResults.
   *
   *  @param root xml.Node to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(root: xml.Node): OdfParseResult = { 
    schemaValidation(root) match {
      case errors : Seq[ParseError] if errors.nonEmpty => 
        println( root.toString )

        Left(errors) 
      case empty : Seq[ParseError] if empty.isEmpty =>

      val requestProcessTime = currentTime

      Try{
        xmlGen.scalaxb.fromXML[ObjectsType](root)
      } match {
        case Failure(e) => 
            println( s"Exception: $e\nStackTrace:\n")
            e.printStackTrace
            Left( Iterable( ScalaxbError( e.getMessage ) ) )
      
        case Success(objects) => 
          Try{
            parseObjects(objects,requestProcessTime)
          } match {
            case Success(odf) => Right(odf)
            case Failure(e) => 
            println( s"Exception: $e\nStackTrace:\n")
            e.printStackTrace
              Left( Iterable( ODFParserError( e.getMessage ) ) )
          }
      }
    }
  }
  private[this] def parseObjects(objects: ObjectsType,requestProcessTime: Timestamp): OdfObjects = {
    OdfObjects( 
      if(objects.ObjectValue.isEmpty)
        Iterable.empty[OdfObject]
      else
        objects.ObjectValue.map{ obj => parseObject( requestProcessTime, obj ) }.toIterable,
        objects.version,
        parseAttributes( objects.attributes - "@version" )
    )
}

private[this] def validateId(stringId: String): Option[String] = {
  stringId.trim match{ 
    case "" => None 
    case trimmedName: String => Some(trimmedName)
  }
}
private[this] def validateId(optionId: Option[String]): Option[String] = for {
  head <- optionId
  validated <- validateId(head)
} yield validated

private[this] def parseObject(requestProcessTime: Timestamp, obj: ObjectType, path: Path = Path("Objects")) :  OdfObject = { 

  val npath = path / validateId(obj.id.headOption.map(_.value)).getOrElse(
    throw new IllegalArgumentException("No <id> on object: " + obj.id.toString)
  )

  OdfObject(
    obj.id.map{ qlmIdType => parseQlmID(qlmIdType)},
    npath, 
    obj.InfoItem.map{ item => parseInfoItem( requestProcessTime, item, npath ) }.toIterable,
    obj.ObjectValue.map{ child => parseObject( requestProcessTime, child, npath ) }.toIterable,
    obj.description.map{ des => OdfDescription( des.value, des.lang )}.headOption,
    obj.typeValue,
    parseAttributes( obj.attributes - "@type" )
  ) 
}

private[this] def parseInfoItem(requestProcessTime: Timestamp, item: InfoItemType, path: Path) : OdfInfoItem  = { 

  // TODO: support many names from item.otherName
  val npath = path / validateId(item.name).getOrElse(
    throw new IllegalArgumentException("No name on infoItem")
  )

  OdfInfoItem(
    npath,
    item.value.map{
      valueType => 
        parseValue(requestProcessTime,valueType)
    },
    item.description.map{ des =>
      OdfDescription( des.value, des.lang ) 
    }.headOption,
    item.MetaData.map{
      md => 
        OdfMetaData(
          md.InfoItem.map{ 
            mItem => parseInfoItem( requestProcessTime, mItem, npath / "MetaData" ) 
          }
        )
    }.headOption,
    item.typeValue,
    parseAttributes( item.attributes - "@name" )
  ) 
}

private[this] def parseValue(requestProcessTime: Timestamp, valueType: ValueType) = { 
  val typeValue = valueType.typeValue
  def handleObjectsValue ={
      val objectsTypes = valueType.mixed.filter{
          case dr: scalaxb.DataRecord[Any] =>
            dr.value match {
              case objectsType: xmlTypes.ObjectsType =>
                true
              case _ => false
            }
        }.map( _.as[xmlTypes.ObjectsType] ).head //XXX: head used should not have multiple Objects
          OdfObjectsValue(
            parseObjects(objectsTypes,requestProcessTime),//.asXML.toString,
            timeSolver(valueType, requestProcessTime)
          )
  } 
    
  typeValue match {
    case "odf" =>  handleObjectsValue
    case "odf:Objects" =>  handleObjectsValue
    case "Objects" =>  handleObjectsValue
      /*
      objectsTypes.map{
        case odf: xmlTypes.ObjectsType => 
          val odfXml = xmlGen.scalaxb.toXML[xmlTypes.ObjectsType](odf,None,Some("Objects"),xmlGen.odfDefaultScope)
          odfXml.toString
          }.foldLeft("")( _ + _)
          */
    case str: String  => 
      val xmlValue = valueType.mixed.map{
        case dr: xmlGen.scalaxb.DataRecord[_] => 
          xmlGen.scalaxb.DataRecord.toXML(dr,None,None,xmlGen.odfDefaultScope,false)
          }.foldLeft(NodeSeq.Empty){
            case (res: NodeSeq, ns: NodeSeq) => res ++ ns
          }
      OdfValue(
        xmlValue.toString,
        typeValue,
        timeSolver(valueType, requestProcessTime)
      )
  }

}

/**
 * Add timestamp values to metadata values as the name suggests.
 * @param meta
 * @param reqTime
 * @return
 */
private[this] def addTimeStampToMetaDataValues(meta: Option[MetaDataType], reqTime: Timestamp): Option[MetaDataType] = {
  meta.map( m =>
    MetaDataType(m.InfoItem.map(ii =>
      ii.copy(
        value = ii.value.map(value =>
          value.copy(
          attributes = value.attributes.-("@dateTime").updated("@unixTime", DataRecord(timeSolver(value, reqTime).getTime)))),

        MetaData = addTimeStampToMetaDataValues(ii.MetaData.headOption,reqTime).toSeq)
    ))
  )
}

/** Resolves time used in the value (unixtime in seconds or datetime): prefers datetime if both present
 */
private[this] def timeSolver(value: ValueType, requestProcessTime: Timestamp) = value.dateTime match {
  case None => value.unixTime match {
    case None => requestProcessTime
    case Some(seconds) => new Timestamp(seconds.toLong * 1000)
  }
  case Some(cal) => 
    new Timestamp(cal.toGregorianCalendar().getTimeInMillis())
}
private[this] def parseQlmID( qlmIdType: QlmIDType): OdfQlmID ={
  OdfQlmID(
    qlmIdType.value,
    qlmIdType.idType,
    qlmIdType.tagType,
      qlmIdType.startDate.map{
        cal => new Timestamp(cal.toGregorianCalendar().getTimeInMillis())
      },
      qlmIdType.endDate.map{
        cal => new Timestamp(cal.toGregorianCalendar().getTimeInMillis())
      },
      HashMap(
        (
          qlmIdType.attributes -( "@idType" ,"@tagType","@startDate","@endDate")
        ).mapValues(_.value.toString).toSeq:_*
      )
    )
  }
  private def parseAttributes( attributes: Map[String,DataRecord[Any]]) : Map[String,String] ={
    attributes.map{
      case (key: String, dr: DataRecord[Any] ) => 
        if( key.startsWith("@") ) key.tail -> dr.value.toString
        else key -> dr.value.toString
    }
  
  }
}
