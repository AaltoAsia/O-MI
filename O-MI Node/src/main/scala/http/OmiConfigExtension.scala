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
import java.net.InetAddress

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

import agentSystem.AgentSystemConfigExtension
import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._
import types.Path



 
class OmiConfigExtension( val config: Config) extends Extension 
  with AgentSystemConfigExtension {

  /**
   * Implicit conversion from java.time.Duration to scala.concurrent.FiniteDuration
   * @param dur duration as java.time.Duration
   * @return given duration converted to FiniteDuration
   */
  implicit def toFiniteDuration(dur: java.time.Duration): FiniteDuration = Duration.fromNanos(dur.toNanos)
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

  val trimInterval : FiniteDuration = config.getDuration("omi-service.trim-interval")

  val snapshotInterval: FiniteDuration  = config.getDuration("omi-service.snapshot-interval")
  /** fast journal databases paths */
  val journalsDirectory: String = config.getString("journalDBs.directory")
  val writeToDisk: Boolean = config.getBoolean("journalDBs.write-to-disk")
  val maxJournalSizeBytes = config.getBytes("journalDBs.max-journal-filesize")
  // Listen interfaces and ports

  val interface: String = config.getString("omi-service.interface")
  //val port: Int = config.getInt("omi-service.port")
  val externalAgentInterface: String = config.getString("omi-service.external-agent-interface")
  //val externalAgentPort: Int = config.getInt("omi-service.external-agent-port")
  //val cliPort: Int = config.getInt("omi-service.agent-cli-port")

  /** analytics settings */
  val enableAnalytics: Boolean = config.getBoolean("analytics.enableAnalytics")
  val analyticsMaxHistoryLength: Int = config.getInt("analytics.maxHistoryLength")
  val updateInterval: FiniteDuration = config.getDuration("analytics.updateInterval")

  val enableReadAnalytics: Boolean = config.getBoolean("analytics.read.enableAnalytics")
  val enableWriteAnalytics: Boolean =config.getBoolean("analytics.write.enableAnalytics")
  val enableUserAnalytics: Boolean = config.getBoolean("analytics.user.enableAnalytics")

  val numReadSampleWindowLength: FiniteDuration = config.getDuration("analytics.read.windowLength")
  val readAvgIntervalSampleSize: Int = config.getInt("analytics.read.intervalSampleSize")
  val numberReadsInfoName: String = config.getString("analytics.read.numberOfReadsInfoItemName")
  val averageReadIAnfoName: String = config.getString("analytics.read.averageReadIntervalInfoItemName")

  val numWriteSampleWindowLength: FiniteDuration = config.getDuration("analytics.write.windowLength")
  val writeAvgIntervalSampleSize: Int = config.getInt("analytics.write.intervalSampleSize")
  val numberWritesInfoName: String = config.getString("analytics.write.numberOfWritesInfoItemName")
  val averageWriteInfoName: String = config.getString("analytics.write.averageWriteIntervalInfoItemName")

  val numUniqueUserSampleWindowLength: FiniteDuration = config.getDuration("analytics.user.windowLength")
  val numberUsersInfoName: String = config.getString("analytics.user.averageNumberOfUsersInfoItemName")
  // Authorization
  //External API
  val enableExternalAuthorization: Boolean = config.getBoolean("omi-service.authorization.enable-external-authorization-service")
  val externalAuthorizationPort: Int = config.getInt("omi-service.authorization.authorization-service-port")
  val externalAuthUseHttps: Boolean = config.getBoolean("omi-service.authorization.use-https")

  //IP
  val inputWhiteListUsers: Vector[String]= config.getStringList("omi-service.input-whitelist-users").toVector

  val inputWhiteListIps: Vector[Vector[Byte]] = config.getStringList("omi-service.input-whitelist-ips").map{
    case s: String => 
    val ip = inetAddrToBytes(InetAddress.getByName(s)) 
    ip.toVector
  }.toVector

  val inputWhiteListSubnets : Map[InetAddress, Int] = config.getStringList("omi-service.input-whitelist-subnets").map{ 
    case (str: String) => 
    val parts = str.split("/")
    require(parts.length == 2)
    val mask = parts.head
    val bits = parts.last
    val ip = InetAddress.getByName(mask)//inetAddrToBytes(InetAddress.getByName(mask))
    (ip, bits.toInt)
  }.toMap 
  private[this] def inetAddrToBytes(addr: InetAddress) : Seq[Byte] = {
    addr.getAddress().toList
  }
 

  /** Time in seconds how long to wait until retrying sending.*/
  val callbackDelay : FiniteDuration  = config.getDuration("omi-service.callback-delay", TimeUnit.SECONDS).seconds 

  /** Time in milliseconds how long to keep trying to resend the messages to callback addresses in case of infinite durations*/
  val callbackTimeout : FiniteDuration = config.getDuration("omi-service.callback-timeout", TimeUnit.MILLISECONDS).milliseconds

  //Haw many messages queued to be send via WS connection, if overflown
  //connection fails
  val websocketQueueSize : Int = config.getInt("omi-service.websocket-queue-size")

}



object OmiConfig extends ExtensionId[OmiConfigExtension] with ExtensionIdProvider {
 
  override def lookup: OmiConfig.type = OmiConfig
   
  override def createExtension(system: ExtendedActorSystem) : OmiConfigExtension =
    new OmiConfigExtension(system.settings.config)
   
  /**
  * Java API: retrieve the Settings extension for the given system.
  */
  override def get(system: ActorSystem): OmiConfigExtension = super.get(system)
}


