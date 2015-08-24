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

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config
 
class OmiConfigExtension(config: Config) extends Extension {
  // Node special settings

  /** Save at least this much data per InfoItem */
  val numLatestValues: Int = config.getInt("omi-service.num-latest-values-stored")

  /** Save some interesting setting values to this path */
  val settingsOdfPath: String = config.getString("omi-service.settings-read-odfpath")


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
  val timeoutOnThreadException: Int = config.getInt("agent-system.timeout-on-threadexception")


  // Authorization
  
  val inputWhiteListUsers = config.getStringList("omi-service.input-whitelist-users") 

  val inputWhiteListIps = config.getStringList("omi-service.input-whitelist-ips") 
  val inputWhiteListSubnets = config.getStringList("omi-service.input-whitelist-subnets") 
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


