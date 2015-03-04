package agents
import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import xml._
import io._
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging
import System.currentTimeMillis


object GenericAgentClient {
  def props(remote: InetSocketAddress) =
  Props(classOf[GenericAgentClient], remote)
}
/** A generic client for agent to connecting and messaging with AgentListener.
  * @param Adress of AgentListener.
  */

class GenericAgentClient(remote: InetSocketAddress) extends Actor with ActorLogging {
 import Tcp._
 import context.system
  
  IO(Tcp) ! Connect(remote)
 
/** A partial function for reacting received messages.
  * 
  */
  def receive = {
    case CommandFailed(_: Connect) =>
      log.warning("Connection failed")
      context stop self
      system.shutdown()
       
    case c @ Connected(remote, local) =>
      val connection = sender()
      log.info(s"Agent connected to $remote from $local")
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
          log.warning("Write failed")

        case Received(data) =>
          println(data.toString)
        case "close" =>
          log.warning("Closing connection")
          connection ! Close
        case _: ConnectionClosed =>
          log.warning("Connection closed")
          context stop self
          system.shutdown()
      }
  }
}

/** A generic agent that read standart input stream and send given valus to AgentListenr via client.
  * @param Path where sensor is.
  * @param Client actor that handles connection with AgentListener
  */

case class Msg(s: String)
class GenericAgent(path: Seq[String], client: ActorRef) extends Actor  with ActorLogging {

   import scala.concurrent.ExecutionContext.Implicits.global
  // XXX: infinite event loop hack!
  self ! "Run"
/** A partial function for reacting received messages.
  * Event loop hack. Better than while(true) if there will be other messages.
  * 
  */
  def receive = {
    case "Run" => run()
    case Msg(value) =>
      client ! <Objects>{genODF(path,value)}</Objects>
      run()
  }

/** Function to loop for getting new values to sensor. 
  * Part of event loop hack. 
  */
  def run() = {
    //if(System.in.available() != 0){
    Future {
      self ! Msg(StdIn.readLine)
      //self ! "Run"
    }
    //}
    //context.system.scheduler.scheduleOnce(1.seconds, self, "Run")
  }

/** Functiong for generating O-DF message
*/
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
/** Simple main object to launch agent's actors 
**/
object GenericMain {
/** Simple main function to launch agent's actors 
  * @param arguments for connecting AgentListener and sensor's path.
**/
  def main(args: Array[String]) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    if(args.length < 3){
      println("arguments are <address> <port> <path of this sensor>")

    } else {
      val address = new InetSocketAddress( args(0),args(1).toInt)
      var path = args(2).split("/").filterNot(_.isEmpty)
      
      if(path.head == "Objects")
        path = path.tail

      implicit val timeout = Timeout(10.seconds)
      implicit val system = ActorSystem("on-generic-agent")

      val client = system.actorOf(
        GenericAgentClient.props(address), "generic-agent-client")  
      val agent = system.actorOf(
        Props(new GenericAgent(path,client)), "generic-agent")
    }
  }
}
