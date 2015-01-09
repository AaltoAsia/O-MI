package agents
import akka.actor.{ Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import xml._

object GenericAgentClient {
  def props(remote: InetSocketAddress, replies: ActorRef) =
  Props(classOf[GenericAgentClient], remote, replies)
}

class GenericAgentClient(remote: InetSocketAddress, listener: ActorRef) extends Actor{
 import Tcp._
 import context.system
  
  IO(Tcp) ! Connect(remote)
 
  def receive = {
    case CommandFailed(_: Connect) =>
      listener ! "connect failed"
      context stop self
       
    case c @ Connected(remote, local) =>
      listener ! c
      val connection = sender()
      connection ! Register(self)
      context become {
        case data: xml.Node => 
          connection ! Write( ByteString( new PrettyPrinter( 80, 2 ).format( data ) ) )
        case CommandFailed(w: Write) => 
        // O/S buffer was full
          listener ! "write failed"
        case Received(data) =>
          listener ! data
        case "close" =>
          connection ! Close
        case _: ConnectionClosed =>
          listener ! "connection closed"
          context stop self
      }
  }
}

