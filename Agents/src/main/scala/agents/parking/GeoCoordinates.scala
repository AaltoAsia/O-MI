package agents.parking

import agents.MVError
import types._
import types.odf._

import scala.util.{Failure, Success, Try}

case class GeoCoordinates(
                           latitude: Double,
  longitude: Double
){
  def distanceTo( other: GeoCoordinates): Double= {
    val radius: Double = 6371e3
    val a1 = other.latitude.toRadians 
    val a2 = latitude.toRadians 
    val deltaLat = (latitude - other.latitude).toRadians
    val deltaLon = (longitude - other.longitude).toRadians 

    val t = math.sin(deltaLat/2) * math.sin(deltaLat/2) + math.cos(a1) * math.cos(a2) * math.sin(deltaLon/2) * math.sin(deltaLon/2)
    val c = 2 * math.asin(math.min(math.sqrt(t), 1))
    val distance = radius * c
    distance 
  }
  def toOdf(parentPath: Path, prefixes: Map[String,String]): Seq[Node] ={
    import GeoCoordinates.mvType
    val prefix = prefixes.get("http://www.schema.org/").map{
      str => if( str.endsWith(":") ) str else str + ":"
    }
    val path: Path= parentPath / "geo"
    Seq(
      Object( 
        Vector( QlmID( "geo")),
        path,
        typeAttribute = Some(s"${mvType(prefix)}")
      ),
      InfoItem(
        "latitude",
        path / "latitude",
        typeAttribute = Some( s"${prefix.getOrElse("")}latitude" ),
        values = Vector( DoubleValue( latitude, currentTimestamp))
      ),
      InfoItem(
        "longitude",
        path / "longitude",
        typeAttribute = Some( s"${prefix.getOrElse("")}longitude" ),
        values = Vector( DoubleValue( longitude, currentTimestamp))
      )
    )  
  }
}
object GeoCoordinates{
  
  def mvType(  prefix: Option[String] )={
    val preStr = prefix.getOrElse("")
    s"${preStr}GeoCoordinates"
  }
  def parseOdf( path: Path, odf: ImmutableODF, prefixes: Map[String,Set[String]]): Try[GeoCoordinates] ={
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
    (odf.get( path / "latitude" ),odf.get( path / "longitude" )) match{
      case (Some(latII:InfoItem),Some(longII:InfoItem)) if latII.values.nonEmpty && longII.values.nonEmpty =>
        getDoubleOption(latII.path.last,path,odf).flatMap{
          lat: Double =>
            getDoubleOption(longII.path.last,path,odf).map{
              long: Double =>
                Success(GeoCoordinates( lat, long ))
            }
        }.getOrElse( Failure(  MVError( s"Latitude or longitude should have double or float value")))
      case (Some(latII:InfoItem),Some(longII:InfoItem)) =>
        Failure( MVError( s"Latitude and longitude should have a value."))
      case ( Some(n:Node), _) =>
        Failure( MVError( s"Latitude and longitude should both be InfoItems."))
      case ( _, Some(n:Node)) =>
        Failure( MVError( s"Latitude and longitude should both be InfoItems."))
      case (  _, None ) =>
        Failure( MVError( s"GeoCoordinates path $path needs both latitude and longitude."))
      case ( None, _) =>
        Failure( MVError( s"GeoCoordinates path $path needs both latitude and longitude."))

    }

      case Some(obj: Object) => 
        Failure( MVError( s"GeoCoordinates path $path has wrong type attribute ${obj.typeAttribute}. ${mvType(None)} expected"))
      case Some(obj: Node) => 
        Failure( MVError( s"GeoCoordinates path $path should be Object with type ${mvType(None)}"))
      case None => 
        Failure( MVError( s"GeoCoordinates path $path not found from given O-DF"))
  }
  }
}
