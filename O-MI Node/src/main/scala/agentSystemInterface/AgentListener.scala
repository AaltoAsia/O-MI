package agentSystemInterface

import akka.actor.{ Actor, ActorRef, Props  }
import akka.io.{ IO, Tcp  }
import akka.util.ByteString
import java.net.InetSocketAddress


class AgentListener extends Actor {
   
  import Tcp._
  import context.system
   
  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 8181))
   
  def receive = {
    case Bound(localAddress) =>
      // TODO: do some logging? or setup?
      println(s"Agent connected from $localAddress")
   
    case CommandFailed(_: Bind) => context stop self
   
    case Connected(remote, local) =>
      val handler = context.actorOf(Props[InputDataHandler])
      val connection = sender()
      connection ! Register(handler)
  }

}

class InputDataHandler extends Actor {
  import Tcp._
  def receive = {
    case Received(data) => println(s"Got data $data")
    case PeerClosed => context stop self
  }
}





