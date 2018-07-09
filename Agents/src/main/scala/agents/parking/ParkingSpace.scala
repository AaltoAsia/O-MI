package agents.parking

import scala.util.{Try, Failure, Success}
import types.odf._
import types.Path

import UserGroup._
import VehicleType._

case class ParkingSpace(
                         id: String,
  validForVehicle: Seq[VehicleType],
  validUserGroups: Seq[UserGroup],
  geo: Option[GeoCoordinates],
  maximumParkingHours: Option[Long],
  available: Option[Boolean],
  user: Option[String],
  charger: Option[Charger],
  height: Option[Double],
  length: Option[Double],
  width: Option[Double]
 ) extends Dimensions{
   def update( other: ParkingSpace ): ParkingSpace= {
     require( id == other.id )
     ParkingSpace(
       id,
       if( other.validForVehicle.nonEmpty ) other.validForVehicle else validForVehicle,
       if( other.validUserGroups.nonEmpty ) other.validUserGroups else validUserGroups,
       other.geo.orElse( geo ),
       other.maximumParkingHours.orElse( maximumParkingHours ),
       other.available.orElse( available ),
       other.user.orElse( user ),
       charger.flatMap{
         ch => 
           other.charger.map{
             och =>
              ch.update(och)
           }
       }.orElse(other.charger),
       other.height.orElse( height ),
       other.length.orElse( length ),
       other.width.orElse( width )
     )
   }
  def toOdf(parentPath: Path): Seq[Node] = {
    val path: Path= parentPath / id
    Seq(
      Object( 
        Vector( QlmID( id)),
        path,
        typeAttribute = Some(ParkingSpace.mvType)
      )
    ) ++ maximumParkingHours.map{ mph => 
      val nII = "maximumParkingHours"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( LongValue( mph, currentTimestamp ))
      )
    }.toSeq ++ height.map{ h => 
      val nII = "vehicleHeighLimitInM"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( h, currentTimestamp ))
      )
    }.toSeq ++ width.map{ w => 
      val nII = "vehicleWidthLimitInM"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( w, currentTimestamp ))
      )
    }.toSeq ++ length.map{ l => 
      val nII = "vehicleLengthLimitInM"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( l, currentTimestamp ))
      )
    }.toSeq ++ available.map{ a => 
      val nII = "available"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( BooleanValue( a, currentTimestamp ))
      )
    }.toSeq ++ user.map{ u => 
      val nII = "user"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( StringValue( u, currentTimestamp ))
      )
    }.toSeq ++ 
    geo.map( g => g.toOdf( path )).toSeq.flatten ++ 
    charger.map( c => c.toOdf( path )).toSeq.flatten 
  }
}

object ParkingSpace{
  def mvType = "mv:ParkingSpace"
  def parseOdf( path: Path, odf: ImmutableODF): Try[ParkingSpace] ={
    Try{
      odf.get(path) match{
        case Some(obj: Object) if obj.typeAttribute.contains(mvType) =>
          val geo = odf.get(path / "geo").map{ 
            n: Node => 
            GeoCoordinates.parseOdf( n.path, odf) match{
              case Success(gps:GeoCoordinates) => gps
              case Failure(e) => throw e
            }
          }
          val charger = odf.get(path / "Charger").map{ 
            n: Node => 
            Charger.parseOdf( n.path, odf) match{
              case Success(c: Charger) => c
              case Failure(e) => throw e
            } 
          }
          ParkingSpace(
            obj.path.last,
            getStringOption("validForVehicle",path,odf).map{
              vs => 
                vs.split(",").map{
                  vStr =>
                    val vt = VehicleType(vStr.replace("mv:","")) 
                    if( vt == VehicleType.Unknown ) throw MVError(s"Found $vStr for validForVehicle when it should contain only set of following types ${VehicleType.values}")
                    vt
                }
            }.toSeq.flatten,
            getStringOption("validForUserGroup",path,odf).map{
              ugs => 
                ugs.split(",").map{
                  ugStr =>
                    val ug = UserGroup(ugStr.replace("mv:","")) 
                    ug.getOrElse(throw MVError(s"Found $ugStr for validForUserGroup when it should contain only set of following types ${UserGroup.values}"))
                }
            }.toSeq.flatten,
            geo,
            getLongOption("maximumParkingHours",path,odf),
            getBooleanOption("available",path,odf),
            getStringOption("user",path,odf),
            charger,
            getDoubleOption("vehicleHeightLimit",path,odf),
            getDoubleOption("vehicleLengthLimit",path,odf),
            getDoubleOption("vehicleWidthLimit",path,odf)
          )
        case Some(obj: Object) => 
          throw MVError( s"ParkingSpace path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"ParkingSpace path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"ParkingSpace path $path not found from given O-DF")
      }
    }
  }
}
