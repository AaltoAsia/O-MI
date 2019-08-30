package agents.parking

import agents.MVError
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
  def toOdf(parentPath: Path, prefixes: Map[String,String]): Seq[Node] ={
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
        str => if( str.endsWith(":") ) str else str + ":"
    }.getOrElse("")
    val path: Path= parentPath / name
    Seq(
      Object( 
        Vector( OdfID( name)),
        path,
        typeAttribute = Some(s"${prefix}RealTimeCapacity")
      )
    ) ++ maximum.map{
      max =>
        InfoItem(
          "maximumValue",
          path / "maximumValue",
          typeAttribute = Some( s"${prefix}maximumValue"),
          values = Vector( LongValue(max,currentTimestamp) )
        )
    }.toSeq  ++ current.map{
      cu =>
        InfoItem(
          "currentValue",
          path / "currentValue",
          typeAttribute = Some( s"${prefix}currentValue"),
          values = Vector( LongValue(cu,currentTimestamp) )
        )
    }.toSeq ++ maximumParkingHours.map{
      mph =>
        InfoItem(
          "maximumParkingHours",
          path / "maximumParkingHours",
          typeAttribute = Some( s"${prefix}maximumParkingHours"),
          values = Vector( LongValue(mph,currentTimestamp) )
        )
    }.toSeq ++ validForVehicle.headOption.map{
      _ => 
        InfoItem(
          "validForVehicle",
          path / "validForVehicle",
          typeAttribute = Some( s"${prefix}validForVehicle"),
          values = Vector( 
            Value(
              validForVehicle.map{ vt => s"${prefix}$vt" }.mkString(","),
              currentTimestamp
            )
          )
        )
    }.toSeq ++ validUserGroup.headOption.map{
      _ => 
        InfoItem(
          "validForUserGroup",
          path / "validForUserGroup",
          typeAttribute = Some( s"${prefix}validForUserGroup"),
          values = Vector( Value(validUserGroup.map{ vt => s"${prefix}$vt" }.mkString(","), currentTimestamp ) )
        )
    }.toSeq
  }
}

object ParkingCapacity{
  def mvType(  prefix: Option[String] = None)={
    val preStr = prefix.getOrElse("")
    s"${preStr}RealTimeCapacity"
  }
  def parseOdf( path: Path, odf: ImmutableODF, prefixes: Map[String,Set[String]]): Try[ParkingCapacity] ={
    Try{
      val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
        prefix: Set[String] => 
          prefix.map{
            str => if( str.endsWith(":") ) str else str + ":"
          }
      }.toSet.flatten
      odf.get(path) match{
        case Some(obj: Object) if prefix.exists{
          prefix: String =>
          obj.typeAttribute.contains(s"${prefix}RealTimeCapacity") || obj.typeAttribute.contains(s"${prefix}Capacity")
        } =>
          ParkingCapacity(
            obj.path.last,
            getLongOption("maximumValue",path,odf),
            getLongOption("currentValue",path,odf),
            getStringOption("validForVehicle",path,odf).map{
              vs => 
                vs.split(",").map{
                  vStr =>
                    val vt = VehicleType(vStr, prefixes) 
                    if( vt == VehicleType.Unknown ) throw MVError(s"validForVehicle should contain only set of following types ${VehicleType.values}")
                    vt
                }
            }.toSeq.flatten,
            getStringOption("validForUserGroup",path,odf).map{
              ugs => 
                ugs.split(",").map{
                  ugStr =>
                    val ug = UserGroup(ugStr, prefixes) 
                    ug.getOrElse(throw MVError(s"validForUserGroup should contain only set of following types ${UserGroup.values}"))
                }
            }.toSeq.flatten,
            getLongOption("maximumParkingHours",path,odf)
          )
        case Some(obj: Object) => 
          throw MVError( s"ParkingCapacity path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"ParkingCapacity path $path should be Object with type ${mvType()}")
        case None => 
          throw MVError( s"ParkingCapacity path $path not found from given O-DF")
      }
    }
  }
}
