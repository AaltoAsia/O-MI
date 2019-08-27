package types
package odf

import java.sql.Timestamp

import scala.collection.SeqView
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.concurrent._

import akka.util.ByteString
import akka.stream.scaladsl.Sink
import akka.stream.Materializer
import akka.stream.alpakka.xml._

import utils.parseEventsToByteSource
import database.journal.PPersistentValue
import database.journal.PPersistentValue.ValueTypeOneof.{ProtoBoolValue, ProtoDoubleValue, ProtoLongValue, ProtoStringValue}
import types.omi.Version.OdfVersion2

trait Value[+V] extends Element{
  val value: V
  val typeAttribute: String
  val timestamp: Timestamp

  def retime(newTimestamp: Timestamp): Value[V]

  def persist(implicit mat: Materializer, ec: ExecutionContext): PPersistentValue = PPersistentValue(timestamp.getTime, typeAttribute, value match {
    case s: Short => ProtoLongValue(s.toLong)
    case i: Int => ProtoLongValue(i)
    case l: Long => ProtoLongValue(l)
    case f: Float => ProtoDoubleValue(f.toDouble)
    case d: Double => ProtoDoubleValue(d)
    case b: Boolean => ProtoBoolValue(b)
    case s: String => ProtoStringValue(s)
    case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] =>
      val f: Future[String] = parseEventsToByteSource(odf.asXMLDocument(Some(OdfVersion2))).runWith(Sink.fold(""){
        case ( result: String, bStr: ByteString ) => result + bStr.utf8String
      })
      ProtoStringValue(Await.result(f, 1.minute))
    case a: Any => ProtoStringValue(a.toString)
  })
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] = {
    val tpAttr = if( typeAttribute == "xs:string" ) None else Some(Attribute("type",typeAttribute))
    Seq(
      StartElement( "value",
        List(
            Attribute("unixTime",(timestamp.getTime / 1000).toString),
            Attribute("dateTime",timestampToDateTimeString(timestamp))
          ) ++ tpAttr.toSeq
      ),
      Characters( value.toString ),
      EndElement("value")
    ).view
  }
}

case class ODFValue(
                     value: ODF,
                     timestamp: Timestamp
                   ) extends Value[ODF] {
  final val typeAttribute: String = "odf"

  def retime(newTimestamp: Timestamp): ODFValue = this.copy(timestamp = newTimestamp)
  override def asXMLEvents: SeqView[ParseEvent,Seq[_]] = {
    Seq(
      StartElement( "value",
        List(
            Attribute("type",typeAttribute),
            Attribute("unixTime",(timestamp.getTime / 1000).toString),
            Attribute("dateTime",timestampToDateTimeString(timestamp))
          )
      )
      ).view ++
    value.asXMLEvents() ++
    Seq(
      EndElement("value")
    )
  }
}

case class StringValue(
                        value: String,
                        timestamp: Timestamp
                      ) extends Value[String] {
  final val typeAttribute: String = "xs:string"

  def retime(newTimestamp: Timestamp): StringValue = this.copy(timestamp = newTimestamp)
}

case class IntValue(
                     value: Int,
                     timestamp: Timestamp
                   ) extends Value[Int] {
  final val typeAttribute: String = "xs:int"

  def retime(newTimestamp: Timestamp): IntValue = this.copy(timestamp = newTimestamp)
}

case class LongValue(
                      value: Long,
                      timestamp: Timestamp
                    ) extends Value[Long] {
  final val typeAttribute: String = "xs:long"

  def retime(newTimestamp: Timestamp): LongValue = this.copy(timestamp = newTimestamp)
}

case class ShortValue(
                       value: Short,
                       timestamp: Timestamp
                     ) extends Value[Short] {
  final val typeAttribute: String = "xs:short"

  def retime(newTimestamp: Timestamp): ShortValue = this.copy(timestamp = newTimestamp)
}

case class FloatValue(
                       value: Float,
                       timestamp: Timestamp
                     ) extends Value[Float] {
  final val typeAttribute: String = "xs:float"

  def retime(newTimestamp: Timestamp): FloatValue = this.copy(timestamp = newTimestamp)
}

case class DoubleValue(
                        value: Double,
                        timestamp: Timestamp
                      ) extends Value[Double] {
  final val typeAttribute: String = "xs:double"

  def retime(newTimestamp: Timestamp): DoubleValue = this.copy(timestamp = newTimestamp)
}

case class BooleanValue(
                         value: Boolean,
                         timestamp: Timestamp
                       ) extends Value[Boolean] {
  final val typeAttribute: String = "xs:boolean"

  def retime(newTimestamp: Timestamp): BooleanValue = this.copy(timestamp = newTimestamp)
}

case class StringPresentedValue(
                                 value: String,
                                 timestamp: Timestamp,
                                 typeAttribute: String = "xs:string"
                               ) extends Value[String] {
  def retime(newTimestamp: Timestamp): StringPresentedValue = this.copy(timestamp = newTimestamp)
}

object Value {
  def apply(
             value: Any,
             timestamp: Timestamp
           ): Value[Any] = {
    value match {
      case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] => 
        ODFValue(odf.toImmutable, timestamp)
      case s: Short => ShortValue(s, timestamp)
      case i: Int => IntValue(i, timestamp)
      case l: Long => LongValue(l, timestamp)
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
           ): Value[Any] = {
    value match {
      case odf: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path]] => 
        ODFValue(odf.toImmutable, timestamp)
      case s: Short => ShortValue(s, timestamp)
      case i: Int => IntValue(i, timestamp)
      case l: Long => LongValue(l, timestamp)
      case f: Float => FloatValue(f, timestamp)
      case d: Double => DoubleValue(d, timestamp)
      case b: Boolean => BooleanValue(b, timestamp)
      case s: String => applyFromString(s, typeValue, timestamp)
      case a: Any => StringPresentedValue(a.toString, timestamp, typeValue)
    }
  }

  def applyFromString(
                       value: String,
                       typeValue: String,
                       timestamp: Timestamp
                     ): Value[Any] = {
    val create = Try {
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
        case str: String =>
          StringPresentedValue(value, timestamp, str)
      }
    }
    create match {
      case Success(_value) => _value
      case Failure(_: Exception) =>
        StringPresentedValue(value, timestamp)
      case Failure(_: Throwable) =>
        StringPresentedValue(value, timestamp)
    }

  }
}
