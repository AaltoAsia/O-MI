package http

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config
 
class OmiConfigExtension(config: Config) extends Extension {
  val numLatestValues: Int = config.getInt("omi-service.num-latest-values-stored")
  val settingsOdfPath: String = config.getString("omi-service.settings-read-odfpath")
  val port: Int = config.getInt("omi-service.port")
  val externalAgentInterface: String = config.getString("omi-service.external-agent-interface")
  val externalAgentPort: Int = config.getInt("omi-service.external-agent-port")
  val cliPort: Int = config.getInt("omi-service.agent-cli-port")
  val interface: String = config.getString("omi-service.interface")
  val agents = config.getObject("agent-system.agents") 
}



object Settings extends ExtensionId[OmiConfigExtension] with ExtensionIdProvider {
 
  override def lookup = Settings
   
  override def createExtension(system: ExtendedActorSystem) =
    new OmiConfigExtension(system.settings.config)
   
  /**
  * Java API: retrieve the Settings extension for the given system.
  */
  override def get(system: ActorSystem): OmiConfigExtension = super.get(system)
}


