package agents.parking

import types._
import types.odf._

import scala.util.{Failure, Success, Try}

case class ParkingFacility(
  val name: String,
  val geo: Option[GeoCoordinates] = None,
  val address: Option[PostalAddress] = None,
  val openingHoursSpecifications: Seq[OpeningHoursSpecification] = Vector.empty,
  val capacities: Seq[ParkingCapacity] = Vector.empty,
  val maximumParkingHours: Option[Long] = None,
  val parkingSpaces: Seq[ParkingSpace] = Vector.empty
) {
  
  def toOdf(parentPath: Path, prefixes: Map[String,String]): Seq[Node] = {
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      str => if( str.endsWith(":") ) str else str + ":"
    }
    val path: Path= parentPath / name
    import ParkingFacility.mvType
    var nodes = Seq(
      Object( 
        Vector( QlmID( name )),
        path,
        typeAttribute = Some(s"${mvType(prefix)}")
      )
    ) ++ maximumParkingHours.map{ mph => 
      val nII = "maximumParkingHours"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( LongValue( mph, currentTimestamp ))
      )
    }.toSeq ++ 
    geo.map( g => g.toOdf( path, prefixes )).toSeq.flatten ++
    address.map( a => a.toOdf( path, prefixes ) ).toSeq.flatten 
    if( capacities.nonEmpty ){
      nodes = nodes ++ Seq(
        Object( 
          path / "Capacities"
        )
      ) ++ capacities.flatMap( c => c.toOdf( path / "Capacities", prefixes) )
    }
    if( openingHoursSpecifications.nonEmpty ){
      nodes = nodes ++ Seq(
        Object( 
          path / "OpeningHoursSpecifications"
        )
      ) ++ openingHoursSpecifications.flatMap( ohs => ohs.toOdf( path / "OpeningHoursSpecifications", prefixes ) ) 
    }
    if( parkingSpaces.nonEmpty ){
      nodes = nodes ++ Seq(
        Object( 
          path / "ParkingSpaces",
          Some("list")
        )
      ) ++ parkingSpaces.flatMap( ps => ps.toOdf( path / "ParkingSpaces", prefixes ) )
    }
    nodes
  }
}
object ParkingFacility{
  def mvType(  prefix: Option[String] )={
    val preStr = prefix.getOrElse("")
    s"${preStr}ParkingFacility"
  }
  def parseOdf( path: Path, odf: ImmutableODF, prefixes: Map[String,Set[String]]): Try[ParkingFacility] ={
    
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      prefix: Set[String] => 
        prefix.map{
          str => if( str.endsWith(":") ) str else str + ":"
        }
    }.toSet.flatten
    Try{
      odf.get(path) match{
        case Some(obj: Object) if prefix.exists{
          prefix: String =>
          obj.typeAttribute.contains(mvType(Some(prefix)))
        } =>
          
          val geo = odf.get(path / "Geo").map{ 
            n: Node => 
            GeoCoordinates.parseOdf( n.path, odf, prefixes) match{
              case Success(gps:GeoCoordinates) => gps
              case Failure(e) => throw e
            }
          }
          val address = odf.get(path / "addres").map{ 
            n: Node => 
            PostalAddress.parseOdf( n.path, odf, prefixes) match{
              case Success(ad: PostalAddress) => ad
              case Failure(e) => throw e
            }
          }
          val ohps: Seq[OpeningHoursSpecification] = odf.get( path / "OpeningHoursSpecifications").map{
            case obj: Object => 
              val (success, fails ) = odf.getChilds( path / "OpeningHoursSpecifications").map {
                node: Node => OpeningHoursSpecification.parseOdf(node.path, odf, prefixes)
              }.partition{ 
                case Success(_) => true
                case Failure(_) => false
              }
              if( fails.nonEmpty ){
                throw ParseError.combineErrors(
                  fails.map{
                    case Failure( pe: ParseError ) => pe
                    case Failure( e ) => throw e
                    case _ => throw new IllegalStateException("Failures should not contain succes")
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
              val (success,fails ) = odf.getChilds( path / "Capacities").map {
                node: Node => ParkingCapacity.parseOdf(node.path, odf, prefixes)
              }.partition{ 
                case Success(_) => true
                case Failure(_) => false
              }
              if( fails.nonEmpty ){
                throw ParseError.combineErrors(
                  fails.map{
                    case Failure( pe: ParseError ) => pe
                    case Failure( e ) => throw e
                    case _ => throw new IllegalStateException("Failures should not contain succes")
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
              val (success, fails ) = odf.getChilds( path /  "ParkingSpaces").map {
                node: Node => ParkingSpace.parseOdf(node.path, odf, prefixes)
              }.partition{ 
                case Success(_) => true
                case Failure(_) => false
              }
              if( fails.nonEmpty ){
                throw ParseError.combineErrors(
                  fails.map{
                    case Failure( pe: ParseError ) => pe
                    case Failure( e ) => throw e
                    case _ => throw new IllegalStateException("Failures should not contain succes")
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
          throw MVError( s"ParkingFacility path $path should be Object with type ${mvType(None)}")
        case None => 
          throw MVError( s"ParkingFacility path $path not found from given O-DF")
      }
    }
  }
}
