package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

object PowerPlug{
  
  def apply( obj: OdfObject ) : PowerPlug ={

     val pt = obj.get( obj.path / "PlugType" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val current = obj.get( obj.path / "currentInmA" ).collect{
       case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
     }.flatten
     val power = obj.get( obj.path / "Power" ).collect{
       case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
     }.flatten
     val voltage = obj.get( obj.path / "Voltage" ).collect{
       case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
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
       current,
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
  current: Option[Double],
  power: Option[Double],
  voltage: Option[Double],
  cableAvailable: Option[String],
  lockerAvailable: Option[String],
  chargingSpeed: Option[String]
){
  def validFor( requested: PowerPlug ): Boolean={
    requested.plugType.forall{ str: String => 
      plugType.contains( str)
    } && requested.cableAvailable.forall{ str: String => 
      cableAvailable.contains( str)
    } && requested.lockerAvailable.forall{ str: String => 
      lockerAvailable.contains( str)
    } && requested.chargingSpeed.forall{ str: String => 
      chargingSpeed.contains( str)
    }  
  }
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
    val currentII = plugType.map{ pT =>
      OdfInfoItem(
        plugPath / "currentInmA",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:currentInmA" )
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
      Vector( OdfQlmID("Plug")),
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
