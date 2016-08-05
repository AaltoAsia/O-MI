
package agents
import agentSystem._ 
import agentSystem.AgentTypes._ 
//import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise
import com.typesafe.config.Config
import akka.actor.Props
object BrokenAgent extends PropsCreator{
  def props(config: Config) : InternalAgentProps = {
    InternalAgentProps( new BrokenAgent(config) )
  }
}

class BrokenAgent(config: Config)  extends ResponsibleAgent(config){
  override protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
        promise.failure(new Exception(s"Broken agent, could not write."))
  }
}
