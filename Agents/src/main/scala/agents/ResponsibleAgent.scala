package agents

import agentSystem._ 
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise
import scala.util.{Success, Failure, Try}
import com.typesafe.config.Config
import akka.actor.Props
import akka.util.Timeout
import akka.pattern.ask
import types.OmiTypes._

object ResponsibleAgent extends PropsCreator{
  def props(config: Config) : Props = Props( new ResponsibleAgent(config) )
}

class ResponsibleAgent(config: Config )  extends BasicAgent(config) with ResponsibleInternalAgent{
  import context.dispatcher
  protected def handleWrite(write: WriteRequest) = {
    implicit val timeout = Timeout( write.handleTTL)

    val senderRef = sender()
    log.debug(s"Passing write to AS, $senderRef, $agentSystem")
    val result = (agentSystem ? ResponsibilityRequest(name, write)).mapTo[ResponsibleAgentResponse]
    result.onComplete{
      case Success( result ) => senderRef ! result 
      case Failure( t ) => 
        log.info("Failed write")
        senderRef ! FailedWrite(write.odf.paths, Vector(t))
    }

  }

  override def receive  = {
    case Start() => sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
    case Update() => update()
    case write: WriteRequest => handleWrite(write)
  }
}
