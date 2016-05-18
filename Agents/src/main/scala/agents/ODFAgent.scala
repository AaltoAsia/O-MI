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
import akka.actor.Cancellable
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable}

import agentSystem._
//import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import types._
import types.OmiTypes.WriteRequest
import types.Path._
import types.OdfTypes._


class ODFAgent extends InternalAgent {
  
  import scala.concurrent.ExecutionContext.Implicits._
  case class Update()
	val rnd: Random = new Random()
  val interval : FiniteDuration = Duration(60, SECONDS) 
	var odf: Option[OdfObjects] = None
  def date = new java.util.Date();
  def name = self.path.name
  protected def configure(configPath: String ) : InternalAgentResponse = {
        val file =  new File(configPath)
        if( file.exists() && file.canRead() ){
        val xml = XML.loadFile(file)
        OdfParser.parse( xml) match {
          case Left( errors ) =>{
            log.warning(s"Odf has errors, $name could not be configured.")
            log.warning("ParseError: "+errors.mkString("\n"))
            CommandFailed("ParserErrer, view log.")
          }
          case Right(odfObjects) => {
            odf = Some( odfObjects )
        }
        case _ => // not possible?
      }
      CommandSuccessful("Successfully configured.")
      } else {
            log.warning(s"File $configPath did not exists or could not read it. $name could not be configured.")
            CommandFailed("Problem with config, view log.")
      }
  }
  var updateSchelude : Option[Cancellable] = None
  protected def start = {
    updateSchelude = Some(context.system.scheduler.schedule(
      Duration(0, SECONDS),
      interval,
      self,
      Update
    ))
    CommandSuccessful("Successfully started.")
  }

  def update() : Unit = {
    odf.map{
      objects =>
      val promiseResult = PromiseResult()
      val infoItems = getInfoItems(objects)

      // Collect metadata 
      val objectsWithMetaData = getOdfNodes(objects) collect {
        case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
      }   
      val updated = infoItems.map{ infoItem => 
          val newVal = infoItem.path.lastOption match {
            case Some( name ) => 
            infoItem.values.lastOption match {
              case Some(oldVal) =>
              genValue(name, oldVal.value.toDouble)
              case None => 
              -1000.0
            }
            case None => -1000.0
          }
        infoItem.copy( values =Vector(OdfValue(
          newVal.toString, 
          "xs:double",
          new Timestamp( date.getTime() )
        )))
      }
      val allNodes = updated ++ objectsWithMetaData
      val newObjects = allNodes.map(fromPath(_)).foldLeft(OdfObjects())(_.union(_))
      
      val write = WriteRequest( interval, newObjects )
      context.parent ! PromiseWrite( promiseResult, write ) 
      promiseResult.isSuccessful.onSuccess{
        //Check if failed promises
        case s =>
        log.debug(s"$name pushed data successfully.")
      }
      newObjects
    } 
  }

  receiver{
    case Update => update
  }
  protected def stop = updateSchelude match{
      case Some(job) =>
      job.cancel() 
      job.isCancelled  match {
      case true =>
        CommandSuccessful("Successfully stopped.")
      case false =>
        CommandFailed("Failed to stop agent.")
    }
    case None => CommandFailed("Failed to stop agent.")
  }
  protected def restart = {
    stop match{
      case success  : InternalAgentSuccess => start
      case error    : InternalAgentFailure => error
    }
  }
  protected def quit = {
    stop match{
      case error    : InternalAgentFailure => error
      case success  : InternalAgentSuccess => 
      sender() ! CommandSuccessful("Successfully quit.")
      context.stop(self) 
      CommandSuccessful("Successfully quit.")
    }
  }
  private def genValue(sensorType: String, oldval: Double ) : String = {
    val newval = (sensorType match {
      case "temperature" => between( 18, oldval + Random.nextGaussian * 0.3, 26)
      case "light" => between(100, oldval + Random.nextGaussian, 2500)
      case "co2" => between(400, oldval + 20 * Random.nextGaussian, 1200)
      case "humidity" => between(40, oldval + Random.nextGaussian, 60)
      case "pir" => Random.nextInt % 2
      case _ => Random.nextInt % 2
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
