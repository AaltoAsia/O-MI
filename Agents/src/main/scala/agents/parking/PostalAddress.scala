package agents.parking

import scala.util.{Try}
import types.odf._
import types.Path
import types._

case class PostalAddress(
  country: Option[String],
  locality: Option[String],
  region: Option[String],
  streetAddress: Option[String],
  postCode: Option[String]
){
  def toOdf(parentPath: Path): Seq[Node] ={
    val path: Path= parentPath / "address"
    Seq(
      Object( 
        Vector( QlmID( "address")),
        path,
        typeAttribute = Some(PostalAddress.mvType)
      )
    ) ++ 
    country.map{ c =>
      InfoItem(
        "addressCountry",
        path / "addressCountry",
        typeAttribute = Some( "schema:addressCountry" ),
        values = Vector( StringValue( c, currentTimestamp, Map())) 
      )
    }.toSeq ++ 
    locality.map{ l =>
      InfoItem(
        "addressLocality",
        path / "addressLocality",
        typeAttribute = Some( "schema:addressLocality" ),
        values = Vector( StringValue( l, currentTimestamp, Map())) 
      )
    }.toSeq ++ 
    region.map{ r =>
      InfoItem(
        "addressRegion",
        path / "addressRegion",
        typeAttribute = Some( "schema:addressRegion" ),
        values = Vector( StringValue( r, currentTimestamp, Map())) 
      )
    }.toSeq ++ 
    streetAddress.map{ sa =>
      InfoItem(
        "streetAddress",
        path / "streetAddress",
        typeAttribute = Some( "schema:streetAddress" ),
        values = Vector( StringValue( sa, currentTimestamp, Map())) 
      )
    }.toSeq ++ 
    postCode.map{ pc =>
      InfoItem(
        "postCode",
        path / "postCode",
        typeAttribute = Some( "schema:postCode" ),
        values = Vector( StringValue( pc, currentTimestamp, Map())) 
      )
    }
  }
}

object PostalAddress{
  def mvType = "schema:PostalAddress"
  def parseOdf( path: Path, odf: ImmutableODF ): Try[PostalAddress] = {
    Try{
      odf.get(path) match{
        case Some(obj: Object ) if obj.typeAttribute.contains(mvType) => 
          val ac = getStringOption("addressCountry",path,odf)
          val al = getStringOption("addressLocality",path,odf) 
          val ar = getStringOption("addressRegion",path,odf) 
          val pc = getStringOption("postCode",path,odf)
          val sa = getStringOption("streetAddress",path,odf)
          PostalAddress(ac,al,ar,pc,sa)
        case Some(obj: Object ) => 
          throw MVError( s"PostalAddress path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"PostalAddress path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"PostalAddress path $path not found from given O-DF")
      }
    }
  }
}
