package agents

import agentSystem._
import parsing.Types._
import parsing.OdfParser
import database._

import scala.io.Source
import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
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
class SmartHouseAgent(uri : String) extends AgentActor {
  // Used to inform that database might be busy
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val system = context.system
  implicit val formats = DefaultFormats
  implicit val timeout = akka.util.Timeout(10 seconds)
  def getSensors(o:Seq[OdfObject]) : Seq[OdfInfoItem] = {
    o.flatten{ o =>
      o.sensors ++ getSensors(o.childs)
    }
  }
  private var odf = OdfParser.parse( XML.loadFile(uri).toString).filter{
    o => o.isRight
  }.map{
    o => o.right.get
  }.flatten{o =>
    o.sensors ++ getSensors(o.childs)
  }
  InputPusher.handlePathMetaDataPairs( 
    odf.filter{info => info.metadata.nonEmpty }.map{
      info  => (info.path, info.metadata.get.data)
    }
  )
  
  system.log.info("Successfully saved SmartHouse MetaData to DB")
  // bring the actor system in scope
  // Define formats
  queueSensors
  def receive = {
    case _ => 
  }
  def queueSensors(): Unit = {
    val date = new java.util.Date()
    odf = odf.map{ info => OdfInfoItem(info.path, Seq(TimedValue(Some(new Timestamp(date.getTime)),Random.nextDouble.toString)))}
    InputPusher.handleInfoItems(odf)
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

/** Class for handling configuration and creating of GenericAgent.
  *
  */
class SmartHouseBoot extends Bootable {
  private var configPath : String = ""
  private var agentActor : ActorRef = null

  /** Startup function that handles configuration and creates SensorAgent.
    * 
    * @param system ActorSystem were GenericAgent will live.
    * @param pathToConfig Path to config file.
    * @return Boolean indicating successfulnes of startup.
    */
  override def startup( system: ActorSystem, pathToConfig: String) : Boolean = { 
    if(pathToConfig.isEmpty || !(new File(pathToConfig).exists()))
      return false 

    configPath = pathToConfig
    val lines = scala.io.Source.fromFile(configPath).getLines().toArray
    var uri = lines.head
    agentActor = system.actorOf(SmartHouseAgent.props(uri), SmartHouseAgent.getClass.getName)    
    return true
  }
  override def shutdown() : Unit = {}
  /** Simple getter fucntion for SensorAgent.
    *
    * @return Sequence of ActorRef containing only ActorRef of SensorAgent.
    */
  override def getAgentActor() : Seq[ActorRef] = Seq(agentActor) 

}

