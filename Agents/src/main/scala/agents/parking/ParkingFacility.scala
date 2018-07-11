package agents.parking

import scala.util.{Try, Failure, Success}
import types.odf._
import types._

class ParkingFacility(
  val name: String,
  val geo: Option[GeoCoordinates] = None,
  val address: Option[PostalAddress] = None,
  val openingHoursSpecifications: Seq[OpeningHoursSpecification] = Vector.empty,
  val capacities: Seq[ParkingCapacity] = Vector.empty,
  val maximumParkingHours: Option[Long] = None,
  val parkingSpaces: Seq[ParkingSpace] = Vector.empty
) extends CivicStructure{
  
  def toOdf(parentPath: Path): Seq[Node] = {
    val path: Path= parentPath / name
    var nodes = Seq(
      Object( 
        Vector( QlmID( name )),
        path,
        typeAttribute = Some(ParkingFacility.mvType)
      )
    ) ++ maximumParkingHours.map{ mph => 
      val nII = "maximumParkingHours"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( LongValue( mph, currentTimestamp ))
      )
    }.toSeq ++ 
    geo.map( g => g.toOdf( path )).toSeq.flatten ++
    address.map( a => a.toOdf( path ) ).toSeq.flatten 
    if( capacities.nonEmpty ){
      nodes = nodes ++ Seq(
        Object( 
          path / "Capacities"
        )
      ) ++ capacities.flatMap( c => c.toOdf( path / "Capacities") )
    }
    if( openingHoursSpecifications.nonEmpty ){
      nodes = nodes ++ Seq(
        Object( 
          path / "OpeningHoursSpecifications"
        )
      ) ++ openingHoursSpecifications.flatMap( ohs => ohs.toOdf( path / "OpeningHoursSpecifications" ) ) 
    }
    if( parkingSpaces.nonEmpty ){
      nodes = nodes ++ Seq(
        Object( 
          path / "ParkingSpaces"
        )
      ) ++ parkingSpaces.flatMap( ps => ps.toOdf( path / "ParkingSpaces" ) )
    }
    nodes
  }
}
object ParkingFacility{
  def mvType = "mv:ParkingFacility"
  def parseOdf( path: Path, odf: ImmutableODF): Try[ParkingFacility] ={
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
          val address = odf.get(path / "address").map{ 
            n: Node => 
            PostalAddress.parseOdf( n.path, odf) match{
              case Success(ad: PostalAddress) => ad
              case Failure(e) => throw e
            }
          }
          val ohps: Seq[OpeningHoursSpecification] = odf.get( path / "OpeningHoursSpecifications").map{
            case obj: Object => 
              val (success, fails ) = odf.getChilds( path / "OpeningHoursSpecifications").map{
                case node: Node => OpeningHoursSpecification.parseOdf(node.path,odf)
              }.partition{ 
                case Success(_) => true
                case Failure(_) => false
              }
              if( fails.nonEmpty ){
                throw ParseError.combineErrors(
                  fails.map{
                    case Failure( pe: ParseError ) => pe
                    case Failure( e ) => throw e
                  }
                )
                
              } else {
                success.collect{
                  case Success( pc: OpeningHoursSpecification) => pc
                }
              }

            case obj: Node => 
              throw MVError( s"Capacities path $path/Capacities should be Object.")
          }.toSeq.flatten
          val capacities: Seq[ParkingCapacity] = odf.get( path / "Capacities").map{
            case obj: Object => 
              val (success,fails ) = odf.getChilds( path / "Capacities").map{
                case node: Node => ParkingCapacity.parseOdf(node.path,odf)
              }.partition{ 
                case Success(_) => true
                case Failure(_) => false
              }
              if( fails.nonEmpty ){
                throw ParseError.combineErrors(
                  fails.map{
                    case Failure( pe: ParseError ) => pe
                    case Failure( e ) => throw e
                  }
                )
                
              } else {
                success.collect{
                  case Success( pc: ParkingCapacity) => pc
                }
              }

            case obj: Node => 
              throw MVError( s"Capacities path $path/Capacities should be Object.")
          }.toSeq.flatten
          val parkingSpaces: Seq[ParkingSpace] = odf.get( path / "ParkingSpaces").map{
            case obj: Object => 
              val (success, fails ) = odf.getChilds( path /  "ParkingSpaces").map{
                case node: Node => ParkingSpace.parseOdf(node.path,odf)
              }.partition{ 
                case Success(_) => true
                case Failure(_) => false
              }
              if( fails.nonEmpty ){
                throw ParseError.combineErrors(
                  fails.map{
                    case Failure( pe: ParseError ) => pe
                    case Failure( e ) => throw e
                  }
                )
                
              } else {
                success.collect{
                  case Success( ps: ParkingSpace) => ps
                }
              }

            case obj: Node => 
              throw MVError( s"ParkingSpace path $path/ParkingSpace should be Object.")
          }.toSeq.flatten
          new ParkingFacility(
            obj.path.last,
            geo,
            address,
            ohps,
            capacities,
            getLongOption("maximumParkingHours",path,odf),
            parkingSpaces
          )
        case Some(obj: Object) => 
          throw MVError( s"ParkingFacility path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"ParkingFacility path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"ParkingFacility path $path not found from given O-DF")
      }
    }
  }
}
