package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

import VehicleType._
import UsageType._

object ParkingFacility{
  def apply(obj: OdfObject ): ParkingFacility={
    val name = obj.id.headOption.map{ qlmid => qlmid.value }.getOrElse( throw new Exception( "ParkingFacility/Object without name/id") )
    val owner = obj.get( obj.path / "Owner" ).collect{ case ii: OdfInfoItem => getStringFromInfoItem(ii) }.flatten
    val maxHs = obj.get( obj.path / "maxParkingHours" ).collect{ case ii: OdfInfoItem => getStringFromInfoItem(ii) }.flatten
    val geo = obj.get( obj.path / "geo" ).collect{ case obj: OdfObject => GPSCoordinates(obj) }
    val ohs = obj.get( obj.path / "openingHoursSpecification" ).collect{ case obj: OdfObject => OpeningHoursSpecification(obj) }
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
      ohs
    )
  }
}
case class ParkingFacility(
  name: String,
  owner: Option[String],
  geo: Option[GPSCoordinates],
  parkingSpaces: Seq[ParkingSpace],
  maxParkingHours: Option[String],
  openingHours: Option[OpeningHoursSpecification]
){
   def numberOfCarsharingParkingSpaces: Int = parkingSpaces.count{ parkingSpot => parkingSpot.usageType.contains(UsageType.Carsharing)}
   def numberOfDisabledPersonParkingSpaces: Int= parkingSpaces.count{ parkingSpot => parkingSpot.usageType.contains(UsageType.DisabledPerson)}
   def numberOfWomensParkingSpaces: Int= parkingSpaces.count{ parkingSpot => parkingSpot.usageType.contains(UsageType.Womens)}

   def numberOfBicycleParkingSpaces: Int = parkingSpaces.count{ parkingSpot => parkingSpot.intendedFor.contains(VehicleType.Bicycle)}
   def numberOfMotorbikeParkingSpaces: Int= parkingSpaces.count{ parkingSpot => parkingSpot.intendedFor.contains(VehicleType.Motorbike)}
   def numberOfTruckParkingSpaces: Int = parkingSpaces.count{ parkingSpot => parkingSpot.intendedFor.contains(VehicleType.Truck)}
   def numberOfCoachParkingSpaces: Int= parkingSpaces.count{ parkingSpot => parkingSpot.intendedFor.contains(VehicleType.Coach)}
   def numberOfCarParkingSpaces: Int= parkingSpaces.count{ parkingSpot => parkingSpot.intendedFor.contains(VehicleType.Car)}
   def numberOfElectricVehicleParkingSpaces: Int= parkingSpaces.count{ parkingSpot => parkingSpot.intendedFor.contains(VehicleType.ElectricVehicle)}

   def numberOfOccupiedParkingSpaces: Int= parkingSpaces.count{ parkingSpace => parkingSpace.available.contains(false)}
   def numberOfVacantParkingSpaces: Int= parkingSpaces.count{ parkingSpace => parkingSpace.available.contains(true)}
   def totalCapacity: Int = parkingSpaces.length
   def containsSpacesFor( vehicle: Vehicle ): Boolean ={
     parkingSpaces.exists{
       parkingSpace: ParkingSpace => 
         parkingSpace.intendedFor.forall{
           vt: VehicleType =>
             vt == vehicle.vehicleType
         }
     }
   }

  def toOdf( parentPath: Path, calculatedPaths: Boolean = false ): OdfObject = {
    val facilityPath = parentPath / name
    val calculatedIIs = if(calculatedPaths) {
      Vector( 
        OdfInfoItem(
          facilityPath / "totalCapacity",
          Vector( OdfValue( totalCapacity, currentTime) ),
          typeValue = Some( "mv:totalCapacity")
        ),
        OdfInfoItem(
          facilityPath / "numberOfVacantParkingSpaces",
          Vector( OdfValue( numberOfVacantParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfVacantParkingSpaces")
        ),
        OdfInfoItem(
          facilityPath / "numberOfOccupiedParkingSpaces",
          Vector( OdfValue( numberOfOccupiedParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfOccupiedParkingSpaces")
        ),
        OdfInfoItem(
          facilityPath / "numberOfParkingSpacesForMotorbikes",
          Vector( OdfValue( numberOfMotorbikeParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfParkingSpacesForMotorbikes")
        ),
        OdfInfoItem(
          facilityPath / "numberOfParkingSpacesForTrucks",
          Vector( OdfValue( numberOfTruckParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfParkingSpacesForTrucks")
        ),
        OdfInfoItem(
          facilityPath / "numberOfParkingSpacesForCars",
          Vector( OdfValue( numberOfCarParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfParkingSpacesForCars")
        ),
        OdfInfoItem(
          facilityPath / "numberOfParkingSpacesForElectricVehicles",
          Vector( OdfValue( numberOfElectricVehicleParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfParkingSpacesForElectricVehicles")
        ),
        OdfInfoItem(
          facilityPath / "numberOfParkingSpacesForCars",
          Vector( OdfValue( numberOfCarParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfParkingSpacesForCars")
        ),
        OdfInfoItem(
          facilityPath / "numberOfParkingSpacesForCoachs",
          Vector( OdfValue( numberOfCoachParkingSpaces, currentTime) ),
          typeValue = Some( "mv:numberOfParkingSpacesForCoachs")
        )
    ) } else Vector()
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

    OdfObject(
      Vector( OdfQlmID( name ) ),
      facilityPath,
      iis ++ calculatedIIs,
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
  closes: Option[String]
){
  def toOdf( parentPath: Path ) ={
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
        )}
      ).flatten,
      typeValue = Some( "schema:OpeningHoursSpecification" )
    )
  }
}
object OpeningHoursSpecification{

  def apply( obj: OdfObject ): OpeningHoursSpecification ={
    assert(obj.typeValue.contains( "schema:OpeningHoursSpecification"))
    OpeningHoursSpecification(
      obj.get( obj.path / "opens" ).collect{
        case ii: OdfInfoItem =>
          getStringFromInfoItem(ii)
      }.flatten,
      obj.get( obj.path / "closes" ).collect{
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

  def toOdf( parentPath: Path, objectName: String ) ={
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
    assert(obj.typeValue.contains( "schema:GeoCoordinates"))
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
