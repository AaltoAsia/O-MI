package agents.parking

import scala.math
import scala.util.Try
import types.odf._
import types._

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
    val distance = radius * c;
    distance 
  }
  def toOdf(parentPath: Path): Seq[Node] ={
    val path: Path= parentPath / "geo"
    Seq(
      Object( 
        Vector( QlmID( "geo")),
        path,
        typeAttribute = Some(GeoCoordinates.mvType)
      ),
      InfoItem(
        "latitude",
        path / "latitude",
        typeAttribute = Some( "schema:latitude" ),
        values = Vector( DoubleValue( latitude, currentTimestamp))
      ),
      InfoItem(
        "longitude",
        path / "longitude",
        typeAttribute = Some( "schema:longitude" ),
        values = Vector( DoubleValue( longitude, currentTimestamp))
      )
    )  
  }
}
object GeoCoordinates{
  def mvType = "schema:GeoCoordinates"
  def parseOdf( path: Path, odf: ImmutableODF): Try[GeoCoordinates] ={
    Try{
      odf.get(path) match{
        case Some(obj: Object) if obj.typeAttribute.contains(mvType) =>
         (odf.get( path / "latitude" ),odf.get( path / "longitude" )) match{
           case (Some(latII:InfoItem),Some(longII:InfoItem)) if latII.values.nonEmpty && longII.values.nonEmpty =>
             getDoubleOption(latII.path.last,path,odf).flatMap{
                lat: Double =>
                  getDoubleOption(longII.path.last,path,odf).map{
                    long: Double =>
                      GeoCoordinates( lat, long )
                  }
              }.getOrElse( throw  MVError( s"Latitude or longitude should have double or float value"))
           case (Some(latII:InfoItem),Some(longII:InfoItem)) =>
            throw MVError( s"Latitude and longitude should have a value.")
          case ( Some(n:Node), _) =>
            throw MVError( s"Latitude and longitude should both be InfoItems.")
          case ( _, Some(n:Node)) =>
            throw MVError( s"Latitude and longitude should both be InfoItems.")
          case (  _, None ) =>
            throw MVError( s"GeoCoordinates path $path needs both latitude and longitude.")
          case ( None, _) =>
            throw MVError( s"GeoCoordinates path $path needs both latitude and longitude.")
             
         }

        case Some(obj: Object) => 
          throw MVError( s"GeoCoordinates path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"GeoCoordinates path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"GeoCoordinates path $path not found from given O-DF")
      }
    }
  }
}
