/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package http

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

import agentSystem.AgentSystemConfigExtension
import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._
import database.Warp10ConfigExtension
import types.Path



 
class OmiConfigExtension( val config: Config) extends Extension 
  with AgentSystemConfigExtension
  with Warp10ConfigExtension {
  // Node special settings

  val ports : Map[String, Int]= config.getObject("omi-service.ports").unwrapped().mapValues{
    case port : java.lang.Integer => port.toInt
    case port : java.lang.Object => 
    throw new Exception("Configs omi-service.ports contain non integer values") 
  }.toMap
  val webclientPort: Int = config.getInt("omi-service.ports.webclient")
  val externalAgentPort: Int = ports("external-agents")
  val cliPort: Int = config.getInt("omi-service.ports.cli")
  /** Save at least this much data per InfoItem */
  val numLatestValues: Int = config.getInt("omi-service.num-latest-values-stored")

  /** Minimum supported interval for interval based subscriptions */
  val minSubscriptionInterval : FiniteDuration= config.getDuration("omi-service.min-subscription-interval", TimeUnit.SECONDS).seconds

  /** Save some interesting setting values to this path */
  val settingsOdfPath: String = config.getString("omi-service.settings-read-odfpath")
  /** Time in milliseconds how long to keep trying to resend the messages to callback addresses in case of infinite durations*/
  val callbackTimeout : FiniteDuration = config.getDuration("omi-service.callback-timeout", TimeUnit.MILLISECONDS).milliseconds

  val trimInterval : FiniteDuration = config.getDuration("omi-service.trim-interval", TimeUnit.SECONDS).seconds

  val snapshotInterval: FiniteDuration  = config.getDuration("omi-service.snapshot-interval", TimeUnit.SECONDS).seconds
  /** fast journal databases paths */
  val journalsDirectory: String = config.getString("journalDBs.directory")
  val writeToDisk: Boolean = config.getBoolean("journalDBs.write-to-disk")
  // Listen interfaces and ports

  val interface: String = config.getString("omi-service.interface")
  //val port: Int = config.getInt("omi-service.port")
  val externalAgentInterface: String = config.getString("omi-service.external-agent-interface")
  //val externalAgentPort: Int = config.getInt("omi-service.external-agent-port")
  //val cliPort: Int = config.getInt("omi-service.agent-cli-port")



  // Authorization
  
  val inputWhiteListUsers = config.getStringList("omi-service.input-whitelist-users") 

  val inputWhiteListIps = config.getStringList("omi-service.input-whitelist-ips") 
  val inputWhiteListSubnets = config.getStringList("omi-service.input-whitelist-subnets") 

  val callbackDelay : FiniteDuration  = config.getDuration("omi-service.callback-delay", TimeUnit.SECONDS).seconds 
}



object Settings extends ExtensionId[OmiConfigExtension] with ExtensionIdProvider {
 
  override def lookup: Settings.type = Settings
   
  override def createExtension(system: ExtendedActorSystem) : OmiConfigExtension =
    new OmiConfigExtension(system.settings.config)
   
  /**
  * Java API: retrieve the Settings extension for the given system.
  */
  override def get(system: ActorSystem): OmiConfigExtension = super.get(system)
}


