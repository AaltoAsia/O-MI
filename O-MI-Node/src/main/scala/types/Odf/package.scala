package types

import java.lang.{Iterable => JavaIterable}
import java.sql.Timestamp
import java.util.{GregorianCalendar, Dictionary}
import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}

import parsing.xmlGen.scalaxb._

import scala.collection.immutable.{Map => ImmutableMap, HashMap => ImmutableHashMap, TreeSet => ImmutableTreeSet}
import scala.collection.Map
import scala.collection.JavaConverters._

package object odf {
  type OdfParseResult = Either[JavaIterable[ParseError], ImmutableODF]
  type OdfCollection[T] = Vector[T]

  trait Unionable[T] {
    def union(t: T): T
  }

  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar = {
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
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
