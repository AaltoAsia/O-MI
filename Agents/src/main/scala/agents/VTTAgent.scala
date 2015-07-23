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


/** Agent for the VTT
  * 
  */
class VTTAgent(configPath : String) extends InternalAgent(configPath) {
  
  private var odfInfoItems : Option[Iterable[(OdfInfoItem, String)]] = None   
  
  override def init() : Unit = {
    if(configPath.isEmpty || !(new File(configPath).exists())){
      InternalAgent.log.warning("ConfigPath was empty or didn't exist, VTTAgent shutting down.")
      shutdown
    } else {
      val lines = scala.io.Source.fromFile(configPath).getLines().toSeq
      if(lines.isEmpty){
        InternalAgent.log.warning("Config file was empty, VTTAgent shutting down.")
        shutdown
      } else {
        val file =  new File(lines.head)
        if(!file.exists() || !file.canRead){
          InternalAgent.log.warning("File "+ lines.head + " doesn't exist or can't be read, VTTAgent shutting down.")
          shutdown
        } else {
          val xml = XML.loadFile(file)
          OdfParser.parse( xml) match {
            case Left( errors ) =>
              InternalAgent.log.warning("Odf has errors, VTTAgent shutting down.")
              InternalAgent.log.warning("VTT: "+errors.mkString("\n"))
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
          }
        }
      }
    }
    odfInfoItems match {
      case None =>
        InternalAgent.log.warning("Odf was empty, VTTAgent shutting down.")
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
    def isNotK1Building(name : Option[String] ) = name match { 
                case Some("K1 Building") => false 
                case None => false
                case _ => true
              } 
    val date = new java.util.Date()
    odfInfoItems =  odfInfoItems.map{ 
      _.collect{ 
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
          OdfInfoItem( info.path, Iterable( OdfValue(  newVal.toString, "xs:double" , Some( new Timestamp( date.getTime) ) ))),
          firstValue
        )
      } 
    }
    
    InternalAgent.log.info("VTTAgent pushed data to DB.")
    InputPusher.handleInfoItems(
      odfInfoItems.getOrElse(Seq.empty).map{ case (info, _) => info} 
    )
    Thread.sleep(60000)
  }

  def finish(): Unit = {
	    println("VTTAgent has died.")
  }
  def genValue(sensorType: String, oldval: Double ) : String = {
    val newval = (sensorType match {
      case "temperature" => between( 18, oldval + Random.nextGaussian * 0.3, 26)
      case "light" => between(100, oldval + Random.nextGaussian, 2500)
      case "co2" => between(400, oldval + 20 * Random.nextGaussian, 1200)
      case "humidity" => between(40, oldval + Random.nextGaussian, 60)
      case "pir" => between(0, oldval + 10*Random.nextGaussian, 40)
    })
    f"$newval%.1f".replace(',', '.')
  }
  def between( begin: Double, value: Double, end: Double ) : Double = {
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
