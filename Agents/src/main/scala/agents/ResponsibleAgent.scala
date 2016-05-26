package agents

import agentSystem._ 
import agentSystem.AgentTypes._ 
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise

class ResponsibleAgent  extends BasicAgent with ResponsibleInternalAgent{
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
    val result = PromiseResult()
    parent ! PromiseWrite( result, write)
    promise.completeWith( result.isSuccessful ) 
  }
}
