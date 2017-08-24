package types 
import java.lang.{Iterable => JavaIterable}
import java.util.GregorianCalendar
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.datatype.DatatypeFactory
import java.sql.Timestamp

import scala.collection.immutable.{ 
  HashMap => ImmutableHashMap,
  TreeSet => ImmutableTreeSet
}

import parsing.xmlGen.xmlTypes
import parsing.xmlGen.scalaxb._
import types.ParseError

package object odf {
  type OdfParseResult = Either[JavaIterable[ParseError], ImmutableODF]
  trait Unionable[T] { 
    def union(t: T): T 
  }
 def timestampToXML(timestamp: Timestamp) : XMLGregorianCalendar ={
   val cal = new GregorianCalendar()
   cal.setTime(timestamp)
   DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
 }

 def attributesToDataRecord( 
  attributes: scala.collection.Map[String,String] ): Map[String,DataRecord[String]] ={
   attributes.map{
      case ( key: String, value: String ) =>
        if( key.startsWith("@") ){
          key -> DataRecord(None,Some(key),value)
        }else{  key -> DataRecord(None,Some(s"$key"),value) }
   }.toMap
 }
  def optionUnion[A]( left: Option[A], right: Option[A] ): Option[A]  ={
    right.orElse( left )
  }
}
