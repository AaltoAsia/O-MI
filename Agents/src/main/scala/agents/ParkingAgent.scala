package agents

import scala.util.{Success, Failure}
import scala.concurrent.Future
import scala.concurrent.Future._

import akka.actor.{Cancellable, Props, Actor, ActorRef}
import akka.util.Timeout
import akka.pattern.ask

import agentSystem._ 
import parsing.OdfParser
import com.typesafe.config.Config
import types.OmiTypes._
import types.OdfTypes._
import types._
import types.Path._
import types.Path

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
  def start() ={
    CommandSuccessful()
  }
  def stop() ={
    CommandSuccessful()
  }
  import context.dispatcher
  val findParkingPath = Path("Objects/ParkingService/FindParking")
  val positionParameterPath = Path("Objects/Parameters/Position")
  val arrivalTimeParameterPath = Path("Objects/Parameters/ArrivalTime")
  val parkingLotsPath = Path("Objects/ParkingService/ParkingLots")

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
                          val pp = getfindParkingParams(value.value)
                          findParking( pp)
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
  def getfindParkingParams(objects: OdfObjects):ParkingParameters ={
    val positionParam = objects.get(positionParameterPath)
    val arrivalTimeParma = objects.get(arrivalTimeParameterPath)
    ParkingParameters()
  }

  case class ParkingParameters()
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
        //log.debug( "Result from DB:\n " + response.asXML.toString)
        val modifiedResponse = ResponseRequest(
          response.results.map{
            case result: OmiResult =>
              result.odf match{
                case Some( objects: OdfObjects ) => 
                  result.copy(
                    odf = objects.get(parkingLotsPath).map{
                      case obj: OdfObject => 
                        obj.copy(
                          objects = obj.objects.headOption.toVector
                        ).createAncestors
                    } 
                  )
                case None => result
              }
          }
        )
        log.debug( "Modified result:\n " + modifiedResponse.asXML.toString)
        modifiedResponse
    }.recover{
      case e: Exception => 
        log.error(e, s"findParking caught: ")
        Responses.InternalError(e)
    }
  }

  override protected def handleWrite(write: WriteRequest) : Future[ResponseRequest] = {
    //All paths in write.odf is owned by this agent.
    //There is nothing to check or do for data so it is just writen. 

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.info(s"$name pushing data received through AgentSystem.")

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
  /**
   * Method that is inherited from akka.actor.Actor and handles incoming messages
   * from other Actors.
   */
  override  def receive : Actor.Receive = {
    //Following are inherited from ScalaInternalActor.
    //Must tell/send return value to sender, ask pattern used.
    case Start() => sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
    //Following are inherited from ResponsibleScalaInternalActor.
    case write: WriteRequest => respondFuture(handleWrite(write))
    case read: ReadRequest => respondFuture(handleRead(read))
    case call: CallRequest => respondFuture(handleCall(call))
  }
}
