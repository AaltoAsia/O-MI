package types
package odf
import java.sql.Timestamp

import scala.collection.{ Seq, Map }
import scala.collection.immutable.HashMap 
import scala.util.{Try, Success, Failure}

import parsing.xmlGen._
import parsing.xmlGen.scalaxb._
import parsing.xmlGen.scalaxb.XMLStandardTypes._
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.xmlTypes.{ValueType}

trait Value[+V]{
  val value:V
  val typeAttribute: String
  val timestamp: Timestamp
  val attributes: Map[String,String]

  def valueAsDataRecord: DataRecord[Any] //= DataRecord(value) 
  implicit def asValueType : ValueType = {
    ValueType(
      Seq(
       valueAsDataRecord 
      ),
    HashMap(
      ("@type" -> DataRecord(typeAttribute)),
      ("@unixTime" -> DataRecord(timestamp.getTime() / 1000)),
      ("@dateTime" -> DataRecord(timestampToXML(timestamp)))
    ) ++ attributesToDataRecord( attributes )
    )
  }
  def asOdfValue: types.OdfTypes.OdfValue[Any] = {
    types.OdfTypes.OdfValue( value, timestamp, HashMap( attributes.toSeq:_*) )
  }
}

case class ODFValue(
  val value: ODF,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[ODF] {
  final val typeAttribute: String = "odf:Objects"
  def valueAsDataRecord = DataRecord(None, Some("Objects"),value.asObjectsType)
}

case class StringValue( 
  val value: String,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[String] {
  final val typeAttribute: String = "xs:string"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

case class IntValue( 
  val value: Int,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[Int] {
  final val typeAttribute: String = "xs:int"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

case class LongValue( 
  val value: Long,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[Long] {
  final val typeAttribute: String = "xs:long"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

case class ShortValue( 
  val value: Short,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[Short] {
  final val typeAttribute: String = "xs:short"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

case class FloatValue( 
  val value: Float,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[Float] {
  final val typeAttribute: String = "xs:float"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

case class DoubleValue( 
  val value: Double,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[Double] {
  final val typeAttribute: String = "xs:double"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

case class BooleanValue( 
  val value: Boolean,
  val timestamp: Timestamp,
  val attributes: Map[String,String]
) extends Value[Boolean] {
  final val typeAttribute: String = "xs:boolean"
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

case class StringPresentedValue( 
  val value: String,
  val timestamp: Timestamp,
  val typeAttribute: String = "xs:string",
  val attributes: Map[String,String]
) extends Value[String] {
  def valueAsDataRecord: DataRecord[Any] = DataRecord(value) 
}

object Value{
  def apply(
    value: Any,
    timestamp: Timestamp,
    attributes: Map[String, String]
  ) : Value[Any] = {
    value match {
      case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] => 
        ODFValue(odf.immutable, timestamp, attributes)
      case s: Short => ShortValue(s, timestamp, attributes)
      case i: Int   => IntValue(i, timestamp, attributes)
      case l: Long  => LongValue(l, timestamp, attributes)
      case f: Float => FloatValue(f, timestamp, attributes)
      case d: Double => DoubleValue(d, timestamp, attributes)
      case b: Boolean => BooleanValue(b, timestamp, attributes)
      case s: String => StringPresentedValue(s, timestamp, attributes = attributes)
      case a: Any => StringPresentedValue(a.toString, timestamp, attributes = attributes)
    }
  }
  def apply(
    value: Any,
    timestamp: Timestamp
  ) : Value[Any] = apply(value,timestamp,HashMap.empty[String,String])
  
  def apply(
    value: Any,
    typeValue: String,
    timestamp: Timestamp,
    attributes: Map[String, String] = HashMap.empty
  ) : Value[Any] = {
    value match {
      case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] => 
        ODFValue(odf.immutable, timestamp, attributes)
      case s: Short => ShortValue(s, timestamp, attributes)
      case i: Int   => IntValue(i, timestamp, attributes)
      case l: Long  => LongValue(l, timestamp, attributes)
      case f: Float => FloatValue(f, timestamp, attributes)
      case d: Double => DoubleValue(d, timestamp, attributes)
      case b: Boolean => BooleanValue(b, timestamp, attributes)
      case s: String => applyFromString( s, typeValue, timestamp, attributes ) 
      case a: Any => StringPresentedValue(a.toString, timestamp, typeValue,attributes = attributes)
    }
  }
  def applyFromString(
    value: String,
    typeValue: String,
    timestamp: Timestamp,
    attributes: Map[String, String] = HashMap.empty
  ) : Value[Any] = {
    val create = Try{
      typeValue match {
        case "xs:float" =>
          FloatValue(value.toFloat, timestamp, attributes)
        case "xs:double" =>
          DoubleValue(value.toDouble, timestamp, attributes)
        case "xs:short" =>
          ShortValue(value.toShort, timestamp, attributes)
        case "xs:int" =>
          IntValue(value.toInt, timestamp, attributes)
        case "xs:long" =>
          LongValue(value.toLong, timestamp, attributes)
        case "xs:boolean" =>
          BooleanValue(value.toBoolean, timestamp, attributes)
        case str: String  =>
          StringPresentedValue(value, timestamp,str, attributes)
      }
    }
    create match {
      case Success(value) => value
      case Failure( e: Exception) =>
        //println( s"Creating of Value failed with type $typeValue, caused by: $e")
        StringPresentedValue(value, timestamp, attributes = attributes)
      case Failure( e: Throwable) =>
        //println( s"Creating of Value failed with type $typeValue, caused by: $e")
        StringPresentedValue(value, timestamp, attributes = attributes)
    }
      
  }
}
