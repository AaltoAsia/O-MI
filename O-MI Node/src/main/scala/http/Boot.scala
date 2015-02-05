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

import agentSystemInterface.AgentListener
import responses._
import parsing._
import database.SQLite
import database._

object Boot extends App {

  // Create our in-memory sensor database


  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-core")


  val settings = Settings(system)
  // TODO:
  system.log.info(s"Number of latest values (per sensor) that will be saved to the DB: ${settings.numLatestValues}")


  // create and start our service actor
  val omiService = system.actorOf(Props(classOf[OmiServiceActor]), "omi-service")

  // create and start sensor data listener
  val sensorDataListener = system.actorOf(Props(classOf[AgentListener]), "agent-listener")

  implicit val timeout = Timeout(5.seconds)

  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(omiService, interface = settings.interface, port = settings.port)
  IO(Tcp)  ? Tcp.Bind(sensorDataListener,
    new InetSocketAddress("localhost", settings.agentPort))
}
