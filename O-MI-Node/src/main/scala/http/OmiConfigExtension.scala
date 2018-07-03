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

import java.lang
import java.util.concurrent.TimeUnit
import java.net.{InetAddress, URLDecoder}

import scala.language.postfixOps
import scala.language.implicitConversions
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try
import agentSystem.AgentSystemConfigExtension
import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._
import akka.http.scaladsl.client.RequestBuilding.{RequestBuilder,Post,Get,Patch,Put,Head,Options}
import types.Path
import types.OmiTypes.RawRequestWrapper.MessageType

class OmiConfigExtension( val config: Config) extends Extension 
  with AgentSystemConfigExtension {

  /**
    * Throws exceptions if invalid url, returns the input parameter if success
    */
  private def testUri(address: String): Uri = {
    val uri = Uri(address)
    val hostAddress = uri.authority.host.address
    // Test address validity (throws exceptions when invalid)
    val ipAddress = InetAddress.getByName(hostAddress)

    uri
  }

  val callbackAuthorizationEnabled: Boolean = config.getBoolean("omi-service.callback-authorization-enabled")
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

  val settingsOdfPath: Path =  Path(config.getString("omi-service.settings-read-odfpath"))
    

  val trimInterval : FiniteDuration = config.getDuration("omi-service.trim-interval")

  val snapshotInterval: FiniteDuration  = config.getDuration("omi-service.snapshot-interval")
  /** fast journal databases paths */
  val journalsDirectory: String = config.getString("journalDBs.directory")
  val writeToDisk: Boolean = config.getBoolean("journalDBs.write-to-disk")
  val maxJournalSizeBytes: lang.Long = config.getBytes("journalDBs.max-journal-filesize")
  // Listen interfaces and ports

  val interface: String = config.getString("omi-service.interface")
  //val port: Int = config.getInt("omi-service.port")
  val externalAgentInterface: String = config.getString("omi-service.external-agent-interface")
  //val externalAgentPort: Int = config.getInt("omi-service.external-agent-port")
  //val cliPort: Int = config.getInt("omi-service.agent-cli-port")

  // Authorization
  val allowedRequestTypes: Set[MessageType] = config.getStringList("omi-service.allowRequestTypesForAll")
    .map((x) => MessageType(x.toLowerCase)).toSet

  // Old External AuthAPIService V1
  val authAPIServiceV1: Config =
    Try(config getConfig "omi-service.authorization") orElse
    Try(config getConfig "omi-service.authAPI.v1") get

  val enableExternalAuthorization: Boolean = authAPIServiceV1.getBoolean("enable-external-authorization-service")
  val enableAuthAPIServiceV1: Boolean = authAPIServiceV1.getBoolean("enable-external-authorization-service")
  val externalAuthorizationPort: Int = authAPIServiceV1.getInt("authorization-service-port")
  val externalAuthUseHttps: Boolean = authAPIServiceV1.getBoolean("use-https")

  // External AuthAPIService V2
  case object AuthApiV2 {
    val authAPIServiceV2: Config = config getConfig "omi-service.authAPI.v2"
    val enable: Boolean = authAPIServiceV2.getBoolean("enable")
    val authenticationEndpoint: Uri = testUri(authAPIServiceV2.getString("authentication.url"))
    val omiHttpHeadersToAuthentication: Set[String] = authAPIServiceV2.getStringList("authentication.copy-request-headers").toSet
    val authorizationEndpoint: Uri = testUri(authAPIServiceV2.getString("authorization.url"))

    type ParameterExtraction = Map[String,Map[String,String]]

    def cmap(c: Config): Map[String,String] = 
      c.root().keys.map(
          (key) => key -> c.getString(key)).toMap

    def mapmap(c: Config): ParameterExtraction = {
      c.root().keys.map{(key) =>
        val innerConfig = c.getConfig(key)
        key.toLowerCase -> cmap(innerConfig)
      }.toMap
    }

    val parameters: Config = authAPIServiceV2.getConfig("parameters") 
    val parametersFromRequest: ParameterExtraction = mapmap(parameters.getConfig("fromRequest")) 
    val parametersFromAuthentication: ParameterExtraction = mapmap(parameters.getConfig("fromAuthentication")) 
    val parametersToAuthentication: ParameterExtraction = mapmap(parameters.getConfig("toAuthentication")) 
    val parametersToAuthorization: ParameterExtraction = mapmap(parameters.getConfig("toAuthorization")) 
    val parametersConstants: Map[String,String] = cmap(parameters.getConfig("constants"))

    def toRequestBuilder(method: String) = method.toLowerCase match {
      case "get" => Get
      case "post" => Post
      case "patch" => Patch
      case "put" => Put
      case "head" => Head
      case "options" => Options
      case x => throw new MatchError(s"Invalid http method in configuration: $x")
    }

    val authenticationMethod: RequestBuilder = toRequestBuilder(authAPIServiceV2.getString("authentication.method"))
    val authorizationMethod: RequestBuilder = toRequestBuilder(authAPIServiceV2.getString("authorization.method"))
  }
  //val userInfoFromRequestHeaders: Map[String,String] = authAPIServiceV2.getObject("userinfo-from-request-headers")
  //
  //TODO
  // earlier lines override conflicting later ones
  // store-from-request = {
  //   headers.cookie.jwt_token = authtoken
  //   auth.bearer = authtoken
  // }
  // put-to-authentication-request = {
  //   query.auth-token = authtoken
  // }
  // store-from-authentication-response = {
  //   email = username
  //   username = username
  // }
  // put-to-authorization-request = {
  //   username = user
  // }

  //IP
  val inputWhiteListUsers: Vector[String]= config.getStringList("omi-service.input-whitelist-users").toVector

  val inputWhiteListIps: Vector[Vector[Byte]] = config.getStringList("omi-service.input-whitelist-ips").map {
    s: String =>
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

  val databaseImplementation : String = config.getString( "omi-service.database" )
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


