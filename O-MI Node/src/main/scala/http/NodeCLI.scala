
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

import akka.actor.{ Props, ActorRef, Actor, ActorLogging, SupervisorStrategy, OneForOneStrategy, ActorInitializationException, ActorKilledException }
import akka.io.{ IO, Tcp  }
import akka.pattern.{ask}
import scala.util.{Success, Failure}
import java.net.InetSocketAddress
import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import akka.util.{ByteString,  Timeout}

/** Object that contains all commands of InternalAgentCLI.
 */
object InternalAgentCLICmds
{
  case class ReStartCmd(agent: String)
  case class StartCmd(agent: String)
  case class StopCmd(agent: String)
  case class ListCmd()
}

import InternalAgentCLICmds._
/** Command Line Interface for internal agent management. 
  *
  */
class OmiNodeCLI(
    sourceAddress: InetSocketAddress,
    agentLoader: ActorRef
  ) extends Actor with ActorLogging {

      val ip = sourceAddress.toString.tail
  implicit val timeout : Timeout = 5.seconds
  import Tcp._
  def receive = {
    case Received(data) =>{ 
      val dataString : String = data.decodeString("UTF-8")

      val args : Array[String] = dataString.split("( |\n)")
      args match {
        case Array("help") =>
          log.info(s"Got help command from $ip")
          sender ! Write(ByteString(
"""Current commands:
> start <agent classname>
> stop  <agent classname> 
> list agents -- NOT IMPLEMENTED
> list subs -- NOT IMPLEMENTED 
> remove sub <subsription id> -- NOT IMPLEMENTED
> remove path <path> -- NOT IMPLEMENTED
"""))
        case Array("list") =>
          log.info(s"Got list command from $ip")
          val trueSender = sender()
          val agents = (agentLoader ? ListCmd()).onComplete{
            case Success(agents : Seq[String]) => 
              log.info("Received list of Agents. Sending ...")
              trueSender ! Write(ByteString("Agents:\n"+ agents.mkString("\n") + "\n"))
            case Failure(a) =>
              log.info("Failed to get list of Agents.\n Sending ...")
              trueSender ! Write(ByteString("Failed to get list of Agents.\n"))

          }

        case Array("start", agent) =>
          log.info(s"Got start command from $ip for $agent")
          agentLoader ! StartCmd(agent)
        case Array("stop", agent) => 
          log.info(s"Got stop command from $ip for $agent")
          agentLoader ! StopCmd(agent)
        case cmd: Array[String] => log.warning(s"Unknown command from $ip: "+ cmd.mkString(" "))
        case a => log.warning(s"Unknown message from $ip: "+ a) 
      }
    }
    case PeerClosed =>{
      log.info(s"CLI disconnected from $ip")
      context stop self
    }
  }
}

class OmiNodeCLIListener(agentLoader: ActorRef)  extends Actor with ActorLogging{

  import Tcp._

  def receive ={
    case Bound(localAddress) =>
    // TODO: do something?
    // It seems that this branch was not executed?

    case CommandFailed(b: Bind) =>
      log.warning(s"CLI connection failed: $b")
      context stop self

    case Connected(remote, local) =>
      val connection = sender()
      log.info(s"CLI connected from $remote to $local")

      val cli = context.system.actorOf(
        Props(new OmiNodeCLI( remote, agentLoader )),
        "cli-" + remote.toString.tail)
      connection ! Register(cli)
    case _ => //noop?
  }

}
