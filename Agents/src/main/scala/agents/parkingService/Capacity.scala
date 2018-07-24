
package agents.parkingService

import Capacity._
import UserGroup._
import VehicleType._
import types.OdfTypes._
import types._
import agents.parkingService._

case class Capacity(
                     name: String,
                     currentCapacity: Option[Long],
                     totalCapacity: Option[Long],
                     validForVehicle: Option[VehicleType],
                     userGroup: Option[UserGroup]
                   ) {

  def toOdf(parentPath: Path): OdfObject = {

    val path = parentPath / name
    val currentCapacityII = currentCapacity.map {
      count: Long =>
        OdfInfoItem(
          path / currentCapacityStr,
          Vector(OdfValue(count, currentTime)),
          typeValue = Some("mv:" + currentCapacityStr)
        )
    }.toVector
    val totalCapacityII = totalCapacity.map {
      count: Long =>
        OdfInfoItem(
          path / totalCapacityStr,
          Vector(OdfValue(count, currentTime)),
          typeValue = Some("mv:" + totalCapacityStr)
        )
    }.toVector
    val validForVehicleII = validForVehicle.map {
      veh: VehicleType =>
        OdfInfoItem(
          path / validForVehicleStr,
          Vector(OdfValue(veh.toString, currentTime)),
          typeValue = Some("mv:" + validForVehicleStr)
        )
    }.toVector
    val userGroupII = userGroup.map {
      veh: UserGroup =>
        OdfInfoItem(
          path / validForUserGroupStr,
          Vector(OdfValue(veh.toString, currentTime)),
          typeValue = Some("mv:" + validForUserGroupStr)
        )
    }.toVector
    OdfObject(
      Vector(OdfQlmID(name)),
      path,
      currentCapacityII ++ totalCapacityII ++ userGroupII ++ validForVehicleII,
      typeValue = Some("mv:" + capacityTypeStr)
    )


  }
}

object Capacity {

  val currentCapacityStr = "realTimeValue"
  val totalCapacityStr = "maximumValue"
  val vehicleTypeStr = "vehicle"
  val validForVehicleStr = "validForVehicle"
  val validForUserGroupStr = "validForUserGroup"
  val userGroupStr = "userGroup"
  val capacityTypeStr = "capacity"

  def apply(obj: OdfObject): Capacity = {
    val nameO = obj.id.headOption
    val currentCapacity: Option[Long] = obj.get(obj.path / currentCapacityStr).collect {
      case ii: OdfInfoItem =>
        getLongFromInfoItem(ii)
    }.flatten
    val totalCapacity: Option[Long] = obj.get(obj.path / totalCapacityStr).collect {
      case ii: OdfInfoItem =>
        getLongFromInfoItem(ii)
    }.flatten
    val validForVehicle: Option[VehicleType] = obj.get(obj.path / vehicleTypeStr).collect {
      case ii: OdfInfoItem =>
        getStringFromInfoItem(ii).map(VehicleType(_))
    }.flatten
    val usageType: Option[UserGroup] = obj.get(obj.path / userGroupStr).collect {
      case ii: OdfInfoItem =>
        getStringFromInfoItem(ii).map(UserGroup(_))
    }.flatten
    nameO.map { name =>
      Capacity(
        name.value,
        currentCapacity,
        totalCapacity,
        validForVehicle,
        usageType
      )
    }.getOrElse {
      throw new Exception(s"No name found for mv:$capacityTypeStr in ${obj.path}")
    }

  }

}
