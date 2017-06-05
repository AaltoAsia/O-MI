package agents

import scala.util.{Random, Success, Failure}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable.{Queue => MutableQueue}

import java.sql.Timestamp;
import java.util.Date
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{Cancellable, Props, Actor, ActorRef}

import com.typesafe.config.Config

import types.Path
import types.OdfTypes._
import types.OmiTypes.{WriteRequest, ResponseRequest, OmiResult,Results}
import agentSystem._ 

/**
 * Companion object for ScalaAgent. Extends PropsCreator to enforce recommended practice in Props creation.
 *  <a href="http://doc.akka.io/docs/akka/2.4/scala/actors.html#Recommended_Practices">Akka recommends to</a>.
 *
 */
object ScalaAgent extends PropsCreator {
  /**
   * Method for creating Props for ScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   * @param requestHandler ActorRef to RequestHandler Actor, that sends request
   * for this agent to handle.
   * @param dbHandler ActorRef to DBHandler Actor that handles all request to DB.
   */
  def props( 
    config: Config,
    requestHandler: ActorRef,
    dbHandler: ActorRef
    ) : Props = { 
    Props(new ScalaAgent(config,requestHandler,dbHandler)) 
  }  

}

/**
 * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 * Extends ScalaInternalAgentTemplate that implements some basic functionality.
 *
 * @param config Contains configuration for this agent, as given in application.conf.
 * @param requestHandler ActorRef to RequestHandler Actor, that sends request
 * for this agent to handle.
 * @param dbHandler ActorRef to DBHandler Actor that handles all request to DB.
 */
class ScalaAgent( 
  val config: Config,
  requestHandler: ActorRef, 
  dbHandler: ActorRef
)  extends ScalaInternalAgentTemplate(requestHandler, dbHandler){

  //Target O-DF path, parsed from configuration
  val path : Path = Path(config.getString("path"))

  //Interval for scheluding generation of new values, parsed from configuration
  val interval : FiniteDuration= config.getDuration(
    "interval",
    TimeUnit.SECONDS
  ).seconds

  //Message for updating values
  case class Update()

  // Schelude update and save job, for stopping
  // Will send Update() message to self every interval
  private val updateSchedule: Cancellable= context.system.scheduler.schedule(
    Duration.Zero,//Delay start
    interval,//Interval between messages
    self,//To
    Update()//Message
  )

  //Random number generator for generating new values
  val rnd: Random = new Random()

  //Helper method for getting current timestamps
  def currentTimestamp : Timestamp = new Timestamp(  new java.util.Date().getTime() )
  def newValueStr : String = rnd.nextInt().toString 

  /**
   * Method to be called when a Update() message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  def update() : Unit = {

    // Generate new OdfValue[Any] 

    // timestamp for the value
    val timestamp : Timestamp = currentTimestamp
    log.info(s"$name updating values at $timestamp")
    // type metadata, default is xs:string
    val typeStr : String= "xs:integer"
    //New value as String
    val valueStr : String = newValueStr

    val odfValue : OdfValue[Any] = OdfValue( newValueStr, typeStr, timestamp ) 

    // Multiple values can be added at the same time but we add one
    val odfValues : Vector[OdfValue[Any]] = Vector( odfValue )

    val metaValueStr : String = newValueStr

    val metaValue : OdfValue[Any] = OdfValue( newValueStr, typeStr, timestamp ) 

    // Multiple values can be added at the same time but we add one
    val metaValues : Vector[OdfValue[Any]] = Vector( odfValue )

    // Create OdfInfoItem to contain the value. 
    val metaInfoItem : OdfInfoItem = OdfInfoItem( path / "MetaData" / "test", metaValues)

    val metaData = OdfMetaData( Vector(metaInfoItem) )

    val description = OdfDescription("test")
    // Create OdfInfoItem to contain the value. 
    val infoItem : OdfInfoItem = OdfInfoItem( path, odfValues, Some(description), Some(metaData))

    // Method createAncestors generates O-DF structure from the path of an OdfNode 
    // and returns the root, OdfObjects
    val objects : OdfObjects =  infoItem.createAncestors

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(s"$name writing data...")

    // Create O-MI write request
    // interval as time to live
    val write : WriteRequest = WriteRequest( objects, None, interval )

    // Execute the request, execution is asynchronous (will not block)
    val result : Future[ResponseRequest] = writeToDB(write) 

    // Asynchronously handle request's execution's completion
    result.onComplete{
      case Success( response: ResponseRequest )=>
        log.info(s"$name wrote got ${response.results.length} results.")
        response.results.foreach{ 
          case wr: Results.Success =>
            // This sends debug log message to O-MI Node logs if
            // debug level is enabled (in logback.xml and application.conf)
            log.info(s"$name wrote paths successfully.")
          case ie: OmiResult => 
            log.warning(s"Something went wrong when $name writed, $ie")
        }
      case Failure( t: Throwable) => 
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.warning(s"$name's write future failed, error: $t")
    }
  }

  /**
   * Method that is inherited from akka.actor.Actor and handles incoming messages
   * from other Actors.
   */
  override  def receive : Actor.Receive = {
    //ScalaAgent specific messages
    case Update() => update
  }

  /**
   * Method to be called when Agent is stopped.
   * This should gracefully stop all activities that the agent is doing.
   */
  override def postStop : Unit = {
    updateSchedule.cancel()
  }
}
