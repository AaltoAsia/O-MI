package agents
import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import xml._
import io._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask

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
      system.shutdown()
       
    case c @ Connected(remote, local) =>
      println(c.toString)
      val connection = sender()
      connection ! Register(self)

      context become {
        case data: Node=> 
          connection ! Write(
            ByteString(
              new PrettyPrinter( 80, 2 ).format( data )
            )
          )
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
          system.shutdown()
      }
  }
}


class GenericAgent(path: Seq[String], client: ActorRef) extends Actor {

  // XXX: infinite event loop hack!
  self ! "Run"
  def receive = {
    case "Run" => run()
  }

  def run() = {
    if(System.in.available() != 0){
      val value = StdIn.readLine
      client ! <Objects>{genODF(path,value)}</Objects>
    }
    self ! "Run"
  }


  def genODF( path: Seq[String], value: String) : Elem ={
    if(path.length == 1)
      <InfoItem name={path.head}>
        <value>{value}</value>
      </InfoItem>
    else
       <Object>
         <id>{path.head}</id>
         {genODF(path.tail,value)}
       </Object>
  }
}
object GenericMain {
  def main(args: Array[String]) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    if(args.length < 3){
      println("arguments are <address> <port> <path of this sensor>")

    } else {
      val address = new InetSocketAddress( args(0),args(1).toInt)
      var path = args(2).split("/").filterNot(_.isEmpty)
      
      if(path.head == "Objects")
        path = path.tail

      implicit val timeout = Timeout(5.seconds)
      implicit val system = ActorSystem("on-generic-agent")

      val client = system.actorOf(
        GenericAgentClient.props(address), "generic-agent-client")  
      val agent = system.actorOf(
        Props(new GenericAgent(path,client)), "generic-agent")
    }
  }
}
