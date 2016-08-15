
package agents

import scala.util.Try
import agentSystem._ 
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise
import com.typesafe.config.Config
import akka.actor.Props

object ResponsiblePasserAgent extends PropsCreator{
  def props(config: Config) : Props = Props( new ResponsiblePasserAgent(config) )
}

class ResponsiblePasserAgent(val config: Config )  extends ResponsibleInternalAgent{
  import context.dispatcher
   def stop : InternalAgentSuccess = {
    CommandSuccessful()
  }
   def start : InternalAgentSuccess = {
    CommandSuccessful()
  }
   def handleWrite( write: WriteRequest) = {
     passWrite(write)
  }
  override def receive  = {
    case Start() => sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
    case write: WriteRequest  =>      handleWrite(write)
  }

}
