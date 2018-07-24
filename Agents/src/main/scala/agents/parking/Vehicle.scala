package agents.parking

import types._
import types.odf._

import scala.util.Try

object VehicleType extends Enumeration{
  type VehicleType = Value
  val Vehicle, Car, Truck, Coach, RecreationalVehicle, Bicycle, Motorbike, ElectricVehicle, BatteryElectricVehicle, PluginHybridElectricVehicle, Unknown = Value   
  def apply( str: String ): VehicleType = {
    val vt = str.replace("mv:","")
    values.find( v => v.toString == vt ).getOrElse( Unknown )
  }
}
import agents.parking.VehicleType._

class Vehicle( 
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Dimensions{
  val vehicleType: VehicleType = VehicleType.Vehicle
  def mvType = s"mv:$vehicleType"
}
case class Bicycle(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.Bicycle
  override def mvType = s"mv:$vehicleType"

}
case class Car(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.Car
  override def mvType = s"mv:$vehicleType"

}

case class Coach(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.Coach
  override def mvType = s"mv:$vehicleType"

}

case class Motorbike(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.Motorbike
  override def mvType = s"mv:$vehicleType"

}

case class RecreationalVehicle(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.RecreationalVehicle
  override def mvType = s"mv:$vehicleType"

}

case class Truck(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.Truck
  override def mvType = s"mv:$vehicleType"

}

class ElectricVehicle(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  override val vehicleType: VehicleType = VehicleType.ElectricVehicle
  override def mvType = s"mv:$vehicleType"

}

class BatteryElectricVehicle(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.BatteryElectricVehicle
  override def mvType = s"mv:$vehicleType"

}

class PluginHybridElectricVehicle(
  override val length: Option[Double],
  override val width: Option[Double],
  override val height: Option[Double]
) extends Vehicle(length,width,height){
  final override val vehicleType: VehicleType = VehicleType.PluginHybridElectricVehicle
  override def mvType = s"mv:$vehicleType"
}

object Vehicle{
  def parseOdf( path: Path, odf: ImmutableODF): Try[Vehicle]={
    Try{
      odf.get(path) match{
        case Some( obj: Object) =>
          lazy val l = getDoubleOption( "length", obj.path, odf)
          lazy val w = getDoubleOption( "width", obj.path, odf)
          lazy val h = getDoubleOption( "height", obj.path, odf)

          obj.typeAttribute.map{ str => VehicleType(str) } match {
            case Some( VehicleType.ElectricVehicle ) => new ElectricVehicle(l,w,h)
            case Some( VehicleType.BatteryElectricVehicle ) => new BatteryElectricVehicle(l,w,h)
            case Some( VehicleType.PluginHybridElectricVehicle ) => new PluginHybridElectricVehicle(l,w,h)
            case Some( VehicleType.Vehicle ) => new Vehicle( l,w,h)
            case Some(VehicleType.Truck) => Truck( l,w,h)
            case Some(VehicleType.RecreationalVehicle ) => RecreationalVehicle( l,w,h)
            case Some(VehicleType.Motorbike) => Motorbike( l,w,h)
            case Some(VehicleType.Coach) => Coach( l,w,h)
            case Some(VehicleType.Car) => Car( l,w,h)
            case Some(VehicleType.Bicycle) => Bicycle( l,w,h)
            case Some(VehicleType.Unknown ) =>
              throw MVError( s"Vehicle path $path has wrong type attribute ${obj.typeAttribute}")
            case Some(other) => throw MVError(s"Vehicle path $path has unknown type attribute ${obj.typeAttribute}")
            case None =>
              throw MVError( s"Vehicle path $path has wrong type attribute ${obj.typeAttribute}")
          }
        case Some(obj: Node) => 
          throw MVError( s"Vehicle path $path should be Object with type mv:Vehicle or some sub class of it")
        case None => 
          throw MVError( s"Vehicle path $path not found from given O-DF")
      }
    }
  }
}
