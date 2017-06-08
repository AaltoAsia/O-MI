
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

import java.net.InetSocketAddress

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import agentSystem.{AgentInfo, AgentName, NewCLI}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, ActorSystem}
import akka.io.Tcp
import akka.io.Tcp._
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import database._
import responses.{RemoveSubscription, RemoveHandler, RemoveHandlerT }
import types.Path

/** Object that contains all commands of InternalAgentCLI.
 */
object CLICmds
{
  case class ReStartAgentCmd(agent: String)
  case class StartAgentCmd(agent: String)
  case class StopAgentCmd(agent: String)
  case class ListAgentsCmd()
  case class ListSubsCmd()
  case class SubInfoCmd(id: Long)
  case class RemovePath(path: String)
}

import http.CLICmds._
object OmiNodeCLI{
  def props(
    connection: ActorRef,
    sourceAddress: InetSocketAddress,   
    removeHandler: RemoveHandlerT,
    agentSystem: ActorRef,
    subscriptionManager: ActorRef
    )(
      ) : Props = Props(
        new OmiNodeCLI(
          connection,
          sourceAddress,
          removeHandler,
          agentSystem,
          subscriptionManager
        )
      )
}
/** Command Line Interface for internal agent management. 
 *
 */
class OmiNodeCLI(
  protected val connection: ActorRef,
  protected val sourceAddress: InetSocketAddress,   
  protected val removeHandler: RemoveHandlerT,
  protected val agentSystem: ActorRef,
  protected val subscriptionManager: ActorRef
) extends Actor with ActorLogging {

  val commands = """Current commands:
  start <agent classname>
  stop  <agent classname> 
  list agents 
  list subs 
  showSub <id>
  remove <subsription id>
  remove <path>
  """
  val ip = sourceAddress.toString
  implicit val timeout : Timeout = 1.minute

  val commandTimeout = 1.minute

  override def preStart: Unit ={
    val connectToManager = (agentSystem ? NewCLI(ip,self)).mapTo[Boolean]
    connectToManager.onSuccess{
      case u: Boolean =>
        send(connection)(s"CLI connected to AgentManager.\r\n>")
     log.info(s"$ip connected to AgentManager. Connection: $connection")
    }
    connectToManager.onFailure{
      case t: Throwable => 
        send(connection)(s"CLI failed connected to AgentManager. Caught: $t.\r\n>")
        log.info(s"$ip failed to connect to AgentManager. Caught: $t. Connection: $connection")
    }
  }
  override def postStop: Unit ={
  
    send(connection)(s"CLI stopped by O-MI Node.\r\n")
  
  }
  private def help(): String = {
    log.info(s"Got help command from $ip")
    commands
  }

  private[http] def agentsStrChart( agents: Vector[AgentInfo] ) : String ={
    val colums = Vector("NAME","CLASS","RUNNING","OWNED COUNT", "CONFIG")
    val msg =
      f"${colums(0)}%-20s | ${colums(1)}%-40s | ${colums(2)} | ${colums(3)}%-11s | ${colums(4)}\r\n" +
    agents.map{
      case AgentInfo(name, classname, config, ref, running, ownedPaths, lang) => 
        f"$name%-20s | $classname%-40s | $running%-7s | ${ownedPaths.size}%-11s | $config" 
    }.mkString("\r\n")
    msg +"\r\n"
  }
  private def listAgents(): String = {
    log.info(s"Got list agents command from $ip")
    val result = (agentSystem ? ListAgentsCmd()).mapTo[Vector[AgentInfo]]
      .map{
        case agents: Vector[AgentInfo @unchecked] =>  // internal type 
          log.info("Received list of Agents. Sending ...")
          agentsStrChart( agents.sortBy{ info => info.name} )
        case _ => ""
      }
        .recover[String]{
          case a : Throwable =>
            log.warning(s"Failed to get list of Agents. Sending error message. " + a.toString)
            "Something went wrong. Could not get list of Agents.\r\n>"
        }
        Await.result(result, commandTimeout)
  }

  def subsStrChart (
    intervals: Set[IntervalSub @unchecked],
    events: Set[EventSub] @unchecked,
    polls: Set[PolledSub] @unchecked) : String = {

      val (idS, intervalS, startTimeS, endTimeS, callbackS, lastPolledS) =
        ("ID", "INTERVAL", "START TIME", "END TIME", "CALLBACK", "LAST POLLED")

      val intMsg= "Interval subscriptions:\r\n" + f"$idS%-10s | $intervalS%-20s | $startTimeS%-30s | $endTimeS%-30s | $callbackS\r\n" +
      intervals.map{ sub=>
        f"${sub.id}%-10s | ${sub.interval}%-20s | ${sub.startTime}%-30s | ${sub.endTime}%-30s | ${ sub.callback.address }"
      }.mkString("\r\n")

      val eventMsg = "Event subscriptions:\r\n" + f"$idS%-10s | $endTimeS%-30s | $callbackS\r\n" + events.map{ sub=>
        f"${sub.id}%-10s | ${sub.endTime}%-30s | ${ sub.callback.address}"
      }.mkString("\r\n")

      val pollMsg = "Poll subscriptions:\r\n" + f"$idS%-10s | $startTimeS%-30s | $endTimeS%-30s | $lastPolledS\r\n" +
      polls.map{ sub=>
        f"${sub.id}%-10s | ${sub.startTime}%-30s | ${sub.endTime}%-30s | ${ sub.lastPolled }"
      }.mkString("\r\n")

      s"$intMsg\r\n$eventMsg\r\n$pollMsg\r\n>"
  } 
  private def listSubs(): String = {
    log.info(s"Got list subs command from $ip")
    val result = (subscriptionManager ? ListSubsCmd())
      .map{
        case (intervals: Set[IntervalSub @unchecked],
          events: Set[EventSub] @unchecked,
          polls: Set[PolledSub] @unchecked) => // type arguments cannot be checked
          log.info("Received list of Subscriptions. Sending ...")

          subsStrChart( intervals, events, polls)
      }.recover{
          case a: Throwable  =>
            log.info("Failed to get list of Subscriptions.\r\n Sending ...")
            "Failed to get list of subscriptions.\r\n>"
      }
    Await.result(result, commandTimeout)
  }
  private def subInfo(id: Long): String = {
    log.info(s"Got sub info command from $ip")
    val result = (subscriptionManager ? SubInfoCmd(id)).mapTo[Option[SavedSub]] 
      .map{
        case Some(intervalSub: IntervalSub) =>
          s"Started: ${intervalSub.startTime}\r\n" +
          s"Ends: ${intervalSub.endTime}\r\n" +
          s"Interval: ${intervalSub.interval}\r\n" +
          s"Callback: ${intervalSub.callback.address}\r\n" +
          s"Paths:\r\n${intervalSub.paths.mkString("\r\n")}\r\n>"
        case Some(eventSub: EventSub) =>
          s"Ends: ${eventSub.endTime}\r\n" +
          s"Callback: ${eventSub.callback.address}\r\n" +
          s"Paths:\r\n${eventSub.paths.mkString("\r\n")}\r\n>"
        case Some(pollSub: PollIntervalSub) =>
          s"Started: ${pollSub.startTime}\r\n" +
          s"Ends: ${pollSub.endTime}\r\n" +
          s"Interval: ${pollSub.interval}\r\n" +
          s"Last polled: ${pollSub.lastPolled}\r\n" +
          s"Paths:\r\n${pollSub.paths.mkString("\r\n")}\r\n>"
        case Some(pollSub: PollEventSub) =>
          s"Started: ${pollSub.startTime}\r\n" +
          s"Ends: ${pollSub.endTime}\r\n" +
          s"Last polled: ${pollSub.lastPolled}\r\n" +
          s"Paths:\r\n${pollSub.paths.mkString("\r\n")}\r\n>"
        case None => 
          log.info(s"Subscription with id $id not found.\r\n Sending ...")
          s"Subscription with id $id not found.\r\n>"
      }.recover{
        case a: Throwable  =>
          log.info(s"Failed to get subscription with $id.\r\n Sending ...")
          s"Failed to get subscription with $id.\r\n>"
      }
    Await.result(result, commandTimeout)
  }

  private def startAgent(agent: AgentName): String = {
    log.info(s"Got start command from $ip for $agent")
    /*
    val result = (agentSystem ! StartAgentCmd(agent)).mapTo[Future[String]]
      .flatMap{ case future : Future[String] => future }
      .map{
        case msg: String =>
          msg +"\r\n"
      }.recover{
        case a : Throwable =>
          "Command failure unknown.\r\n"
      }

    Await.result(result, commandTimeout)
    */
    agentSystem ! StartAgentCmd(agent)
    ">"
  }

  private def stopAgent(agent: AgentName): String = {
    log.info(s"Got stop command from $ip for $agent")
    agentSystem ! StopAgentCmd(agent)
    ">"
  }

  private def remove(pathOrId: String): String = {
    log.info(s"Got remove command from $ip with parameter $pathOrId")

    if(pathOrId.forall(_.isDigit)){
      val id = pathOrId.toInt
      log.info(s"Removing subscription with id: $id")

      val result = (subscriptionManager ? RemoveSubscription(id))
        .map{
          case true =>
            s"Removed subscription with $id successfully.\r\n>"
          case false =>
            s"Failed to remove subscription with $id. Subscription does not exist or it is already expired.\r\n>"
        }.recover{
          case a : Throwable =>
            "Command failure unknown.\r\n>"
        }
      Await.result(result, commandTimeout)
    } else {
      log.info(s"Trying to remove path $pathOrId")
      if (removeHandler.handlePathRemove(Path(pathOrId))) {
        log.info(s"Successfully removed path")
        s"Successfully removed path $pathOrId\r\n>"
      } else {
        log.info(s"Given path does not exist")
        s"Given path does not exist\r\n>"
      }
    } //requestHandler isn't actor

  }

  private def send(receiver: ActorRef)(msg: String): Unit =
    receiver ! Write(ByteString(msg)) 


  def receive : Actor.Receive = {
    case Received(data) =>{ 
      val dataString : String = data.decodeString("UTF-8")

      val splitRegex = """\"((?:\\\"|[^\"])*)\"|(\S+)""".r
      //match inside quotes or non-whitespace sequences, escaped "-characters allowed (\")

      //note: without mapping the groups the result would still contain the "-characters
      val args = splitRegex.findAllMatchIn(dataString).map( m =>
          if(null == m.group(1))
            m.group(2)
          else
            // replace escaped "-characters (regex escapes: \\ ", string escapes: \\ \\ \")
            m.group(1).replaceAll("\\\\\"", "\"")
      ).toVector

      args match {
        case Vector("help") => send(sender)(help())
        case Vector("showSub", id) => send(sender)(subInfo(id.toLong))
        case Vector("list", "agents") => send(sender)(listAgents())
        case Vector("list", "subs") => send(sender)(listSubs())
        case Vector("start", agent) => send(sender)(startAgent(agent))
        case Vector("stop", agent)  => send(sender)(stopAgent(agent))
        case Vector("remove", pathOrId) => send(sender)(remove(pathOrId))
        case Vector(cmd @ _*) => 
          log.warning(s"Unknown command from $ip: "+ cmd.mkString(" "))
          send(sender)("Unknown command. Use help to get information of current commands.\r\n>") 
      }
    }
    case PeerClosed =>{
      log.info(s"CLI disconnected from $ip")
      context stop self
    }
    case str: String if sender() == agentSystem =>
      send(connection)(str + "\r\n>")
  }

}

class OmiNodeCLIListener(
  protected val system: ActorSystem,
  protected val agentSystem: ActorRef,
  protected val subscriptionManager: ActorRef,
  protected val singleStores: SingleStores,
  protected val dbConnection: DB

)  extends Actor with ActorLogging{

  import Tcp._

  def receive : Actor.Receive={
    case Bound(localAddress) =>
      // TODO: do something?
      // It seems that this branch was not executed?

    case CommandFailed(b: Bind) =>
      log.warning(s"CLI connection failed: $b")
      //context stop self

    case Connected(remote, local) =>
      val connection = sender()
      log.info(s"CLI connected from $remote to $local")
      val remover = new RemoveHandler(singleStores, dbConnection )(system)

      val cli = context.system.actorOf(
        OmiNodeCLI.props(connection, remote,remover,agentSystem, subscriptionManager),
      "cli-" + remote.toString.tail)
      connection ! Register(cli)
    case _ => //noop?
  }

}
