package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

case class ParkingFacility(
  name: String,
  owner: String,
  maxParkingHours: String,
  geo: GPSCoordinates,
  openingHours: OpeningHoursSpecification,
  spotTypes: Seq[ParkingSpotType]
){
   val numberOfCarsharingSpots 
   val numberOfBicycleSpots
   val numberOfDisabledPersonSpots
   val numberOfMotorbikeSpots
   val numberOfWomensSpots
   val parkingSpots: Seq[ParkingSpace]
   def numberOfOccupiedSpots: Int
   def numberOfVacantSpots: Int
   def totalCapacity: Int
  def toOdf( parentPath: Path ) = {
    val facilityPath = parentPath / name
    OdfObject(
      Vector( QlmID( name ) ),
      facilityPath,
      Vector(
        OdfInfoItem(
          facilityPath / "Owner",
          Vector( OdfValue( owner, currentTime) ),
          typeValue = Some( "mv:isOwnedBy")
        ),
        OdfInfoItem(
          facilityPath / "MaxParkingHours",
          Vector( OdfValue( maxParkingHours, currentTime) )
        )
      ),
      Vector( geo.toOdf(facilityPath, "geo" ), openingHours.toOdf( facilityPath) ) ++ 
      spotTypes.map( _.toOdf( facilityPath ) ).toVector,
      typeValue = Some( "schema:ParkingFacility" )
    )
  }
}
/*
case class AutomatedParkingGarage extends ParkingFacility
case class BicycleParkingStation extends ParkingFacility
case class ParkingGarage extends ParkingFacility
case class ParkingLot extends ParkingFacility
case class UndergroundParkingGarage extends ParkingFacility
*/

case class OpeningHoursSpecification( 
  opens: String,
  closes: String
){
  def toOdf( parentPath: Path ) ={
    val ohsPath = parentPath / "openingHoursSpecification"
    OdfObject( 
      Vector( QlmID( "openingHoursSpecification"  ) ),
      ohsPath,
      Vector(
        OdfInfoItem(
          ohsPath / "opens",
          Vector( OdfValue( opens, "schema:Time", currentTime) )
        ),
        OdfInfoItem(
          ohsPath / "closes",
          Vector( OdfValue( closes, "schema:Time", currentTime) )
        )
      ),
      typeValue = Some( "schema:OpeningHoursSpecification" )
    )
  }
}
case class GPSCoordinates(
  latitude: Double,
  longitude: Double,
  address: Option[PostalAddress] = None
){
  def distance( other: GPSCoordinates ): Double ={
    val radius: Double = 6371e3

    val a1: Double = other.latitude.toRadians 
    val a2: Double = this.latitude.toRadians 
    val deltaLat: Double= (this.latitude - other.latitude).toRadians
    val deltaLon: Double= (this.longitude - other.longitude).toRadians 

    val t: Double = math.sin(deltaLat/2) * math.sin(deltaLat/2) +
            math.cos(a1) * math.cos(a2) *
            math.sin(deltaLon/2) * math.sin(deltaLon/2)
    var c: Double = 2 * math.asin(math.min(math.sqrt(t), 1))
    val distance: Double = radius * c;
    distance
  }
  def toOdf( parentPath: Path, objectName: String ) ={
    val geoPath = parentPath / objectName
    OdfObject(
      Vector( QlmID( objectName ) ),
      geoPath,
      Vector( 
        OdfInfoItem( 
          geoPath / "latitude",
          Vector( OdfValue( latitude, currentTime ) )
        ),
      OdfInfoItem( 
        geoPath / "longitude",
        Vector( OdfValue( longitude, currentTime )  )
      )
    ),
  address.map{ a => a.toOdf(geoPath ) }.toVector,
  typeValue = Some( "schema:GeoCoordinates" )
)
  }
}

case class PostalAddress(
  country: String,
  locality: String,
  region: String,
  streetAddress: String,
  postCode: String
) {
  def toOdf( parentPath: Path ) ={
    val addressPath = parentPath / "address"
    val countryII = OdfInfoItem( 
      addressPath / "addressCountry",
      Vector( OdfValue( country, currentTime ))
    )
    val localityII = OdfInfoItem( 
      addressPath / "addressLocality",
      Vector( OdfValue( locality, currentTime ))
    )
    val regionII = OdfInfoItem( 
      addressPath / "addressRegion",
      Vector( OdfValue( region, currentTime ))
    )
    val streetII = OdfInfoItem( 
      addressPath / "streetAddress",
      Vector( OdfValue( streetAddress, currentTime ))
    )
    val postCodeII = OdfInfoItem( 
      addressPath / "postCode",
      Vector( OdfValue( postCode, currentTime ))
    )

    OdfObject(
      Vector( QlmID( "address" ) ),
      addressPath,
      Vector(countryII, localityII, regionII, streetII, postCodeII ),
      Vector(),
      None,
      typeValue = Some( "schema:PostalAddress" )
    )
  }

}
