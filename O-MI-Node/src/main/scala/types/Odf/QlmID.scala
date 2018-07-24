package types
package odf

import java.sql.Timestamp

import database.journal.{PQlmid, PTimestamp}
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes.QlmIDType

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
                ) {
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

  implicit def asQlmIDType: QlmIDType = {
    val idTypeAttr: Seq[(String, DataRecord[Any])] = idType.map {
      typ =>
        "@idType" -> DataRecord(typ)
    }.toSeq
    val tagTypeAttr: Seq[(String, DataRecord[Any])] = tagType.map {
      typ =>
        "@tagType" -> DataRecord(typ)
    }.toSeq

    val startDateAttr = startDate.map {
      startDate =>
        "@startDate" -> DataRecord(timestampToXML(startDate))
    }.toSeq
    val endDateAttr = endDate.map {
      endDate =>
        "@endDate" -> DataRecord(timestampToXML(endDate))
    }.toSeq
    QlmIDType(
      id,
      (
        idTypeAttr ++
          tagTypeAttr ++
          startDateAttr ++
          endDateAttr
        ).toMap ++ attributesToDataRecord(attributes)
    )
  }

  def persist: PQlmid = PQlmid(id,
    idType.getOrElse(""),
    tagType.getOrElse(""),
    startDate.map(time => PTimestamp(time.getTime)),
    endDate.map(time => PTimestamp(time.getTime)),
    attributes.toMap)
}
