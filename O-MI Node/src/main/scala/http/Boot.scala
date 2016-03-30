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
import scala.concurrent.Await
import scala.concurrent.Awaitable
import java.util.Date
import java.net.InetSocketAddress
import scala.collection.JavaConversions.asJavaIterable

import agentSystem._
import responses.{RequestHandler, SubscriptionHandler}
import types.Path
import types.OdfTypes._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import database._

import scala.util.{Try, Failure, Success}
import xml._

import scala.language.postfixOps

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

  val subHandlerDbConn: DB = new DatabaseConnection
  val subHandler = system.actorOf(Props(new SubscriptionHandler()(subHandlerDbConn)), "subscription-handler")
  

  /**
   * This is called in [[init]]. Create input pusher actor for handling agent input.
   * @param dbConnection Use a specific db connection for all agents, intended for testing
   */
  def initInputPusher(dbConnection: DB = new DatabaseConnection, actorname: String = "input-db-pusher") = {
    InputPusher.ipdb = system.actorOf(Props(new DBPusher(dbConnection, subHandler)), actorname)
  }

  /** 
   * Setup database and apply config [[settings]].
   *
   * @param dbConnection Use a specific db connection for one-time db actions, intended for testing
   */
  def init(dbConnection: DB = new DatabaseConnection): Unit = {
    // Create input pusher actor
    initInputPusher(dbConnection)

    // Save settings as sensors values
    saveSettingsOdf()
  }

  def saveSettingsOdf() = {
    if (settings.settingsOdfPath.nonEmpty) {
      // Same timestamp for all OdfValues of the settings
      val date = new Date();
      val currentTime = new java.sql.Timestamp(date.getTime)

      // Save settings in db, this works also as a test for writing
      val numDescription =
        "Number of latest values (per sensor) that will be saved to the DB"
      system.log.info(s"$numDescription: ${settings.numLatestValues}")
      system.log.info("Testing InputPusher...")
      val dataSaveTest = InputPusher.handleInfoItems(Iterable(
        OdfInfoItem(
          Path(settings.settingsOdfPath + "num-latest-values-stored"), 
          Iterable(OdfValue(settings.numLatestValues.toString, "xs:integer", currentTime)),
          Some(OdfDescription(numDescription))
        )
      ), new Timeout(60, SECONDS))

      Await.result(dataSaveTest, 60 seconds) match {
        case Success(true) => system.log.info("O-MI InputPusher system working.")
        case Success(false) => system.log.error("O-MI InputPusher system returned false; problem with saving data")
        case Failure(e) => system.log.error(e, "O-MI InputPusher system not working; exception:")
      }
    }
  }


  /**
   * Start as stand-alone server.
   * Creates single Actors.
   * Binds to configured external agent interface port.
   *
   * @return O-MI Service actor which is not yet bound to the configured http port
   */
  def start(dbConnection: DB = new DatabaseConnection): ActorRef = {

    // create and start sensor data listener
    // TODO: Maybe refactor to an internal agent!
    val sensorDataListener = system.actorOf(Props(classOf[ExternalAgentListener]), "agent-listener")

    val agentLoader = system.actorOf(
      InternalAgentLoader.props(),
      "agent-loader"
    )

    val requestHandler = new RequestHandler(subHandler)(dbConnection)

    val omiNodeCLIListener =system.actorOf(
      Props(new OmiNodeCLIListener(  agentLoader, subHandler, requestHandler)),
      "omi-node-cli-listener"
    )

    // create omi service actor
    val omiService = system.actorOf(Props(
      new OmiServiceActor(
        requestHandler
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
object Boot extends Starter {// with App{
  def main(args: Array[String]) = {
  Try {
    init()
    val serviceActor = start()
    bindHttp(serviceActor)
  } match {
    case Failure(ex) => system.log.error(ex, "Error during startup")
    case Success(_) => system.log.info("Process exited normally")
  }
  }

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
