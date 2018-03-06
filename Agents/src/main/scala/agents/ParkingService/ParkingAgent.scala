package agents
package parkingService

import java.io.File
import java.sql.Timestamp
import java.util.Date
import java.net.URLDecoder

import scala.util.{Success, Failure, Try}
import scala.util.control.NonFatal
import scala.concurrent.ExecutionException
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future}
import scala.concurrent.Future._
import scala.collection.mutable.{ Map => MutableMap, HashMap => MutableHashMap}
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
import types.odf.{ OldTypeConverter, NewTypeConverter}
import types.Path._
import types.Path
import parsing.OdfParser
import scala.xml.XML
import UserGroup._
import VehicleType._

/**
 * TODO: Rewrite using ODF type. Note that new types are not as easy to parse to
 * parking types as
 * objects because of linearity of new data structure. *Objects do not know it's
 * childobjects directly, but from set of paths from ODF.
 */

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

  def configStringToPath( name: String ): Path ={
    Path(config.getString(name))
  }
  //Base path for service, contains at least all method
  val servicePath: Path = configStringToPath("servicePath")

  //Path to object containing all parking lots.
  val parkingLotsPath: Path = configStringToPath("parkingFacilitiesPath")

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

  val findParkingPath: Path = servicePath / "FindParking"
  
  case class ParkingSpaceStatus( path: Path, user: Option[String], free: Boolean)
  //TODO: Populate!!!
  val parkingSpaceStatuses: MutableMap[Path,ParkingSpaceStatus] = MutableHashMap()
  val initialWrite: Future[ResponseRequest] = writeToDB( WriteRequest(OldTypeConverter.convertOdfObjects(initialODF)) )

  initialWrite.recover{
    case e: Exception => 
      throw new Exception(s"Could not set initial state for $name. Initial write to DB failed.", e)
    
  }.flatMap{
    case response: ResponseRequest => 
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
      val initialisationStatus: Future[Unit] = updateParkingSpaceStatuses 
      initialisationStatus.recover{
        case e: Exception =>
          log.error(e.getMessage)
          context.stop(self)
      }
  }

  private def updateParkingSpaceStatuses: Future[Unit] ={
    getCurrentParkingFacilities.map{
      case currentPFs: Seq[ParkingFacility] =>
        if( currentPFs.nonEmpty ){
          val entries = currentPFs.flatMap{
            pf: ParkingFacility =>
              pf.parkingSpaces.map{
                space: ParkingSpace =>
                  val path = parkingLotsPath / pf.name / "ParkingSpaces" / space.name
                  path -> ParkingSpaceStatus( path, space.user, space.user.isEmpty ) 
              }
          }
          parkingSpaceStatuses ++= entries
          //log.debug( parkingSpaceStatuses.mkString("\n") ) 
        } else {throw new Exception( "No parking facilities found from db.")}
    }
  }
  override protected def handleCall(call: CallRequest) : Future[ResponseRequest] = {
     val methodInfoItemO = call.odf.get(findParkingPath)
      methodInfoItemO match {
        case None =>    
          log.warning(s"Not found $findParkingPath path for service.")
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
            val odfValues = ii.values.collect{
              case value: OdfObjectsValue =>
                value.typeValue match {
                  case "odf" => Some(value.value)
                  case other: String => 
                    log.debug(s"Unknown type: $other for parameters")
                    None
                }
            }
            log.debug( s"Found ${odfValues.length} O-DFs that should contain parameters for method." )

            val requests = odfValues.map{
              case None =>
                    Future{
                      Responses.InvalidRequest(Some(s"Unknown type for parameter value."))
                    }
              case Some(odf: OdfObjects) =>
                Try{getfindParkingParams(odf)} match{
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
            case e: ExecutionException => 
              log.error("ParkingAgent call request: ", e)
              Responses.InternalError(e.getCause())                                                                                                                                                             
              
            case NonFatal(e) => 
              log.error("ParkingAgent call request: ", e)
              Responses.InternalError(e)

          }
      }
  }
  case class ParkingParameters(
    destination: GPSCoordinates,
    distanceFromDestination: Double,
    vehicle: Vehicle,
    validForUserGroup: Option[UserGroup],
    charger: Option[Charger],
    arrivalTime: Option[String]
  )
  val parameterPath =  Path("Objects/Parameters")
  val destinationParameterPath: Path = parameterPath / "Destination"
  val vehicleParameterPath: Path = parameterPath / "Vehicle"
  val arrivalTimeParameterPath: Path = parameterPath / "ArrivalTime"
  val distanceFromDestinationParameterPath: Path = parameterPath / "DistanceFromDestination"
  val validForUserGroupParameterPath: Path = parameterPath / "ParkingUserGroup"
  val chargerParameterPath: Path = parameterPath / "Charger"
  def getfindParkingParams(objects: OdfObjects): Option[ParkingParameters] ={
    val destinationO = objects.get(destinationParameterPath).collect{
      case obj: OdfObject =>
        GPSCoordinates(obj)
    }
    val vehicleO = objects.get(vehicleParameterPath ).collect{
      case obj: OdfObject =>
        Vehicle(obj)
    }
    val validForUserGroupO = objects.get(validForUserGroupParameterPath).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem(ii).map( UserGroup(_))
    }.flatten
    val arrivalTimeO = objects.get(arrivalTimeParameterPath).collect{
      case ii: OdfInfoItem =>
        getStringFromInfoItem(ii)
    }.flatten
    val chargerO = objects.get(chargerParameterPath ).collect{
      case obj: OdfObject =>
        Charger(obj)
    }
    val distanceFromDestinationO = objects.get(distanceFromDestinationParameterPath).collect{
      case ii: OdfInfoItem =>
        getDoubleFromInfoItem(ii)
    }.flatten
    for{
      destination <- destinationO
      distanceFromDestination <- distanceFromDestinationO.orElse( Some( 1000.0) )
      vehicle <- vehicleO
    } yield ParkingParameters( destination, distanceFromDestination, vehicle, validForUserGroupO, chargerO, arrivalTimeO) 
  }

  def findParking( parameters: ParkingParameters ):Future[ResponseRequest]= getCurrentParkingFacilities.map{
   case parkingFacilities: Vector[ParkingFacility] =>
    val nearbyParkingFacilities = parkingFacilities.filter{
      pf: ParkingFacility =>
        pf.geo.flatMap{
          case gps: GPSCoordinates => 
            gps.distanceFrom( parameters.destination).map{ 
              dist: Double => 
                log.debug( s"$dist < ${parameters.distanceFromDestination}" )
                dist <= parameters.distanceFromDestination
            }
        }.getOrElse(false) //&& pf.containsSpacesFor( parameters.vehicle )
    }
    val parkingFacilitiesWithMatchingSpots: Vector[ParkingFacility]= nearbyParkingFacilities.map{
      pf: ParkingFacility =>
        pf.copy(
          parkingSpaces = pf.parkingSpaces.filter{
            pS: ParkingSpace => 
              val vehicleCheck = pS.validForVehicle.forall{
                vT: VehicleType => 
                  vT == parameters.vehicle.vehicleType
              } 
              val usageCheck = parameters.validForUserGroup.forall{
                check: UserGroup =>
                pS.validForUserGroup.forall{
                  uT: UserGroup => 
                    uT == check
                }
              }
              val sizeCheck = pS.validDimensions( parameters.vehicle )
              val chargerCheck = parameters.charger.forall{
                case charger: Charger =>
                  pS.charger.exists{
                    case pSCharger: Charger =>
                      pSCharger.validFor( charger )
                  }
              }
              vehicleCheck && usageCheck && sizeCheck && chargerCheck
          }
        )
    }

    if( parkingFacilitiesWithMatchingSpots.nonEmpty ){
        val odf:OdfObjects = parkingFacilitiesWithMatchingSpots.foldLeft(OdfObjects()){
          case (odf: OdfObjects, pf: ParkingFacility) =>
            val add: OdfObjects = pf.toOdf(parkingLotsPath).createAncestors 
            odf.union( add)
        
        }
        Responses.Success(Some(OldTypeConverter.convertOdfObjects(odf)), 10 seconds)
    } else{
        Responses.NotFound("Could not find parking facility with matching parking spaces." )
    }
  }
  override protected def handleWrite(write: WriteRequest) : Future[ResponseRequest] = {
    def plugMeasureUpdate( plug: PowerPlug): Boolean =  plug.currentInA.nonEmpty || plug.powerInkW.nonEmpty || plug.voltageInV.nonEmpty
    val currentPFsF = getCurrentParkingFacilities
    val response = currentPFsF.flatMap{
      currentPFs: Vector[ParkingFacility] =>
        val odfToPFs = Future{
          write.odf.get(parkingLotsPath).collect{
            case obj: OdfObject =>
              obj.objects.map{
                pfObj: OdfObject =>
                  ParkingFacility( pfObj)
              }
          }.toVector.flatten
        }
        odfToPFs.flatMap{
          case writingPfs:Vector[ParkingFacility] => 
            val (existingPFs,newPFs) = writingPfs.partition{
              pf: ParkingFacility => 
                currentPFs.exists{
                  existingPF: ParkingFacility => 
                    existingPF.name == pf.name 
                }
            }
            if( newPFs.isEmpty && existingPFs.nonEmpty ){
              if( existingPFs.length == 1 ){
                existingPFs.headOption match{
                  case None => Future{ Responses.InvalidRequest( Some( "Empty head. IMPOSSIBLE"))}
                  case Some(targetPF: ParkingFacility) =>
                    if( targetPF.parkingSpaces.length == 1 ){
                      val event = targetPF.parkingSpaces.headOption.map{
                        case ParkingSpace(name,_,_,Some(false),Some(user),chargerO,_,_,_) =>
                          val path = parkingLotsPath / targetPF.name / "ParkingSpaces" / name
                          if( isParkingSpaceFree(path) ){
                            val openLid: Boolean= chargerO.map{ 
                              case charger: Charger => lidOpen(charger)
                            }.getOrElse(false)
                            Reservation(path, user, openLid)
                          } else throw AllreadyReserved(path)

                        case ParkingSpace(name,_,_,Some(true),Some(user),chargerO,_,_,_) =>
                          val path = parkingLotsPath / targetPF.name / "ParkingSpaces" / name
                          if( isUserCurrentReserver(path, user) ){
                            val openLid: Boolean = chargerO.map{ 
                              case charger: Charger => lidOpen(charger)
                            }.getOrElse(false)
                            FreeReservation(path, user, openLid)
                          } else throw WrongUser(path)

                        case ParkingSpace(name,_,_,_,Some(user),Some(charger),_,_,_) if lidOpen(charger) =>
                          val path = parkingLotsPath / targetPF.name / "ParkingSpaces" / name
                          if( isUserCurrentReserver(path, user) ){
                            OpenLid( path, user)
                          } else throw WrongUser(path)

                          /*
                        case ParkingSpace(name,_,_,_,_,Some(Charger(_,_,_,_,_,_,_,_,_,plugs)),_,_,_) if plugs.exists(plugMeasureUpdate(_)) =>
                          val path = parkingLotsPath / targetPF.name / "ParkingSpaces" / name
                          UpdatePlugMeasurements( path, plug.currentInA, plug.powerInkW, plug.voltageInV) 
                          */
                        case ps: ParkingSpace =>
                          val path = parkingLotsPath / targetPF.name / "ParkingSpaces" / name
                          throw UnknownEvent(path)
                       }
                      if( event.nonEmpty ) handleEvents( event.toVector )
                      else Future{ Responses.InvalidRequest( Some( "Empty Event"))}
                    } else Future{ Responses.InvalidRequest( Some( "Multiple parking spaces for single facility."))}
                }
              } else Future{ Responses.InvalidRequest( Some( "Multiple existing parking facilities."))}
            } else if( newPFs.nonEmpty && existingPFs.isEmpty ){
              log.debug("Adding new parking facility")
              val responseF = writeToDB( WriteRequest( OldTypeConverter.convertOdfObjects(newPFs.map( _.toOdf(parkingLotsPath,true).createAncestors).fold(OdfObjects())( _.union(_) ))) )
              responseF.map{
                case response: ResponseRequest =>
                  val succResult = response.results.collect{
                    case success: Results.Success =>
                      success
                  }
                  if( succResult.nonEmpty ){
                    val entries = newPFs.flatMap{ 
                      pf: ParkingFacility =>
                      pf.parkingSpaces.map{
                        space: ParkingSpace =>
                          val path = parkingLotsPath / pf.name / space.name
                          path -> ParkingSpaceStatus( path, space.user, space.user.isEmpty ) 
                      }
                    }
                    parkingSpaceStatuses ++= entries
                  }

                  response
              }
            } else if(  newPFs.nonEmpty && existingPFs.nonEmpty){
              Future{
                Responses.InvalidRequest( Some("O-DF contains both new and existing Parking Facilities."))
              }
            } else {//if(  newPFs.isEmpty && events.isEmpty){
              Future{
                Responses.InvalidRequest( Some("O-DF does not contain new or existing Parking Facilities."))
              }
            }
      }.recover{
          case e: ExecutionException =>
            log.error("ParkingAgent write request: ", e)
            Responses.InternalError(e.getCause())                                                                                                                                                             
          case NonFatal(e) => 
            log.error("ParkingAgent write request: ", e)
            Responses.InternalError(e)
      
      }
    }
    val res = Try{ Await.ready(response, 10.minutes) }
    response
  }
  
  def handleEvents( events: Seq[ParkingEvent] ): Future[ResponseRequest] ={
    if( events.length == 1 ){
      events.headOption.map{
        case reservation: Reservation => 
          log.debug("Reserving parking space")

          val responseF = writeToDB( WriteRequest( OldTypeConverter.convertOdfObjects(reservation.toOdf.createAncestors ) ) )
            responseF.onSuccess{
              case response: ResponseRequest =>
                if( reservation.openLid ){
                  closeLidIn( reservation.path / "Charger" / "LidStatus" )
                }
                updateCalculatedIIsToDB
                parkingSpaceStatuses.get(reservation.path).foreach{
                  case ParkingSpaceStatus( path, user, available ) =>
                    parkingSpaceStatuses.update( path, ParkingSpaceStatus( path, Some(reservation.user), false ) )
                }
            }
          responseF
        case freeing: FreeReservation => 
          log.debug("Freeing parking space")
          val responseF = writeToDB( WriteRequest( OldTypeConverter.convertOdfObjects(freeing.toOdf.createAncestors) ) )
          responseF.onSuccess{
            case response: ResponseRequest =>
              if( freeing.openLid ){
                closeLidIn( freeing.path / "Charger" / "LidStatus" )
              }
              updateCalculatedIIsToDB
              parkingSpaceStatuses.get(freeing.path).foreach{
                case ParkingSpaceStatus( path, user, available ) =>
                  parkingSpaceStatuses.update( path, ParkingSpaceStatus( path, None, true ) )
              }
          }
          responseF
        case oL: OpenLid =>
          log.debug("Opening lid")
          val responseF = writeToDB( WriteRequest( OldTypeConverter.convertOdfObjects(oL.toOdf.createAncestors) ) )
          responseF.onSuccess{
            case response: ResponseRequest =>
              closeLidIn( oL.path / "Charger" / "LidStatus" )
              updateCalculatedIIsToDB
          }
          responseF
        case upl: UpdatePlugMeasurements  =>
          log.debug("Received update from plug")
          val responseF = writeToDB( WriteRequest( OldTypeConverter.convertOdfObjects(upl.toOdf.createAncestors) ) )
          responseF
      }.getOrElse{
        Future{
          Responses.InvalidRequest(Some("No reservations, freeing or lid opening events found from write."))
        }
      }
    } else if(events.isEmpty) {
      Future{
        Responses.InvalidRequest(Some("No reservations, freeing or lid opening events found from write."))
      }
    } else  {
      Future{
        Responses.InvalidRequest(Some("Multipre reservations, freeing or lid opening events found from write. Should contain only one."))
      }
    }
  }

  def lidOpen( charger: Charger ): Boolean ={
    charger.lidStatus.exists{
      str: String =>
        str.toLowerCase.contains("open")
    }
  }

  def isUserCurrentReserver( path: Path, user: String ): Boolean = parkingSpaceStatuses.get( path).map{
    pSS: ParkingSpaceStatus => 
      log.debug( s" Current user: ${pSS.user}, sender: $user")
      pSS.user.contains( user )
  }.getOrElse( false )

  def isParkingSpaceFree( path: Path): Boolean = {
    parkingSpaceStatuses.get( path).map{
      pSS: ParkingSpaceStatus => 
        log.debug( s"Is free? $pSS")
        pSS.free
    }.getOrElse(false)
    
  }

  case class CloseLid( pathToLidState: Path )
  def closeLidIn( pathToLidState: Path, delay: FiniteDuration = 2.seconds ): Cancellable ={
    context.system.scheduler.scheduleOnce( delay, self, CloseLid( pathToLidState) )
  }
  def closeLid( pathToLidState: Path ): Future[ResponseRequest] ={
   val write = WriteRequest( OldTypeConverter.convertOdfObjects(OdfInfoItem(
     pathToLidState,
     values = Vector( OdfValue( "Locked", currentTime ))
   ).createAncestors))
     
   writeToDB( write)
    
  }
  override  def receive : Actor.Receive = {
    case CloseLid( path ) => closeLid( path)
    //Following are inherited from ResponsibleScalaInternalActor.
    case write: WriteRequest => respondFuture(handleWrite(write))
    case call: CallRequest => respondFuture(handleCall(call))
  }
  def getCurrentParkingFacilities: Future[Vector[ParkingFacility]]={
    val request = ReadRequest(
      OldTypeConverter.convertOdfObjects(
        OdfObject( Vector(OdfQlmID(parkingLotsPath.last)),parkingLotsPath).createAncestors
      )
    )

    val result = readFromDB(request)
    result.map{
      case response: ResponseRequest => 
        log.debug( s"getCurrentParkingFacilities got ${response.results.length}")
        val pfs = response.results.find{
          result : OmiResult =>
            result.returnValue.returnCode == ReturnCode.Success  && result.odf.nonEmpty
        }.flatMap{
          result : OmiResult => result.odf.map( NewTypeConverter.convertODF(_) )
        }.flatMap{
          odf: OdfObjects =>
            odf.get( parkingLotsPath ).map{
              case pfsObj: OdfObject =>
                pfsObj.objects.map( ParkingFacility( _))
            }
        }.toVector.flatten
        log.debug( s"Found current ${pfs.length} parking facilities")
        pfs
    }
  }
  def updateCalculatedIIsToDB: Future[ResponseRequest] ={
    getCurrentParkingFacilities.flatMap{
      parkingFacilities: Vector[ParkingFacility] =>
        val newPFs= parkingFacilities.map{
          pf: ParkingFacility =>
            val npf = ParkingFacility( 
              pf.name, 
              None,
              None,
              pf.parkingSpaces.map{
                ps: ParkingSpace =>
                  ParkingSpace( 
                    ps.name,
                    ps.validForUserGroup,
                    ps.validForVehicle,
                    ps.available,
                    None,
                    None,
                    None,
                    None,
                    None
                  )
              },
              None,
              None
            )
            npf.toOdf(parkingLotsPath,true).createAncestors
        }
        val writeOdf = newPFs.fold(OdfObjects()){
          case ( odf: OdfObjects, l: OdfObjects) => odf.union(l)
        }
        val request = WriteRequest( OldTypeConverter.convertOdfObjects(writeOdf) )
        writeToDB( request )
    }
  }
}


