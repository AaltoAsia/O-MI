package agents

import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{Cancellable, Props}
import agentSystem._ 
import types.Path
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise
import scala.concurrent.duration._
import java.sql.Timestamp;
import java.util.Date
import scala.util.{Random, Try}
import scala.concurrent.ExecutionContext.Implicits._
import scala.collection.mutable.{Queue => MutableQueue}
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit

object BasicAgent extends PropsCreator {

  def props( config: Config) : Props = { 
    Props(new BasicAgent(config)) 
  }  

}

class BasicAgent( override val config: Config)  extends ScalaInternalAgent{
    log.debug(s"Constructing: $name")

     
   val interval : FiniteDuration= config.getDuration("interval", TimeUnit.SECONDS).seconds
	
   val path : Path = Path(config.getString("path"))

  //Message for updating values
  case class Update()
  
  //Interval for scheluding generation of new values
  //Cancellable update of values, "mutable Option"
  case class UpdateSchedule( var option: Option[Cancellable]  = None)
  private val updateSchedule = UpdateSchedule( None )
  
   def start : InternalAgentResponse  ={
    log.debug(s"Starting: $name")
  
    // Schelude update and save job, for stopping
    // Will send Update message to self every interval
    updateSchedule.option = Some(
      context.system.scheduler.schedule(
        Duration(0, SECONDS),
        interval,
        self,
        Update()
      )
    )
  
    CommandSuccessful()
  }
  
   def stop : InternalAgentResponse = {
    updateSchedule.option match{
      //If agent has scheluded update, cancel job
      case Some(job: Cancellable) =>
      
      job.cancel() 
      
      //Check if job was cancelled
      if(job.isCancelled){
        updateSchedule.option = None
        CommandSuccessful()
      }else throw CommandFailed("Failed to stop agent.")
       
      case None => throw CommandFailed("Failed to stop agent, no job found.")
    }
  }

  //Random number generator for generating new values
   val rnd: Random = new Random()
   def newValueStr = rnd.nextInt().toString 
  
  //Helper function for current timestamps
   def currentTimestamp = new Timestamp(  new java.util.Date().getTime() )
  
  //Update values in paths
   def update() : Unit = {

    val timestamp = currentTimestamp
    val typeStr = "xs:integer"

    //Generate new values and create O-DF
    val infoItem = OdfInfoItem( path, Vector( OdfValue( newValueStr, typeStr, timestamp ) ) )

    //createAncestors generate O-DF structure from a node's path and retuns OdfObjects
    val objects : OdfObjects = createAncestors( infoItem )

    log.debug(s"$name pushing data...")
    //interval as time to live
    implicit val timeout = Timeout(interval)
    val write = WriteRequest( objects, None, interval )

    //Let's tell agentSystem about our write, results will be received and handled througth promiseResult
    val result = (agentSystem ? ResponsibilityRequest(name, write)).mapTo[ResponsibleAgentResponse]

    result.onSuccess{
      case s: SuccessfulWrite =>
      log.debug(s"$name pushed data successfully.")
    }

    result.onFailure{
      case e: Throwable => 
      log.warning(s"$name failed to write all data, error: $e")
    }
  }

  override  def receive  = {
    case Start() => 
      log.debug(s"Received Start: $name")
      sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
    case Update() => update
  }
}
