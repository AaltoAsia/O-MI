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
  def props(
    config: Config,
    requestHandler: ActorRef, 
    dbHandler: ActorRef
  ) : Props = Props( new ResponsibleScalaAgent(config, requestHandler, dbHandler) )
}

class ResponsibleScalaAgent(
  config: Config,
  requestHandler: ActorRef, 
  dbHandler: ActorRef
) extends ScalaAgent(config, requestHandler,dbHandler)
  with ResponsibleScalaInternalAgent{
  //Execution context
  import context.dispatcher

  override protected def handleWrite(write: WriteRequest) : Future[ResponseRequest] = {
    //All paths in write.odf is owned by this agent.
    //There is nothing to check or do for data so it is just writen. 

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.info(s"$name pushing data received through AgentSystem.")

    // Asynchronous execution of request 
    val result : Future[ResponseRequest] = writeToDB(write)

    // Asynchronously handle request's execution's completion
    result.onComplete{
      case Success( response: ResponseRequest )=>
        response.results.foreach{ 
          case wr: Results.Success =>
            // This sends debug log message to O-MI Node logs if
            // debug level is enabled (in logback.xml and application.conf)
            log.debug(s"$name wrote paths successfully.")
          case ie: OmiResult => 
            log.warning(s"Something went wrong when $name writed, $ie")
        }
      case Failure( t: Throwable) => 
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.warning(s"$name's write future failed, error: $t")
        Responses.InternalError(t)
    }
    result.recover{
      case t: Throwable => 
      Responses.InternalError(t)
    }
  }

  /**
   * Method that is inherited from akka.actor.Actor and handles incoming messages
   * from other Actors.
   */
  override  def receive : Actor.Receive = {
    //Following are inherited from ResponsibleScalaInternalActor.
    case write: WriteRequest => respondFuture(handleWrite(write))
    //ScalaAgent specific messages
    case Update() => update
  }
}
