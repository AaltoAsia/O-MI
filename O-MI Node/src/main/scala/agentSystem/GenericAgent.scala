package agentSystem
import parsing.Types._
import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
import akka.event.{Logging, LoggingAdapter}
import akka.io.{ IO, Tcp }
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import xml._
import io._
import scala.concurrent.duration._
import scala.concurrent.Future
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.io.File
import java.sql.Timestamp
import System.currentTimeMillis

case class Start()
/** A generic agent that read standart input stream and send given valus to AgentListenr via client.
  * @param Path where sensor is.
  * @param Client actor that handles connection with AgentListener
  */

class GenericAgent( path: Seq[String], fileToRead: File)  extends IAgentActor {

  case class Msg(msg: String)
  import scala.concurrent.ExecutionContext.Implicits.global
  // XXX: infinite event loop hack!
/** A partial function for reacting received messages.
  * Event loop hack. Better than while(true) if there will be other messages.
  * 
  */
  def receive = {
    case Start => run()
    case Msg(value) =>
      genODF(path, value) match {
        case i: OdfInfoItem =>
        case o: OdfObject =>
          InputPusher.handleObjects(Seq(o))
      }
      run()
  }

/** Function to loop for getting new values to sensor. 
  * Part of event loop hack. 
  */
  def run() = {
    Future {
      for(line <- io.Source.fromFile(fileToRead).getLines)
      self ! Msg(line)
    }
  }

/** Functiong for generating O-DF message
*/
  def genODF( path: Seq[String], value: String, deepnest : Int = 1) : OdfNode =
  {
    if(deepnest == path.size){
      OdfInfoItem( path, Seq( TimedValue( None, value ) ), Seq.empty )
    } else {
      genODF(path, value, deepnest + 1) match {
        case i: OdfInfoItem =>
          OdfObject( path.take(deepnest), Seq.empty, Seq(i)) 
        case o: OdfObject =>
          OdfObject( path.take(deepnest), Seq(o), Seq.empty) 
      }
    }
  }
}

object GenericAgent {
  def props( path: Seq[String], file: File) : Props = {Props(new GenericAgent(path,file)) }
}

class GenericBoot extends Bootable {
  private var configPath : String = ""
  private var agentActor : ActorRef = null

  override def startup( system: ActorSystem, pathToConfig: String) : Boolean = {
    if(pathToConfig.isEmpty || !(new File(pathToConfig).exists()))
      return false 

    configPath = pathToConfig
    val lines = io.Source.fromFile(configPath).getLines().toArray
    var path = lines.head.split("/")
    var file = new File(lines.last)
    if(!file.canRead)
      return false
    
    agentActor = system.actorOf(GenericAgent.props(path, file), "Generic-Agent")    
    agentActor ! Start
    return true
  }
  override def shutdown() : Unit = {}
  override def getAgentActor() : ActorRef = agentActor 

}

