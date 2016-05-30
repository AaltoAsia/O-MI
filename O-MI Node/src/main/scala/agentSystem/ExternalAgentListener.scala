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
package agentSystem

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp
import http.Authorization.ExtensibleAuthorization
import http.IpAuthorization
import parsing.OdfParser
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import types._

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.duration._

object  ExternalAgentListener{
  def props( agentSystem: ActorRef ): Props = {
          Props(new ExternalAgentListener( agentSystem))
  }
}
/** AgentListener handles connections from agents.
  */
class ExternalAgentListener( agentSystem: ActorRef )
  extends Actor with ActorLogging
  with ExtensibleAuthorization with IpAuthorization
  // NOTE: This class cannot implement authorization based on http headers as it is only a tcp server
  {
  
  
  import Tcp._
  /** Partial function for handling received messages.
    */
  def receive : Actor.Receive = {
    case Bound(localAddress) =>
      // TODO: do something?
      // It seems that this branch was not executed?
   
    case CommandFailed(b: Bind) =>
      log.warning(s"Agent connection failed: $b")
      context stop self
   
    case Connected(remote, local) =>
      val connection = sender()

      // Code for ip address authorization check
      val user = Some(remote.getAddress())
      val requestForPermissionCheck = OmiTypes.WriteRequest(Duration.Inf, OdfObjects())

      if( ipHasPermission(user)(requestForPermissionCheck).isDefined ){
        log.info(s"Agent connected from $remote to $local")

        val handler = context.actorOf(
          ExternalAgentHandler.props( remote, agentSystem),
          "agent-handler-"+remote.toString
        )
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

        val parsedEntries = OdfParser.parse(storage)
        storage = ""
        parsedEntries match {
          case Left(errors) =>
            log.warning(s"Malformed odf received from agent ${sender()}: ${errors.mkString("\n")}")
          case Right(odf) =>
            val write = WriteRequest( Duration(5,SECONDS), odf)
            val promiseResult = PromiseResult()
            agentSystem ! PromiseWrite( promiseResult, write) 
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
