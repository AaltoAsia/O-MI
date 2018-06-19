package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

import VehicleType._
import UserGroup._

object ParkingFacility{
  def apply(obj: OdfObject ): ParkingFacility={
    val name = obj.id.headOption.map{ qlmid => qlmid.value }.getOrElse( throw new Exception( "ParkingFacility/Object without name/id") )
    val owner = obj.get( obj.path / "Owner" ).collect{ case ii: OdfInfoItem => getStringFromInfoItem(ii) }.flatten
    val maxHs = obj.get( obj.path / "maxParkingHours" ).collect{ case ii: OdfInfoItem => getStringFromInfoItem(ii) }.flatten
    val geo = obj.get( obj.path / "geo" ).collect{ case obj: OdfObject => GPSCoordinates(obj) }
    val ohs = obj.get( obj.path / "openingHoursSpecification" ).collect{ case obj: OdfObject => OpeningHoursSpecification(obj) }
    val capas = obj.get( obj.path / "Capacities" ).collect{ case obj: OdfObject => Capacity(obj) }.toVector
    val pSpaces = obj.get( obj.path / "ParkingSpaces" ).collect{ 
      case obj: OdfObject => 
      obj.objects.map{ 
        psObj: OdfObject => 
          ParkingSpace(psObj) 
      } 
    }.toVector.flatten
    ParkingFacility(
      name,
      owner,
      geo,
      pSpaces,
      maxHs,
      ohs,
      capas
    )
  }
}
case class ParkingFacility(
  name: String,
  owner: Option[String],
  geo: Option[GPSCoordinates],
  parkingSpaces: Seq[ParkingSpace],
  maxParkingHours: Option[String],
  openingHours: Option[OpeningHoursSpecification],
  capacities: Vector[Capacity] = Vector.empty
){

   def containsSpacesFor( vehicle: Vehicle ): Boolean ={
     parkingSpaces.exists{
       parkingSpace: ParkingSpace => 
         parkingSpace.validForVehicle.forall{
           vt: VehicleType =>
             vt == vehicle.vehicleType
         }
     }
   }

  /*
   * TODO:
   * Are capacities calculated based on parking spaces in parking facility or
   * are they just calculated based on something else? If calculated other way
   * parking facility would not need information about parking spaces. However
   * then we could need to store capacity some other way.
   *
   * TODO:
   * * Replace InfoItems with Capacities objects.
   * * Dicide naming converntion for capacity objects.
  def calculateCapacities: ParkingFacility = this.copy( capacities =  
    parkingSpaces.groupBy{ 
     case space: ParkingSpace => 
       (space.validForVehicle, space.validForUserGroup)
   }.map{
     case (Tuple2( vehicle, validForUserGroup), parkingSpaceses) => 
       Capacity( // Are these always calculated?
         ???, //TODO: What is name of this actually "vehicles for usergroup"? 
         Some( parkingSpaceses.count( _.available.getOrElse(false) ) ),
         Some( parkingSpaceses.length ),
         vehicle,
         validForUserGroup
       )
   }.toVector
  )
   */
  def toOdf( parentPath: Path, calculatedPaths: Boolean = false ): OdfObject = {
    val facilityPath = parentPath / name
    val iis = owner.map{ o: String =>
        OdfInfoItem(
          facilityPath / "Owner",
          Vector( OdfValue( o, currentTime) ),
          typeValue = Some( "mv:isOwnedBy")
        )}.toVector ++ 
        maxParkingHours.map{ mPH: String =>
        OdfInfoItem(
          facilityPath / "MaxParkingHours",
          Vector( OdfValue( mPH, currentTime) )
        )
        }.toVector
    val spaces = OdfObject(
      Vector( OdfQlmID( "ParkingSpaces" ) ),
      facilityPath / "ParkingSpaces",
      Vector(),
      parkingSpaces.map( _.toOdf(facilityPath / "ParkingSpaces") ).toVector,
      typeValue = Some( "list" )
    )

    val capacitiesObj = OdfObject(
      Vector( OdfQlmID( "Capacities" ) ),
      facilityPath / "Capacitiess",
      Vector(),
      capacities.map( _.toOdf(facilityPath / "Capacities") ).toVector,
      typeValue = Some( "list" )
    )

    OdfObject(
      Vector( OdfQlmID( name ) ),
      facilityPath,
      iis,
      geo.map( _.toOdf(facilityPath, "geo" )).toVector ++ openingHours.map( _.toOdf( facilityPath)).toVector ++ Vector( spaces ),
      typeValue = Some( "schema:ParkingFacility" )
    )
  }
}
/*
case class AutomatedParkingGarage extends ParkingFacility
case class BicycleParkingStation extends ParkingFacility
case class ParkingGarage extends ParkingFacility
case class ParkingLot extends ParkingFacility
case class UndergroundParkingGarage extends ParkingFacility
*/

case class OpeningHoursSpecification( 
  opens: Option[String],
  closes: Option[String],
  dayOfWeek: Option[String]
){
  def toOdf( parentPath: Path ): OdfObject ={
    val ohsPath = parentPath / "openingHoursSpecification"
    OdfObject( 
      Vector( OdfQlmID( "openingHoursSpecification"  ) ),
      ohsPath,
      Vector(
        opens.map{ time: String  => 
        OdfInfoItem(
          ohsPath / "opens",
          Vector( OdfValue( time, "schema:Time", currentTime) )
        )},
        closes.map{ time: String  => 
        OdfInfoItem(
          ohsPath / "closes",
          Vector( OdfValue( time, "schema:Time", currentTime) )
        )},
        dayOfWeek.map{ time: String  => 
        OdfInfoItem(
          ohsPath / "dayOfWeek",
          Vector( OdfValue( time, "schema:DayOfWeek", currentTime) )
        )}
      ).flatten,
      typeValue = Some( "schema:OpeningHoursSpecification" )
    )
  }
}
object OpeningHoursSpecification{

