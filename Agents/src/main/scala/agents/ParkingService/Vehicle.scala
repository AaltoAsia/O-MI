package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

object VehicleType extends Enumeration{
  type UsageType = Value
  val Car, Truck, Coach, RecreationalVehicle, Bicycle, Motorbike, ElectricVehicle = Value   
}
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
    final val vehicleType: VehicleType = Bicycle

  }
  case class Car(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle {
    final val vehicleType: VehicleType = Car

  }

  case class Coach(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = Coach

  }

	case class Motorbike(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = Motorbike

  }

	case class RecreationalVehicle(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = RecreationalVehicle

  }

	case class Truck(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = Truck

  }

case class ElectricVehicle(
  val length: Option[Double],
  val width: Option[Double],
  val height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = ElectricVehicle

  }
