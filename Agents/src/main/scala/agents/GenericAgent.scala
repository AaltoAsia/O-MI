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
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable
}
/** A generic agent that read standart input stream and send given valus to AgentListenr via client.
  * @param Path where sensor is.
  */
class GenericAgent( configPath: String) extends InternalAgent(configPath) {
  private var path : Option[Path] = None 
  private var source : Option[Iterator[String]] = None
  
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath's file didn't exist. Shutting down.")
      shutdown
    } else {

      val lines = io.Source.fromFile(configPath).getLines().toArray
      lines.headOption.foreach{
        case odfpath =>
        path = Some(odfpath.split("/").toSeq)
        lines.lastOption.foreach{
          case pipeName =>
          val file = new File(pipeName)
          file.canRead match{
            case false =>
              InternalAgent.log.warning("ConfigPath's file couldn't be read. Shutting down.")
              shutdown
            case true =>
              source  = Some(io.Source.fromFile(file).getLines)
          }
        } 
      }
    }
  } 
    
  /** Function to loop for getting new values to sensor. 
    */
  def loopOnce() : Unit = {
    source.foreach{ src =>
        //Should block
        val line = src.next 

        val date = new java.util.Date()
        path.foreach{ odfpath =>
        InputPusher.handlePathValuePairs( Seq( 
          Tuple2( odfpath, OdfValue( line, "",Some(new Timestamp(date.getTime) ) ) ) ) )
      }
    }
  }

  def finish= {
	    println("GenericAgent has died.")
  }
}