  def apply( obj: OdfObject ): OpeningHoursSpecification ={
    assert(obj.typeValue.contains( "schema:OpeningHoursSpecification"), "Wrong type for OpeningHoursSpecification Object.")
    OpeningHoursSpecification(
      obj.get( obj.path / "opens" ).collect{
        case ii: OdfInfoItem =>
          getStringFromInfoItem(ii)
      }.flatten,
      obj.get( obj.path / "closes" ).collect{
        case ii: OdfInfoItem =>
          getStringFromInfoItem(ii)
      }.flatten,
      obj.get( obj.path / "dayOfWeek" ).collect{
        case ii: OdfInfoItem =>
          getStringFromInfoItem(ii)
      }.flatten
    )
  }
}
case class GPSCoordinates(
  latitude: Option[Double],
  longitude: Option[Double],
  address: Option[PostalAddress] = None
){
  def distanceFrom( other: GPSCoordinates ): Option[Double] ={
    val radius: Double = 6371e3
    latitude.flatMap{
      latitude1: Double  =>
        other.latitude.flatMap{
          latitude2: Double  =>
            longitude.flatMap{
              longitude1: Double  =>
                other.longitude.map{
                  longitude2: Double  =>

                    val a1 = latitude2.toRadians 
                    val a2 = latitude1.toRadians 
                    val deltaLat = (latitude1 - latitude2).toRadians
                    val deltaLon = (longitude1 - longitude2).toRadians 

                    val t = math.sin(deltaLat/2) * math.sin(deltaLat/2) + math.cos(a1) * math.cos(a2) * math.sin(deltaLon/2) * math.sin(deltaLon/2)
                    val c = 2 * math.asin(math.min(math.sqrt(t), 1))
                    val distance = radius * c;
                    distance 
                }
            }
        }
    }
  }

  def toOdf( parentPath: Path, objectName: String ): OdfObject ={
    val geoPath = parentPath / objectName
    OdfObject(
      Vector( OdfQlmID( objectName ) ),
      geoPath,
      Vector( 
        latitude.map{ l: Double => 
          OdfInfoItem( 
            geoPath / "latitude",
            Vector( OdfValue( l, currentTime ) )
          )
        },
        longitude.map{
          l: Double =>
            OdfInfoItem( 
              geoPath / "longitude",
              Vector( OdfValue( l, currentTime )  )
            )
        }
    ).flatten,
  address.map{ a => a.toOdf(geoPath ) }.toVector,
  typeValue = Some( "schema:GeoCoordinates" )
)
  }
}

object GPSCoordinates{

  def apply( obj: OdfObject ): GPSCoordinates ={
    assert(obj.typeValue.contains( "schema:GeoCoordinates"), "Wrong type for GeoCoordinates Object")
    GPSCoordinates(
      obj.get( obj.path / "latitude" ).collect{
        case ii: OdfInfoItem =>
          getDoubleFromInfoItem(ii)
      }.flatten,
      obj.get( obj.path / "longitude" ).collect{
        case ii: OdfInfoItem =>
          getDoubleFromInfoItem(ii)
      }.flatten,
      obj.get( obj.path / "address" ).collect{
        case pAObj: OdfObject => 
          PostalAddress( pAObj )
      }
    )
  }
}
case class PostalAddress(
  country: Option[String],
  locality: Option[String],
  region: Option[String],
  streetAddress: Option[String],
  postCode: Option[String]
) {
  def toOdf( parentPath: Path ) ={
    val addressPath = parentPath / "address"
    val countryII = country.map{ c: String => 
      OdfInfoItem( 
        addressPath / "addressCountry",
        Vector( OdfValue( c, currentTime ))
      )
    }
    val localityII = locality.map{ l:String =>
      OdfInfoItem( 
        addressPath / "addressLocality",
        Vector( OdfValue( l, currentTime ))
      )
    }
    val regionII = region.map{ r: String =>
      OdfInfoItem( 
        addressPath / "addressRegion",
        Vector( OdfValue( r, currentTime ))
      )
    }
    val streetII = streetAddress.map{ street: String => 
      OdfInfoItem( 
        addressPath / "streetAddress",
        Vector( OdfValue( street, currentTime ))
      )
    }
    val postCodeII = postCode.map{ code: String =>
      OdfInfoItem( 
        addressPath / "postCode",
        Vector( OdfValue( code, currentTime ))
      )
    }

    OdfObject(
      Vector( OdfQlmID( "address" ) ),
      addressPath,
      Vector(countryII, localityII, regionII, streetII, postCodeII ).flatten,
      Vector(),
      None,
      typeValue = Some( "schema:PostalAddress" )
    )
  }

}

object PostalAddress{
  def apply( obj: OdfObject ): PostalAddress ={
    PostalAddress(
      obj.get( obj.path / "addressCountry" ).collect{
        case ii: OdfInfoItem => getStringFromInfoItem( ii)
      }.flatten,
      obj.get( obj.path / "addressLocality" ).collect{
        case ii: OdfInfoItem => getStringFromInfoItem( ii)
      }.flatten,
      obj.get( obj.path / "addressRegion" ).collect{
        case ii: OdfInfoItem => getStringFromInfoItem( ii)
      }.flatten,
      obj.get( obj.path / "streetAddress" ).collect{
        case ii: OdfInfoItem => getStringFromInfoItem( ii)
      }.flatten,
      obj.get( obj.path / "postCode" ).collect{
        case ii: OdfInfoItem => getStringFromInfoItem( ii)
      }.flatten

    )
  }

}
