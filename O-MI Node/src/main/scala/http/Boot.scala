package http

import akka.actor.{ActorSystem, Props}
import akka.io.{IO, Tcp}
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.net.InetSocketAddress

import sensorDataStructure.SensorMap
import agentSystemInterface.AgentListener

object Boot extends App {

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // Create our in-memory sensor database
  val sensormap: SensorMap = new SensorMap("")

  // create and start our service actor
  val omiService = system.actorOf(Props(classOf[OmiServiceActor], sensormap), "omi-service")

  // create and start sensor data listener
  val sensorDataListener = system.actorOf(Props(classOf[AgentListener], sensormap))

  implicit val timeout = Timeout(5.seconds)

  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(omiService, interface = "0.0.0.0", port = 8080)
  IO(Tcp)  ! Tcp.Bind(sensorDataListener, new InetSocketAddress("localhost", 8181))
}
