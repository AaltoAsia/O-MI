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
package OdfTypes

import java.lang.{Iterable => JavaIterable}
import java.sql.Timestamp
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory

import scala.collection.immutable.HashMap
import scala.util.{Try, Success, Failure}
import scala.xml.XML

import parsing.OdfParser
import parsing.xmlGen._
import parsing.xmlGen.scalaxb._
import parsing.xmlGen.scalaxb.XMLStandardTypes._
import parsing.xmlGen.xmlTypes._
import types.OdfTypes.OdfTreeCollection._

/** Class implementing OdfInfoItem. */
class  OdfInfoItemImpl(
  path:                 Path,
  values:               OdfTreeCollection[OdfValue[Any]] = OdfTreeCollection(),
  description:          Option[OdfDescription] = None,
  metaData:             Option[OdfMetaData] = None,
  typeValue: Option[String] = None,
  attributes:           Map[String,String] = HashMap.empty
) extends Serializable {


  /** 
   * Method for combining two OdfInfoItems with same path 
   */
  def combine(another: OdfInfoItem) : OdfInfoItem ={
    require(path == another.path, s"Should have same paths, got $path versus ${another.path}")
    OdfInfoItem(
      path,
      (values ++ another.values),
      another.description orElse description,{
      val combined : Option[OdfMetaData] = for{
        t <- metaData
        o <- another.metaData
        i = t.combine(o)
      } yield i
      if( combined.isEmpty )
        if( this.metaData.isEmpty ) another.metaData else this.metaData
      else combined},
      another.typeValue orElse typeValue,
      this.attributes ++ another.attributes
    )
  }

  def hasMetadataTag: Boolean = metaData match {
    case Some(_) => true
    case _ => false
  }
  /**
   * Non empty metadata
   */
  def hasMetadata: Boolean = metaData.isDefined

  def hasDescription: Boolean = description.nonEmpty

  /** Method to convert to scalaxb generated class. */
  implicit def asInfoItemType: InfoItemType = {
    InfoItemType(
      description = description.map( des => des.asDescription ).toSeq,
      MetaData = metaData.map(_.asMetaData).toSeq,
      iname = Vector.empty,
      //Seq(QlmIDType(path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: $path")))),
      value = values.map{ 
        value : OdfValue[Any] => value.asValueType
      }.toSeq,
      attributes = HashMap{
        "@name" -> DataRecord(
          path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: $path"))
        )
      } ++ typeValue.map{ tv =>"@type" -> DataRecord(typeValue) }.toVector
      ++ attributesToDataRecord( this.attributes )
      // ++  typeValue.map{ n => ("@type" -> DataRecord(n))}
    )
  }

}

/** Class presenting MetaData structure of O-DF format. */
case class OdfMetaData(
  infoItems: OdfTreeCollection[OdfInfoItem]
) {
  /** Method to convert to scalaxb generated class. */
  implicit def asMetaData : MetaDataType = MetaDataType( infoItems.map(_.asInfoItemType ) )
  
  def combine(another: OdfMetaData) : OdfMetaData ={
    OdfMetaData(
      (infoItems ++another.infoItems).groupBy(_.path).flatMap{
        case (p:Path, items:Seq[OdfInfoItem]) =>
          assert(items.size < 3)
          items.size match {
            case 1 => items.headOption
            case 2 => 
            for{
              head <- items.headOption
              last <- items.lastOption
              infoI <- Some(head.combine(last))
            } yield infoI.withNewest
          }
      }.map{
        info: OdfInfoItem => //Is this what is really wanted? May need all history values, not only neewst
          info.copy( values = info.values.sortBy{ v: OdfValue[Any] => v.timestamp.getTime }.lastOption.toVector )
      }
    )
  }
}

trait SerializableAttribute[A]

