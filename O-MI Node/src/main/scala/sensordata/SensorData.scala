import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import akka.pattern.ask
import scala.language.postfixOps

/* JSON4s */
import org.json4s._
import org.json4s.native.JsonMethods._

// Akka Actor system
import akka.actor.ActorSystem

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

// Database
import database._

// Need to wrap in a package to get application supervisor actor
// "you need to provide exactly one argument: the class of the application supervisor actor"
package sensordata {
  /**
   * The main program for getting SensorData
   */
  class SensorData(uri : String) {
    // Used to inform that database might be busy
    var loading = false

    import scala.concurrent.ExecutionContext.Implicits.global
    // bring the actor system in scope
    implicit val system = ActorSystem()
    // Define formats
    implicit val formats = DefaultFormats

    implicit val timeout = akka.util.Timeout(10 seconds)

    def httpRef = IO(Http) //If problems change to def

    def queueSensors(): Unit = {
      // Set loading to true, 
      loading = true

      system.log.info("Queuing for new sensor data from: " + uri)


      // send GET request with absolute URI (http://121.78.237.160:2100/)
      val futureResponse: Future[HttpResponse] =
        (httpRef ? HttpRequest(GET, Uri(uri))).mapTo[HttpResponse]

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
          //        

          system.log.info("Sensors Added to Database!")

          // Schedule for new future in 5 minutes
          //TEST: 1 minute
          akka.pattern.after(300 seconds, using = system.scheduler)(Future { queueSensors() })
          loading = false

        case Failure(error) =>
          loading = false
          system.log.error("An error has occured: " + error.getMessage)
      }
    }

    /**
     * Generate ODF from the parsed & formatted Json data
     * @param list of sensor-value pairs
     * @return generated XML Node
     */
    private def addToDatabase(list: List[(String, String)]): Unit = {
      // Define dateformat for dateTime value
      val date = new java.util.Date()
      var i = 0

      if (!list.isEmpty) {
        // InfoItems filtered out
        SQLite.setMany(list.filter(_._1.split('_').length > 3).map(item => {
          val sensor: String = item._1
          val value: String = item._2 // Currently as string, convert to double?
          // Split name from underlines
          val split = sensor.split('_')

          // Object id
          val objectId: String = split(0) + "_" + split(1) + "_" + split.last
          val infoItemName: String = split.drop(2).dropRight(1).mkString("_")

          ("Objects/" + objectId + "/" + infoItemName, value)
        }))
      }
    }
  }
} 