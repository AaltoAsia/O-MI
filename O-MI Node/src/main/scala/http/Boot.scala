package http

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.util.Date;
import java.text.SimpleDateFormat;

import sensorDataStructure._

object Boot extends App {

  // Create our in-memory sensor database
  val sensormap: SensorMap = new SensorMap("")

  sensormap.set("Objects", new SensorMap("Objects"))
  sensormap.set("Objects/Refrigerator123", new SensorMap("Objects/Refrigerator123"))

  val date = new Date();
  val formatDate = new SimpleDateFormat ("yyyy-MM-dd'T'hh:mm:ss");
  sensormap.set("Objects/Refrigerator123/PowerConsumption", new SensorData("Objects/Refrigerator123/PowerConsumption", "0.123", formatDate.format(date)))
  sensormap.set("Objects/Refrigerator123/RefrigeratorDoorOpenWarning", new SensorData("Objects/Refrigerator123/RefrigeratorDoorOpenWarning", "Nothing wrong with door", formatDate.format(date)))
  sensormap.set("Objects/Refrigerator123/RefrigeratorProbeFault", new SensorData("Objects/Refrigerator123/RefrigeratorProbeFault", "Nothing wrong with probe", formatDate.format(date)))

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  val service = system.actorOf(Props(new OmiServiceActor(sensormap)), "omi-service")

  implicit val timeout = Timeout(5.seconds)

  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 8080)
}
