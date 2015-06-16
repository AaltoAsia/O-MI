package agents
import types._
import types.OdfTypes._
import agentSystem._
import xml._
import io._
import scala.concurrent.duration._
import scala.concurrent.Future
import java.io.File
import java.sql.Timestamp
import System.currentTimeMillis
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable

/** A generic agent that read standart input stream and send given valus to AgentListenr via client.
  * @param Path where sensor is.
  */
class GenericAgent( configPath: String) extends InternalAgent(configPath) {
  var fileToRead : Option[File] = None 
  var path : Option[Path] = None 
  var source : Option[Iterator[String]] = None
  
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath's file didn't exist. Shutting down.")
      shutdown
      return
    }

    val lines = io.Source.fromFile(configPath).getLines().toArray
    path = Some(lines.head.split("/").toSeq)
    var file = new File(lines.last)
    if(!file.canRead){
      InternalAgent.log.warning("ConfigPath's file couldn't be read. Shutting down.")
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
      val date = new java.util.Date()
      InputPusher.handlePathValuePairs(Seq( Tuple2( path.get, OdfValue( line, "",Some(new Timestamp(date.getTime)) ) )))
    }
  }

  def finish= {
	    println("GenericAgent has died.")
  }
}
