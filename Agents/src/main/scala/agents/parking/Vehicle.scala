package agents.parking

import agents.MVError
import types._
import types.odf._

import scala.util.{Failure, Success, Try}

object VehicleType extends Enumeration{
  type VehicleType = Value
  val Vehicle, Car, Truck, Coach, RecreationalVehicle, Bicycle, Motorbike, ElectricVehicle, BatteryElectricVehicle, PluginHybridElectricVehicle, Unknown = Value   
  def apply( str: String, prefixes: Map[String,Set[String]] ): VehicleType = {
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      prefix: Set[String] => 
        prefix.map{
          str => if( str.endsWith(":") ) str else str + ":"
        }
    }.toSet.flatten
    val vt = prefix.fold(str){
      case (v: String, prefix: String ) => v.replace(prefix,"")
    }
    values.find( v => v.toString == vt ).getOrElse( Unknown )
  }
  def toMvType( v: VehicleType, prefixes: Map[String,String]  ): String ={ 
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      str => if( str.endsWith(":") ) str else str + ":"
    }.getOrElse("")
    s"${prefix}$v"
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
  def parseOdf( path: Path, odf: ImmutableODF, prefixes: Map[String,Set[String]]): Try[Vehicle]={
    odf.get(path) match{
      case Some( obj: Object) =>
        lazy val l = getDoubleOption( "length", obj.path, odf)
        lazy val w = getDoubleOption( "width", obj.path, odf)
        lazy val h = getDoubleOption( "height", obj.path, odf)

        obj.typeAttribute.map{ str => VehicleType(str, prefixes) } match {
          case Some( VehicleType.ElectricVehicle ) => Success( new ElectricVehicle(l,w,h))
          case Some( VehicleType.BatteryElectricVehicle ) => Success( new BatteryElectricVehicle(l,w,h))
          case Some( VehicleType.PluginHybridElectricVehicle ) => Success( new PluginHybridElectricVehicle(l,w,h))
          case Some( VehicleType.Vehicle ) => Success( new Vehicle( l,w,h))
          case Some(VehicleType.Truck) => Success( Truck( l,w,h))
          case Some(VehicleType.RecreationalVehicle ) => Success( RecreationalVehicle( l,w,h))
          case Some(VehicleType.Motorbike) => Success( Motorbike( l,w,h))
          case Some(VehicleType.Coach) =>Success( Coach( l,w,h))
          case Some(VehicleType.Car) => Success( Car( l,w,h))
          case Some(VehicleType.Bicycle) => Success( Bicycle( l,w,h) )
          case Some(VehicleType.Unknown ) =>
            Failure(MVError( s"Vehicle path $path has wrong type attribute ${obj.typeAttribute}"))
          case Some(other) => Failure(MVError(s"Vehicle path $path has unknown type attribute ${obj.typeAttribute}"))
          case None =>
            Failure(MVError( s"Vehicle path $path has wrong type attribute ${obj.typeAttribute}"))
        }
        case Some(obj: Node) => 
          Failure(MVError( s"Vehicle path $path should be Object with type mv:Vehicle or some sub class of it"))
        case None => 
          Failure(MVError( s"Vehicle path $path not found from given O-DF"))
    }
  }
}
