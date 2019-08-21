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
package agents

import java.net.InetSocketAddress

import agentSystem._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.RemoteAddress
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import authorization.Authorization.ExtensibleAuthorization
import authorization.IpAuthorization
import com.typesafe.config.Config
import http.{OmiConfig, OmiConfigExtension}
import org.slf4j.LoggerFactory
import types.omi._
import types._
import types.odf.{ImmutableODF}
import types.odf.parsing.ODFStreamParser
import akka.stream.ActorMaterializer

import scala.collection.JavaConverters
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object  ExternalAgentListener extends PropsCreator{
  def props( config: Config, requestHandler: ActorRef, dbHandler: ActorRef ): Props = {
          Props(new ExternalAgentListener(config,requestHandler, dbHandler))
  }
}
/** AgentListener handles connections from agents.
  */
class ExternalAgentListener(
  val config: Config, 
  requestHandler: ActorRef, 
  dbHandler: ActorRef
) extends ScalaInternalAgentTemplate(requestHandler,dbHandler)
  // NOTE: This class cannot implement authorization based on http headers as it is only a tcp server
  {
  class ExtAgentAuthorization extends {
    override val log = LoggerFactory.getLogger(classOf[ExternalAgentListener])
    val settings :OmiConfigExtension = OmiConfig(actorSystem)
  } with ExtensibleAuthorization with IpAuthorization

  private val authorization = new ExtAgentAuthorization
   implicit val timeout: FiniteDuration = config.getDuration("timeout", SECONDS).seconds
   val port: Int = config.getInt("port")
   val interface: AgentName = config.getString("interface")
  import Tcp._
  implicit def actorSystem : ActorSystem = context.system

  override def preStart: Unit = {
    val binding = (IO(Tcp)  ? Tcp.Bind(self,
      new InetSocketAddress(interface, port)))(timeout)
    Await.result(binding, timeout )
  }

  override def postStop : Unit = {
    val unbinding = (IO(Tcp)  ? Tcp.Unbind)(timeout)
    Await.result( unbinding, timeout )
  }
  
  /** Partial function for handling received messages.
    */
  override  def receive: PartialFunction[Any, Unit] = {
    case Bound(localAddress) =>
      // TODO: do something?
      // It seems that this branch was not executed?
   
    case CommandFailed(b: Bind) =>
      log.warning(s"Agent connection failed: $b")
      context stop self
   
    case Connected(remote, local) =>
      val connection = sender()

      // Code for ip address authorization check
      val user = RemoteAddress(remote)//remote.getAddress())
      val requestForPermissionCheck = omi.WriteRequest(ImmutableODF(), None, Duration.Inf)

      if( authorization.ipHasPermission(user)(requestForPermissionCheck).isSuccess ){
        log.info(s"Agent connected from $remote to $local")

        val handler = context.actorOf(
          ExternalAgentHandler.props( remote, agentSystem),
          "handler-"
            + remote.getHostString
            + ":"
            + remote.getPort()
            //+ "-"
            //+ System.nanoTime()
        )
        //log.info(s"created handler: $handler")
        connection ! Register(handler)

      } else {
        log.warning(s"Unauthorized " + remote+  " tried to connect as external agent.")
      }
    case _ =>
  }
}

object ExternalAgentHandler{
  def props(
    sourceAddress: InetSocketAddress,
    agentSystem: ActorRef
  ) : Props = {
          Props(new ExternalAgentHandler( sourceAddress, agentSystem))
  }

}

/** A handler for data received from a agent.
  * @param sourceAddress Agent's address
  */
class ExternalAgentHandler(
    sourceAddress: InetSocketAddress,
    requestHandler: ActorRef
  ) extends Actor with ActorLogging {
  implicit val m = ActorMaterializer()

  import Tcp._
  private var storage: String = ""

  /** Partial function for handling received messages.
    */
  def receive : Actor.Receive = {
    case Received(data) =>
    { 
      val dataString = data.decodeString("UTF-8")
      val odfPrefix = "<Objects"

      val beginning = dataString.dropWhile(_.isWhitespace).take(odfPrefix.length())

      log.debug(s"Got following data from $sender:\n$dataString")

      beginning match {
        case b if b.startsWith("<?xml") || b.startsWith(odfPrefix) =>
          storage = dataString.dropWhile(_.isWhitespace)
        case b if storage.nonEmpty =>
          storage += dataString
        case _ => //noop
      }

      val parsedEntries = ODFStreamParser.parse(storage)
      storage = ""
      parsedEntries onComplete {
        case Failure(errors) =>
          log.warning(
            s"Malformed odf received from agent ${sender()}: " +
              s"${errors.toString}")
        case Success(odf) =>
          val ttl  = Duration(5,SECONDS)
          implicit val timeout: Timeout = Timeout(ttl)
          val write = WriteRequest( odf, None,  Duration(5,SECONDS))
          val result = (requestHandler ? write).mapTo[ResponseRequest]
          result.onComplete{
            case Success( response: ResponseRequest )=>
              response.results.foreach{ 
                case wr: Results.Success =>
                  // This sends debug log message to O-MI Node logs if
                  // debug level is enabled (in logback.xml and application.conf)
                  log.debug(s"$sourceAddress pushed data successfully.")
                case ie: OmiResult => 
                  log.warning(s"Something went wrong when $sourceAddress wrote, $ie")
              }
                case Failure( t: Throwable) => 
                  // This sends debug log message to O-MI Node logs if
                  // debug level is enabled (in logback.xml and application.conf)
                  log.warning(s"$sourceAddress's write future failed, error: $t")
          }
          log.info(s"External agent sent data from $sender to AgentSystem")
        case _ => // not possible
      }
    }
    case PeerClosed =>
    {
      log.info(s"Agent disconnected from $sourceAddress")
      context stop self
    }
  }
  
}
