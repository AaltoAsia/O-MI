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

import scala.util.Try
import scala.xml.XML

import parsing.xmlGen._
import parsing.xmlGen.scalaxb.{CanWriteXML, DataRecord}
import parsing.xmlGen.xmlTypes._
import types.OdfTypes.OdfTreeCollection._

/** Class implementing OdfInfoItem. */
class  OdfInfoItemImpl(
  path:                 Path,
  values:               OdfTreeCollection[OdfValue[Any]] = OdfTreeCollection(),
  description:          Option[OdfDescription] = None,
  metaData:             Option[OdfMetaData] = None
) extends Serializable {

  /** Method for combining two OdfInfoItems with same path */
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
      else combined}
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
      description = description.map( des => des.asDescription ),
      MetaData = metaData.map(_.asMetaData),
      name = path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: $path")),
      value = values.map{ 
        value : OdfValue[Any] =>
        value.asValueType
      }.toSeq,
      attributes = Map.empty
    )
  }

}

/** Class presenting MetaData structure of O-DF format. */
case class OdfMetaData(
  infoItems: OdfTreeCollection[OdfInfoItem]
) {
  /** Method to convert to scalaxb generated class. */
  implicit def asMetaData : MetaData = MetaData( infoItems.map(_.asInfoItemType ):_* )
  
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
            } yield infoI
          }
      }.map{
        info: OdfInfoItem =>//Is this what is really wanted? May need all history values, not only neewst 
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
  def attributes:           Map[String, String]
  /** Method to convert to scalaxb generated class. */
  implicit def asValueType : ValueType = {
    ValueType(
      value.toString,
      typeValue,
      unixTime = Option(timestamp.getTime),
      dateTime = Option{
        timestampToXML(timestamp)
      },
      attributes = attributes.mapValues(DataRecord(_))
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
     case _ =>
       true
   }
}
  final case class OdfIntValue(value: Int, timestamp: Timestamp, attributes: Map[String, String]) extends OdfValue[Int]{
    def typeValue:            String = "xs:int"
  } 
  final case class  OdfLongValue(value: Long, timestamp: Timestamp, attributes: Map[String, String]) extends OdfValue[Long]{
    def typeValue:            String = "xs:long"
  } 
  final case class  OdfShortValue(value: Short, timestamp: Timestamp, attributes: Map[String, String]) extends OdfValue[Short]{
    def typeValue:            String = "xs:short"
  } 
  final case class  OdfFloatValue(value: Float, timestamp: Timestamp, attributes: Map[String, String]) extends OdfValue[Float]{
    def typeValue:            String = "xs:float"
  } 
  final case class  OdfDoubleValue(value: Double, timestamp: Timestamp, attributes: Map[String, String]) extends OdfValue[Double]{
    def typeValue:            String = "xs:double"
  } 
  final case class  OdfBooleanValue(value: Boolean, timestamp: Timestamp, attributes: Map[String, String]) extends OdfValue[Boolean]{
    def typeValue:            String = "xs:boolean"
  } 
  final case class  OdfStringPresentedValue(value: String,  timestamp: Timestamp, typeValue : String = "xs:string", attributes: Map[String, String]  ) extends OdfValue[String]

object OdfValue{
  def apply(
    value: Any,
    timestamp: Timestamp,
    attributes: Map[String, String]
  ) : OdfValue[Any] = {
    value match {
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
  ) : OdfValue[Any] = apply(value,timestamp,Map.empty[String,String])
  
  def apply(
    value: String,
    typeValue: String,
    timestamp: Timestamp,
    attributes: Map[String, String] = Map.empty
  ) : OdfValue[Any] = {
    Try{
      typeValue match {
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
    }.getOrElse(
      OdfStringPresentedValue(value, timestamp, attributes = attributes)
    )
  }
}
