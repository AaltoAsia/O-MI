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
class SmartHouseAgent(configPath : String) extends InternalAgent(configPath) {
  
  private var odfInfoItems : Option[Iterable[(OdfInfoItem, String)]] = None   
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath was empty or didn't exist, SmartHouseAgent shutting down.")
      shutdown
    } else {
      val lines = scala.io.Source.fromFile(configPath).getLines().toSeq
      if(lines.isEmpty){
        InternalAgent.log.warning("Config file was empty, SmartHouseAgent shutting down.")
        shutdown
      } else {
        val file =  new File(lines.head)
        if(!file.exists() || !file.canRead){
          InternalAgent.log.warning("File "+ lines.head + " doesn't exist or can't be read, SmartHouseAgent shutting down.")
          shutdown
        } else {
          val xml = XML.loadFile(file)
          OdfParser.parse( xml) match {
            case Left( errors ) =>
              InternalAgent.log.warning("Odf has errors, SmartHouseAgent shutting down.")
              InternalAgent.log.warning("SmartHouse: "+errors.mkString("\n"))
              shutdown
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
        shutdown
      case Some(infoItems) =>
        InputPusher.handlePathMetaDataPairs( 
          odfInfoItems.getOrElse(Iterable.empty).collect{ 
            case ((info @ OdfInfoItem(_,_,_, Some(metaData)), firstValue:String )) => 
              (info.path, metaData.data)
          }
        )
    }
  }
  
  def loopOnce(): Unit = {
    val date = new java.util.Date()
    odfInfoItems =  odfInfoItems.map{ 
      _.map{ case ((info: OdfInfoItem, firstValue:String )) =>
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

  def finish(): Unit = {
	    println("SmartHouseAgent has died.")
  }
}
