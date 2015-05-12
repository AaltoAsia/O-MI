package agents

import agentSystem._
import parsing.Types._
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
  
  private var odf : Option[Seq[OdfInfoItem]] = None   
  private var odfFile : Option[String] = None   
  
  def getSensors(o:Seq[OdfObject]) : Seq[OdfInfoItem] = {
    o.flatten{ o =>
      o.sensors ++ getSensors(o.childs)
    }
  }
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      shutdown
      return
    }
    val lines = scala.io.Source.fromFile(configPath).getLines().toArray
    if(lines.isEmpty){
      shutdown
      return
    }
    odfFile = Some(lines.head)
    val tmp = OdfParser.parse( XML.loadFile(odfFile.get).toString)
    val errors = getErrors(tmp)
    if(errors.nonEmpty) {
      shutdown
      return
    }
    odf = Some(
      getObjects(tmp).flatten{o =>
        o.sensors ++ getSensors(o.childs)
      }
    )
    if(odf.isEmpty){
      shutdown
      return
    }
    InputPusher.handlePathMetaDataPairs( 
      odf.get.filter{info => info.metadata.nonEmpty }.map{
        info  => (info.path, info.metadata.get.data)
      }
    )
  }
  
  def loopOnce(): Unit = {
    val date = new java.util.Date()
    odf = Some( 
      odf.get.map{ info => 
        OdfInfoItem( info.path, Seq( TimedValue( Some( new Timestamp( date.getTime)), Random.nextDouble.toString)))
      } 
    )
    InputPusher.handleInfoItems(odf.get)
    Thread.sleep(10000)
  }

  def finish(): Unit = {
  }
}
