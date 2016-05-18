package agents

import agentSystem._ 
//import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import types._
import types.OdfTypes._
import types.OmiTypes._
import java.sql.Timestamp;
import java.util.Random;
import java.util.Date;
import scala.util.{Success, Failure}
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.Cancellable
import akka.pattern.ask

class ScalaAgent  extends ResponsibleInternalAgent{
  import scala.concurrent.ExecutionContext.Implicits._
  //Path of owned O-DF path, Option because ugly mutable state
	var pathOwned: Option[Path] = None
  //Path of  O-DF Infoitem t, Option because ugly mutable state
	var pathPublic: Option[Path] = None
  protected def configure(config: String ) : InternalAgentResponse = {
      pathOwned = Some( new Path(config ++ "Owned"))
      pathPublic = Some( new Path(config ++ "Public"))
      CommandSuccessful("Successfully configured.")
  }

  //Message for updating values
  case class Update()
  //Interval for scheluding generation of new values
  val interval : FiniteDuration = Duration(60, SECONDS) 
  //Cancellable update of values, Option because ugly mutable state
  var updateSchelude : Option[Cancellable] = None
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
  
  //Stop agent
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
    case None => CommandFailed("Failed to stop agent, no job wound.")
  }
  //Restart agent, first stop it and then start it
  protected def restart = {
    stop match{
      case success  : InternalAgentSuccess => start
      case error    : InternalAgentFailure => error
    }
  }
  //Quit agent should terminate agent, call context.stop(self) after agent has done some clean up.
  protected def quit = {
    stop match{
      case error    : InternalAgentFailure => error
      case success  : InternalAgentSuccess => 
      context.stop(self) 
      CommandSuccessful("Successfully quit.")
    }
  }

  //Random number generator for generating new values
	val rnd: Random = new Random()
  def newValueStr = rnd.nextInt().toString 
  //Helper function for current timestamps
  def currentTimestamp = new Timestamp(  new java.util.Date().getTime() )
  //Update values in paths
  def update() : Unit = {
    pathOwned.foreach{
      ownedPath => 
      pathPublic.foreach{
        publicPath => //This is run only if both paths are found
        val timestamp = currentTimestamp
        val typeStr = "xs:integer"
        //Generate new values and create O-DF
        val ownedItem = OdfInfoItem(ownedPath,Vector(OdfValue(newValueStr,typeStr,timestamp)))
        val publicItem = OdfInfoItem(publicPath,Vector(OdfValue(newValueStr,typeStr,timestamp)))
        //fromPath generate O-DF structure from node's path and retuns OdfObjects
        val objects = fromPath(ownedItem).union(fromPath(publicItem))
        //Updates interval as time to live
        val write = WriteRequest( interval, objects )
        //PromiseResults contains Promise containing Iterable of Promises and has some helper methods.
        //First level Promise is used for getting answer from AgentSystem and second level Promises are
        //used to get results of actual writes and from agents that owned paths that this agent wanted to write.
        val promiseResult = PromiseResult()
        //Lets fire and forget our write, results will be received and handled hrougth promiseResult
        context.parent ! PromiseWrite( promiseResult, write ) 
        //isSuccessful will return combined result. If a write fails, all other .
        promiseResult.isSuccessful.onSuccess{
          case s: SuccessfulWrite =>
          log.debug(s"$name pushed data successfully.")
        }
      }
    }
  }

  receiver{
    case Update => update
  }
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
    val leafs = getLeafs(write.odf)
      val same = leafs.headOption.exists{ i => pathOwned.contains(i.path) }
        val promiseResult = PromiseResult()
        context.parent ! PromiseWrite( promiseResult, write)
        promise.completeWith( promiseResult.isSuccessful ) 
  }
}
