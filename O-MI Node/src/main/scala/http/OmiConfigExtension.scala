/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package http

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

 
class OmiConfigExtension(config: Config) extends Extension {
  // Node special settings

  /** Save at least this much data per InfoItem */
  val numLatestValues: Int = config.getInt("omi-service.num-latest-values-stored")

  /** Minimum supported interval for interval based subscriptions */
  val minSubscriptionInterval = config.getDuration("omi-service.min-subscription-interval", TimeUnit.SECONDS)

  /** Save some interesting setting values to this path */
  val settingsOdfPath: String = config.getString("omi-service.settings-read-odfpath")
  /** Time in milliseconds how long to keep trying to resend the messages to callback addresses in case of infinite durations*/
  val callbackTimeout: Long = config.getDuration("omi-service.callback-timeout", TimeUnit.MILLISECONDS)

  val trimInterval = config.getDuration("omi-service.trim-interval", TimeUnit.SECONDS)

  val snapshotInterval = config.getDuration("omi-service.snapshot-interval", TimeUnit.SECONDS)
  /** fast journal databases paths */
  val journalsDirectory: String = config.getString("journalDBs.directory")
  val writeToDisk: Boolean = config.getBoolean("journalDBs.write-to-disk")
  // Listen interfaces and ports

  val interface: String = config.getString("omi-service.interface")
  val port: Int = config.getInt("omi-service.port")
  val externalAgentInterface: String = config.getString("omi-service.external-agent-interface")
  val externalAgentPort: Int = config.getInt("omi-service.external-agent-port")
  val cliPort: Int = config.getInt("omi-service.agent-cli-port")


  // Agents

  val internalAgents = config.getObject("agent-system.internal-agents") 

  /**
   * Time in milliseconds how long an actor has to at least run before trying
   * to restart in case of ThreadException */
  val timeoutOnThreadException: Int = config.getDuration("agent-system.timeout-on-threadexception", TimeUnit.MILLISECONDS).toInt


  // Authorization
  
  val inputWhiteListUsers = config.getStringList("omi-service.input-whitelist-users") 

  val inputWhiteListIps = config.getStringList("omi-service.input-whitelist-ips") 
  val inputWhiteListSubnets = config.getStringList("omi-service.input-whitelist-subnets") 

  //val log = LoggerFactory.getLogger(classOf[OmiConfigExtension])
  //log.info(config.root().render())
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


