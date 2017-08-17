package agents
package parkingService

import java.io.File
import java.sql.Timestamp
import java.util.Date

import scala.util.{Success, Failure, Try}
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future}
import scala.concurrent.Future._
import scala.collection.mutable.{ Map => MutableMap, HashMap => MHashMap}
import scala.xml.PrettyPrinter

import com.typesafe.config.Config

import akka.actor.{Cancellable, Props, Actor, ActorRef}
import akka.util.Timeout
import akka.pattern.ask

import agentSystem._ 
import parsing.OdfParser
import types.OmiTypes._
import types.OdfTypes._
import types._
import types.Path._
import types.Path
import parsing.OdfParser
import scala.xml.XML
import UsageType._

/**
 * Companion object for ResponsibleScalaAgent. Extends PropsCreator to enforce recommended practice in Props creation.
 *  <a href="http://doc.akka.io/docs/akka/2.4/scala/actors.html#Recommended_Practices">Akka recommends to</a>.
 *
 *  @param _config Contains configuration for this agent, as given in application.conf.
 */
object ParkingAgent extends PropsCreator{
  /**
   * Method for creating Props for ResponsibleScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   */
  def props(
    config: Config,
    requestHandler: ActorRef, 
    dbHandler: ActorRef
  ) : Props = Props( new ParkingAgent(config, requestHandler, dbHandler) )
}

