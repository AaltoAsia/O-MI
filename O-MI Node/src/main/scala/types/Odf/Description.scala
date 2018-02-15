package types
package odf

import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
object Description{
  def unionReduce( descs: Seq[Description] ): Seq[Description] ={
    descs.groupBy( _.language ).mapValues{
      case descriptions => descriptions.foldLeft( Description(""))( _ union _)
    }.values.toVector
  }

}
case class  Description(
  val text: String,
  val language: Option[String] = None
) {
  def union( other: Description ): Description ={
    Description(
      if( other.text.nonEmpty ){
        other.text
      } else text,
      other.language.orElse( language )
    )
  }

  implicit def asDescriptionType : DescriptionType ={
    DescriptionType(
      text, 
      language.fold(Map.empty[String, DataRecord[Any]]){
        n=>Map( ("@lang" -> DataRecord(n)) ) 
      }
    )
  }

}
