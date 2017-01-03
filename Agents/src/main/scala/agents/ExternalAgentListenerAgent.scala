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

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try

import akka.actor.{Actor, ActorSystem, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.RemoteAddress
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{Cancellable, Props}
import akka.io.{IO, Tcp}
import akka.util.Timeout
import org.slf4j.LoggerFactory
import http.Authorization.ExtensibleAuthorization
import http.{OmiConfig, IpAuthorization, OmiConfigExtension}
import parsing.OdfParser
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import types._
import agentSystem._
import com.typesafe.config.Config
import scala.concurrent.ExecutionContext.Implicits.global

object  ExternalAgentListener extends PropsCreator{
  def props( config: Config ): Props = {
          Props(new ExternalAgentListener(config))
  }
}
/** AgentListener handles connections from agents.
  */
class ExternalAgentListener(override val config: Config)
  extends ScalaInternalAgent
  // NOTE: This class cannot implement authorization based on http headers as it is only a tcp server
  {
  class ExtAgentAuthorization extends {
    override val log = LoggerFactory.getLogger(classOf[ExternalAgentListener])
    val settings :OmiConfigExtension = OmiConfig(actorSystem)
  } with ExtensibleAuthorization with IpAuthorization

  private val authorization = new ExtAgentAuthorization
   implicit val timeout = config.getDuration("timeout", SECONDS).seconds
   val port = config.getInt("port")
   val interface = config.getString("interface")
  import Tcp._
  implicit def actorSystem : ActorSystem = context.system

  def start : InternalAgentResponse = {
    val binding = (IO(Tcp)  ? Tcp.Bind(self,
      new InetSocketAddress(interface, port)))(timeout)
    Await.result(
      binding.map{
        case Bound(localAddress: InetSocketAddress) =>
          CommandSuccessful()
      }.recover{
        case t : Throwable =>
          StartFailed(t.getMessage, Some(t))
      }, timeout
    )

  }

  def stop : InternalAgentResponse = {
    val unbinding = (IO(Tcp)  ? Tcp.Unbind)(timeout)
    Await.result(
      unbinding.map{
        case Tcp.Unbound =>
          CommandSuccessful()
      }.recover{
        case t : Throwable =>
          StopFailed(t.getMessage, Some(t))
      }, timeout
      )

  }
  
  /** Partial function for handling received messages.
    */
  override  def receive  = {
    case Start() => sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
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
      val requestForPermissionCheck = OmiTypes.WriteRequest(OdfObjects(), None, Duration.Inf)

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
  * @param sourceAddress Agent's adress 
  */
class ExternalAgentHandler(
    sourceAddress: InetSocketAddress,
    agentSystem: ActorRef
  ) extends Actor with ActorLogging {

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

      val lastCharIndex = storage.lastIndexWhere(!_.isWhitespace) //find last whiteSpace location
      //check if the last part of the message contains closing xml tag
      if(storage.slice(lastCharIndex - 9, lastCharIndex + 1).endsWith("</Objects>")) {

        val parsedEntries = OdfParser.parse(storage, None)
        storage = ""
        parsedEntries match {
          case Left(errors) =>
            log.warning(s"Malformed odf received from agent ${sender()}: ${errors.mkString("\n")}")
          case Right(odf) =>
            val ttl  = Duration(5,SECONDS)
            implicit val timeout = Timeout(ttl)
            val write = WriteRequest( odf, None,  Duration(5,SECONDS))
            val result = (agentSystem ? ResponsibilityRequest(sourceAddress.toString, write)).mapTo[ResponsibleAgentResponse]
            result.onSuccess{
              case s: SuccessfulWrite =>
                log.debug(s"$sourceAddress pushed data successfully.")
            }

            result.onFailure{
              case e: Throwable => 
                log.warning(s"$sourceAddress failed to write all data, error: $e")
            }
            log.info(s"External agent sent data from $sender to AgentSystem")
          case _ => // not possible
        }
      }
    }
    case PeerClosed =>
    {
      log.info(s"Agent disconnected from $sourceAddress")
      context stop self
    }
  }
  
}
