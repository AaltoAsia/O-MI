package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

object VehicleType extends Enumeration{
  type VehicleType = Value
  val Car, Truck, Coach, RecreationalVehicle, Bicycle, Motorbike, ElectricVehicle, Unknown = Value   
  def apply( str: String ): VehicleType ={
    str match {
      case "Car" => Car
      case "Truck" => Truck
      case "Coach" => Coach
      case "RecreationalVehicle" => RecreationalVehicle
      case "Bicycle" => Bicycle
      case "Motorbike" => Motorbike
      case "ElectricVehicle" => ElectricVehicle
      case s: String => Unknown
    }
  }
}
import VehicleType._

trait Vehicle{
  val length: Option[Double]
  val width: Option[Double]
  val height: Option[Double]
  val vehicleType: VehicleType
}
case class Bicycle(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Bicycle

  }
  case class Car(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle {
    final val vehicleType: VehicleType = VehicleType.Car

  }

  case class Coach(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Coach

  }

	case class Motorbike(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Motorbike

  }

	case class RecreationalVehicle(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.RecreationalVehicle

  }

	case class Truck(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Truck

  }

case class ElectricVehicle(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.ElectricVehicle

  }
