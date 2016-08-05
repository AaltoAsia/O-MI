
package agents

import scala.util.Try
import agentSystem._ 
import agentSystem.AgentTypes._ 
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise
import com.typesafe.config.Config
import akka.actor.Props

object ResponsiblePasserAgent extends PropsCreator{
  def props(config: Config) : InternalAgentProps = InternalAgentProps( new ResponsiblePasserAgent(config) )
}

class ResponsiblePasserAgent(val config: Config )  extends ResponsibleInternalAgent{
  import context.dispatcher
  protected def stop : Try[InternalAgentSuccess ] = Try{
    CommandSuccessful()
  }
  protected def start : Try[InternalAgentSuccess ] = Try{
    CommandSuccessful()
  }
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
    val result = PromiseResult()
    parent ! PromiseWrite( result, write)
    promise.completeWith( result.isSuccessful ) 
  }
}
