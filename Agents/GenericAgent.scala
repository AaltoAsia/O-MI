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
import akka.actor.ActorLogging
import java.net.URLClassLoader
import java.io.File

abstract trait IAgentActor extends Actor with ActorLogging{

}

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

//Agents should have their own config files
case class Start()
/** A generic agent that read standart input stream and send given valus to AgentListenr via client.
  * @param Path where sensor is.
  * @param Client actor that handles connection with AgentListener
  */

class GenericAgent( path: Seq[String], agentListener: ActorRef)  extends IAgentActor {

  // XXX: infinite event loop hack!
/** A partial function for reacting received messages.
  * Event loop hack. Better than while(true) if there will be other messages.
  * 
  */
  def receive = {
    case Start => run()
  }

/** Function to loop for getting new values to sensor. 
  * Part of event loop hack. 
  */
  def run() = {
    if(System.in.available() != 0){
      val value = StdIn.readLine
      agentListener ! genODF(path,value)
    }
    self ! "Run"
  }

/** Functiong for generating O-DF message
*/
  def genODF( path: Seq[String], value: String) : Unit ={
  }
}


trait Bootable {
  def hasConfig : Boolean 
  def setConfigPath( path : String): Unit  
  def isConfigValid : Boolean
  def getAgentActor : ActorRef
  /** 
    * Callback run on microkernel startup.
    * Create initial actors and messages here.
    */
  def startup(): Boolean

  /** 
    * Callback run on microkernel shutdown.
    * Shutdown actor systems here.
    */
  def shutdown(): Unit
}

class GenericBoot extends Bootable {
  private var configPath : String = ""
  private var configSet : Boolean = false
  private var agentActor : ActorRef = null
  override def setConfigPath(path : String ) : Unit = { 
    configPath = path
    configSet = true 
  }
  override def hasConfig : Boolean = configSet
  override def isConfigValid : Boolean = {
    hasConfig && new File(configPath).exists()  
  }

  override def startup() : Boolean = {
    if(!hasConfig || !isConfigValid)
      return false

    val lines = io.Source.fromFile(configPath).getLines().toArray
    var path = lines.head
    return true


  }
  override def shutdown() : Unit = {}
  override def getAgentActor() : ActorRef = agentActor 

}

