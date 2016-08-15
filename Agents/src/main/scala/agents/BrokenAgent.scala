
package agents
import agentSystem._ 
import types.OmiTypes.WriteRequest
import scala.concurrent.Promise
import com.typesafe.config.Config
import akka.actor.Props
object BrokenAgent extends PropsCreator{
  def props(config: Config) : Props = {
    Props( new BrokenAgent(config) )
  }
}

class BrokenAgent(config: Config)  extends ResponsibleAgent(config){
  override protected def handleWrite(write: WriteRequest) = {
        FailedWrite(write.odf.paths, Vector(new Exception(s"Broken agent, could not write.")))
  }
}
