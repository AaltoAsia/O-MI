package agents

import agentSystem._ 
import agentSystem.AgentTypes._ 
//import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import types._
import types.OdfTypes._
import types.OmiTypes._
import akka.util.Timeout
import akka.actor.Cancellable
import akka.pattern.ask
import java.sql.Timestamp;
import java.util.{Random, Date};
import scala.util.{Success, Failure}
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }
import scala.concurrent._
import scala.concurrent.duration._

class BrokenAgent  extends InternalAgent{
  import scala.concurrent.ExecutionContext.Implicits._
  case class Update()
	protected val rnd: Random = new Random()
  protected val interval : FiniteDuration = Duration(60, SECONDS) 
	protected var pathOwned: Option[Path] = None
	protected var pathPublic: Option[Path] = None
  protected def date = new java.util.Date();
  protected def configure(config: String ) : InternalAgentResponse = {
      pathOwned = Some( new Path(config ++ "Owned"))
      pathPublic = Some( new Path(config ++ "Public"))
      CommandSuccessful("Successfully configured.")
  }
  var updateSchelude : Option[Cancellable] = None
  protected def start = {
    updateSchelude = Some(context.system.scheduler.schedule(
      Duration(0, SECONDS),
      interval,
      self,
      Update
    ))
    CommandSuccessful("Successfully started.")
  }

  protected def update() : Unit = {
    val promiseResult = PromiseResult()
    for{
      ownedPath <- pathOwned
      publicPath <- pathPublic
      ownedItem = fromPath(OdfInfoItem( 
        ownedPath,
        Vector(OdfValue(
          rnd.nextInt().toString, 
          "xs:integer",
          new Timestamp( date.getTime() )
        ))
      ))
      publicItem = fromPath(OdfInfoItem( 
        publicPath,
        Vector(OdfValue(
          rnd.nextInt().toString, 
          "xs:integer",
          new Timestamp( date.getTime() )
        ))
      ))
      objects = ownedItem.union(publicItem)
      write = WriteRequest( interval, objects )
      u = context.parent ! PromiseWrite( promiseResult, write ) 
    } yield write 
    
    promiseResult.isSuccessful.onSuccess{
      //Check if failed promises
      case s =>
      log.debug(s"$name pushed data successfully.")
    }
  }

  protected def receiver = {
    case Update => update
  }
  protected def stop = updateSchelude match{
      case Some(job) =>
      job.cancel() 
      job.isCancelled  match {
      case true =>
        CommandSuccessful("Successfully stopped.")
      case false =>
        CommandFailed("Failed to stop agent.")
    }
    case None => CommandFailed("Failed to stop agent.")
  }
  protected def restart = {
    stop match{
      case success  : InternalAgentSuccess => start
      case error    : InternalAgentFailure => error
    }
  }
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
        promise.failure(new Exception(s"Broken agent, could not write."))
  }
}
