package agents

import agentSystem.AgentTypes._ 
import agentSystem._ 
import types._
import types.OdfTypes._
import types.OmiTypes._
import akka.util.Timeout
import akka.actor.{Cancellable, Props}
import akka.pattern.ask
import scala.util.{Success, Failure}
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }
import scala.concurrent._
import scala.concurrent.duration._
import java.sql.Timestamp;
import java.util.Random;
import java.util.Date;
import scala.concurrent.ExecutionContext.Implicits._
import com.typesafe.config.Config

object BasicAgent extends PropsCreator {
  def props( config: Config ) : Props = { Props(new BasicAgent) }  

}
class BasicAgent  extends InternalAgent{
  //Path of owned O-DF InfoItem, Option because ugly mutable state
	protected var pathO: Option[Path] = None
  protected def configure(config: String ) : InternalAgentResponse = {
      pathO  = Some( Path(config) )
      CommandSuccessful("Successfully configured.")
  }

  //Message for updating values
  case class Update()
  //Interval for scheluding generation of new values
  protected val interval : FiniteDuration = Duration(60, SECONDS) 
  //Cancellable update of values, Option because ugly mutable state
  protected var updateSchelude : Option[Cancellable] = None
  protected def start = {
    // Schelude update and save job, for stopping
    // Will send Update message to self every interval
    updateSchelude = Some(context.system.scheduler.schedule(
      Duration(0, SECONDS),
      interval,
      self,
      Update
    ))
    CommandSuccessful("Successfully started.")
  }
  
  protected def stop = updateSchelude match{
      //If agent has scheluded update, cancel job
      case Some(job) =>
      job.cancel() 
      //Check if job was cancelled
      job.isCancelled  match {
      case true =>
        CommandSuccessful("Successfully stopped.")
      case false =>
        CommandFailed("Failed to stop agent.")
    }
    case None => CommandFailed("Failed to stop agent, no job found.")
  }
  //Restart agent, first stop it and then start it
  protected def restart = {
    stop match{
      case success  : InternalAgentSuccess => start
      case error    : InternalAgentFailure => error
    }
  }
  
  //Random number generator for generating new values
  protected val rnd: Random = new Random()
  protected def newValueStr = rnd.nextInt().toString 
  //Helper function for current timestamps
  protected def currentTimestamp = new Timestamp(  new java.util.Date().getTime() )
  //Update values in paths
  protected def update() : Unit = {
    pathO.foreach{ //Only run if some path found 
      path => 
      val timestamp = currentTimestamp
      val typeStr = "xs:integer"
      //Generate new values and create O-DF
      val infoItem = OdfInfoItem(path,Vector(OdfValue(newValueStr,typeStr,timestamp)))
      //fromPath generate O-DF structure from a ode's path and retuns OdfObjects
      val objects : OdfObjects = fromPath(infoItem)
      //Updates interval as time to live
      val write = WriteRequest( interval, objects )
      //PromiseResults contains Promise containing Iterable of Promises and has some helper methods.
      //First level Promise is used for getting answer from AgentSystem and second level Promises are
      //used to get results of actual writes and from agents that owned paths that this agent wanted to write.
      val result = PromiseResult()
      //Lets fire and forget our write, results will be received and handled hrougth promiseResult
      parent ! PromiseWrite( result, write )
      //isSuccessful will return combined result or first failed write.
      val succ = result.isSuccessful
      succ.onSuccess{
        case s: SuccessfulWrite =>
        log.debug(s"$name pushed data successfully.")
      }
      succ.onFailure{
        case e => 
        log.warning(s"$name failed to write all data, error: $e")
      }
    }
  }

  protected def receiver = {
    case Update => update
  }
}
