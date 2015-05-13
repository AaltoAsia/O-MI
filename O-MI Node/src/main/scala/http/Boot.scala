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

/**
 * Initialize functionality, seperated for testing purposes
 */
object Starter {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-core")

  val settings = Settings(system)
  
  /**
   * Setup database and apply config parameters
   */
  def init(): Unit = {
    database.setHistoryLength(settings.numLatestValues)

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

    val dbobject = new SQLiteConnection

    // Save settings as sensors
    system.log.info(s"Number of latest values (per sensor) that will be saved to the DB: ${settings.numLatestValues}")
    dbobject.set(new DBSensor(
      Path(settings.settingsOdfPath + "num-latest-values-stored"), settings.numLatestValues.toString, testTime))

    InputPusher.ipdb = system.actorOf(Props(new DBPusher(new SQLiteConnection)),"input-pusher-for-db")
    // Fill subs
    responses.OMISubscription.fillSubQueue()(dbobject)
    // Clean old pollable subs
    responses.OMISubscription.checkSubs()(dbobject)
  }

  /**
   * Start as stand-alone server.
   * Creates single Actors.
   * Binds to configured O-MI API port and agent interface port.
   */
  def start(): Unit = {
    val subHandler = system.actorOf(Props(classOf[SubscriptionHandler]), "subscription-handler")

    // create and start our service actor
    val omiService = system.actorOf(Props(new OmiServiceActor(subHandler)), "omi-service")

    // TODO: FIXME: Move to an optional agent module
    // create and start sensor data listener

    val sensorDataListener = system.actorOf(Props(classOf[ExternalAgentListener]), "agent-listener")

    val agentLoader = system.actorOf(InternalAgentLoader.props() , "agent-loader")
    // send config update to (re)load agents
    agentLoader ! ConfigUpdated


    implicit val timeout = Timeout(5.seconds)

    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ? Http.Bind(omiService, interface = settings.interface, port = settings.port)
    IO(Tcp)  ? Tcp.Bind(sensorDataListener,
      new InetSocketAddress("localhost", settings.agentPort))
  }
}

/**
 * Starting point of the stand-alone program
 */
object Boot extends App {
  Starter.init()
  Starter.start()
}
