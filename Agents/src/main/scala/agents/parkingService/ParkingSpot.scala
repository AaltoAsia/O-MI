package agents.parkingService

import types.OdfTypes._
import types._
import parkingService._

object UserGroup extends Enumeration{
  type UserGroup = Value
  val Carsharing, DisabledPerson, Taxi, Women, Inhabitants, Families, Unknown = Value   
  def apply( str: String ): UserGroup ={
    str match{
      case "CarsharingUsers" => Carsharing
      case "PersonWithDisabledParkingPermit" => DisabledPerson
      case "TaxiDrivers" => Taxi
      case "Women" => Women
      case "Families" => Families
      case "Inhabitants" => Inhabitants
      case s: String => Unknown
    }
  }
  def toString( str: UserGroup): String ={
    str match{
      case Carsharing => "CarsharingUsers" 
      case DisabledPerson => "PersonWithDisabledParkingPermit" 
      case Taxi => "TaxiDrivers"
      case Women => "Women"
      case Inhabitants => "Inhabitants"
      case Families => "Families"
      case Unknown => "Unknown"
    }
  }
}
import agents.parkingService.UserGroup._
import agents.parkingService.VehicleType._

case class ParkingSpace(
                         name: String,
  validForUserGroup: Option[UserGroup],
  validForVehicle: Option[VehicleType],
  available: Option[Boolean],
  user: Option[String],
  charger: Option[Charger],
  maxHeight: Option[Double],
  maxLength: Option[Double],
  maxWidth: Option[Double]
){
  def validDimensions( vehicle: Vehicle ): Boolean ={
    lazy val dimensionCheck = {
      maxHeight.forall{ limit => vehicle.height.forall( _ <= limit ) } &&
      maxLength.forall{ limit => vehicle.length.forall( _  <= limit ) } &&
      maxWidth.forall{ limit => vehicle.width.forall( _  <= limit ) }
    }

    validForVehicle.contains( vehicle.vehicleType ) || dimensionCheck
  }
  def toOdf( parentPath: Path ): OdfObject ={
    val spotPath = parentPath / name
    val availableII = available.map{
      b: Boolean =>
      OdfInfoItem(
        spotPath / "available",
        Vector( OdfValue( b, currentTime ) )
      ) 
    }.toVector
    val validTypeII = validForVehicle.map{
      v: VehicleType =>
      OdfInfoItem(
        spotPath / "validForVehicle",
        Vector( OdfValue( VehicleType.toString(v), currentTime ) ),
        typeValue = Some( "mv:intededForVehicle")
      ) 
    }.toVector
    val userGroupII = validForUserGroup.map{
      v: UserGroup =>
      OdfInfoItem(
        spotPath / "validForUserGroup",
        Vector( OdfValue( UserGroup.toString(v), currentTime ) ),
        typeValue = Some( "mv:validForUserGroup")
      ) 
    }.toVector
    val maxHII = maxHeight.map{
      v: Double=>
      OdfInfoItem(
        spotPath / "vechileHeightLimit",
        Vector( OdfValue( v, currentTime ) ),
        typeValue = Some( "mv:vehicleHeightLimit")
      ) 
    }.toVector
    val maxLII = maxLength.map{
      v: Double=>
      OdfInfoItem(
        spotPath / "vechileLengthLimit",
        Vector( OdfValue( v, currentTime ) ),
        typeValue = Some( "mv:vehicleLengthLimit")
      ) 
    }.toVector
    val maxWII = maxWidth.map{
      v: Double=>
      OdfInfoItem(
        spotPath / "vechileWidthLimit",
        Vector( OdfValue( v, currentTime ) ),
        typeValue = Some( "mv:vehicleWidthLimit")
      ) 
    }.toVector
    val userII = user.map{ str =>
      OdfInfoItem(
        spotPath / "User",
        Vector( OdfValue( str, currentTime ) )
      ) 
    }.toVector
    OdfObject( 
      Vector( OdfQlmID( name ) ),
      spotPath,
      availableII ++ userII ++ maxHII ++ maxWII ++ maxLII ++ validTypeII ++ userGroupII,
      charger.map{ ch => ch.toOdf( spotPath ) }.toVector,
      typeValue = Some( "mv:ParkingSpace" )
    )
  }
}
object ParkingSpace {

  def apply( obj: OdfObject ) : ParkingSpace ={
    val nameO = obj.id.headOption
    val available: Option[Boolean] = obj.get( obj.path / "available" ).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem( ii ).map{ 
          str: String =>  
            str.toLowerCase match{
              case "true" => true
              case "false" => false
            }
        }
    }.flatten
    val user = obj.get( obj.path / "User" ).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem( ii ).flatMap{
          str: String  =>
          if( str.toLowerCase == "none" ) None  else Some( str )
        }
    }.flatten
    val iFV = obj.get( obj.path / "validForVehicle" ).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem( ii ).map{
          case str: String  =>  VehicleType(str)
        }
    }.flatten
    val ut = obj.get( obj.path / "validForUserGroup" ).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem( ii ).map{
          case str: String  =>  UserGroup(str)
        }
    }.flatten
    val charger = obj.get( obj.path / "Charger" ).collect{
      case cobj: OdfObject =>
        Charger( cobj )
    }
    val hL = obj.get( obj.path / "vehicleHeightLimit" ).collect{
      case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
    }.flatten
    val lL = obj.get( obj.path / "vehicleLengthLimit" ).collect{
      case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
    }.flatten
    val wL = obj.get( obj.path / "vehicleWidthLimit" ).collect{
      case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
    }.flatten

    nameO.map{ name =>
      ParkingSpace( name.value, ut, iFV, available, user, charger, hL, lL, wL)
      }.getOrElse{
        throw new Exception( s"No name found for parking space in ${obj.path}" )
      }
  }
}
