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
import scala.util.Try
import scala.xml.XML

import parsing.xmlGen._
import parsing.xmlGen.scalaxb.{CanWriteXML, DataRecord}
import parsing.xmlGen.xmlTypes._
import types.OdfTypes.OdfTreeCollection._

/** Class implementing OdfInfoItem. */
class  OdfInfoItemImpl(
  path:                 Path,
  values:               OdfTreeCollection[OdfValue] = OdfTreeCollection(),
  description:          Option[OdfDescription] = None,
  metaData:             Option[MetaData] = None
) extends Serializable {

  def combineMetaData(another: Option[MetaData], first: Option[MetaData]): Option[MetaData] = {
    (another, first) match {
        case (Some(amData), Some(bmData)) => {
          val mInfoItems = (amData.InfoItem ++ bmData.InfoItem).groupBy(_.name).map{
            case (path, infos) => InfoItemType(
              infos.collectFirst{
                case InfoItemType(ids @ id::b, _,_,_,_,_) => ids
              }.getOrElse(Nil), // TODO: better merging of QLMIDS currently find first with Id and use that.
              infos.collectFirst{
                case InfoItemType(_, Some(des), _, _, _, _) => des}, // Find first metadata with description and use that
              infos.foldLeft[Option[MetaData]](None)( (col, next ) => combineMetaData(col, next.MetaData)), //recursively merge metadatas
              infos.flatMap(_.value).distinct, // combine values by flatmapping and remove duplicates
              path,
              //merge attributes by preferring the attributes of another over first
              infos.map(_.attributes).foldRight[Map[String, DataRecord[Any]]](Map.empty)((col, next) => col ++ next)
            )
          }
          Some(MetaData(mInfoItems: _*))
        }
        case (a, b) => a orElse b
      }
  }
  /** Method for combining two OdfInfoItems with same path */
  def combine(another: OdfInfoItem) : OdfInfoItem ={
    require(path == another.path, s"Should have same paths, got $path versus ${another.path}")
    OdfInfoItem(
      path,
      (values ++ another.values),
      another.description orElse description,
      combineMetaData(another.metaData, this.metaData)
      //another.metaData orElse metaData
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
      MetaData = metaData,
      name = path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: $path")),
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

trait SerializableAttribute[A]

/** Class presenting Value tag of O-DF format. */
sealed trait OdfValue{
  def value:                Any
  def typeValue:            String 
  def timestamp:            Timestamp
  def attributes:           HashMap[String, String]
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
  final case class OdfIntValue(value: Int, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue{
    def typeValue:            String = "xs:int"
  } 
  final case class  OdfLongValue(value: Long, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue{
    def typeValue:            String = "xs:long"
  } 
  final case class  OdfShortValue(value: Short, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue{
    def typeValue:            String = "xs:short"
  } 
  final case class  OdfFloatValue(value: Float, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue{
    def typeValue:            String = "xs:float"
  } 
  final case class  OdfDoubleValue(value: Double, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue{
    def typeValue:            String = "xs:double"
  } 
  final case class  OdfBooleanValue(value: Boolean, timestamp: Timestamp, attributes: HashMap[String, String]) extends OdfValue{
    def typeValue:            String = "xs:boolean"
  } 
  final case class  OdfStringPresentedValue(value: String,  timestamp: Timestamp, typeValue : String = "xs:string", attributes: HashMap[String, String]  ) extends OdfValue

object OdfValue{
  def apply(
    value: Any,
    timestamp: Timestamp,
    attributes: HashMap[String, String]
  ) : OdfValue = {
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
    value: String,
    typeValue: String,
    timestamp: Timestamp,
    attributes: HashMap[String, String] = HashMap.empty
  ) : OdfValue = {
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
