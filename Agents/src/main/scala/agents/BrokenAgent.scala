package agents

import agentSystem._ 
//import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import agentSystem.InputPusher
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

class BrokenAgent  extends ResponsibleInternalAgent{
  import scala.concurrent.ExecutionContext.Implicits._
  case class Update()
	val rnd: Random = new Random()
  val interval : FiniteDuration = Duration(60, SECONDS) 
	var pathOwned: Option[Path] = None
	var pathPublic: Option[Path] = None
  def date = new java.util.Date();
  def name = self.path.name
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

  def update() : Unit = {
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

  receiver{
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
  protected def quit = {
    stop match{
      case error    : InternalAgentFailure => error
      case success  : InternalAgentSuccess => 
      sender() ! CommandSuccessful("Successfully quit.")
      context.stop(self) 
      CommandSuccessful("Successfully quit.")
    }
  }
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
        promise.failure(new Exception(s"Broken agent, could not write."))
  }
}
