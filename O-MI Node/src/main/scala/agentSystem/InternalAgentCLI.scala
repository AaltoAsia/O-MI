package agentSystem

import akka.actor.{ Actor, ActorRef, Props  }
import akka.io.{ IO, Tcp  }
import akka.util.ByteString
import akka.actor.ActorLogging
import java.net.InetSocketAddress

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
          context.parent ! StartCmd(agent)
        case Array("re-start", agent) =>
          log.debug(s"Got re-start command from $sender for $agent")
          context.parent ! ReStartCmd(agent)
        case Array("stop", agent) => 
          log.debug(s"Got stop command from $sender for $agent")
          context.parent ! StopCmd(agent)
        case cmd: Array[String] => log.warning(s"Unknown command from $sender: "+ cmd.mkString(" "))
      }
    }
  case PeerClosed =>{
    log.info(s"InternalAgent CLI disconnected from $sourceAddress")
    context stop self
  }
  }
}
