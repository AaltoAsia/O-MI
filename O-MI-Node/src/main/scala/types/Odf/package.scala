package types

import java.lang.{Iterable => JavaIterable}
import java.time.{ZoneId, OffsetDateTime}
import java.sql.Timestamp
import java.util.{Dictionary, GregorianCalendar, TimeZone}

import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}

import scala.collection.JavaConverters._
import scala.collection.Map
import scala.collection.immutable.{Map => ImmutableMap}

package object odf extends InfoItem.Builders{

  type OdfParseResult = Either[JavaIterable[_ <:ParseError], ImmutableODF]
  type OdfCollection[T] = Vector[T]
  val dataTypeFactory = DatatypeFactory.newInstance() //THIS MIGHT BE UNSAFE

  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar = {
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
  }

  def timestampToDateTimeString(timestamp: Timestamp): String = {
    val gc = new GregorianCalendar()
    gc.setTimeInMillis(timestamp.getTime())
    gc.setTimeZone( TimeZone.getTimeZone( ZoneId.systemDefault()))
    dataTypeFactory.newXMLGregorianCalendar( gc ).toXMLFormat()
    //OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault()).toString
  }
  def optionUnion[A](left: Option[A], right: Option[A]): Option[A] = {
    right.orElse(left)
  }

  def attributeUnion(left: ImmutableMap[String, String],
                     right: ImmutableMap[String, String]): ImmutableMap[String, String] = {
    left ++ right filter (_._2.nonEmpty)
  }

  def optionAttributeUnion(left: Option[String], right: Option[String]): Option[String] = {
    right orElse left filter (_.nonEmpty)
  }

  def dictionaryToMap[K, V](dict: Dictionary[K, V]): Map[K, V] = {
    dict.asScala.toMap
  }

}

trait Element
