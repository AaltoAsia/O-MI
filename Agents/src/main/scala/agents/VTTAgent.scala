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
    if(configPath.isEmpty){
      InternalAgent.log.warning("ConfigPath was empty, VTTAgent shutting down.")
    } else {
      val file =  new File(configPath)
      if(!file.exists()){
        InternalAgent.log.warning("File" + file + " doesn't exists, VTTAgent shutting down.")
      } else if(!file.canRead){
        InternalAgent.log.warning("File "+ file + " can't be read, VTTAgent shutting down.")     
      } else {
        val xml = XML.loadFile(file)
        OdfParser.parse( xml) match {
          case Left( errors ) =>{
            InternalAgent.log.warning("Odf has errors, VTTAgent shutting down.")
            InternalAgent.log.warning("VTT: "+errors.mkString("\n"))
          }
          case Right(odfObjects) => {
            InputPusher.handleOdf(odfObjects)
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
        case _ => // not possible?
      }
      odfInfoItems match {
        case None =>
          InternalAgent.log.warning("Odf was empty, VTTAgent shutting down.")
        case Some(infoItems) =>//Success.
          initialized = true
      }
    }
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
