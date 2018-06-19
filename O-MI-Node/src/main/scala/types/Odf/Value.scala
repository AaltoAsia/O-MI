package types
package odf
import java.sql.Timestamp

import database.journal.PPersistentValue.ValueTypeOneof.{ProtoBoolValue, ProtoDoubleValue, ProtoLongValue, ProtoStringValue}
import database.journal.{PPersistentValue}
import parsing.xmlGen._
import parsing.xmlGen.scalaxb.XMLStandardTypes._
import parsing.xmlGen.scalaxb._
import parsing.xmlGen.xmlTypes.{ValueType, _}

import scala.collection.immutable.HashMap
import scala.collection.{Map, Seq}
import scala.util.{Failure, Success, Try}

trait Value[+V]{
  val value:V
  val typeAttribute: String
  val timestamp: Timestamp

  def valueAsDataRecord: DataRecord[Any] //= DataRecord(value) 
  implicit def asValueType : ValueType = {
    ValueType(
      Seq(
       valueAsDataRecord 
      ),
    HashMap(
      "@type" -> DataRecord(typeAttribute),
      "@unixTime" -> DataRecord(timestamp.getTime() / 1000),
      "@dateTime" -> DataRecord(timestampToXML(timestamp))
    )
    )
  }
  def asOdfValue: types.OdfTypes.OdfValue[Any] = {
    types.OdfTypes.OdfValue( value, timestamp)
  }
  def retime(newTimestamp: Timestamp): Value[V]
  def persist: PPersistentValue = PPersistentValue(timestamp.getTime,typeAttribute,value match {
    case s: Short => ProtoLongValue(s.toLong)
    case i: Int   => ProtoLongValue(i)
    case l: Long  => ProtoLongValue(l)
    case f: Float => ProtoDoubleValue(f.toDouble)
    case d: Double => ProtoDoubleValue(d)
    case b: Boolean => ProtoBoolValue(b)
    case s: String => ProtoStringValue(s)
    case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] =>
      ProtoStringValue(odf.asXML.toString())
    case a: Any => ProtoStringValue(a.toString)
  })
}

case class ODFValue(
                     value: ODF,
  timestamp: Timestamp
) extends Value[ODF] {
  final val typeAttribute: String = "odf"
  def valueAsDataRecord: DataRecord[ObjectsType] = DataRecord(None, Some("Objects"),value.asObjectsType)
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class StringValue(
                        value: String,
  timestamp: Timestamp
) extends Value[String] {
  final val typeAttribute: String = "xs:string"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class IntValue(
                     value: Int,
  timestamp: Timestamp
) extends Value[Int] {
  final val typeAttribute: String = "xs:int"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class LongValue(
                      value: Long,
  timestamp: Timestamp
) extends Value[Long] {
  final val typeAttribute: String = "xs:long"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class ShortValue(
                       value: Short,
  timestamp: Timestamp
) extends Value[Short] {
  final val typeAttribute: String = "xs:short"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class FloatValue(
                       value: Float,
  timestamp: Timestamp
) extends Value[Float] {
  final val typeAttribute: String = "xs:float"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class DoubleValue(
                        value: Double,
  timestamp: Timestamp
) extends Value[Double] {
  final val typeAttribute: String = "xs:double"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class BooleanValue(
                         value: Boolean,
  timestamp: Timestamp
) extends Value[Boolean] {
  final val typeAttribute: String = "xs:boolean"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

case class StringPresentedValue(
                                 value: String,
  timestamp: Timestamp,
  typeAttribute: String = "xs:string"
) extends Value[String] {
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
  def retime(newTimestamp: Timestamp) =  this.copy( timestamp = newTimestamp)
}

object Value{
  def apply(
    value: Any,
    timestamp: Timestamp
  ) : Value[Any] = {
    value match {
      case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] => 
        ODFValue(odf.immutable, timestamp)
      case s: Short => ShortValue(s, timestamp)
      case i: Int   => IntValue(i, timestamp)
      case l: Long  => LongValue(l, timestamp)
      case f: Float => FloatValue(f, timestamp)
      case d: Double => DoubleValue(d, timestamp)
      case b: Boolean => BooleanValue(b, timestamp)
      case s: String => StringPresentedValue(s, timestamp)
      case a: Any => StringPresentedValue(a.toString, timestamp)
    }
  }

  def apply(
    value: Any,
    typeValue: String,
    timestamp: Timestamp
  ) : Value[Any] = {
    value match {
      case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] => 
        ODFValue(odf.immutable, timestamp)
      case s: Short => ShortValue(s, timestamp)
      case i: Int   => IntValue(i, timestamp)
      case l: Long  => LongValue(l, timestamp)
      case f: Float => FloatValue(f, timestamp)
      case d: Double => DoubleValue(d, timestamp)
      case b: Boolean => BooleanValue(b, timestamp)
      case s: String => applyFromString( s, typeValue, timestamp)
      case a: Any => StringPresentedValue(a.toString, timestamp, typeValue)
    }
  }
  def applyFromString(
    value: String,
    typeValue: String,
    timestamp: Timestamp
  ) : Value[Any] = {
    val create = Try{
      typeValue match {
        case "xs:float" =>
          FloatValue(value.toFloat, timestamp)
        case "xs:double" =>
          DoubleValue(value.toDouble, timestamp)
        case "xs:short" =>
          ShortValue(value.toShort, timestamp)
        case "xs:int" =>
          IntValue(value.toInt, timestamp)
        case "xs:long" =>
          LongValue(value.toLong, timestamp)
        case "xs:boolean" =>
          BooleanValue(value.toBoolean, timestamp)
        case str: String  =>
          StringPresentedValue(value, timestamp,str)
      }
    }
    create match {
      case Success(_value) => _value
      case Failure( _: Exception) =>
        StringPresentedValue(value, timestamp)
      case Failure( _: Throwable) =>
        StringPresentedValue(value, timestamp)
    }
      
  }
}
