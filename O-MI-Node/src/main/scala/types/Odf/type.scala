package types.odf

import akka.http.scaladsl.model.Uri

case class TypeDefinition(value: String, prefix: Option[String], vocabulary: Option[Uri]){
  override def equals(other: Any ): Boolean ={
    other match{
      case td: TypeDefinition =>
        td.value == this.value && ( prefix == td.prefix || vocabulary == td.vocabulary)
      case a: Any => false
    }
  }
}
