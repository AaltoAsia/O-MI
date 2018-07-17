package agents.parking


import types._
import types.odf._

import scala.util.Try

case class OpeningHoursSpecification( 
  name: String,
  opens: Option[String],
  closes: Option[String],
  dayOfWeek: Option[String]
){
  def toOdf(parentPath: Path): Seq[Node] ={
    val path: Path= parentPath / name
    Seq(
      Object( 
        Vector( QlmID( name)),
        path,
        typeAttribute = Some(OpeningHoursSpecification.mvType)
      )
    ) ++ 
    opens.map{
      str: String => 
      InfoItem(
        "opens",
        path / "opens",
        typeAttribute = Some( "schema:opens" ),
        values = Vector( StringValue( str, currentTimestamp))
      )
    }.toSeq ++
    closes.map{
      str: String =>
      InfoItem(
        "closes",
        path / "closes",
        typeAttribute = Some( "schema:closes" ),
        values = Vector( StringValue( str, currentTimestamp))
      )
    }.toSeq ++ 
    dayOfWeek.map{
      str: String => 
      InfoItem(
        "String",
        path / "String",
        typeAttribute = Some("schema:DayOfWeek"),
        values = Vector( StringValue( str, currentTimestamp))
      )
    }.toSeq  
  }
}

object OpeningHoursSpecification{
  def mvType = "schema:OpeningHoursSpecification"
  def parseOdf(path:Path, odf: ImmutableODF): Try[OpeningHoursSpecification] ={
    Try{
      odf.get(path) match{
        case Some(obj: Object) if obj.typeAttribute.contains(mvType) =>
          val closes = getStringOption("closes",path,odf)
          val opens = getStringOption("opens",path,odf)
          val dow = getStringOption("DayOfWeek",path,odf)
          OpeningHoursSpecification(obj.path.last,opens,closes,dow)
        case Some(obj: Object) => 
          throw MVError( s"OpeninHoursSpecification path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"OpeninHoursSpecification path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"OpeninHoursSpecification path $path not found from given O-DF")
      }
    }
  }
}
