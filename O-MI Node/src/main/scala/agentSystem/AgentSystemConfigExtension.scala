package agentSystem

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

import akka.actor.Extension
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._
import types.Path
import types.OmiTypes.OmiRequestType._


 
trait AgentSystemConfigExtension  extends Extension {
  def config: Config
  
  val internalAgentsStartTimout : FiniteDuration= config.getDuration("agent-system.starting-timeout", TimeUnit.SECONDS).seconds
  val agentConfigurations: Seq[AgentConfigEntry] = {
    
    Try{
      iterableAsScalaIterable(
        config.getConfigList("agent-system.internal-agents")
      ).toVector 
    } match {
      case Success(internalAgents) =>
        internalAgents.flatMap{ 
          agentConfig : Config =>
            Try{
              AgentConfigEntry(agentConfig)
            }.toOption
        }.toVector
      case Failure(e: Missing) => Vector.empty
      case Failure(e) => throw e
    }
  }
}
//
//agent-system {
//  starting-timeout = 10 seconds
//  internal-agents = [
//    {
//      name = "agent"
//      language = "scala"
//      class = "agents."
//      ??? = {
//        "path" = "rwp"
//        ...
//      }
//    },
//    ...
//  ]
//
//  ]
//}
//
//

class AgentConfig (
  val name: AgentName,
  val className: String,
  val language: Language,
  val requestToResponsiblePath: Map[OmiRequestType,Set[Path]],
  val config: Config
)

