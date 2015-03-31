package agents
import parsing.Types._
import agentSystem._
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

/** A generic agent that read standart input stream and send given valus to AgentListenr via client.
  * @param Path where sensor is.
  * @param Client actor that handles connection with AgentListener
  */
class GenericAgent( path: Seq[String], fileToRead: File)  extends AgentActor {

  case class Msg(msg: String)
  import scala.concurrent.ExecutionContext.Implicits.global
  run
  // XXX: infinite event loop!
  /** A partial function for reacting received messages.
    * Event loop.
    *  
    */
  def receive = {
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
  def run() : Unit = {
    Future {
      for(line <- io.Source.fromFile(fileToRead).getLines)
      self ! Msg(line)
    }
  }

/** Functiong for generating O-DF message
  * @param path Path of InfoItem/Object.
  * @param value Value of Infoitem.
  * @param deepnest Recursion parameter.
  * @return OdfNode containing structure and data of sensors.
  */
  def genODF( path: Seq[String], value: String, deepnest : Int = 1) : OdfNode =
  {
    if(deepnest == path.size){
      OdfInfoItem( path, Seq( TimedValue( None, value ) ), None )
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

/** Helper obejct for creating GenericAgent.
  *
  */
object GenericAgent {
  def apply( path: Seq[String], file: File) : Props = props(path, file)
  def props( path: Seq[String], file: File) : Props = {Props(new GenericAgent(path,file)) }
}

/** Class for handling configuration and creating of GenericAgent.
  *
  */
class GenericBoot extends Bootable {
  private var configPath : String = ""
  private var agentActor : ActorRef = null
  /** Startup function that handles configuration and creates GenericAgent.
    * 
    * @param system ActorSystem were GenericAgent will live.
    * @param pathToConfig Path to config file.
    * @return Boolean indicating successfulnes of startup.
    */
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
    return true
  }
  override def shutdown() : Unit = {}
  /** Simple getter fucntion for GenericAgent.
    *
    * @return Sequence of ActorRef containing only ActorRef of GenericAgent
    */
  override def getAgentActor() : Seq[ActorRef] = Seq(agentActor)

}

