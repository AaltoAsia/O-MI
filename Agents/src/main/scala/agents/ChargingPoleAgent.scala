package agents

import scala.util.{Random, Success, Failure}
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{Cancellable, Props, Actor, ActorRef}
import akka.util.Timeout
import akka.pattern.ask

import java.sql.Timestamp;
import java.util.Date
import java.util.concurrent.TimeUnit
import java.security._

import agentSystem._ 
import com.typesafe.config.Config
import types.OmiTypes._
import types.OdfTypes._
import types.Path
import parsing.xmlGen.odf.QlmID

/**
 * Companion object for ResponsibleScalaAgent. Extends PropsCreator to enforce recommended practice in Props creation.
 *  <a href="http://doc.akka.io/docs/akka/2.4/scala/actors.html#Recommended_Practices">Akka recommends to</a>.
 *
 *  @param _config Contains configuration for this agent, as given in application.conf.
 */
object ChargingPoleAgent extends PropsCreator{
  /**
   * Method for creating Props for ResponsibleScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   */
  def props(config: Config) : Props = Props( new ChargingPoleAgent(config) )
}

class ChargingPoleAgent(override val config: Config )
  extends ResponsibleScalaInternalAgent{
  //Execution context
  import context.dispatcher

  val interval : FiniteDuration = config.getDuration(
    "interval",
    TimeUnit.SECONDS
  ).seconds


  case class Update()

  //Helper function for current timestamps
  def currentTimestamp : Timestamp = new Timestamp(  new java.util.Date().getTime() )

  //Cancellable update of values, "mutable Option"
  case class UpdateSchedule( var option: Option[Cancellable]  = None)
  private val updateSchedule = UpdateSchedule( None )

  /**
   * Method to be called when a Start() message is received.
   */
  def start : InternalAgentResponse  ={

    // Schelude update and save job, for stopping
    // Will send Update() message to self every interval
    updateSchedule.option = Some(
      context.system.scheduler.schedule(
        Duration.Zero,//Delay start
        interval,//Interval between messages
        self,//To
        Update()//Message
      )
    )

    CommandSuccessful()
  }

  /**
   * Method to be called when a Stop() message is received.
   * This should gracefully stop all activities that the agent is doing.
   */
  def stop : InternalAgentResponse = {
    updateSchedule.option match{
      //If agent has scheluded update, cancel job
      case Some(job: Cancellable) =>

        job.cancel() 

        //Check if job was cancelled
        if(job.isCancelled){
          updateSchedule.option = None
          CommandSuccessful()
        } else StopFailed("Failed to stop agent.", None)

      case None => 
        CommandSuccessful()
    }
  }

  //Random number generator for generating new values
  val rnd: Random = new Random()
  // TODO: salt
  def newSalt() : String = rnd.nextInt().toString 

  def update(): Unit = {
    //TODO
  }

  var scheduleID = 0

  def md5Hash(text: String) : String = 
    java.security.MessageDigest.getInstance("MD5").digest(
      text.getBytes()).map(0xFF & _).map {
        "%02x".format(_) }.foldLeft(""){_ + _}

  protected def handleWrite(write: WriteRequest) : Unit = {
    //All paths in write.odf is owned by this agent.
    //There is nothing to check or do for data so it is just writen. 
    log.debug(s"$name registering user in hidden store.")

    write.odf.get(Path("Objects/ChargingPole/Users/register")) match {
      case Some(info: OdfInfoItem) =>
        val userPassStr: String = info.values.head.value.toString // FIXME

        val userPass = userPassStr.split(':') // FIXME
        val user = userPass(0)
        val salt = newSalt() + newSalt()
        val saltpass = salt + userPass(1)
        val pass = salt + ":" + md5Hash(saltpass)

        val opath = Path("Objects/ChargingPole/Users/" + (user.hashCode.toHexString))
        val userid = OdfInfoItem(opath / "UserID", OdfTreeCollection(OdfValue(user, currentTimestamp)))
        val passObj = OdfInfoItem(opath / "Auth" / "PassMD5", OdfTreeCollection(OdfValue(pass, currentTimestamp)))
        val userObjects = userid.createAncestors union passObj.createAncestors

        writeToNode(WriteRequest(userObjects, None, 30.seconds))
        
      case None => //nop
    }
    write.odf.get(Path("Objects/ChargingPole/Reservations/AddReservation")) match {
      case Some(info: OdfInfoItem) =>
        val userReservationStr = info.values.head.value.toString
        val userReservation = userReservationStr.split(':')
        val user = userReservation(0)
        val time = userReservation(1).toInt
        val starts = currentTimestamp.getTime() - time // FIXME?
        val ends = starts + 10

        val opath = Path("Objects/ChargingPole/Reservations/" + (user.hashCode.toHexString))
        val userid = OdfInfoItem(opath / scheduleID.toString / "UserID", OdfTreeCollection(OdfValue(user, currentTimestamp)))
        val startsAfter = OdfInfoItem(opath / scheduleID.toString / "StartsAfter", OdfTreeCollection(OdfValue(starts.toString, currentTimestamp)))
        val endsAfter = OdfInfoItem(opath / scheduleID.toString / "EndsAfter", OdfTreeCollection(OdfValue(starts.toString, currentTimestamp)))
        val userObjects = userid.createAncestors union startsAfter.createAncestors union endsAfter.createAncestors

        scheduleID = (scheduleID +1)%3

        writeToNode(WriteRequest(userObjects, None, 30.seconds))

      case None => //nop
    }

    //val result : Future[ResponseRequest] = writeToNode(write)
    // Store sender for asynchronous handling of request's execution's completion
    val senderRef : ActorRef = sender()

    senderRef ! Responses.Success() 
      //case Failure( t: Throwable) => 
      //  // This sends debug log message to O-MI Node logs if
      //  // debug level is enabled (in logback.xml and application.conf)
      //  log.warning(s"$name's write future failed, error: $t")
      //  senderRef ! Responses.InternalError(t)
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
    case write: WriteRequest => handleWrite(write)
    //ScalaAgent specific messages
    case Update() => update
  }
}
