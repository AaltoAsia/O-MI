package agents

import agentSystem._
import types._
import types.OdfTypes._
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

import types.Path._

// Scala XML
import scala.xml
import scala.xml._

// Mutable map for sensordata
import scala.collection.mutable.Map
import scala.util.Random
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable


/** Agent for the VTT
  * 
  */
class VTTAgent(configPath : String) extends InternalAgent(configPath) {
  // Used to inform that database might be busy
  
  private var odfInfoItems : Option[Iterable[(OdfInfoItem, String)]] = None   
  
  def getSensors(o:Iterable[OdfObject]) : Iterable[OdfInfoItem] = {
    o.flatten{ o =>
      o.infoItems ++ getSensors(o.objects)
    }
  }
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath was empty or didn't exist, VTTAgent shutting down.")
      shutdown
      return
    }
    val lines = scala.io.Source.fromFile(configPath).getLines().toArray
    if(lines.isEmpty){
      InternalAgent.log.warning("Config file was empty, VTTAgent shutting down.")
      shutdown
      return
    }
    val file =  new File(lines.head)
    if(!file.exists() || !file.canRead){
      InternalAgent.log.warning("File "+ lines.head + " doesn't exist or can't be read, VTTAgent shutting down.")
      shutdown
      return
    }
      
    val xml = XML.loadFile(file)
    val tmp_odf = OdfParser.parse( xml)
    val errors = getErrors(tmp_odf)
    if(errors.nonEmpty) {
      InternalAgent.log.warning("Odf has errors, VTTAgent shutting down.")
      InternalAgent.log.warning("VTT: "+errors.mkString("\n"))
      shutdown
      return
    }
    odfInfoItems = Some(
      getObjects(tmp_odf).flatten{o =>
        o.infoItems ++ getSensors(o.objects)
      }.map{ info => (info, info.values.headOption.getOrElse(Random.nextInt).toString) }
    )
    if(odfInfoItems.isEmpty){
      InternalAgent.log.warning("Odf was empty, VTTAgent shutting down.")
      shutdown
      return
    }
    InputPusher.handlePathMetaDataPairs( 
      odfInfoItems.get.filter{ case ((info: OdfInfoItem, firstValue:String ))  => info.metaData.nonEmpty }.map{
        case ((info: OdfInfoItem, firstValue:String )) => (info.path, info.metaData.get.data)
      }
    )
  }
  
  def loopOnce(): Unit = {
    val date = new java.util.Date()
    odfInfoItems = Some( 
      odfInfoItems.get.map{ case ((info: OdfInfoItem, firstValue:String )) =>
        val newVal = info.values.headOption match {
          case Some(value)  => value.value.toDouble  + firstValue.toDouble/ 10 *Random.nextGaussian
          case None => Random.nextInt
        }
        (
          OdfInfoItem( info.path, Iterable( OdfValue(  newVal.toString, "" , Some( new Timestamp( date.getTime) ) ))),
          firstValue
        )
      } 
    )
    InternalAgent.log.info("VTTAgent pushed data to DB.")
    InputPusher.handleInfoItems(odfInfoItems.get.map{ case (info, _) => info})
    Thread.sleep(10000)
  }

  def finish(): Unit = {
	    println("VTTAgent has died.")
  }
}
