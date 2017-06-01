package agents
package parkingService

import types.OdfTypes._
import types._

case class ParkingSpot(
  name: String,
  available: Option[String],
  user: Option[String],
  charger: Option[Charger]
){
  def toOdf( parentPath: Path ) ={
    val spotPath = parentPath / name
    val availableII = available.map{ str =>
      OdfInfoItem(
        spotPath / "Available",
        Vector( OdfValue( str, currentTime ) )
      ) 
    }.toVector
    val userII = user.map{ str =>
      OdfInfoItem(
        spotPath / "User",
        Vector( OdfValue( str, currentTime ) )
      ) 
    }.toVector
    OdfObject( 
      Vector( QlmID( name ) ),
      spotPath,
      availableII ++ userII,
      charger.map{ ch => ch.toOdf( spotPath ) }.toVector,
      typeValue = Some( "mv:ParkingSpace" )
    )
  }
}

case class Charger(
  brand: Option[String],
  model: Option[String],
  plug: PowerPlug
){
  def toOdf( parentPath: Path ) ={
    val chargerPath = parentPath / "Charger"
    val brandII = brand.map{
      str => 
        OdfInfoItem(
          chargerPath / "Brand",
          Vector( OdfValue( str, currentTime )),
          typeValue = Some( "mv:Brand" )
        )
    }.toVector
    val modelII = model.map{
      str => 
        OdfInfoItem(
          chargerPath / "Model",
          Vector( OdfValue( str, currentTime )),
          typeValue = Some( "mv:Model" )
        )
    }
    OdfObject(
      Vector( QlmID( "Charger" )),
      chargerPath,
      brandII ++ modelII,
      Vector( plug.toOdf( chargerPath ) ),
      typeValue = Some( "mv:Charger" )
    )
  }
}

case class PowerPlug(
  plugType: Option[String],
  power: Option[String],
  voltage: Option[String],
  cableAvailable: Option[String],
  lockerAvailable: Option[String],
  chargingSpeed: Option[String]
){
  def toOdf( parentPath: Path ) ={
    val plugPath = parentPath / "Plug"
    val pTII = plugType.map{ pT =>
      OdfInfoItem(
        plugPath / "PlugType",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:PlugType" )
      )
    }.toVector
    val powerII = power.map{ pT =>
      OdfInfoItem(
        plugPath / "Power",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:Power" )
      )
    }.toVector
    val voltageII = plugType.map{ pT =>
      OdfInfoItem(
        plugPath / "Voltage",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:Voltage" )
      )
    }.toVector
    val cableAvailableII = cableAvailable.map{ pT =>
      OdfInfoItem(
        plugPath / "CableAvailable",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:CableAvailable" )
      )
    }.toVector
    val lockerAvailableII = lockerAvailable.map{ pT =>
      OdfInfoItem(
        plugPath / "LockerAvailable",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:LockerAvailable" )
      )
    }.toVector
    val chargingSpeedII = chargingSpeed.map{ pT =>
      OdfInfoItem(
        plugPath / "ChargingSpeed",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:ChargingSpeed" )
      )
    }.toVector

    OdfObject(
      Vector( QlmID("Plug")),
      plugPath,
      pTII ++ 
      powerII ++ 
      voltageII ++ 
      cableAvailableII ++ 
      lockerAvailableII ++ 
      chargingSpeedII,
      typeValue = Some( "mv:Plug" )
    )

  }
}

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
case class GPSCoordinates(
  latitude: Double,
  longitude: Double,
  address: Option[PostalAddress] = None
){
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
