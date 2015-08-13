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

import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }


/** Agent for the SmartHouse
  * 
  */
class SmartHouseAgent extends InternalAgent {
  
  private var odfInfoItems : Option[Iterable[(OdfInfoItem, String)]] = None   
  private var initialized = false
  
  override def init(configPath : String) : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath was empty or didn't exist, SmartHouseAgent shutting down.")
       
        return;     
    } else {
      val lines = scala.io.Source.fromFile(configPath).getLines().toSeq
      if(lines.isEmpty){
        InternalAgent.log.warning("Config file was empty, SmartHouseAgent shutting down.")
        return;     
         
      } else {
        val file =  new File(lines.head)
        if(!file.exists() || !file.canRead){
          InternalAgent.log.warning("File "+ lines.head + " doesn't exist or can't be read, SmartHouseAgent shutting down.")
           
        } else {
          val xml = XML.loadFile(file)
          OdfParser.parse( xml) match {
            case Left( errors ) =>
              InternalAgent.log.warning("Odf has errors, SmartHouseAgent shutting down.")
              InternalAgent.log.warning("SmartHouse: "+errors.mkString("\n"))
               
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
            case _ => // not possible?
          }
        }
      }
    }
    odfInfoItems match {
      case None =>
        InternalAgent.log.warning("Odf was empty, SmartHouseAgent shutting down.")
        return;     
      case Some(infoItems) =>
        InputPusher.handlePathMetaDataPairs( 
          odfInfoItems.getOrElse(Iterable.empty).collect{ 
            case ((info @ OdfInfoItem(_,_,_, Some(metaData)), firstValue:String )) => 
              (info.path, metaData.data)
          }
        )
    }
    initialized = true
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
            OdfInfoItem( info.path, Iterable( OdfValue(  newVal.toString, "xs:double" , Some( new Timestamp( date.getTime) ) ))),
            firstValue
            )
          } 
        }
        InternalAgent.log.info("SmartHouseAgent pushed data to DB.")
        InputPusher.handleInfoItems(
          odfInfoItems.getOrElse(Seq.empty).map{ case (info, _) => info} 
        )
        Thread.sleep(10000)
      }
    }catch{
      case e : InterruptedException =>
      InternalAgent.log.warning("SmartHouseAgent has been interrupted.");
      InternalAgent.loader ! ThreadException(this,e) 
    }finally{
      InternalAgent.log.warning("SmartHouseAgent has died.");
    }
  }

}
