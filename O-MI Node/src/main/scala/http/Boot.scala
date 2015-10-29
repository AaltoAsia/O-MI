/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package http

import akka.actor.{ActorSystem, Props, ActorRef}
import akka.io.{IO, Tcp}
import spray.can.Http
import spray.servlet.WebBoot
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.util.Date
import java.net.InetSocketAddress
import scala.collection.JavaConversions.asJavaIterable

import agentSystem._
import responses.{RequestHandler, SubscriptionHandler}
import types.Path
import types.OdfTypes._
import database._

import xml._

/**
 * Initialize functionality with [[Starter.init]] and then start standalone app with [[Starter.start]],
 * seperated for testing purposes and for easier implementation of different starting methods (standalone, servlet)
 */
trait Starter {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-core")

  /**
   * Settings loaded by akka (typesafe config) and our [[OmiConfigExtension]]
   */
  val settings = Settings(system)
  

  /**
   * This is called in [[init]]. Create input pusher actor for handling agent input.
   * @param dbConnection Use a specific db connection for all agents, intended for testing
   */
  def initInputPusher(dbConnection: DB = new DatabaseConnection, actorname: String = "input-pusher-for-db") = {
    InputPusher.ipdb = system.actorOf(Props(new DBPusher(dbConnection)),actorname)
  }


  /** 
   * Setup database and apply config [[settings]].
   *
   * @param dbConnection Use a specific db connection for one-time db actions, intended for testing
   */
  def init(dbConnection: DB = new DatabaseConnection): Unit = {
    database.setHistoryLength(settings.numLatestValues)

    // Create input pusher actor
    initInputPusher(dbConnection)

    // Save settings as sensors values
    saveSettingsOdf()
  }

  def saveSettingsOdf() = {
    // Same timestamp for all OdfValues of the settings
    val date = new Date();
    val currentTime = new java.sql.Timestamp(date.getTime)

    val numDescription =
      "Number of latest values (per sensor) that will be saved to the DB"
    system.log.info(s"$numDescription: ${settings.numLatestValues}")
    InputPusher.handleInfoItems(Iterable(
      OdfInfoItem(
        Path(settings.settingsOdfPath + "num-latest-values-stored"), 
        Iterable(OdfValue(settings.numLatestValues.toString, "xs:integer", Some(currentTime))),
        Some(OdfDescription(numDescription))
      )
    ))
  }


  /**
   * Start as stand-alone server.
   * Creates single Actors.
   * Binds to configured external agent interface port.
   *
   * @return O-MI Service actor which is not yet bound to the configured http port
   */
  def start(dbConnection: DB = new DatabaseConnection): ActorRef = {
    val subHandler = system.actorOf(Props(new SubscriptionHandler()(dbConnection)), "subscription-handler")

    // create and start sensor data listener
    // TODO: Maybe refactor to an internal agent!
    val sensorDataListener = system.actorOf(Props(classOf[ExternalAgentListener]), "agent-listener")

    val agentLoader = system.actorOf(
      InternalAgentLoader.props(),
      "agent-loader"
    )

    val omiNodeCLIListener =system.actorOf(
      Props(new OmiNodeCLIListener(  agentLoader, subHandler)),
      "omi-node-cli-listener"
    )

    // Latest values stored
    val latestStore = org.prevayler.PrevaylerFactory.createPrevayler(LatestValues.empty, settings.journalsDirectory)

    // create omi service actor
    val omiService = system.actorOf(Props(
      new OmiServiceActor(
        new RequestHandler(subHandler, latestStore)(dbConnection)
      )
    ), "omi-service")



    implicit val timeoutForBind = Timeout(5.seconds)

    IO(Tcp)  ? Tcp.Bind(sensorDataListener,
      new InetSocketAddress(settings.externalAgentInterface, settings.externalAgentPort))
    IO(Tcp)  ? Tcp.Bind(omiNodeCLIListener,
      new InetSocketAddress("localhost", settings.cliPort))

    return omiService
  }



  /** Start a new HTTP server on configured port with our service actor as the handler.
   */
  def bindHttp(service: ActorRef): Unit = {

    implicit val timeoutForBind = Timeout(5.seconds)

    IO(Http) ? Http.Bind(service, interface = settings.interface, port = settings.port)
  }
}



/**
 * Starting point of the stand-alone program.
 */
object Boot extends Starter with App{
//def main(args: Array[String]) = {
    init()
    val serviceActor = start()
    bindHttp(serviceActor)
  //}
}


/**
 * Starting point of the servlet program.
 */
class ServletBoot extends Starter with WebBoot {
  override implicit val system = Boot.system
  init()
  val serviceActor = start()
  // bindHttp is not called
}
