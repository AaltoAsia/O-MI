package agents

import agentSystem._ 
import agentSystem.AgentTypes._ 
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise
import com.typesafe.config.Config
import akka.actor.Props

object ResponsibleAgent extends PropsCreator{
  def props(config: Config) : InternalAgentProps = InternalAgentProps( new ResponsibleAgent(config) )
}

class ResponsibleAgent(config: Config )  extends BasicAgent(config) with ResponsibleInternalAgent{
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
    val result = PromiseResult()
    parent ! PromiseWrite( result, write)
    promise.completeWith( result.isSuccessful ) 
  }
}
