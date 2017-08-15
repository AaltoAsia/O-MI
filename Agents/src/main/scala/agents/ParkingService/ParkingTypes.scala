package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._




case class Dimensions(
  length: Double,
  width: Double,
  height: Double
){
  def toOdf( parentPath: Path, objectName: String = "Dimensions") = {
    val dimensionsPath = parentPath / objectName
    OdfObject( 
      Vector( QlmID( "Dimensions" ) ),
      dimensionsPath,
      Vector( 
        OdfInfoItem( 
          dimensionsPath / "length",
          Vector( OdfValue( length, currentTime ) )
        ),
        OdfInfoItem( 
          dimensionsPath / "width",
          Vector( OdfValue( width, currentTime ) )
        ),
        OdfInfoItem( 
          dimensionsPath / "height",
          Vector( OdfValue( height, currentTime ) )
        )
      ),
      typeValue = Some( "mv:Dimensions" )
    )
  }
}

case class ParkingSpotType(
  spotType: String,
  totalCapacity: Int,
  spotsAvailable: Int,
  hourlyPrice: String,
  maxHeight: String,
  maxLength: String,
  maxWitdh: String,
  spots: Seq[ParkingSpot]
){
  def toOdf( parentPath: Path ) ={
    val id = { if(spotType.startsWith("mv:") ) spotType.drop(3) else spotType }
    val pstPath = parentPath / id
    OdfObject(
      Vector( QlmID( id) ),
      pstPath,
      Vector(),
      spots.map( _.toOdf(pstPath) ).toVector,
      typeValue = Some( "mv:ParkingUsageType" )
    )
  } 
}

case class ParkingFacility(
  name: String,
  owner: String,
  maxParkingHours: String,
  geo: GPSCoordinates,
  openingHours: OpeningHoursSpecification,
  spotTypes: Seq[ParkingSpotType]
){
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

