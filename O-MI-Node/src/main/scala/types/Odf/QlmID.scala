package types
package odf

import java.sql.Timestamp
import akka.stream.alpakka.xml._

import database.journal.{PQlmid, PTimestamp}

import scala.collection.immutable.HashMap
import scala.collection.{Map, Seq}

object QlmID {

  def unionReduce(ids: Seq[QlmID]): Seq[QlmID] = {
    ids.groupBy(_.id).map {
      case (id, _ids) =>
        _ids.foldLeft(QlmID(id))(_ union _)
    }.toVector
  }
}

case class QlmID(
                  id: String,
                  idType: Option[String] = None,
                  tagType: Option[String] = None,
                  startDate: Option[Timestamp] = None,
                  endDate: Option[Timestamp] = None,
                  attributes: Map[String, String] = HashMap.empty
                ) extends Element {
  def union(other: QlmID): QlmID = {
    assert(id == other.id)
    QlmID(
      id,
      optionUnion(idType, other.idType),
      optionUnion(tagType, other.tagType),
      optionUnion(startDate, other.startDate),
      optionUnion(endDate, other.endDate),
      attributes ++ other.attributes
    )
  }

  def persist: PQlmid = PQlmid(id,
    idType.getOrElse(""),
    tagType.getOrElse(""),
    startDate.map(time => PTimestamp(time.getTime)),
    endDate.map(time => PTimestamp(time.getTime)),
    attributes.toMap)
  
  def asXMLEvents(label:String) ={
    Seq(
      StartElement( label,
        tagType.map{
          str: String =>
            Attribute("tagType",str)
          }.toList ++ idType.map{
            str: String =>
            Attribute("idType",str)
          }.toList ++ startDate.map{
            timestamp: Timestamp =>
            Attribute("startDate",timestampToDateTimeString(timestamp))
          }.toList ++ endDate.map{
            timestamp: Timestamp =>
            Attribute("endDate",timestampToDateTimeString(timestamp))
          }.toList ++ attributes.map{
            case (key: String, value: String) => Attribute(key,value)
          }.toList

      ),
      Characters( id ),
      EndElement(label)
    )
  }
}
