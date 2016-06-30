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
import parsing.xmlGen.xmlTypes._
import types.OdfTypes.OdfTreeCollection._

/** Class implementing OdfInfoItem. */
class  OdfInfoItemImpl(
  path:                 Path,
  values:               OdfTreeCollection[OdfValue] = OdfTreeCollection(),
  description:          Option[OdfDescription] = None,
  metaData:             Option[OdfMetaData] = None
) extends Serializable {

  /** Method for combining two OdfInfoItems with same path */
  def combine(another: OdfInfoItem) : OdfInfoItem ={
    require(path == another.path, "Should have same paths")
    OdfInfoItem(
      path,
      (values ++ another.values).toSeq,
      another.description orElse description,
      another.metaData orElse metaData
    )
  }

  def hasMetadataTag: Boolean = metaData match {
    case Some(_) => true
    case _ => false
  }
  /**
   * Non empty metadata
   */
  def hasMetadata: Boolean = metaData match {
    case Some(meta) => meta.data.trim.nonEmpty
    case _ => false
  }
  
  def hasDescription: Boolean = description.nonEmpty

  /** Method to convert to scalaxb generated class. */
  implicit def asInfoItemType: InfoItemType = {
    InfoItemType(
      description = description.map( des => des.asDescription ),
      MetaData = metaData.map{ odfMetaData => odfMetaData.asMetaData},
      name = path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: ${path}")),
      value = values.map{ 
        value : OdfValue =>
        value.asValueType
      }.toSeq,
      attributes = Map.empty
    )
  }

}

/** Class presenting MetaData structure of O-DF format. */
case class OdfMetaData(
  data:                 String
) {
  /** Method to convert to scalaxb generated class. */
  implicit def asMetaData : MetaData = {
    scalaxb.fromXML[MetaData]( XML.loadString( data ) ) // What if agent inserts malformed xml string with InputPushe/DBPusher functions
  }
}

/** Class presenting Value tag of O-DF format. */
sealed trait OdfValue{
  def value:                Any
  def typeValue:            String 
  def timestamp:            Timestamp
  /** Method to convert to scalaxb generated class. */
  implicit def asValueType : ValueType = {
    ValueType(
      value.toString,
      typeValue,
      unixTime = Option(timestamp.getTime/1000),
      dateTime = Option{
        timestampToXML(timestamp)
      },
      attributes = Map.empty
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
  final case class OdfIntValue(value: Int, timestamp: Timestamp) extends OdfValue{
    def typeValue:            String = "xs:int"
  } 
  final case class  OdfLongValue(value: Long, timestamp: Timestamp) extends OdfValue{
    def typeValue:            String = "xs:long"
  } 
  final case class  OdfShortValue(value: Short, timestamp: Timestamp) extends OdfValue{
    def typeValue:            String = "xs:short"
  } 
  final case class  OdfFloatValue(value: Float, timestamp: Timestamp) extends OdfValue{
    def typeValue:            String = "xs:float"
  } 
  final case class  OdfDoubleValue(value: Double, timestamp: Timestamp) extends OdfValue{
    def typeValue:            String = "xs:double"
  } 
  final case class  OdfBooleanValue(value: Boolean, timestamp: Timestamp) extends OdfValue{
    def typeValue:            String = "xs:boolean"
  } 
  final case class  OdfStringPresentedValue(value: String,  timestamp: Timestamp, typeValue : String = "xs:string"  ) extends OdfValue 

object OdfValue{
  def apply(value: Any, timestamp: Timestamp) : OdfValue = {
    value match {
      case s: Short => OdfShortValue(s, timestamp) 
      case i: Int   => OdfIntValue(i, timestamp)
      case l: Long  => OdfLongValue(l, timestamp)
      case f: Float => OdfFloatValue(f, timestamp)
      case d: Double => OdfDoubleValue(d, timestamp)
      case b: Boolean => OdfBooleanValue(b, timestamp)
      case s: String => OdfStringPresentedValue(s, timestamp)
      case a: Any => OdfStringPresentedValue(a.toString, timestamp)
    }
  }
  def apply(value: String, typeValue: String, timestamp: Timestamp) : OdfValue = {
    Try{
      typeValue match {
        case "xs:float" =>
          OdfFloatValue(value.toFloat, timestamp)
        case "xs:double" =>
          OdfDoubleValue(value.toDouble, timestamp)
        case "xs:short" =>
          OdfShortValue(value.toShort, timestamp)
        case "xs:int" =>
          OdfIntValue(value.toInt, timestamp)
        case "xs:long" =>
          OdfLongValue(value.toLong, timestamp)
        case "xs:boolean" =>
          OdfBooleanValue(value.toBoolean, timestamp)
        case str: String  =>
          OdfStringPresentedValue(value, timestamp,str)
      }
    }.getOrElse(
      OdfStringPresentedValue(value, timestamp)
    )
  }
}
