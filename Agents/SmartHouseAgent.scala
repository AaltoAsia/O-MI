package agents

import agentSystem._
import parsing.Types._
import parsing.Types.OdfTypes._
import parsing.OdfParser
import parsing.OmiParser
import database._

import scala.io.Source
import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.io.{ IO, Tcp }
import akka.util.{ByteString, Timeout}
import akka.pattern.ask
import scala.language.postfixOps

import java.sql.Timestamp
import java.io.File

/* JSON4s */
import org.json4s._
import org.json4s.native.JsonMethods._

// HTTP related imports
import spray.can.Http
import spray.http._
import HttpMethods._
import spray.client.pipelining._

// Futures related imports

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{ Success, Failure }

import parsing.Types._
import parsing.Types.Path._

// Scala XML
import scala.xml
import scala.xml._

// Mutable map for sensordata
import scala.collection.mutable.Map
import scala.util.Random


/** Agent for the SmartHouse
  * 
  */
class SmartHouseAgent(configPath : String) extends InternalAgent(configPath) {
  // Used to inform that database might be busy
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val system = context.system
  implicit val formats = DefaultFormats
  implicit val timeout = akka.util.Timeout(10 seconds)
  
  private var odf : Option[Seq[OdfInfoItem]] = None   
  private var odfFile : Option[String] = None   
  
  def getSensors(o:Seq[OdfObject]) : Seq[OdfInfoItem] = {
    o.flatten{ o =>
      o.infoItems ++ getSensors(o.objects)
    }
  }
  
  override def preStart() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      context.parent !  ActorInitializationException
      return
    }
    val lines = scala.io.Source.fromFile(configPath).getLines().toArray
    if(lines.isEmpty){
      context.parent !  ActorInitializationException
      return
    }
    odfFile = Some(lines.head)
    val tmp = OdfParser.parse( XML.loadFile(odfFile.get).toString)
    val errors = getErrors(tmp)
    if(errors.nonEmpty) {
        log.warning(errors.mkString("\n"))
        context.parent !  ActorInitializationException
        return
    }
    odf = Some(
      getObjects(tmp).flatten{o =>
        o.infoItems ++ getSensors(o.objects)
      }
    )
    if(odf.isEmpty){
      context.parent !  ActorInitializationException
      return
    }
    InputPusher.handlePathMetaDataPairs( 
      odf.get.filter{info => info.metaData.nonEmpty }.map{
        info  => (info.path, info.metaData.get.data)
      }
    )
    system.log.info("Successfully saved SmartHouse MetaData to DB")
    queueSensors
  }
  
  // bring the actor system in scope
  // Define formats
  def receive = {
    case _ => 
  }
  def queueSensors(): Unit = {
    val date = new java.util.Date()
    odf = Some( 
      odf.get.map{ info => 
        OdfInfoItem( info.path, Seq( OdfValue(  Random.nextDouble.toString, "" , Some( new Timestamp( date.getTime) ) )))
      } 
    )
    InputPusher.handleInfoItems(odf.get)
    system.log.info("Successfully saved SmartHouse data to DB.")
    akka.pattern.after(10 seconds, using = system.scheduler)(Future { queueSensors() })
  }

}

/** Helper obejct for creating SensorAgent.
  *
  */
object SmartHouseAgent {
  def apply( uri: String) : Props = props(uri)
  def props( uri: String) : Props = {Props(new SmartHouseAgent(uri)) }
}

