package types
package odf

import scala.collection.{ Seq, Map }
import scala.collection.immutable.HashMap

import java.sql.Timestamp
import parsing.xmlGen.xmlTypes.QlmIDType
import parsing.xmlGen.scalaxb.DataRecord

object QlmID{

  def unionReduce( ids: Seq[QlmID] ): Seq[QlmID] ={
    ids.groupBy( _.id ).map{
      case (id, ids) =>
        ids.foldLeft(QlmID(id))( _ union _ )
    }.toVector
  }
}

case class QlmID(
  val id: String,
  val idType: Option[String] =  None,
  val tagType: Option[String] =  None,
  val startDate: Option[Timestamp] =  None,
  val endDate: Option[Timestamp] =  None,
  val attributes: Map[String,String] =  HashMap.empty
) {
  def union( other: QlmID ) : QlmID ={
    assert( id == other.id )
    QlmID(
      id,
      optionUnion( idType, other.idType ),
      optionUnion( tagType, other.tagType ),
      optionUnion( startDate, other.startDate ),
      optionUnion( endDate, other.endDate ),
      attributes ++ other.attributes
    )
  }
  
  implicit def asQlmIDType: QlmIDType = {
    val idTypeAttr: Seq[(String,DataRecord[Any])] = idType.map{
          typ =>
            ("@idType" -> DataRecord(typ))
        }.toSeq 
    val tagTypeAttr: Seq[(String,DataRecord[Any])]  = tagType.map{
          typ =>
            ("@tagType" -> DataRecord(typ))
        }.toSeq  
  
    val startDateAttr = startDate.map{
              startDate =>
                ("@startDate" -> DataRecord(timestampToXML(startDate)))
            }.toSeq
    val endDateAttr = endDate.map{
              endDate =>
                ("@endDate" -> DataRecord(timestampToXML(endDate)))
        }.toSeq
    QlmIDType(
      id,
        (
          idTypeAttr ++ 
          tagTypeAttr ++ 
          startDateAttr ++
          endDateAttr
        ).toMap ++ attributesToDataRecord( attributes )
    )
  }
}
