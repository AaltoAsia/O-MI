
package agents
package parkingService

import types.OdfTypes._
import types._

case class AllreadyReserved( path: Path ) extends Exception( s"Parking space at $path is allready reserved.")
case class WrongUser( path: Path ) extends Exception( s"Parking space at $path is received to other user.")
case class UnknownEvent(path: Path) extends Exception( s"Received unknown event fo Parking space at $path.")
sealed trait ParkingEvent
case class Reservation( path: Path, user: String, openLid: Boolean = false )extends ParkingEvent{

  def toOdf: OdfObject ={
    val lidStatus = if( openLid ){
      Vector(
      OdfObject(
        Vector( OdfQlmID( "Charger" )),
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
      Vector( OdfQlmID( path.last )),
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
        Vector( OdfQlmID( "Charger" )),
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
      Vector( OdfQlmID( path.last )),
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

  def lidStatusPath: Path = path / "Charger" / "LidStatus"
  def toOdf: OdfObject ={
    OdfObject(
      Vector( OdfQlmID( path.last )),
      path,
      objects = Vector(
        OdfObject(
          Vector( OdfQlmID( "Charger" )),
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

case class UpdatePlugMeasurements( path: Path, currentInmA: Option[Double], powerInW: Option[Double], voltageInV: Option[Double]) extends ParkingEvent{

  val chargerPath: Path = path / "Charger"
  val plugPath: Path = chargerPath / "Plug"
  def toOdf: OdfObject ={
    val voltageII = voltageInV.map{
      voltage: Double =>
      OdfInfoItem( 
        plugPath / "Voltage", 
        values = Vector( OdfValue( voltage, currentTime)),
        typeValue = Some( "mv:Voltage")
      )
    }.toVector
    val powerII = powerInW.map{
      power: Double =>
      OdfInfoItem( 
        plugPath / "Power", 
        values = Vector( OdfValue( power, currentTime)),
        typeValue = Some( "mv:Power")
      )
    }.toVector
    val currentII = currentInmA.map{
      currentmA: Double =>
      OdfInfoItem( 
        plugPath / "currentInmA", 
        values = Vector( OdfValue( currentmA, currentTime)),
        typeValue = Some( "mv:currentInmA")
      )
    }.toVector
    OdfObject(
      Vector( OdfQlmID( path.last )),
      path,
      objects = Vector(
        OdfObject(
          Vector( OdfQlmID( chargerPath.last )),
          chargerPath,
          objects = Vector(
            OdfObject(
              Vector( OdfQlmID( plugPath.last )),
              plugPath,
              infoItems = currentII ++ powerII ++ voltageII
            )
          )
        )
      )
    )
  }
}