/** Class presenting Value tag of O-DF format. */
sealed trait OdfValue[+T]{
  def value:                T
  def typeValue:            String 
  def timestamp:            Timestamp
  def attributes:           HashMap[String, String]
  def valueAsDataRecord: DataRecord[Any] //= DataRecord(value)
  /** Method to convert to scalaxb generated class. */
  implicit def asValueType : ValueType = {
    ValueType(
      Seq(
       valueAsDataRecord 
      ),
    HashMap(
      ("@type" -> DataRecord(typeValue)),
      ("@unixTime" -> DataRecord(timestamp.getTime() / 1000)),
      ("@dateTime" -> DataRecord(timestampToXML(timestamp)))
    ) ++(attributes.mapValues(DataRecord(_)))
    )
  }
  def isNumeral : Boolean = typeValue match {
     case "xs:float" =>
       true
     case "xs:double" =>
       true
     case "xs:short" =>
       true
     case "xs:int" =>
       true
     case "xs:long" =>
       true
     case "odf" =>
       false
     case _ =>
       false
   }
  def isPresentedByString : Boolean = typeValue match {
     case "xs:float" =>
       false
     case "xs:double" =>
       false
     case "xs:short" =>
       false
     case "xs:int" =>
       false
     case "xs:long" =>
       false
     case "odf" =>
       false
     case _ =>
       true
   }
}
  final case class OdfObjectsValue(
    value: OdfObjects, 
    timestamp: Timestamp, 
    attributes: HashMap[String, String] = HashMap.empty
  ) extends OdfValue[OdfObjects]{
    override def typeValue: String = "odf"
    /*
    lazy val objects: OdfObjects = OdfParser.parse(value) match {
      case Right(odf: OdfObjects ) => odf
      case Left( spe: Seq[ParseError] ) => throw spe.head
    }*/
    def valueAsDataRecord = DataRecord(None, Some("Objects"),value.asObjectsType)
  } 
  final case class OdfIntValue(value: Int, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue[Int]{
    def typeValue:            String = "xs:int"
    def valueAsDataRecord = DataRecord(value)
  } 
  final case class  OdfLongValue(value: Long, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue[Long]{
    def typeValue:            String = "xs:long"
    def valueAsDataRecord = DataRecord(value)
  } 
  final case class  OdfShortValue(value: Short, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue[Short]{
    def typeValue:            String = "xs:short"
    def valueAsDataRecord = DataRecord(value)
  } 
  final case class  OdfFloatValue(value: Float, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue[Float]{
    def typeValue:            String = "xs:float"
    def valueAsDataRecord = DataRecord(value)
  } 
  final case class  OdfDoubleValue(value: Double, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue[Double]{
    def typeValue:            String = "xs:double"
    def valueAsDataRecord = DataRecord(value)
  } 
  final case class  OdfBooleanValue(value: Boolean, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue[Boolean]{
    def typeValue:            String = "xs:boolean"
    def valueAsDataRecord = DataRecord(value)
  } 
  final case class  OdfStringPresentedValue(
    value: String,
    timestamp: Timestamp,
    typeValue : String = "xs:string",
    attributes: HashMap[String, String]  
  ) extends OdfValue[String]{
    def valueAsDataRecord = DataRecord(value)
  }

object OdfValue{
  def apply(
    value: Any,
    timestamp: Timestamp,
    attributes: HashMap[String, String]
  ) : OdfValue[Any] = {
    value match {
      case odf: OdfObjects => OdfObjectsValue(odf, timestamp, attributes)
      case s: Short => OdfShortValue(s, timestamp, attributes)
      case i: Int   => OdfIntValue(i, timestamp, attributes)
      case l: Long  => OdfLongValue(l, timestamp, attributes)
      case f: Float => OdfFloatValue(f, timestamp, attributes)
      case d: Double => OdfDoubleValue(d, timestamp, attributes)
      case b: Boolean => OdfBooleanValue(b, timestamp, attributes)
      case s: String => OdfStringPresentedValue(s, timestamp, attributes = attributes)
      case a: Any => OdfStringPresentedValue(a.toString, timestamp, attributes = attributes)
    }
  }
  def apply(
    value: Any,
    timestamp: Timestamp
  ) : OdfValue[Any] = apply(value,timestamp,HashMap.empty[String,String])
  
  def apply(
    value: String,
    typeValue: String,
    timestamp: Timestamp,
    attributes: HashMap[String, String] = HashMap.empty
  ) : OdfValue[Any] = {
    val create = Try{
      typeValue match {
        case "odf" =>
          val result = OdfParser.parse(value)
          result match {
            case Left( pes: Seq[ParseError]) =>
              throw pes.head
            case Right( odf: OdfObjects) =>
              OdfObjectsValue(odf,timestamp, attributes)
          }
        case "xs:float" =>
          OdfFloatValue(value.toFloat, timestamp, attributes)
        case "xs:double" =>
          OdfDoubleValue(value.toDouble, timestamp, attributes)
        case "xs:short" =>
          OdfShortValue(value.toShort, timestamp, attributes)
        case "xs:int" =>
          OdfIntValue(value.toInt, timestamp, attributes)
        case "xs:long" =>
          OdfLongValue(value.toLong, timestamp, attributes)
        case "xs:boolean" =>
          OdfBooleanValue(value.toBoolean, timestamp, attributes)
        case str: String  =>
          OdfStringPresentedValue(value, timestamp,str, attributes)
      }
    }
    create match {
      case Success(value) => value
      case Failure( e: Exception) =>
        //println( s"Creating of OdfValue failed with type $typeValue, caused by: $e")
        OdfStringPresentedValue(value, timestamp, attributes = attributes)
    }
      
  }
}
