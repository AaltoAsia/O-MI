package agents
package parkingService

import types.OdfTypes._
import types._

sealed trait ParkingEvent
case class Reservation( path: Path, user: String, openLid: Boolean = false )extends ParkingEvent{

  def toOdf: OdfObject ={
    val lidStatus = if( openLid ){
      Vector(
      OdfObject(
        Vector( QlmID( "Charger" )),
        path / "Charger",
        infoItems = Vector(
          OdfInfoItem(
            path / "Charger" / "LidStatus",
            values = Vector( OdfValue( "Open", currentTime ) )
          )
        )
      ))
    } else Vector()
    OdfObject(
      Vector( QlmID( path.last )),
      path,
      objects = lidStatus,
      infoItems = Vector(
        OdfInfoItem(
          path / "Available",
          values = Vector( OdfValue( "false", currentTime ) )
        ),
        OdfInfoItem(
          path / "User",
          values = Vector( OdfValue( user, currentTime ) )
        )
      )
    )
  }
}
case class FreeReservation( path: Path, user: String, openLid: Boolean = false )extends ParkingEvent{

  def toOdf: OdfObject ={
    val lidStatus = if( openLid ){
      Vector(
      OdfObject(
        Vector( QlmID( "Charger" )),
        path / "Charger",
        infoItems = Vector(
          OdfInfoItem(
            path / "Charger" / "LidStatus",
            values = Vector( OdfValue( "Open", currentTime ) )
          )
        )
      ))
    } else Vector()
    OdfObject(
      Vector( QlmID( path.last )),
      path,
      objects = lidStatus,
      infoItems = Vector(
        OdfInfoItem(
          path / "Available",
          values = Vector( OdfValue( "true", currentTime ) )
        ),
        OdfInfoItem(
          path / "User",
          values = Vector( OdfValue( "NONE", currentTime ) )
        )
      )
    )
  }
}
case class OpenLid( path: Path, user: String ) extends ParkingEvent{

  def lidStatusPath = path / "Charger" / "LidStatus"
  def toOdf: OdfObject ={
    OdfObject(
      Vector( QlmID( path.last )),
      path,
      objects = Vector(
        OdfObject(
          Vector( QlmID( "Charger" )),
          path / "Charger",
          infoItems = Vector(
            OdfInfoItem(
              path / "Charger" / "LidStatus",
              values = Vector( OdfValue( "Open", currentTime ) )
            )
          )
        )
      )
    )
  }
}


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
object ParkingSpot {

  def apply( obj: OdfObject ) : ParkingSpot ={
     val nameO = obj.id.headOption
     val available = obj.get( obj.path / "Available" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val user = obj.get( obj.path / "User" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val charger = obj.get( obj.path / "Charger" ).collect{
       case cobj: OdfObject =>
         Charger( cobj )
     }

    nameO.map{ name =>
      ParkingSpot( name.value, available, user, charger )
    }.getOrElse{
      throw new Exception( s"No name found for parking space in ${obj.path}" )
    }
  }
}

object Charger{

  def apply( obj: OdfObject ) : Charger ={
     val name = obj.id.headOption
     val brand = obj.get( obj.path / "Brand" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val model = obj.get( obj.path / "Model" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val lidStatus = obj.get( obj.path / "LidStatus" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val plugO = obj.get( obj.path / "Plug" ).collect{
       case cobj: OdfObject =>
         PowerPlug( cobj )
     }

    Charger( brand, model, lidStatus, plugO )
  }
}
case class Charger(
  brand: Option[String],
  model: Option[String],
  lidStatus: Option[String],
  plug: Option[PowerPlug]
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
    val lidStatusII = lidStatus.map{
      str => 
        OdfInfoItem(
          chargerPath / "LidStatus",
          Vector( OdfValue( str, currentTime ))
        )
    }
    OdfObject(
      Vector( QlmID( "Charger" )),
      chargerPath,
      brandII ++ modelII,
      plug.map( _.toOdf( chargerPath ) ).toVector ,
      typeValue = Some( "mv:Charger" )
    )
  }
}

object PowerPlug{
  
  def apply( obj: OdfObject ) : PowerPlug ={

     val pt = obj.get( obj.path / "PlugType" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val power = obj.get( obj.path / "Power" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val voltage = obj.get( obj.path / "Voltage" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val cableAvailable = obj.get( obj.path / "CableAvailable" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val lockerAvailable = obj.get( obj.path / "LockerAvailable" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val chargingSpeed = obj.get( obj.path / "ChargingSpeed" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     PowerPlug(
       pt,
       power,
       voltage,
       cableAvailable,
       lockerAvailable,
       chargingSpeed
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
