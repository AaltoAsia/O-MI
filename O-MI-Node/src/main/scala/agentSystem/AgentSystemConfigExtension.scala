package agentSystem

import java.util.concurrent.TimeUnit

import akka.actor.Extension
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._
import types.omi.OmiRequestType._
import types.Path

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


trait AgentSystemConfigExtension extends Extension {
  def config: Config

  val internalAgentsStartTimeout: FiniteDuration = config.getDuration("agent-system.starting-timeout", TimeUnit.SECONDS)
    .seconds
  val agentConfigurations: Seq[AgentConfigEntry] = {

    Try {
      config.getConfigList("agent-system.internal-agents").asScala.toVector
    } match {
      case Success(internalAgents) =>
        internalAgents.flatMap {
          agentConfig: Config =>
            Try {
              AgentConfigEntry(agentConfig)
            } match {
              case Success(ace: AgentConfigEntry) => Some(ace)
              case Failure(e) =>
                //println( e.getMessage)
                None
            }
        }
      case Failure(e: Missing) =>
        //println("Received missing from agent-system.internal-agents configuration")
        Vector.empty
      case Failure(e) => throw e
    }
  }
}

class AgentConfig(
                   val name: AgentName,
                   val className: String,
                   val language: Language,
                   val requestToResponsiblePath: Map[OmiRequestType, Set[Path]],
                   val config: Config
                 )

