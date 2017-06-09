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

  val initialWrite = writeToDB( WriteRequest(initialODF) )
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
  val positionParameterPath     = Path("Objects/Parameters/Destination")
  val arrivalTimeParameterPath  = Path("Objects/Parameters/ArrivalTime")
  val spotTypeParameterPath     = Path("Objects/Parameters/ParkingUsageType")
  val chargerParameterPath     = Path("Objects/Parameters/Charger")
  val pathToSpaces: MutableMap[Path,ParkingSpot] = MHashMap( 
    initialODF.getNodesOfType( "mv:ParkingSpace" ).collect{
      case obj: OdfObject => 
        obj.path -> ParkingSpot( obj )
    }:_* 
  )
  log.debug( pathToSpaces.keys.mkString("\n") )

  val reservations: MutableMap[Path,Reservation] = MHashMap()

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
                          val ppO : Option[ParkingParameters]= getfindParkingParams(value.value)
                          ppO match{
                            case Some( pp ) =>
                              findParking( pp)
                            case None =>
                              Future{
                                Responses.InvalidRequest(Some(s"Invalid parameters for find parking."))
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

  def parseGPSCoordinates( obj: OdfObject ) : Option[GPSCoordinates] ={
        val map = obj.infoItems.map{
          case ii: OdfInfoItem => ii.path.last -> ii
        }.toMap

        val latitudeO: Option[Double] = map.get("latitude").collect{
          case iI: OdfInfoItem => getDoubleFromInfoItem(iI)
        }.flatten 
        val longitudeO: Option[Double] = map.get("longitude").collect{
          case iI: OdfInfoItem => getDoubleFromInfoItem(iI)
        }.flatten
        val gpsO: Option[GPSCoordinates] = for{
          latitude <- latitudeO
          longitude <- longitudeO
        } yield GPSCoordinates( latitude, longitude )
        gpsO
  }
  case class ParkingParameters(
    destination: GPSCoordinates,
    spotType: String,
    arrivalTime: Option[String],
    charger: Option[Charger]
  )
  def getfindParkingParams(objects: OdfObjects): Option[ParkingParameters] ={
      val positionParamO: Option[GPSCoordinates] = objects.get(positionParameterPath).collect{
        case obj: OdfObject => parseGPSCoordinates( obj )
      }.flatten
    val arrivalTimeParamO: Option[String]  = 
      objects.get(arrivalTimeParameterPath).collect{
        case iI: OdfInfoItem => getStringFromInfoItem(iI)
      }.flatten
    val spotTypeParamO: Option[String] =
      objects.get(spotTypeParameterPath ).collect{
        case iI: OdfInfoItem => getStringFromInfoItem(iI)
      }.flatten.map{
        case typeStr: String => if( typeStr.startsWith("mv:") ) typeStr.drop(3) else typeStr
      }
    val chargerO = objects.get( chargerParameterPath ).collect{
      case obj: OdfObject =>
        Charger( obj )
    } 
    for{
      destination <- positionParamO
      spotType <- spotTypeParamO
      
    } yield ParkingParameters(destination, spotType, arrivalTimeParamO, chargerO)
  }

  def findParking( params: ParkingParameters ):Future[ResponseRequest]={
    log.debug("FindParking called")
    val request = ReadRequest(
        OdfObject(
          Vector(QlmID(parkingLotsPath.last)),
          parkingLotsPath
       ).createAncestors
      )
    //log.debug( "Request:\n " + request.toString)
    val results = readFromDB(
      request
    )
    results.map{
      case response: ResponseRequest =>
        log.debug( "Result from DB:" )//+ response.asXML.toString)
        val modifiedResponse = ResponseRequest(
          response.results.map{
            case result: OmiResult =>
              result.odf match{
                case Some( objects: OdfObjects ) => 
                  log.debug( "Result with ODF found:" )
                  result.copy(
                    odf = objects.get(parkingLotsPath).map{
                      case obj: OdfObject => 
                        log.debug( s"found $parkingLotsPath" )
                        val modifiedParkingLots = obj.objects.flatMap{
                              case o: OdfObject => 
                                log.debug( s"found parking lot" )
                                handleParkingLotForCall(o,params)
                            }
                        log.debug( s"Found ${modifiedParkingLots.size} parking lots near the destination" )
                        obj.copy(
                          objects = modifiedParkingLots 
                        ).createAncestors
                    } 
                  )
                case None => result
              }
          }
        )
       // val pp = new PrettyPrinter(100, 4)
        //log.debug( "Modified result:\n " + pp.format(modifiedResponse.asXML.head))
        modifiedResponse
    }.recover{
      case e: Exception => 
        log.error(e, s"findParking caught: ")
        Responses.InternalError(e)
    }
  }
  /*
  def isReservation( path: Path, parkingSpot: ParkingSpot ) ={
    val notReserved = pathToSpaces.get( path ).map{
      case current: ParkingSpot => current.user == Some( "NONE" )
    }.getOrElse(false)

    parkingSpot.available == Some( "false" )  && 
    notReserved
  }
  def isFreeing( path: Path, parkingSpot: ParkingSpot ) ={
    val currentUsing = pathToSpaces.get( path ).map{
      case current: ParkingSpot => current.user == parkingSpot.user
    }.getOrElse(fals  e)

    parkingSpot.available == Some( "true" )  && 
    notReserved
  }
  def openlid( path: Path, parkingSpot: ParkingSpot ) = {
    parkingSpot.charger.map{
      case charger: Charger =>
        charger.lidStatus == Some("Open")
    }.getOrElse( false )
  
  }*/

  

  override protected def handleWrite(write: WriteRequest) : Future[ResponseRequest] = {
    val parkingSpaces =  write.odf.getNodesOfType( "mv:ParkingSpace" )
    if(write.odf.get(findParkingPath).nonEmpty ){
        Future{
          Responses.InvalidRequest(
            Some("Trying to write to path containing a service method.")
          )
        }
    } else if( parkingSpaces.nonEmpty ){
      // Asynchronous execution of request 
      //TODO:Check for reservation writes
      val ( parkingEvents: Seq[Try[ParkingEvent]], failures: Seq[Try[ParkingEvent]] ) = parkingSpaces.collect{
        case obj: OdfObject => 
          Try{
            val ps = ParkingSpot( obj )
            log.debug( s"Got ParkingSpace in ${obj.path}" )
            val currentPS = pathToSpaces.get( obj.path ).getOrElse{ throw  new Exception( "Write to unknow parking space.") }
            if( ps.user.nonEmpty && ps.available.nonEmpty ){ //Reservation or freeing
              log.debug( s"Has user and available. Either reservation or release" )
              if( 
                ps.available == Some( "false")
               ){ //Reservation
                if( currentPS.user != Some( "NONE" ) ){ 
                  log.debug( s"Reservation unavailable spot" )
                  throw  new Exception( "Trying to reserve already reserved parking space.") 
                } else {
                  log.debug( s"Reservation" )
                  val openLid = ps.charger.map{
                    case charger: Charger => 
                      log.debug( s"Lid Status found" )
                      charger.lidStatus == Some( "Open" )
                  }.getOrElse( false)
                  val reserve =Reservation( obj.path, ps.user.get, openLid )
                  reserve
                }
              } else if( 
                ps.available == Some( "true" )
              ){
                if( currentPS.user != ps.user ){
                  log.debug( s"Trying releasa spot that some one else reserved" )
                  throw  new Exception( "Trying to free parking space reserved by someone else.") 
                } else {
                  log.debug( s"Releasing spot" )
                  val openLid = ps.charger.map{
                    case charger: Charger => 
                      log.debug( s"Lid Status found" )
                      charger.lidStatus == Some( "Open" )
                  }.getOrElse( false)
                  FreeReservation( obj.path, ps.user.get, openLid )
                }
              }
            } else if( ps.user.nonEmpty ){
              val openLid = ps.charger.map{
                case charger: Charger => 
                  log.debug( s"Lid Status found" )
                  charger.lidStatus == Some( "Open" )
              }.getOrElse( false)
              if( openLid && ps.user == currentPS.user ) {
                OpenLid( obj.path, ps.user.get )
              } else if( ps.user != currentPS.user ){
                  throw  new Exception( "Trying to open lid of charger of parking space reserved by someone else.") 
              } else {
                  throw  new Exception( "Trying to open lid of charger of parking space reserved by someone else.") 
              }
            } else{
                  throw  new Exception( "Trying to some odd write.") 
            }
          }
      }.partition{
        case Success(event: ParkingEvent ) => true
        case Failure(e ) => false
        case Success(_) => false
      }

      if( failures.collect{
          case a @ Failure( e: Exception ) =>a
        }.nonEmpty ){
         failures.collect{
          case a @ Failure( e: Exception ) =>a
        }.head match {
          case Failure( e: Exception ) =>
            Future{
              Responses.InvalidRequest(Some( e.getMessage) ) 
            }
        }
      } else {
        val events =parkingEvents.map{
          case Success( event: ParkingEvent ) => event
        }
        log.debug(s"Got ${events.length} events")
        val results = events.map{
          case reservation : Reservation=>
            val write = WriteRequest(reservation.toOdf.createAncestors)
            log.debug(write.asXML.toString ) 
            val result = writeToDB( write )
            result.onSuccess{
              case response: ResponseRequest =>
                response.results.foreach{
                  case sc: Results.Success =>
                    reservations += reservation.path -> reservation
                    pathToSpaces.get( reservation.path).map{
                      case parkingSpace : ParkingSpot => 
                        parkingSpace.copy( 
                          user = Some( reservation.user),
                          available = Some( "false" )
                        )
                    }.foreach{
                      case updated: ParkingSpot =>
                        pathToSpaces.update( reservation.path, updated )
                    }
                    if( reservation.openLid ){
                      closeLidIn( reservation.path / "Charger" / "LidStatus" )
                    }
                }
            }
            result
          case freeing : FreeReservation=>
            val write = WriteRequest(freeing.toOdf.createAncestors)
            log.debug(write.asXML.toString ) 
            val result = writeToDB( write )
            result.onSuccess{
              case response: ResponseRequest =>
                response.results.foreach{
                  case sc: Results.Success =>
                    reservations -= freeing.path
                    pathToSpaces.get( freeing.path).map{
                      case parkingSpace : ParkingSpot => 
                        parkingSpace.copy( 
                          user = Some( "NONE" ),
                          available = Some( "true" )
                        )
                    }.foreach{
                      case updated: ParkingSpot =>
                        pathToSpaces.update( freeing.path, updated )
                    }
                    if( freeing.openLid ){
                      closeLidIn( freeing.path / "Charger" / "LidStatus" )
                    }
                }
            }
            result
          case openLid : OpenLid =>
            val write = WriteRequest(openLid.toOdf.createAncestors)
            log.debug(write.asXML.toString ) 
            val result = writeToDB( write )
            result.onSuccess{
              case response: ResponseRequest =>
                response.results.foreach{
                  case sc: Results.Success =>
                    closeLidIn( openLid.lidStatusPath )
                }
            }
            result
        }
        Future.sequence(results).map{
          case responses =>
            ResponseRequest(
              Results.unionReduce(responses.flatMap( _.results ).toVector).toVector
            )
        }
      }
    } else {
        Future{
          Responses.InvalidRequest(
            Some("Trying to write without mv:ParkingSpace typed Object.")
          )
        }
    }
  }

  def positionCheck( destination: GPSCoordinates, lotsPosition: GPSCoordinates ) : Boolean = true 
  def handleParkingLotForCall( obj: OdfObject, param: ParkingParameters ): Option[OdfObject] ={
    val positionO: Option[GPSCoordinates] = obj.get( obj.path / "geo" ).collect{
      case o: OdfObject => parseGPSCoordinates(o )
    }.flatten
    positionO.flatMap{
      case position: GPSCoordinates => 
        log.debug( s"Got location of parking lot" )
        if( positionCheck( param.destination, position) ){
          log.debug( s"Parking lot is near" )
          val newSTs: Option[OdfObject] = obj.get(obj.path / "ParkingSpaceTypes" ).flatMap{
            case spotTypesList : OdfObject =>
              log.debug( s"Got ParkingSpaceTypes. Finding ${param.spotType}..." )
              val rightTypes = spotTypesList.get( spotTypesList.path / param.spotType ).collect{
                case spotTypeObj: OdfObject => 
                  log.debug( s"Got ${param.spotType}" )
                  if( param.spotType == "ElectricVehicleParkingSpace" && param.charger.nonEmpty ){
                   val filteredSpaces = spotTypeObj.get( spotTypesList.path / param.spotType / "Spaces" ).collect{
                      case spacesO: OdfObject => 
                        spacesO.copy(
                          objects = spacesO.objects.filter{//Filter parking spaces with correct charger
                            case obj: OdfObject => 
                              val ps = ParkingSpot( obj )

                              def plugMatches( matchPlugO: Option[PowerPlug], plugO: Option[PowerPlug] ): Boolean= ( matchPlugO, plugO ) match {
                                case (None,None) => true
                                case (Some(matchPlug),None) => false
                                case (None, Some(plug)) => true
                                case (Some(matchPlug),Some(plug)) =>
                                  ( matchPlug.plugType.isEmpty || matchPlug.plugType == plug.plugType ) &&
                                  ( matchPlug.power.isEmpty || matchPlug.power == plug.power ) &&
                                  ( matchPlug.voltage.isEmpty || matchPlug.voltage == plug.voltage ) &&
                                  ( matchPlug.cableAvailable.isEmpty || matchPlug.cableAvailable == plug.cableAvailable ) &&
                                  ( matchPlug.lockerAvailable.isEmpty || matchPlug.lockerAvailable == plug.lockerAvailable ) &&
                                  ( matchPlug.chargingSpeed.isEmpty || matchPlug.chargingSpeed == plug.chargingSpeed ) 
                              }
                              val chargerMatches = ( ps.charger, param.charger ) match {
                                case (None,None) => true
                                case (Some(charger),None) => false
                                case (None, Some(matchCharger)) => true
                                case (Some(charger),Some(matcherCharger)) =>
                                  ( matcherCharger.brand.isEmpty || charger.brand == matcherCharger.brand ) &&
                                  ( matcherCharger.model.isEmpty || charger.model == matcherCharger.model ) &&
                                  plugMatches( matcherCharger.plug, charger.plug)
                              }
                          
                              chargerMatches
                          }
                        )
                    }
                   spotTypeObj.copy(
                      objects = spotTypeObj.objects.filter{
                        case obj: OdfObject => 
                          obj.path.last != "Spaces"
                      } ++ filteredSpaces
                    )
                  } else {
                    spotTypeObj
                  }
              }.toVector
              if( rightTypes.nonEmpty ){
                log.debug( s"Modifying ParkingSpaceTypes to contain only ${param.spotType}" )
                Some(
                  spotTypesList.copy(
                    objects = rightTypes
                  )
                )
              } else None
          }
          newSTs.map{
            case nSTs: OdfObject =>
            log.debug( s"Modifying parking lot to contain new ParkingSpaceTypes" )
            obj.copy(
              objects = obj.objects.filter{
                case o: OdfObject => o.path.last != "ParkingSpaceTypes"
              } ++ Vector(nSTs)
            )
          }
        } else None
    }
  }
                case class CloseLid( pathToLidState: Path )
  def closeLidIn( pathToLidState: Path, delay: FiniteDuration = 2.seconds ) ={
    context.system.scheduler.scheduleOnce( delay, self, CloseLid( pathToLidState) )
  }
  def closeLid( pathToLidState: Path ) ={
   val write = WriteRequest( OdfInfoItem(
     pathToLidState,
     values = Vector( OdfValue( "Locked", currentTime ))
   ).createAncestors)
     
   writeToDB( write)
    
  }
  override  def receive : Actor.Receive = {
    case CloseLid( path ) => closeLid( path)
    //Following are inherited from ResponsibleScalaInternalActor.
    case write: WriteRequest => respondFuture(handleWrite(write))
    case call: CallRequest => respondFuture(handleCall(call))
  }
  /*
  def handleParkingSpaces( odf: OdfObjects ) ={
    def getStringFromPath( path: Path, oo: OdfObject ): Option[String] ={
      oo.get(path).collect{
        case ii: InfoItem => 
          getStringFromInfoItem( ii) 
      }.flatten
    }
    val containedParkingSpacePaths = odf.paths.filter{
      case path: Path => pathToSpaces.contains( path )
    }
    val modifiedObject =containedParkingSpacePaths.map{
      case path: Path =>
        val objOption = odf.get( path )
        val newObjO = objOption.map{
          case obj: OdfObject => 
              val parkingSpace = ParkingSpace( obj )
            val userO = getStringFromPath(obj.path / "User", obj )
            val availableO = getStringFromPath(obj.path / "Available", obj )
            val lidStatusO =getStringFromPath(obj.path / "Charger" / "LidStatus", obj )
            val currentPSO = pathToSpaces.get(obj.path)
            val newObjO: Option[OdfObject] = currentPSO.map{
              case currentPS: ParkingSpot =>
              (userO, currentPS.user ) match{
                case ( None, cUserO ) =>
                  throw new Exception( "User is not given. Can not authorize for empty." )
                case ( Some(user), Some(none) ) if none.equalsIgnoreCase( "none")  =>//Reservation 
                  (availableO,lidStatusO) match {
                    case (Some( newStatus ), None) 
                      if newStatus.equalsIgnoreCase("false") =>
                        obj.update( obj.path / "Available", Vector( OdfValue( "false", currentTime )))
                    case (Some( newStatus ), Some(lidStatus)) 
                      if newStatus.equalsIgnoreCase("false") =>
                      chechLidStatus(lidStatus, obj)
                          .update( obj.path / "Available", Vector( OdfValue( "false", currentTime )))
                    case (Some( newStatus ), Some(lidStatus)) 
                      if newStatus.equalsIgnoreCase("true") =>
                        throw new Exception( "Only reserver may open lid. There is no reservation" )
                    case (Some( newStatus ),None) if newStatus.equalsIgnoreCase("true") =>
                        //Re-release
                        obj
                          .update( obj.path / "Available", Vector( OdfValue( "true", currentTime )))
                          .update( obj.path / "User", Vector( OdfValue( "NONE", currentTime )))
                    case (Some( ns ),_)=>
                      throw new Exception( "Unknown available status. Should be either true or false." )
                  }

                case ( Some(user), Some(currentUser) ) if user != currentUser =>//Release or lidStatus
                   throw new Exception( s"Parking space $path is already reserved." )

                case ( Some(user), Some(currentUser) ) if user == currentUser =>//Release or lidStatus
                  (availableO,lidStatusO) match {
                    case (Some( newStatus ),None)  =>
                        obj
                    case (Some( newStatus ),Some(lidStatus)) 
                      if newStatus.equalsIgnoreCase("false") =>
                      //re reservation.
                      chechLidStatus(lidStatus, obj)
                    case (Some( newStatus), None) 
                      if newStatus.equalsIgnoreCase("true") =>
                        obj
                          .update( obj.path / "User", Vector( OdfValue( "NONE", currentTime) ))
                    case (Some( newStatus ),Some(lidStatus)) 
                      if newStatus.equalsIgnoreCase("true") =>
                      //release
                        chechLidStatus(lidStatus, obj)
                          .update( obj.path / "User", Vector( OdfValue( "NONE", currentTime) ))
                    case (Some( ns ),ls)=>
                      throw new Exception( "Unknown available status. Should be either true or false." )
                  }
              }
            }.flatten
            newObjO
        }.flatten
    }.flatten
    if( modifiedObject.nonEmpty ){
      val objs: OdfObjects = modifiedObject.fold{
        case (l: OdfObject, r: OdfObject ) =>
          l.createAncestors.combine( r.createAncestors )
      }
      val write = WriteRequest(objs)
      writeToDB(write)
    }
  }
  def checkLidStatus( lidStatus: Option[String], obj: OdfObject ): OdfObject={
    if( lidStatus.equalsIgnoreCase("Open") ){
      closeLidIn( obj.path / "Charger" / "LidStatus" )
      obj
        .update( obj.path / "Charger" / "LidStatus" , Vector( OdfValue( "Open", currentTime )))
    } else {
        throw new Exception( "Only open status expected for lid status from outsite." )
    }
  }
  */
}


