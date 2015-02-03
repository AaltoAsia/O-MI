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
  val agentPort: Int = config.getInt("omi-service.agent-input-port")
  val interface: String = config.getString("omi-service.interface")
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


