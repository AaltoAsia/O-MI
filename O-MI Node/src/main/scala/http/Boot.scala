package http

import akka.actor.{ActorSystem, Props}
import akka.io.{IO, Tcp}
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.util.Date
import java.net.InetSocketAddress
import agentSystem._
import responses._
import parsing.Types._
import database._

import xml._

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

    //Peer/Usability testing
    /*
    val odf = OdfParser.parse( XML.loadFile("SmartHouse.xml").toString)
    println(odf)
    system.log.warning(odf.filter{o => o.isLeft}.map{o => o.left.get.msg}.mkString("\n"))
    InputPusher.handleObjects(odf.filter{o => o.isRight}.map{o => o.right.get})
    */
    system.log.info(s"Number of latest values (per sensor) that will be saved to the DB: ${settings.numLatestValues}")
    SQLite.set(new DBSensor(
      Path(settings.settingsOdfPath + "num-latest-values-stored"), settings.numLatestValues.toString, testTime))

  }

  def start(): Unit = {
    val subHandler = system.actorOf(Props(classOf[SubscriptionHandlerActor]), "subscription-handler")

    // create and start our service actor
    val omiService = system.actorOf(Props(new OmiServiceActor(subHandler)), "omi-service")

    // TODO: FIXME: Move to an optional agent module
    // create and start sensor data listener
    val sensorDataListener = system.actorOf(Props(classOf[AgentListener]), "agent-listener")
    val agentLoader = system.actorOf(AgentLoader.props() , "agent-loader")
    agentLoader ! ConfigUpdated  

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
