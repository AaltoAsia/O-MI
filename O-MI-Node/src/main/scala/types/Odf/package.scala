package types

import java.lang.{Iterable => JavaIterable}
import java.time.{ZoneId, OffsetDateTime}
import java.sql.Timestamp
import java.util.{Dictionary, GregorianCalendar}

import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}
import parsing.xmlGen.scalaxb._

import scala.collection.JavaConverters._
import scala.collection.{Map}
import scala.collection.immutable.{Map => ImmutableMap}

package object odf extends InfoItem.Builders {

  type OdfParseResult = Either[JavaIterable[_ <:ParseError], ImmutableODF]
  type OdfCollection[T] = Vector[T]

  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar = {
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
  }

  def timestampToDateTimeString(timestamp: Timestamp): String = {
    OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault()).toString
  }
  def attributesToDataRecord(attributes: Map[String, String]): ImmutableMap[String, DataRecord[String]] = {
    attributes.map {
      case (key: String, value: String) =>
        if (key.startsWith("@"))
          key -> DataRecord(None, Some(key.tail), value)
        else
          "@" + key -> DataRecord(None, Some(key), value)
    }.toMap
  }

  /*
 def attributesToDataRecord( 
  attributes: scala.collection.Map[String,String] ): Map[String,DataRecord[String]] ={
   attributes.map{
      case ( key: String, value: String ) =>
        if( key.startsWith("@") ){
          key -> DataRecord(None,Some(key),value)
        }else{  key -> DataRecord(None,Some(s"$key"),value) }
   }.toMap
 }*/
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
