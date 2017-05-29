package agents

import java.io.File

import scala.util.{Success, Failure}
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future}
import scala.concurrent.Future._
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

  def getStringFromInfoItem( iI: OdfInfoItem): Option[String] ={
        iI.values.headOption.map{ value => value.value.toString} 
  }
  def getDoubleFromInfoItem( iI: OdfInfoItem): Option[Double] ={
            iI.values.headOption.map{
              value => 
                value.value match {
                  case d: Double => d
                  case f: Float => f.toDouble
                  case s: String => s.toDouble
                  case a: Any => a.toString.toDouble
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
  case class GPSCoordinates( latitude: Double, longitude: Double )
  case class ParkingParameters(
    destination: GPSCoordinates,
    spotType: String,
    arrivalTime: Option[String]
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
    for{
      destination <- positionParamO
      spotType <- spotTypeParamO
      
    } yield ParkingParameters(destination, spotType, arrivalTimeParamO)
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

  override protected def handleWrite(write: WriteRequest) : Future[ResponseRequest] = {
    if(write.odf.get(findParkingPath).nonEmpty ){
        Future{
          Responses.InvalidRequest(
            Some("Trying to write to path containing a service method.")
          )
        }
    } else {
      // Asynchronous execution of request 
      val result : Future[ResponseRequest] = writeToDB(write)

      // Asynchronously handle request's execution's completion
      result.onComplete{
        case Success( response: ResponseRequest )=>
          response.results.foreach{ 
            case wr: Results.Success =>
              // This sends debug log message to O-MI Node logs if
              // debug level is enabled (in logback.xml and application.conf)
              log.debug(s"$name wrote paths successfully.")
            case ie: OmiResult => 
              log.warning(s"Something went wrong when $name writed, $ie")
          }
            case Failure( t: Exception) => 
              // This sends debug log message to O-MI Node logs if
              // debug level is enabled (in logback.xml and application.conf)
              log.warning(s"$name's write future failed, error: $t")
              Responses.InternalError(t)
      }
      result.recover{
        case t: Exception => 
          Responses.InternalError(t)
      }
    }
  }
  override protected def handleRead(read: ReadRequest) : Future[ResponseRequest] = {
    log.info(s"$name is handling read:\n$read")

    val result : Future[ResponseRequest] = readFromDB(read)

    // Asynchronously handle request's execution's completion
    result.onComplete{
      case Success( response: ResponseRequest )=>
        response.results.foreach{ 
          case read: Results.Read =>
            // This sends debug log message to O-MI Node logs if
            // debug level is enabled (in logback.xml and application.conf)
            log.debug(s"$name read paths successfully.")
            log.info(s"$read")
          case ie: OmiResult => 
            log.warning(s"Something went wrong when $name read, $ie")
        }
      case Failure( t: Exception) => 
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.warning(s"$name's read future failed, error: $t")
        Responses.InternalError(t)
    }
    result.recover{
      case t: Exception => 
      Responses.InternalError(t)
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
                case o: OdfObject => 
                  log.debug( s"Got ${param.spotType}" )
                  o
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

  /*
    case class ParkingSpot(
      name: String,
      available: Option[String],
      user: Option[String],
    )
    case class ParkingSpotType(
      spotsTotal: Int,
      spotsAvailable: Int,
      hourlyPrice: String,
      maxHeight: String,
      maxWitdh: String
    )
    case class ParkingLot(
      name: String,
      owner: String,
      geo: GPSCoordinates,
      openingTime: String,
      closingTime: String,
      spotTypes: Seq[ParkingSpotType]
    )
    def extractValueFromInfoItem( iio: Option[OdfInfoItem] ): Option[String] = {
      iio.flatMap{ 
        case ii: OdfInfoItem =>
          ii.value.headOption{
            value => value.value.toString
          }
      }

    }
    def createPosition( obj: OdfObject ) : Position = {

      val iiMap = obj.infoItems.map{ ii => ii.path.last -> ii }.toMap

      val long = extractValueFromInfoItem(iiMap.get("Longitude")) 
      val lati = extractValueFromInfoItem(iiMap.get("Latitude"))
      val addr = extractValueFromInfoItem(iiMap.get("Address"))
      if( long.isEmpty ){
        throw new Exception("No longitude found for Position")
      } else if( lati.isEmpty ){
        throw new Exception("No latitude found for Position")
      } else {
        Position( 
          addr,
          long,
          lati
        )
      }
    }

    def createParkingSpot( obj: OdfObject ) : ParkingSpot = {

      val iiMap = obj.infoItems.map{ ii => ii.path.last -> ii }.toMap

      val available = extractValueFromInfoItem(iiMap.get("Available"))
      val user = extractValueFromInfoItem(iiMap.get("User"))
      val spotType = extractValueFromInfoItem(iiMap.get("SpotType"))
      if( long.isEmpty ){
        throw new Exception("No longitude found for Position")
      } else if( lati.isEmpty ){
        throw new Exception("No latitude found for Position")
      } else {
        Position( 
          addr,
          long,
          lati
        )
      }
      */
}


