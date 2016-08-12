package agents

import agentSystem.AgentTypes._ 
import agentSystem._ 
import types.Path
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import akka.actor.{Cancellable, Props}
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

  def props( config: Config) : InternalAgentProps = { InternalAgentProps(new BasicAgent(config)) }  

}

class BasicAgent( override val config: Config)  extends InternalAgent{

  protected val interval : FiniteDuration= config.getDuration("interval", TimeUnit.SECONDS).seconds
	
  protected val path : Path = Path(config.getString("path"))

  //Message for updating values
  case class Update()
  
  //Interval for scheluding generation of new values
  //Cancellable update of values, "mutable Option"
  case class UpdateSchedule( var option: Option[Cancellable]  = None)
  private val updateSchedule = UpdateSchedule( None )
  
  protected def start : Try[InternalAgentSuccess ] = Try{
  
    // Schelude update and save job, for stopping
    // Will send Update message to self every interval
    updateSchedule.option = Some(
      context.system.scheduler.schedule(
        Duration(0, SECONDS),
        interval,
        self,
        Update
      )
    )
  
    CommandSuccessful()
  }
  
  protected def stop : Try[InternalAgentSuccess ] = Try{
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
  protected val rnd: Random = new Random()
  protected def newValueStr = rnd.nextInt().toString 
  
  //Helper function for current timestamps
  protected def currentTimestamp = new Timestamp(  new java.util.Date().getTime() )
  
  //Update values in paths
  protected def update() : Unit = {

    val timestamp = currentTimestamp
    val typeStr = "xs:integer"

    //Generate new values and create O-DF
    val infoItem = OdfInfoItem( path, Vector( OdfValue( newValueStr, typeStr, timestamp ) ) )

    //createAncestors generate O-DF structure from a node's path and retuns OdfObjects
    val objects : OdfObjects = createAncestors( infoItem )

    //interval as time to live
    val write = WriteRequest( objects, None, interval )

    //PromiseResults contains Promise containing Iterable of Promises and has some helper methods.
    //First level Promise is used for getting answer from AgentSystem and second level Promises are
    //used to get results of actual writes and from agents that owned paths that this agent wanted to write.
    val result = PromiseResult()

    //Let's tell agentSystem about our write, results will be received and handled througth promiseResult
    agentSystem.tell( PromiseWrite( result, write ), self )

    //isSuccessful will return combined result or first failed write.
    val succ = result.isSuccessful

    succ.onSuccess{
      case s: SuccessfulWrite =>
      log.debug(s"$name pushed data successfully.")
    }

    succ.onFailure{
      case e: Throwable => 
      log.warning(s"$name failed to write all data, error: $e")
    }
  }

  override protected def receiver = {
    case Update => update
  }
}
