package agents.parking


import agents.MVError
import types._
import types.odf._

import scala.util.Try

case class OpeningHoursSpecification( 
  name: String,
  opens: Option[String],
  closes: Option[String],
  dayOfWeek: Option[String]
){
  def toOdf(parentPath: Path, prefixes: Map[String,String]): Seq[Node] ={
    import OpeningHoursSpecification.mvType
    val prefix = prefixes.get("http://www.schema.org/").map{
      str => if( str.endsWith(":") ) str else str + ":"
    }
    val path: Path= parentPath / name
    Seq(
      Object( 
        Vector( QlmID( name)),
        path,
        typeAttribute = Some(s"${mvType(prefix)}")
      )
    ) ++ 
    opens.map{
      str: String => 
      InfoItem(
        "opens",
        path / "opens",
        typeAttribute = Some( s"${prefix.getOrElse("")}opens" ),
        values = Vector( StringValue( str, currentTimestamp))
      )
    }.toSeq ++
    closes.map{
      str: String =>
      InfoItem(
        "closes",
        path / "closes",
        typeAttribute = Some( s"${prefix.getOrElse("")}closes" ),
        values = Vector( StringValue( str, currentTimestamp))
      )
    }.toSeq ++ 
    dayOfWeek.map{
      str: String => 
      InfoItem(
        "String",
        path / "String",
        typeAttribute = Some(s"${prefix.getOrElse("")}DayOfWeek"),
        values = Vector( StringValue( str, currentTimestamp))
      )
    }.toSeq  
  }
}

object OpeningHoursSpecification{
  def mvType(  prefix: Option[String] )={
    val preStr = prefix.getOrElse("")
    s"${preStr}OpeningHoursSpecification"
  }
  def parseOdf(path:Path, odf: ImmutableODF, prefixes: Map[String,Set[String]]): Try[OpeningHoursSpecification] ={
    Try{
      val prefix = prefixes.get("http://www.schema.org/").map{
        prefix: Set[String] => 
          prefix.map{
            str => if( str.endsWith(":") ) str else str + ":"
          }
      }.toSet.flatten
      odf.get(path) match{
        case Some(obj: Object) if prefix.exists{
          prefix: String =>
          obj.typeAttribute.contains(mvType(Some(prefix)))
        } =>
          val closes = getStringOption("closes",path,odf)
          val opens = getStringOption("opens",path,odf)
          val dow = getStringOption("DayOfWeek",path,odf)
          OpeningHoursSpecification(obj.path.last,opens,closes,dow)
        case Some(obj: Object) => 
          throw MVError( s"OpeningHoursSpecification path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"OpeningHoursSpecification path $path should be Object with type ${mvType(None)}")
        case None => 
          throw MVError( s"OpeningHoursSpecification path $path not found from given O-DF")
      }
    }
  }
}
