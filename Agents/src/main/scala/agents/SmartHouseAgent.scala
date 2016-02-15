package agents

import java.io.File
import scala.io.Source

import java.sql.Timestamp
import scala.util.Random

// Scala XML contains also parsing package
import parsing.OdfParser
import scala.xml._

import scala.concurrent.duration._
import akka.util.Timeout
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable}

import agentSystem._
import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import types._
import types.Path._
import types.OdfTypes._


/** Agent for the SmartHouse
  * 
  */
class SmartHouseAgent extends InternalAgent {
  
  private var odfInfoItems : Option[Vector[(OdfInfoItem, String)]] = None   
  private var initialized = false
  val t : FiniteDuration = Duration(5, SECONDS) 
  
  override def init(configPath : String) : Unit = {
    if(configPath.isEmpty){
      InternalAgent.log.warning("ConfigPath was empty, SmartHouseAgent shutting down.")
    } else {
      val file =  new File(configPath)
      if(!file.exists()){
        InternalAgent.log.warning("File" + file + " doesn't exists, SmartHouseAgent shutting down.")
      } else if(!file.canRead){
        InternalAgent.log.warning("File "+ file + " can't be read, SmartHouseAgent shutting down.")     
      } else {
        val xml = XML.loadFile(file)
        OdfParser.parse( xml) match {
          case Left( errors ) =>{
            InternalAgent.log.warning("Odf has errors, SmartHouseAgent shutting down.")
            InternalAgent.log.warning("SmartHouse: "+errors.mkString("\n"))
          }
          case Right(odfObjects) => {
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
          InternalAgent.log.warning("Odf was empty, SmartHouseAgent shutting down.")
        case Some(infoItems) =>//Success. Maybe push MetaData.
          initialized = true
      }
    }
    }
  }
  
  private def date = new java.util.Date()
  override def run(): Unit = {
    try{
      while( !Thread.interrupted && initialized ){
        odfInfoItems =  odfInfoItems.map{ 
          _.map{
            case ((info: OdfInfoItem, firstValue:String )) =>
            val newVal = info.values.headOption match {
              case Some(value)  => value.value.toDouble  + firstValue.toDouble/ 10 *Random.nextGaussian
              case None => Random.nextInt
            }
            (
            OdfInfoItem( info.path, Vector( OdfValue(  newVal.toString, "xs:double" , new Timestamp( date.getTime) ))),
            firstValue
            )
          } 
        }
        InternalAgent.log.info("SmartHouseAgent pushed data to DB.")
        InputPusher.handleInfoItems(
          odfInfoItems.getOrElse(Seq.empty).map{ case (info, _) => info},
          new Timeout(t)
        )
        Thread.sleep(10000)
      }
    }catch{
      case e : InterruptedException =>
      InternalAgent.log.warning("SmartHouseAgent has been interrupted.");
      InternalAgent.loader ! AgentInterruption(this,e) 
      case e : Exception =>
      InternalAgent.log.warning("SmartHouseAgent has caught an exception.");
      InternalAgent.loader ! AgentException(this,e) 
    }finally{
      InternalAgent.log.warning("SmartHouseAgent has died.");
    }
  }

}
