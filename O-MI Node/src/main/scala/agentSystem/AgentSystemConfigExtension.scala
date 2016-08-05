package agentSystem

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

import akka.actor.Extension
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._
import types.Path


 
trait AgentSystemConfigExtension  extends Extension {
  def config: Config
  
  val internalAgentsStartTimout : FiniteDuration= config.getDuration("agent-system.starting-timeout", TimeUnit.SECONDS).seconds
  val agentConfigurations: Array[AgentConfigEntry] = {
    val internalAgents = config.getObject("agent-system.internal-agents") 
    val names : Set[String] = asScalaSet(internalAgents.keySet()).toSet // mutable -> immutable
    names.map{ 
      name =>
      val conf = internalAgents.toConfig()
      val classname : String= conf.getString(s"$name.class")

      val ownedPaths : Seq[Path] = Try{
        conf.getStringList(s"$name.owns").map{ str => Path(str)}
      } match {
        case Success(s) => s
        case Failure(e: Missing) => Seq.empty
        case Failure(e) => throw e
      }
      val config = conf.getObject(s"$name.config").toConfig
      AgentConfigEntry(name, classname.toString, config, ownedPaths) 
    }.toArray
  }
}

