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
class GenericAgent( configPath: String) extends InternalAgent(configPath) {
  var fileToRead : Option[File] = None 
  var path : Option[Path] = None 
  var source : Option[Iterator[String]] = None
  
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      shutdown
      return
    }

    val lines = io.Source.fromFile(configPath).getLines().toArray
    path = Some(lines.head.split("/").toSeq)
    var file = new File(lines.last)
    if(!file.canRead){
      shutdown
      return
    }
    source  = Some(io.Source.fromFile(file).getLines)
  } 
    
  /** Function to loop for getting new values to sensor. 
    */
  def loopOnce() : Unit = {
    if(path.nonEmpty && source.nonEmpty){
      val line = source.get.next
      InputPusher.handlePathValuePairs(Seq( Tuple2( path.get, TimedValue(None, line) )))
    }
  }

  def finish= {
  }
}
