
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

import java.net.InetSocketAddress

import agentSystem.AgentInfo
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import database.{EventSub, IntervalSub, PolledSub}
import responses.{RemoveSubscription, RequestHandler}
import types.Path

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Object that contains all commands of InternalAgentCLI.
 */
object CLICmds
{
  case class ReStartAgentCmd(agent: String)
  case class StartAgentCmd(agent: String)
  case class StopAgentCmd(agent: String)
  case class ListAgentsCmd()
  case class ListSubsCmd()
  case class RemovePath(path: String)
}

import http.CLICmds._
object OmiNodeCLI{
  def props(
    sourceAddress: InetSocketAddress,
    agentSystem: ActorRef,
    subscriptionHandler: ActorRef,
    requestHandler: RequestHandler
  ) : Props = Props( new OmiNodeCLI( sourceAddress, agentSystem, subscriptionHandler, requestHandler ))
}
/** Command Line Interface for internal agent management. 
  *
  */
class OmiNodeCLI(
    sourceAddress: InetSocketAddress,
    agentLoader: ActorRef,
    subscriptionHandler: ActorRef,
    requestHandler: RequestHandler
  ) extends Actor with ActorLogging {

  val commands = """Current commands:
start <agent classname>
stop  <agent classname> 
list agents 
list subs 
remove <subsription id>
remove <path>
"""
  val ip = sourceAddress.toString.tail
  implicit val timeout : Timeout = 5.seconds
  import Tcp._
  def receive : Actor.Receive = {
    case Received(data) =>{ 
      val dataString : String = data.decodeString("UTF-8")

      val args : Array[String] = dataString.split("( |\n)")
      args match {
        case Array("help") =>
          log.info(s"Got help command from $ip")
          sender ! Write(ByteString( commands )) 
        case Array("list", "agents") =>
          log.info(s"Got list agents command from $ip")
          val trueSender = sender()
          val future = (agentLoader ? ListAgentsCmd())
          future.map{
            case agents: Seq[AgentInfo] => 
              log.info("Received list of Agents. Sending ...")
              val colums = Vector("NAME","CLASS","RUNNING","CONFIG")
              val msg = f"${colums(0)}%-20s | ${colums(1)}%-40s | ${colums(2)} | ${colums(3)}\n"+ agents.map{
                case AgentInfo(name, classname, config, ref, running) => 
                f"$name%-20s | $classname%-40s | $running%-7s | $config " 
              }.mkString("\n")
              trueSender ! Write(ByteString(msg +"\n"))
            case agents: Seq[AgentInfo] => 
              log.warning("Could not receive list of Agents. Sending ...")
              val msg = "Something went wrong. Could not get list of Agents."
              trueSender ! Write(ByteString(msg +"\n"))
          }
          future.recover{
            case a : Throwable =>
              log.info("Failed to get list of Agents.\n Sending ...")
              trueSender ! Write(ByteString("Failed to get list of Agents.\n"))

          }

        case Array("list", "subs") =>
          log.info(s"Got list subs command from $ip")
          val trueSender = sender()
          val future = (subscriptionHandler ? ListSubsCmd())
          future.map{
            case (intervals: Set[IntervalSub], events: Set[EventSub], polls: Set[PolledSub]) =>
              log.info("Received list of Subscriptions. Sending ...")
              val (idS, intervalS, startTimeS, endTimeS, callbackS, lastPolledS) =
                ("ID", "INTERVAL", "START TIME", "END TIME", "CALLBACK", "LAST POLLED")
              val intMsg= "Interval subscriptions:\n" + f"$idS%-10s | $intervalS%-20s | $startTimeS%-30s | $endTimeS%-30s | $callbackS\n" +intervals.map{ sub=>
                 f"${sub.id}%-10s | ${sub.interval}%-20s | ${sub.startTime}%-30s | ${sub.endTime}%-30s | ${ sub.callback }"
              }.mkString("\n")
              val eventMsg = "Event subscriptions:\n" + f"$idS%-10s | $endTimeS%-30s | $callbackS\n" + events.map{ sub=>
                 f"${sub.id}%-10s | ${sub.endTime}%-30s | ${ sub.callback }"
              }.mkString("\n")
              val pollMsg = "Poll subscriptions:\n" + f"$idS%-10s | $startTimeS%-30s | $endTimeS%-30s | $lastPolledS\n" + polls.map{ sub=>
                 f"${sub.id}%-10s | ${sub.startTime}%-30s | ${sub.endTime}%-30s | ${ sub.lastPolled }"
              }.mkString("\n")
              trueSender ! Write(ByteString(intMsg + "\n" + eventMsg + "\n"+ pollMsg+ "\n"))
            }
            future.recover{
            case a =>
              log.info("Failed to get list of Subscriptions.\n Sending ...")
              trueSender ! Write(ByteString("Failed to get list of subscriptions.\n"))

          }
        case Array("start", agent) =>
          val trueSender = sender()
          log.info(s"Got start command from $ip for $agent")
          val future = (agentLoader ? StartAgentCmd(agent))
          future.map{
            case msg:String  =>
            trueSender ! Write(ByteString(msg +"\n"))
          }
          future.recover{
            case a =>
              trueSender ! Write(ByteString("Command failure unknown.\n"))
          }
        case Array("stop", agent) => 
          val trueSender = sender()
          log.info(s"Got stop command from $ip for $agent")
          val future = (agentLoader ? StopAgentCmd(agent))
          future.map{
            case msg:String => 
              trueSender ! Write(ByteString(msg +"\n"))
          }
          future.recover{
            case a =>
              trueSender ! Write(ByteString("Command failure unknown.\n"))
          }

        case Array("remove", pathOrId) => {
          val trueSender = sender()
          log.info(s"Got remove command from $ip with parameter $pathOrId")

          if(pathOrId.forall(_.isDigit)){
            val id = pathOrId.toInt
            log.info(s"Removing subscription with id: $id")

            val future = (subscriptionHandler ? RemoveSubscription(id))
            future.map{
              case true =>
                trueSender ! Write(ByteString(s"Removed subscription with $id successfully.\n"))
              case false =>
                trueSender ! Write(ByteString(s"Failed to remove subscription with $id. Subscription does not exist or it is already expired.\n"))
            }
            future.recover{
              case a =>
                trueSender ! Write(ByteString("Command failure unknown.\n"))
            }
          } else {
              log.info(s"Trying to remove path $pathOrId")
            requestHandler.handlePathRemove(Path(pathOrId)) match {
              case true => {
                trueSender ! Write(ByteString(s"Successfully removed path $pathOrId\n"))
                log.info(s"Successfully removed path")
              }
              case _    => {
                trueSender ! Write(ByteString(s"Given path does not exist\n"))
                log.info(s"Given path does not exist")
              }
            } //requestHandler isn't actor

          }
        }
        case cmd: Array[String] => 
          log.warning(s"Unknown command from $ip: "+ cmd.mkString(" "))
          sender() ! Write(ByteString(
            "Unknown command. Use help to get information of current commands.\n" 
          ))
        case _ => log.warning(s"Unknown message from $ip: ") 
          sender() ! Write(ByteString(
            "Unknown message. Use help to get information of current commands.\n" 
          ))
      }
    }
    case PeerClosed =>{
      log.info(s"CLI disconnected from $ip")
      context stop self
    }
  }
}

class OmiNodeCLIListener(agentLoader: ActorRef, subscriptionHandler: ActorRef, requestHandler: RequestHandler)  extends Actor with ActorLogging{

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
        OmiNodeCLI.props( remote, agentLoader, subscriptionHandler, requestHandler ),
        "cli-" + remote.toString.tail)
        connection ! Register(cli)
    case _ => //noop?
  }

}
