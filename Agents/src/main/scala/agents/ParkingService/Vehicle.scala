package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

//TODO: How to present validFor*?
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
  def toString( vt: VehicleType ): String = vt match {
      case Car => "Car"
      case Truck => "Truck"
      case Coach => "Coach"
      case RecreationalVehicle => "RecreationalVehicle"
      case Bicycle => "Bicycle"
      case Motorbike => "Motorbike"
      case ElectricVehicle => "ElectricVehicle"
      case Unknown => "Unknown"
  }
}
import VehicleType._

object Vehicle{
  def apply( obj: OdfObject ): Vehicle ={
    obj.typeValue.collect{
      case typeStr: String if typeStr.startsWith("mv:")=>
        val lO = obj.get(obj.path / "length").collect{
          case ii: OdfInfoItem =>
            getDoubleFromInfoItem(ii)
        }.flatten
        val wO = obj.get(obj.path / "width").collect{
          case ii: OdfInfoItem =>
            getDoubleFromInfoItem(ii)
        }.flatten
        val hO = obj.get(obj.path / "height").collect{
          case ii: OdfInfoItem =>
            getDoubleFromInfoItem(ii)
        }.flatten
        val vt = VehicleType(typeStr.drop(3))
        vt match {
          case VehicleType.Car => Car(lO,wO,hO)
          case VehicleType.Truck => Truck(lO,wO,hO)
          case VehicleType.Coach => Coach(lO,wO,hO)
          case VehicleType.RecreationalVehicle => RecreationalVehicle(lO,wO,hO)
          case VehicleType.Bicycle => Bicycle(lO,wO,hO)
          case VehicleType.Motorbike => Motorbike(lO,wO,hO)
          case VehicleType.ElectricVehicle => ElectricVehicle(lO,wO,hO)
          case Unknown => throw new Exception( "Unknown Vehicle types")
        }
    }.getOrElse( throw new Exception("Unknown type for object that should contain subclass of mv:vehicle"))
  
  }
}
trait Vehicle{
  val length: Option[Double]
  val width: Option[Double]
  val height: Option[Double]
  val vehicleType: VehicleType
}
case class Bicycle(
                    length: Option[Double],
                    width: Option[Double],
                    height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Bicycle

  }
  case class Car(
                  length: Option[Double],
                  width: Option[Double],
                  height: Option[Double]
) extends Vehicle {
    final val vehicleType: VehicleType = VehicleType.Car

  }

  case class Coach(
                    length: Option[Double],
                    width: Option[Double],
                    height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Coach

  }

	case class Motorbike(
                        length: Option[Double],
                        width: Option[Double],
                        height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Motorbike

  }

	case class RecreationalVehicle(
                                  length: Option[Double],
                                  width: Option[Double],
                                  height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.RecreationalVehicle

  }

	case class Truck(
                    length: Option[Double],
                    width: Option[Double],
                    height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.Truck

  }

case class ElectricVehicle(
                            length: Option[Double],
                            width: Option[Double],
                            height: Option[Double]
) extends Vehicle{
    final val vehicleType: VehicleType = VehicleType.ElectricVehicle

  }
