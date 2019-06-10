package agents.parking

import agents.parking.UserGroup._
import agents.parking.VehicleType._
import types.Path
import types.odf._

import scala.util.{Failure, Success, Try}

case class ParkingSpace(
                         id: String,
  validForVehicle: Seq[VehicleType],
  validUserGroups: Seq[UserGroup],
  geo: Option[GeoCoordinates],
  maximumParkingHours: Option[Long],
  available: Option[Boolean],
  isOccupied: Option[Boolean],
  user: Option[String],
  chargers: Seq[Charger],
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
       other.isOccupied.orElse( isOccupied ),
       other.user.orElse( user ),
      other.chargers.groupBy(_.id).mapValues(_.head).foldLeft(chargers.groupBy(_.id).mapValues(_.head)){
        case (current:Map[String,Charger], (id: String, charger: Charger)) =>
          current.get(id) match{
            case Some(currentCharger: Charger) => 
              current ++ Map(id -> currentCharger.update(charger))
            case None =>
              current ++ Map(id -> charger)
          }
      }.values.toSeq,

       other.height.orElse( height ),
       other.length.orElse( length ),
       other.width.orElse( width )
     )
   }
  def toOdf(parentPath: Path, prefixes: Map[String,String]): Seq[Node] = {
    import ParkingSpace.mvType
    val path: Path= parentPath / id
    val  prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      str => if( str.endsWith(":") ) str else str + ":"
    }.getOrElse("")
    Seq(
      Object( 
        Vector( QlmID( id)),
        path,
        typeAttribute = Some(s"${mvType(Some(prefix))}")
      )
    ) ++ maximumParkingHours.map{ mph => 
      val nII = "maximumParkingHours"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( LongValue( mph, currentTimestamp ))
      )
    }.toSeq ++ height.map{ h => 
      val nII = "vehicleHeightLimit"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( DoubleValue( h, currentTimestamp ))
      )
    }.toSeq ++ width.map{ w => 
      val nII = "vehicleWidthLimit"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( DoubleValue( w, currentTimestamp ))
      )
    }.toSeq ++ length.map{ l => 
      val nII = "vehicleLengthLimit"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( DoubleValue( l, currentTimestamp ))
      )
    }.toSeq ++ available.map{ a => 
      val nII = "available"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( BooleanValue( a, currentTimestamp ))
      )
    }.toSeq ++ isOccupied.map{ a => 
      val nII = "isOccupied"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( BooleanValue( a, currentTimestamp ))
      )
    }.toSeq ++ user.map{ u => 
      val nII = "user"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( StringValue( u, currentTimestamp ))
      )
    }.toSeq ++ validForVehicle.headOption.map{ u => 
      val nII = "validForVehicle"
      
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( StringValue( validForVehicle.map(VehicleType.toMvType(_, prefixes)).mkString(","), currentTimestamp ))
      )
    }.toSeq ++ validUserGroups.headOption.map{ u => 
      val nII = "validUserGroup"
      
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix}$nII"),
        values = Vector( StringValue( validUserGroups.map(UserGroup.toMvType(_, prefixes)).mkString(","), currentTimestamp ))
      )
    }.toSeq ++ 
    geo.map( g => g.toOdf( path, prefixes )).toSeq.flatten ++ 
    chargers.flatMap(c => c.toOdf(path, prefixes))
  }
}

object ParkingSpace{
  def mvType(  prefix: Option[String] = None)={
    val preStr = prefix.getOrElse("")
    s"${preStr}ParkingSpace"
  }
  def parseOdf( path: Path, odf: ImmutableODF, prefixes: Map[String,Set[String]]): Try[ParkingSpace] ={
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
          obj.typeAttribute.contains(mvType(Some(prefix)))
        } =>
          val geo = odf.get(path / "geo").map{ 
            n: Node => 
            GeoCoordinates.parseOdf( n.path, odf, prefixes) match{
              case Success(gps:GeoCoordinates) => gps
              case Failure(e) => throw e
            }
          }
          val chargers = odf.getChilds(path).collect{ 
            case obj: Object if prefix.exists{
              prefix: String =>
                obj.typeAttribute.contains(s"${prefix}Charger")
            } =>
            Charger.parseOdf( obj.path, odf, prefixes) match{
              case Success(c: Charger) => c
              case Failure(e) => throw e
            } 
          }
          val available = getBooleanOption("available",path,odf)
          val occupied = getBooleanOption("isOccupied",path,odf)
          val availabilityResult = available.orElse(occupied.map(!_))
          
          ParkingSpace(
            obj.path.last,
            getStringOption("validForVehicle",path,odf).map{
              vs => 
                vs.split(",").map{
                  vStr =>
                    val vt = VehicleType(vStr, prefixes) 
                    if( vt == VehicleType.Unknown ) throw MVError(s"Found $vStr for validForVehicle when it should contain only set of following types ${VehicleType.values}")
                    vt
                }
            }.toSeq.flatten,
            getStringOption("validForUserGroup",path,odf).map{
              ugs => 
                ugs.split(",").map{
                  ugStr =>
                    val ug = UserGroup(ugStr, prefixes) 
                    ug.getOrElse(throw MVError(s"Found $ugStr for validForUserGroup when it should contain only set of following types ${UserGroup.values}"))
                }
            }.toSeq.flatten,
            geo,
            getLongOption("maximumParkingHours",path,odf),
            availabilityResult,         // NOTE: this will add extra infoitem if isOccupied is used
            availabilityResult.map(!_), // NOTE: ^
            getStringOption("user",path,odf),
            chargers.toSeq,
            getDoubleOption("vehicleHeightLimit",path,odf),
            getDoubleOption("vehicleLengthLimit",path,odf),
            getDoubleOption("vehicleWidthLimit",path,odf)
          )
        case Some(obj: Object) => 
          throw MVError( s"ParkingSpace path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"ParkingSpace path $path should be Object with type ${mvType(None)}")
        case None => 
          throw MVError( s"ParkingSpace path $path not found from given O-DF")
      }
    }
  }
}
