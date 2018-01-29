package agents
package parkingService

import scala.math
import scala.util._
import types.OdfTypes._
import types._


//TODO: 'Rename' to UserGroup
object UsageType extends Enumeration{
  type UsageType = Value
  val Carsharing, DisabledPerson, Taxi, Womens, ElectricVehicle, Unknown = Value   
  def apply( str: String ): UsageType ={
    str match{
      case "CarsharingParkingSpace" => Carsharing
      case "DisabledParkingSpace" => DisabledPerson
      case "TaxiParkingSpace" => Taxi
      case "Wonen'sParkingSpace" => Womens
      case "ElectricVehicle" => ElectricVehicle
      case s: String => Unknown
    }
  }
  def toString( str: UsageType): String ={
    str match{
      case Carsharing => "CarsharingParkingSpace" 
      case DisabledPerson => "DisabledParkingSpace" 
      case Taxi => "TaxiParkingSpace"
      case Womens => "Wonen'sParkingSpace"
      case ElectricVehicle => "ElectricVehicle"
      case Unknown => "Unknown"
    }
  }
}
import UsageType._
import VehicleType._

case class ParkingSpace(
  val name: String,
  val userGroup: Option[UsageType],
  val intendedFor: Option[VehicleType],
  val available: Option[Boolean],
  val user: Option[String],
  val charger: Option[Charger],
  val maxHeight: Option[Double],
  val maxLength: Option[Double],
  val maxWidth: Option[Double]
){
  def validForVehicle( vehicle: Vehicle ): Boolean ={
    lazy val dimensionCheck = {
      maxHeight.forall{ limit => vehicle.height.forall( _ <= limit ) } &&
      maxLength.forall{ limit => vehicle.length.forall( _  <= limit ) } &&
      maxWidth.forall{ limit => vehicle.width.forall( _  <= limit ) }
    }

    intendedFor.contains( vehicle.vehicleType ) || dimensionCheck
  }
  def toOdf( parentPath: Path ) ={
    val spotPath = parentPath / name
    val availableII = available.map{
      b: Boolean =>
      OdfInfoItem(
        spotPath / "Available",
        Vector( OdfValue( b, currentTime ) )
      ) 
    }.toVector
    val intendedTypeII = intendedFor.map{
      v: VehicleType =>
      OdfInfoItem(
        spotPath / "intendedForVechile",
        Vector( OdfValue( VehicleType.toString(v), currentTime ) ),
        typeValue = Some( "mv:intededForVehicel")
      ) 
    }.toVector
    val userGroupII = userGroup.map{
      v: UsageType =>
      OdfInfoItem(
        spotPath / "parkingUsageType",
        Vector( OdfValue( UsageType.toString(v), currentTime ) ),
        typeValue = Some( "mv:parkingUsageType")
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
      availableII ++ userII ++ maxHII ++ maxWII ++ maxLII ++ intendedTypeII ++ userGroupII,
      charger.map{ ch => ch.toOdf( spotPath ) }.toVector,
      typeValue = Some( "mv:ParkingSpace" )
    )
  }
}
object ParkingSpace {

  def apply( obj: OdfObject ) : ParkingSpace ={
    val nameO = obj.id.headOption
    val available: Option[Boolean] = obj.get( obj.path / "Available" ).collect{
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
    val iFV = obj.get( obj.path / "intendedForVehicle" ).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem( ii ).map{
          case str: String  =>  VehicleType(str)
        }
    }.flatten
    val ut = obj.get( obj.path / "parkingUsageType" ).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem( ii ).map{
          case str: String  =>  UsageType(str)
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
