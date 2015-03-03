package http

import akka.actor.{ActorSystem, Props}
import akka.io.{IO, Tcp}
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.util.Date
import java.text.SimpleDateFormat;
import java.net.InetSocketAddress

import agentSystemInterface.AgentListener
import responses._
import parsing.Types._
import parsing.Types.Path._
import database.SQLite
import database._

import sensordata.SensorData

// Initialize functionality seperated for testing purposes
object Starter {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-core")

  val settings = Settings(system)
  
  def init(): Unit = {
    SQLite.setHistoryLength(settings.numLatestValues)

    // Create test data
    val date = new Date();
    val testTime = new java.sql.Timestamp(date.getTime)
    val sensorData = new SensorData("http://zanagi.herokuapp.com/sensors/")
    sensorData.queueSensors()
    /*
    val testData = Map(
          "Objects/Refrigerator123/PowerConsumption" -> "0.123",
          "Objects/Refrigerator123/RefrigeratorDoorOpenWarning" -> "door closed",
          "Objects/Refrigerator123/RefrigeratorProbeFault" -> "Nothing wrong with probe",
          "Objects/RoomSensors1/Temperature/Inside" -> "21.2",
          "Objects/RoomSensors1/CarbonDioxide" -> "too much",
          "Objects/RoomSensors1/Temperature/Outside" -> "12.2"
      )

    for ((path, value) <- testData){
        SQLite.set(new DBSensor(path, value, testTime))
    }  */

    //sensorData.queueSensors()

    
    system.log.info(s"Number of latest values (per sensor) that will be saved to the DB: ${settings.numLatestValues}")
    SQLite.set(new DBSensor(
      Path(settings.settingsOdfPath + "num-latest-values-stored"), settings.numLatestValues.toString, testTime))

  }

  def start(): Unit = {
    val subHandler = system.actorOf(Props(classOf[SubscriptionHandlerActor]), "subscription-handler")

    // create and start our service actor
    val omiService = system.actorOf(Props(new OmiServiceActor(subHandler)), "omi-service")

    // create and start sensor data listener
    val sensorDataListener = system.actorOf(Props(classOf[AgentListener]), "agent-listener")

    implicit val timeout = Timeout(5.seconds)

    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ? Http.Bind(omiService, interface = settings.interface, port = settings.port)
    IO(Tcp)  ? Tcp.Bind(sensorDataListener,
      new InetSocketAddress("localhost", settings.agentPort))
  }
}

// MAIN
object Boot extends App {
  Starter.init()
  Starter.start()
}
