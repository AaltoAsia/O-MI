package agents

import agentSystem._
import types._
import types.OdfTypes._
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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }

import types.Path._

// Scala XML
import scala.xml
import scala.xml._

// Mutable map for sensordata
import scala.collection.mutable.Map

import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable}

/** Agent for the korean server's JSON data
  * It is now deprecated as the server is not online any more.
  */
class SensorAgent(configPath : String) extends InternalAgent(configPath) {
  // Used to inform that database might be busy
  private var uri : Option[Uri] = None
  // bring the actor system in scope
  // Define formats
  implicit val system =  ActorSystem("Sensor-Agent")
  implicit val timeout = akka.util.Timeout(10 seconds)
  
  override def init() : Unit = {
    Try {
      val configFile = new File(configPath)

      if (configPath.isEmpty || !configFile.exists()){
        throw new RuntimeException("ConfigPath's file didn't exist. Shutting down.")
      }

      if (!configFile.canRead){
        throw new RuntimeException("ConfigPath's file couldn't be read. Shutting down.")
      }

      val lines = scala.io.Source.fromFile(configFile).getLines().toArray
      if (lines.isEmpty){
        throw new RuntimeException("ConfigPath's file was empty. Shutting down.")
      }

      Uri(lines.head)
    } match {
      case Success(res) =>
        uri = Some(res)
        
      case Failure(e) =>
        system.shutdown
        InternalAgent.log.warning(e.getMessage)
    }
  }
  def httpRef = IO(Http) //If problems change to def

  def loopOnce(): Unit = {

      // send GET request with absolute URI
      val futureResponse: Future[HttpResponse] =
        (httpRef ? HttpRequest(
          GET, uri.getOrElse(throw new RuntimeException("Missing uri in configuration")))
        ).mapTo[HttpResponse]

      // wait for Future to complete
      futureResponse onComplete {
        case Success(response) =>
          // Json data received from the server
          val json = parse(response.entity.asString)

          // List of (sensorname, value) objects
          val list = for {
            JObject(child) <- json
            JField(sensor, JString(value)) <- child
          } yield (sensor, value)
          addToDatabase(list)

        case Failure(error) =>
      }
      Thread.sleep(300000)
    }

    /**
     * Generate ODF from the parsed & formatted Json data
     * @param list of sensor-value pairs
     */
    private def addToDatabase(list: List[(String, String)]): Unit = {
      // Define dateformat for dateTime value
      val date = new java.util.Date()

      if (!list.isEmpty) {
        // InfoItems filtered out
        val data = list.filter{case (sensorName, _) => sensorName.split('_').length > 3}.map(item => {
          val sensor: String = item._1
          val value: String = item._2 // Currently as string, convert to double?
          // Split name from underlines
          val split = sensor.split('_')

          // Object id
          val path = if(split(0) == "vtt") split.dropRight(2) ++  split.takeRight(2).reverse
          else split
          OdfInfoItem(Seq("Objects") ++ path.toSeq, Seq(OdfValue(value, "", Some(new Timestamp(date.getTime)))))
        })
        InputPusher.handleInfoItems(data);
        //InputPusher.handlePathValuePairs(data);
      }
    }
    def finish = {
      system.shutdown
      InternalAgent.log.info("SensorAgent has died.")
    }
}
