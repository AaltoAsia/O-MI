package http

import akka.actor.{ActorSystem, Props}
import akka.io.{IO, Tcp}
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.util.Date;
import java.text.SimpleDateFormat;
import java.net.InetSocketAddress

import sensorDataStructure.{SensorMap,SensorData}
import agentSystemInterface.AgentListener
import responses._
import parsing._
import database._

object Boot extends App {

  // Create our in-memory sensor database

  //val sensormap: SensorMap = new SensorMap("")

  //sensormap.set("Objects", new SensorMap("Objects"))
  SQLite.addObjects("Objects/Refrigerator123")
  SQLite.addObjects("Objects/RoomSensors1")
  SQLite.addObjects("Objects/Roomsensors1/Temperature")

  val date = new Date();
  val testtime = new java.sql.Timestamp(date.getTime)
  val testData = Map(
        "Objects/Refrigerator123/PowerConsumption" -> "0.123",
        "Objects/Refrigerator123/RefrigeratorDoorOpenWarning" -> "door closed",
        "Objects/Refrigerator123/RefrigeratorProbeFault" -> "Nothing wrong with probe",
        "Objects/RoomSensors1/Temperature/Inside" -> "21.2",
        "Objects/RoomSensors1/CarbonDioxide" -> "too much",
        "Objects/RoomSensors1/Temperature/Outside" -> "12.2"
    )

  for ((path, value) <- testData){
      SQLite.set(new DBSensor(path, value,new java.sql.Timestamp(date.getTime) ))
  }


  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-core")

  // create and start our service actor
  val omiService = system.actorOf(Props(classOf[OmiServiceActor]), "omi-service")

  // create and start sensor data listener
  val sensorDataListener = system.actorOf(Props(classOf[AgentListener]), "agent-listener")

  implicit val timeout = Timeout(5.seconds)

  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(omiService, interface = "0.0.0.0", port = 8080)
  IO(Tcp)  ? Tcp.Bind(sensorDataListener, new InetSocketAddress("localhost", 8181))
}
