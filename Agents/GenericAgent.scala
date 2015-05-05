package agents
import parsing.Types._
import agentSystem._
import akka.actor._
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
class GenericAgent( configPath: String) extends InternalAgentActor(configPath) {
  var fileToRead : Option[File] = None 
  var path : Option[Path] = None 
  case class Msg(msg: String)
  
  import scala.concurrent.ExecutionContext.Implicits.global
  
  override def preStart() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      context.parent !  ActorInitializationException
      return
    }

    val lines = io.Source.fromFile(configPath).getLines().toArray
    path = Some(lines.head.split("/").toSeq)
    var file = new File(lines.last)
    if(!file.canRead){
      context.parent !  ActorInitializationException
      return
    }
    fileToRead = Some(file)
    run
  } 
    
  // XXX: infinite event loop!
  /** A partial function for reacting received messages.
    * Event loop.
    *  
    */
  def receive = {
    case Msg(value) =>
    if(path.nonEmpty && fileToRead.nonEmpty)
      genODF(path.get, value) match {
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
      for(line <- io.Source.fromFile(fileToRead.get).getLines)
      self ! Msg(line)
    }
  }

/** Functiong for generating O-DF message
  * @param path Path of InfoItem/Object.
  * @param value Value of Infoitem.
  * @param deepnest Recursion parameter.
  * @return OdfNode containing structure and data of sensors.
  */
  def genODF( path: Path, value: String, deepnest : Int = 1) : OdfNode =
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
  def apply( configPath: String) : Props = props(configPath)
  def props( configPath: String) : Props = {Props(new GenericAgent(configPath)) }
}

