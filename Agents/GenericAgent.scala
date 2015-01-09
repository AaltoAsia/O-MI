package agents
import akka.actor.{ ActorSystem, Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import xml._
import io._

object GenericAgentClient {
  def props(remote: InetSocketAddress) =
  Props(classOf[GenericAgentClient], remote)
}

class GenericAgentClient(remote: InetSocketAddress) extends Actor{
 import Tcp._
 import context.system
  
  IO(Tcp) ! Connect(remote)
 
  def receive = {
    case CommandFailed(_: Connect) =>
      println("connect failed")
      context stop self
       
    case c @ Connected(remote, local) =>
      println(c.toString)
      val connection = sender()
      connection ! Register(self)
      context become {
        case data: Node=> 
          connection ! Write( ByteString( new PrettyPrinter( 80, 2 ).format( data ) ) )
        case CommandFailed(w: Write) => 
        // O/S buffer was full
          println("write failed")
        case Received(data) =>
          println(data.toString)
        case "close" =>
          connection ! Close
        case _: ConnectionClosed =>
          println("connection closed")
          context stop self
      }
  }
}

object GenericAgent extends App {
  override def main(args: Array[String]) = {
    if(args.length < 3){
      println("arguments are <address> <port> <sensor's path>")
    } else {
      val address = new InetSocketAddress( args(0),args(1).toInt)
      var path = args(2).split("/")
      
      if(path.head == "Objects")
        path = path.tail

      implicit val system = ActorSystem("on-generic-agent")
      val client = system.actorOf(GenericAgentClient.props(address), "generic-agent")  

      while(true){
        val value = StdIn.readLine
        client ! <Objects>{genODF(path,value)}</Objects>
      }
    }
    def genODF( path: Seq[String], value: String) : Elem ={
      if(path.length == 1)
        <InfoItem name={path.head}><value>{value}</value></InfoItem>
      else
        <Object><id>{path.head}</id>{genODF(path.tail,value)}</Object>
    }
  }
}
