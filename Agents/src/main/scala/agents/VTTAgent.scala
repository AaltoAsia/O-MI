package agents

import agentSystem._
import types._
import types.Path._
import types.OdfTypes._
import parsing.OdfParser

import java.io.File
import scala.io.Source

import java.sql.Timestamp
import scala.util.Random

// Scala XML
import scala.xml._

import scala.concurrent.duration._
import akka.util.Timeout
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }
import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}


/** Agent for the VTT
  * 
  */
class VTTAgent extends InternalAgent {
  
  private var odfInfoItems : Option[Iterable[(OdfInfoItem, String)]] = None   
  private var initialized = false
  val t : FiniteDuration = Duration(5, SECONDS) 
  
  override def init(configPath : String) : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath was empty or didn't exist, VTTAgent shutting down.")
    } else {
      val lines = scala.io.Source.fromFile(configPath).getLines().toSeq
      if(lines.isEmpty){
        InternalAgent.log.warning("Config file was empty, VTTAgent shutting down.")
      } else {
        val file =  new File(lines.head)
        if(!file.exists() || !file.canRead){
          InternalAgent.log.warning("File "+ lines.head + " doesn't exist or can't be read, VTTAgent shutting down.")
        } else {
          val xml = XML.loadFile(file)
          OdfParser.parse( xml) match {
            case Left( errors ) =>
              InternalAgent.log.warning("Odf has errors, VTTAgent shutting down.")
              InternalAgent.log.warning("VTT: "+errors.mkString("\n"))
            case Right(odfObjects) =>
              odfInfoItems = Some(
                getLeafs(odfObjects).collect{
                  case infoItem : OdfInfoItem if infoItem.values.nonEmpty =>
                   (
                     infoItem,
                     infoItem.values.headOption.map( _.value.toString ).getOrElse("")
                   ) 
                 }
              )
          }
        }
      }
      initialized = true
    }
    odfInfoItems match {
      case None =>
        InternalAgent.log.warning("Odf was empty, VTTAgent shutting down.")
      case Some(infoItems) =>
        /*InputPusher.handlePathMetaDataPairs( 
          odfInfoItems.getOrElse(Iterable.empty).collect{ 
            case ((info @ OdfInfoItem(_,_,_, Some(metaData)), firstValue:String )) => 
              (info.path, metaData.data)
          },
          new Timeout(t)
        )*/
    }
  }
  
  private def isNotK1Building(name : Option[String] ) = name match { 
    case Some("K1 Building") => false 
    case None => false
    case _ => true
  } 
  private def date = new java.util.Date()
  override def run(): Unit = {
    try{
    while( !Thread.interrupted && initialized ){
      odfInfoItems = odfInfoItems.map{ 
        infoItems =>
        infoItems.collect{ 
          case (info: OdfInfoItem, firstValue:String ) if info.path.nonEmpty && isNotK1Building(info.path.lastOption ) =>
          val newVal = info.path.lastOption match {
            case Some( name ) => 
            info.values.lastOption match {
              case Some(oldVal) =>
              genValue(name, oldVal.value.toDouble)
              case None => 
              -1000.0
            }
            case None => -1000.0
          }
          (
            OdfInfoItem( 
              info.path,
              Iterable( 
                OdfValue(
                  newVal.toString,
                  "xs:double",
                  Some( new Timestamp( date.getTime) ) 
                )
              )
            ),
            firstValue
          )
        } 
      }

      InternalAgent.log.info("VTTAgent pushed data to DB.")
      InputPusher.handleInfoItems(
        odfInfoItems.getOrElse(Seq.empty).map{ case (info, _) => info}, 
          new Timeout(t)
      )
      Thread.sleep(60000)
    }
    }catch{
      case e : InterruptedException =>
      InternalAgent.log.warning("VTTAgent has been interrupted.");
      InternalAgent.loader ! AgentInterruption(this,e) 
      case e : Exception =>
      InternalAgent.log.warning("VTTAgent has caught an exception.");
      InternalAgent.loader ! AgentException(this,e) 
    }finally{
      InternalAgent.log.warning("VTTAgent has died.");
    }
  }

  private def genValue(sensorType: String, oldval: Double ) : String = {
    val newval = (sensorType match {
      case "temperature" => between( 18, oldval + Random.nextGaussian * 0.3, 26)
      case "light" => between(100, oldval + Random.nextGaussian, 2500)
      case "co2" => between(400, oldval + 20 * Random.nextGaussian, 1200)
      case "humidity" => between(40, oldval + Random.nextGaussian, 60)
      case "pir" => Random.nextInt % 2
    })
    f"$newval%.1f".replace(',', '.')
  }
  private def between( begin: Double, value: Double, end: Double ) : Double = {
    (begin <= value, value <= end) match {
      case (false, true) =>
        begin
      case (true, true) =>
        value
      case (true, false) =>
        end
      case (false, false) =>
        Double.NaN
    }
  }
}
