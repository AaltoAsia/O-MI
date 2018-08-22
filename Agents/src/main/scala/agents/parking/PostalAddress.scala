package agents.parking

import types.Path
import types.odf._

import scala.util.Try

case class PostalAddress(
  country: Option[String],
  locality: Option[String],
  region: Option[String],
  streetAddress: Option[String],
  postCode: Option[String]
){
  def toOdf(parentPath: Path, prefixes: Map[String,String]): Seq[Node] ={
    import PostalAddress.mvType
      val prefix = prefixes.get("http://www.schema.org/").map{
        str => if( str.endsWith(":") ) str else str + ":"
      }
    val path: Path= parentPath / "address"
    Seq(
      Object( 
        Vector( QlmID( "address")),
        path,
        typeAttribute = Some(s"${mvType(prefix)}")
      )
    ) ++ 
    country.map{ c =>
      InfoItem(
        "addressCountry",
        path / "addressCountry",
        typeAttribute = Some( s"${prefix.getOrElse("")}addressCountry" ),
        values = Vector( StringValue( c, currentTimestamp))
      )
    }.toSeq ++ 
    locality.map{ l =>
      InfoItem(
        "addressLocality",
        path / "addressLocality",
        typeAttribute = Some( s"${prefix.getOrElse("")}addressLocality" ),
        values = Vector( StringValue( l, currentTimestamp))
      )
    }.toSeq ++ 
    region.map{ r =>
      InfoItem(
        "addressRegion",
        path / "addressRegion",
        typeAttribute = Some( s"${prefix.getOrElse("")}addressRegion" ),
        values = Vector( StringValue( r, currentTimestamp))
      )
    }.toSeq ++ 
    streetAddress.map{ sa =>
      InfoItem(
        "streetAddress",
        path / "streetAddress",
        typeAttribute = Some( s"${prefix.getOrElse("")}streetAddress" ),
        values = Vector( StringValue( sa, currentTimestamp))
      )
    }.toSeq ++ 
    postCode.map{ pc =>
      InfoItem(
        "postCode",
        path / "postCode",
        typeAttribute = Some( s"${prefix.getOrElse("")}postCode" ),
        values = Vector( StringValue( pc, currentTimestamp))
      )
    }
  }
}

object PostalAddress{
  def mvType(  prefix: Option[String] )={
    val preStr = prefix.getOrElse("")
    s"${preStr}PostalAddress"
  }
  def parseOdf( path: Path, odf: ImmutableODF, prefixes: Map[String,Set[String]] ): Try[PostalAddress] = {
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
          val ac = getStringOption("addressCountry",path,odf)
          val al = getStringOption("addressLocality",path,odf) 
          val ar = getStringOption("addressRegion",path,odf) 
          val pc = getStringOption("postCode",path,odf)
          val sa = getStringOption("streetAddress",path,odf)
          PostalAddress(ac,al,ar,pc,sa)
        case Some(obj: Object ) => 
          throw MVError( s"PostalAddress path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"PostalAddress path $path should be Object with type ${mvType(None)}")
        case None => 
          throw MVError( s"PostalAddress path $path not found from given O-DF")
      }
    }
  }
}
