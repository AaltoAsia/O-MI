
package agents.parking

import agents.parking.UserGroup._
import agents.parking.VehicleType._
import types._
import types.odf._

import scala.util.Try

case class ParkingCapacity(
                            name: String,
  maximum: Option[Long],
  current: Option[Long],
  validForVehicle: Seq[VehicleType],
  validUserGroup: Seq[UserGroup],
  maximumParkingHours: Option[Long]
){
  def toOdf(parentPath: Path): Seq[Node] ={
    val path: Path= parentPath / name
    Seq(
      Object( 
        Vector( QlmID( name)),
        path,
        typeAttribute = Some(ParkingCapacity.mvType)
      )
    ) ++ maximum.map{
      max =>
        InfoItem(
          "maximumValue",
          path / "maximumValue",
          typeAttribute = Some( "mv:maximumValue"),
          values = Vector( LongValue(max,currentTimestamp) )
        )
    }.toSeq  ++ current.map{
      cu =>
        InfoItem(
          "currentValue",
          path / "currentValue",
          typeAttribute = Some( "mv:currentValue"),
          values = Vector( LongValue(cu,currentTimestamp) )
        )
    }.toSeq ++ maximumParkingHours.map{
      mph =>
        InfoItem(
          "maximumParkingHours",
          path / "maximumParkingHours",
          typeAttribute = Some( "mv:maximumParkingHours"),
          values = Vector( LongValue(mph,currentTimestamp) )
        )
    }.toSeq ++ validForVehicle.headOption.map{
      _ => 
        InfoItem(
          "validForVehicle",
          path / "validForVehicle",
          typeAttribute = Some( "mv:validForVehicle"),
          values = Vector( 
            Value(
              validForVehicle.map{ vt => s"mv:$vt" }.mkString(","),
              currentTimestamp
            )
          )
        )
    }.toSeq ++ validUserGroup.headOption.map{
      _ => 
        InfoItem(
          "validForUserGroup",
          path / "validForUserGroup",
          typeAttribute = Some( "mv:validForUserGroup"),
          values = Vector( Value(validUserGroup.map{ vt => s"mv:$vt" }.mkString(","), currentTimestamp ) )
        )
    }.toSeq
  }
}

object ParkingCapacity{
  def mvType = "mv:RealTimeCapacity"
  def parseOdf( path: Path, odf: ImmutableODF): Try[ParkingCapacity] ={
    Try{
      odf.get(path) match{
        case Some(obj: Object) if obj.typeAttribute.contains(mvType) || obj.typeAttribute.contains("mv:Capacity")=>
          ParkingCapacity(
            obj.path.last,
            getLongOption("maximumValue",path,odf),
            getLongOption("currentValue",path,odf),
            getStringOption("validForVehicle",path,odf).map{
              vs => 
                vs.split(",").map{
                  vStr =>
                    val vt = VehicleType(vStr.replace("mv:","")) 
                    if( vt == VehicleType.Unknown ) throw MVError(s"validForVehicle should contain only set of following types ${VehicleType.values}")
                    vt
                }
            }.toSeq.flatten,
            getStringOption("validForUserGroup",path,odf).map{
              ugs => 
                ugs.split(",").map{
                  ugStr =>
                    val ug = UserGroup(ugStr.replace("mv:","")) 
                    ug.getOrElse(throw MVError(s"validForUserGroup should contain only set of following types ${UserGroup.values}"))
                }
            }.toSeq.flatten,
            getLongOption("maximumParkingHours",path,odf)
          )
        case Some(obj: Object) => 
          throw MVError( s"ParkingCapacity path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"ParkingCapacity path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"ParkingCapacity path $path not found from given O-DF")
      }
    }
  }
}
