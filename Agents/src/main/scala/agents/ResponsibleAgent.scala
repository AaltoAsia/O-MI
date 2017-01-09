package agents

import scala.util.{Success, Failure}
import scala.concurrent.Future

import akka.actor.{Cancellable, Props, Actor, ActorRef}
import akka.util.Timeout
import akka.pattern.ask

import agentSystem._ 
import com.typesafe.config.Config
import types.OmiTypes._

/**
 * Companion object for ResponsibleScalaAgent. Extends PropsCreator to enforce recommended practice in Props creation.
 *  <a href="http://doc.akka.io/docs/akka/2.4/scala/actors.html#Recommended_Practices">Akka recommends to</a>.
 *
 *  @param _config Contains configuration for this agent, as given in application.conf.
 */
object ResponsibleScalaAgent extends PropsCreator{
  /**
   * Method for creating Props for ResponsibleScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   */
  def props(config: Config) : Props = Props( new ResponsibleScalaAgent(config) )
}

class ResponsibleScalaAgent(config: Config )
  extends ScalaAgent(config)
  with ResponsibleScalaInternalAgent{
  //Execution context
  import context.dispatcher

  protected def handleWrite(write: WriteRequest) : Unit = {
    //All paths in write.odf is owned by this agent.
    //There is nothing to check or do for data so it is just writen. 

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(s"$name pushing data received through AgentSystem.")

    // Asynchronous execution of request 
    val result : Future[ResponseRequest] = writeToNode(write)

    // Store sender for asynchronous handling of request's execution's completion
    val senderRef : ActorRef = sender()

    // Asynchronously handle request's execution's completion
    result.onComplete{
      case Success( sw: SuccessfulWrite )=>
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.debug(s"$name wrote all paths successfully.")
        senderRef ! result 
      case Success( fw : FailedWrite ) =>
        log.warning(
          s"$name failed to write to paths:\n" + fw.paths.mkString("\n") +
          " because of following reason:\n" + fw.reasons.mkString("\n")
        )
        senderRef ! result 
      case Success( mw : MixedWrite ) =>
        log.warning(
          s"$name successfully wrote to paths:\n" + mw.successed.mkString("\n") +
          " and failed to write to paths:\n" + mw.failed.paths.mkString("\n") +
          " because of following reason:\n" + mw.failed.reasons.mkString("\n")
        )
        senderRef ! result 
      case Failure( t : Throwable ) => 
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.warning(s"$name failed to write all data received through AgentSystem, error: $t")
        // Send results back to actual sender
        senderRef ! FailedWrite(write.odf.paths, Vector(t))
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
    case write: WriteRequest => handleWrite(write)
    //ScalaAgent specific messages
    case Update() => update
  }
}
