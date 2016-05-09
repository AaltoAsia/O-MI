package agents

import agentSystem._ 
//import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import agentSystem.InputPusher
import types._
import types.OdfTypes._
import java.sql.Timestamp;
import java.util.Random;
import java.util.Date;
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.Cancellable
import akka.pattern.ask

class ScalaAgent  extends InternalAgent{
  import scala.concurrent.ExecutionContext.Implicits._
  case class Update()
	val rnd: Random = new Random()
  val interval : FiniteDuration = Duration(5, SECONDS) 
	var pathO: Option[Path] = None
  def date = new java.util.Date();
  def name = self.path.name
  protected def configure(config: String ) : InternalAgentResponse = {
      pathO = Some( new Path(config))
      log.info(s"$name has been configured.");
      CommandSuccessful("Successfully configured.")
  }
  var updateSchelude : Option[Cancellable] = None
  protected def start = {
    log.info(s"$name has been started.");
    updateSchelude = Some(context.system.scheduler.schedule(
      Duration(0, SECONDS),
      interval,
      self,
      Update
    ))
    CommandSuccessful("Successfully started.")
  }

  def update() : Unit = {
    val tuple = pathO.map{
      path => ( 
        path,
        OdfValue(
          rnd.nextInt().toString, 
          "xs:integer",
          new Timestamp( date.getTime() )
        ) ) 
    }
    val values = Iterable(tuple).flatten
    log.info(s"$name pushing data.");
    InputPusher.handlePathValuePairs(values, new Timeout(interval) )

  }     
  def extendedReceive = {
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
}
