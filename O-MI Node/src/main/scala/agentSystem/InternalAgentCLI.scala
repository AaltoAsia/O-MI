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

import akka.actor.{ Actor, Props  }
import akka.io.{ IO, Tcp  }
import akka.actor.ActorLogging
import java.net.InetSocketAddress

object InternalAgentCLICmds
{
  case class ReStartCmd(agent: String)
  case class StartCmd(agent: String)
  case class StopCmd(agent: String)
}

import InternalAgentCLICmds._
class InternalAgentCLI(
    sourceAddress: InetSocketAddress
  ) extends Actor with ActorLogging {

  import Tcp._
  def receive = {
    case Received(data) =>{ 
      val dataString = data.decodeString("UTF-8")

      val args = dataString.split(" ")
      args match {
        case Array("start", agent) =>
          log.debug(s"Got start command from $sender for $agent")
          context.parent ! StartCmd(agent.dropRight(1))
        case Array("re-start", agent) =>
          log.debug(s"Got re-start command from $sender for $agent")
          context.parent ! ReStartCmd(agent.dropRight(1))
        case Array("stop", agent) => 
          log.debug(s"Got stop command from $sender for $agent")
          context.parent ! StopCmd(agent.dropRight(1))
        case cmd: Array[String] => log.warning(s"Unknown command from $sender: "+ cmd.mkString(" "))
        case a => log.warning(s"Unknown message from $sender: "+ a) 
      }
    }
  case PeerClosed =>{
    log.info(s"InternalAgent CLI disconnected from $sourceAddress")
    context stop self
  }
  }
}