class ParkingAgent(
  val config: Config,
  val requestHandler: ActorRef, 
  val dbHandler: ActorRef
) extends ResponsibleScalaInternalAgent{
  //Execution context
  import context.dispatcher

  //Base path for service, contains at least all method
  val servicePath = Path( config.getString("servicePath"))

  //Path to object containing all parking lots.
  val parkingLotsPath = Path( config.getString("parkingFacilitiesPath"))

  //File used to populate node with initial state
  val startStateFile =  new File(config.getString("initialStateFile"))
  val initialODF: OdfObjects = if( startStateFile.exists() && startStateFile.canRead() ){
    val xml = XML.loadFile(startStateFile)
    OdfParser.parse( xml) match {
      case Left( errors : Seq[ParseError]) =>
        val msg = errors.mkString("\n")
        log.warning(s"Odf has errors, $name could not be configured.")
        log.debug(msg)
        throw ParseError.combineErrors( errors )
      case Right(odf) => odf
    }
  } else if( !startStateFile.exists() ){
    throw AgentConfigurationException(s"Could not get initial state for $name. File $startStateFile do not exists.")
  } else {
    throw  AgentConfigurationException(s"Could not get initial state for $name. Could not read file $startStateFile.")
  }

  val pfs = initialODF.get( parkingLotsPath ).collect{
    case obj: OdfObject =>
     val pfs =  obj.objects.filter{
        pfObj: OdfObject =>
          pfObj.typeValue.contains( "mv:ParkingFacility") ||
          pfObj.typeValue.contains( "mv:ParkingLot") ||
          pfObj.typeValue.contains( "mv:ParkingGarage") ||
          pfObj.typeValue.contains( "mv:UndergroundParkingGarage") ||
          pfObj.typeValue.contains( "mv:AutomatedParkingGarage") ||
          pfObj.typeValue.contains( "mv:BicycleParkingStation") 
      }.map{
        pfObj: OdfObject =>
          ParkingFacility( pfObj )
      }
      pfs.toVector
  }.getOrElse( throw new Exception("No parking facilities found in O-DF or configured path is wrong"))
  val odfToWrite = pfs.map{ pf => pf.toOdf(parkingLotsPath).createAncestors }.fold(OdfObjects())( _ union _)
  val initialWrite = writeToDB( WriteRequest(odfToWrite) )
  val initialisationWriteTO = 10.seconds
  val initializationResponse = Await.ready(initialWrite, initialisationWriteTO)
  initializationResponse.value match{
    case None => 
      throw new Exception(s"Could not set initial state for $name. Initial write to DB timedout after $initialisationWriteTO.")
    case Some( Failure( e )) => 
      throw new Exception(s"Could not set initial state for $name. Initial write to DB failed.", e)
    case Some( Success( response )) => 
      response.results.foreach{
        case result: OmiResult =>
          result.returnValue match {
            case succ: Returns.ReturnTypes.Successful =>
              log.info( s"Successfully initialized state for $name" )
            case other =>
              log.warning( s"Could not set initial state for $name. Got result:$result.")
              throw new Exception( s"Could not set initial state for $name. Got following result from DB: $result.")
          }
      }
  }
  val findParkingPath = servicePath / "FindParking"


  override protected def handleCall(call: CallRequest) : Future[ResponseRequest] = {
     val methodInfoItemO = call.odf.get(findParkingPath)
      methodInfoItemO match {
        case None =>    
          Future{
            Responses.InvalidRequest(Some(s"Not found $findParkingPath path for service."))
          }
        case Some(o: OdfObject) =>    
          Future{
            Responses.InvalidRequest(Some(s"Object found in service path: $findParkingPath"))
          }
        case Some(ii: OdfInfoItem) =>
          //log.debug("Service parameters:\n "+ ii.values.mkString("\n"))
          Future.sequence{
            val requests = ii.values.collect{
              case value: OdfObjectsValue =>
                value.typeValue match {
                  case "odf" =>
                  //  val result = OdfParser.parse(value.value)
                  //  val f = result match{
                  //    case Right(odf) =>
                          Try{getfindParkingParams(value.value)} match{
                            case Success( Some(pp:ParkingParameters) ) =>
                              findParking( pp)
                            case Success( None ) =>
                              Future{
                                Responses.InvalidRequest(Some(s"Invalid parameters for find parking: Either Destination or Vehicle missing."))
                              }
                            case Failure(t) =>
                              Future{
                                Responses.InvalidRequest(Some(s"Invalid parameters for find parking: $t"))
                              }
                          }
                  //    case Left( spe: Seq[ParseError] ) =>
                  //      Future{
                   //       Responses.ParseErrors(spe.toVector)
                   //     }
                   // }
                   // f                  
                  case other =>
                    log.debug(s"Unknown type: $other for parameters")
                    Future{
                      ResponseRequest(Vector())
                    }
                }
            }
            log.debug("Parameters total: "+requests.size)
            requests
          }.map{
            case responses: Vector[ResponseRequest] =>
              log.debug(s"Total Responses ${responses.size}")
              val response = if( responses.size == 1 ){
                    //log.debug(s"response:\n" + responses.head.asXML.toString)
                    responses.head
              } else {
                  responses.foldLeft(ResponseRequest(Vector())){
                    case (response, resp) =>
                      //log.debug(s"Responses to union:\n${resp.asXML.toString}")
                      ResponseRequest(response.results ++ resp.results)
                  }
              }
              //log.debug(s"${responses.size} to response:\n" + response.asXML.toString)
              response
          }.recover{
            case e: Exception => 
              log.error(e, "Caught exception: ")
              Responses.InternalError(e)
          }
      }
  }
  case class ParkingParameters(
    destination: GPSCoordinates,
    distanceFromDestination: Double,
    vehicle: Vehicle,
    usageType: Option[UsageType],
    charger: Option[Charger],
    arrivalTime: Option[String]
  )
  val parameterPath =  Path("Objects\\/Parameters")
  val destinationParameterPath     = parameterPath / "Destination"
  val vehicleParameterPath     = parameterPath / "Vehicle"
  val arrivalTimeParameterPath  = parameterPath / "ArrivalTime"
  val usageTypeParameterPath     = parameterPath / "ParkingUsageType"
  val chargerParameterPath     = parameterPath / "Charger"
  def getfindParkingParams(objects: OdfObjects): Option[ParkingParameters] ={
    val destinationO = objects.get(destinationParameterPath).collect{
      case obj: OdfObject =>
        GPSCoordinates(obj)
    }
    val vehicleO = objects.get(vehicleParameterPath ).collect{
      case obj: OdfObject =>
        Vehicle(obj)
    }
    val usageTypeO = objects.get(usageTypeParameterPath).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem(ii).map( UsageType(_))
    }.flatten
    val arrivalTimeO = objects.get(arrivalTimeParameterPath).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem(ii)
    }.flatten
    val chargerO = objects.get(chargerParameterPath ).collect{
      case obj: OdfObject =>
        Charger(obj)
    }
    val distanceFromDestinationO = objects.get(arrivalTimeParameterPath).collect{
      case ii: OdfInfoItem =>
        getDoubleFromInfoItem(ii)
    }.flatten
    for{
      destination <- destinationO
      distanceFromDestination <- distanceFromDestinationO.orElse( Some( 1000.0) )
      vehicle <- vehicleO
    } yield ParkingParameters( destination, distanceFromeDestination, vehicle, usageTypeO, chargerO, arrivalTimeO) 
  }

  def findParking( parameters: ParkingParameters ):Future[ResponseRequest]={
    //TODO: Get current parking facilities
    val parkingFacilities: Vector[ParkingFacility] = Vector()
    val nearbyParkingFacilities = parkingFacilities.filter{
      pf: ParkingFacility =>
        pf.geo.forall{
          case gps: GPSCoordinates => 
            gps.distanceFrom( parameters.destination).forall{ 
              dist: Double => 
                dist <= parameters.distanceFromDestination
            }
        } && pf.containsSpacesFor( parameters.vehicle )
    }
    val parkingFacilitiesWithMatchingSpots = nearbyParkingFacilities.map{
      pf: ParkingFacility =>
        pf.copy(
          parkingSpaces = pf.parkingSpaces.filter{
            pS: ParkingSpace => 
              val vehicleCheck = pS.identedFor.forall{ 
                vT: VehicleType => 
                  vT == parameters.vehicle.vehicleType
              } 
              val usageCheck = parameters.usageType.forall{
                check: UsageType =>
                pS.usageType.forall{
                  uT: UsageType => 
                    uT == check
                }
              }
              val sizeCheck = pS.validForVehicle( parameters.vehicle )
              val chargerCheck = parameters.forall{
                case charger: Charger =>
                  pS.charger.exists{
                    case pSCharger: Charger =>
                      pSHCharger.validFor( charger )
                  }
              }
              vehicleCheck && usageCheck && sizeCheck && chargerCheck
          }
        )
    }

    
    if( parkingFacilitiesWithMatchingSpots.nonEmpty() ){
      Future{
        Responses.Success(
        parkingFacilitiesWithMatchingSpots.fold(OdfObjects()){
          case (odf: OdfObjects, pf: ParkingFacility) =>
            odf union( pf.toOdf(parkingLotsPath).createAncestors )
        
        })
      }
    } else{
      Future{
        Responses.NotFound("Could not find parking facility with matching parking spaces." )
      }
    }
  }
  override protected def handleWrite(write: WriteRequest) : Future[ResponseRequest] = {
    val response = Responses.NotImplemented()
    Future.successful( response )
  }
}


