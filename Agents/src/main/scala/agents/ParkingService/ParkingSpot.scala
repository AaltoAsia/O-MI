package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

object UsageType extends Enumeration{
  type UsageType = Value
  val Carsharing, DisabledPerson, Taxi, Women, ElectricVehicle = Value   
}

trait ParkingSpot{
  val name: String
  val available: Option[String]
  val user: Option[String]
  val charger: Option[Charger]
  val usageType: Option[UsageType]
  val idElectricVehicleFor: Option[VehicleType]
  val maxHeight: String
  val maxLength: String
  val maxWitdh: String
}
