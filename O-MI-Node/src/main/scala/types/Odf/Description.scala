package types
package odf

import database.journal.PDescription
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._

object Description {
  def unionReduce(descs: Set[Description]): Set[Description] = {
    descs.groupBy(_.language).mapValues(
      descriptions => descriptions.foldLeft(Description(""))(_ union _)).values.toSet
  }

  def empty: Description = Description("")
}

case class Description(
                        text: String,
                        language: Option[String] = None
                      ) {
  def union(other: Description): Description = {
    Description(
      if (other.text.nonEmpty) {
        other.text
      } else text,
      other.language.orElse(language)
    )
  }

  implicit def asDescriptionType: DescriptionType = {
    DescriptionType(
      text,
      language.fold(Map.empty[String, DataRecord[Any]]) {
        n => Map("@lang" -> DataRecord(n))
      }
    )
  }

  def persist(): PDescription = PDescription(text, language.getOrElse(""))

}
