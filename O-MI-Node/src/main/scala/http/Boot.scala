/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package http

import java.net.InetSocketAddress
import java.util.Date

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.{Http, HttpExt}
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import responses.CLIHelper

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
//import akka.http.WebBoot
//import akka.http.javadsl.ServerBinding
//

import agentSystem._
import database._
import influxDB._
import http.OmiServer._
import responses.{CallbackHandler, RequestHandler, SubscriptionManager}
import types.omi.Returns.ReturnTypes._
import types.omi._
import types.odf._

class OmiServer extends OmiNode {


  // we need an ActorSystem to host our application in
  implicit val system: ActorSystem = ActorSystem("on-core")
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system) // execution context for future

  /**
    * Settings loaded by akka (typesafe config) and our [[OmiConfigExtension]]
    */
  val settings: OmiConfigExtension = OmiConfig(system)
  val metricsReporter = system.actorOf(MetricsReporter.props(settings),"metric-reporter")

  val singleStores = SingleStores(settings)
  val dbConnection: DB = settings.databaseImplementation.toUpperCase match {
    case "SLICK" => new DatabaseConnection()(
      system,
      singleStores,
      settings
    )
    case "INFLUXDB" => new InfluxDBImplementation(
      settings
    )(system, singleStores)
    case "WARP10" => ???

    case "NONE" => 
      new StubDB(singleStores, system, settings)
    case str: String =>
      throw new Exception(s"Unknown omi-service.database parameter. Should be one of slick, influxdb, warp10 or none. Was $str")
  }
  /*
    val dbConnection: DB = new influxdb.InfluxDBImplementation(
      InfluxDB(system)
      )(
      system,
      singleStores
    )*/

  val callbackHandler: CallbackHandler = new CallbackHandler(settings, singleStores)(system, materializer)
  // val analytics: Option[ActorRef] =
  //   if(settings.enableAnalytics)
  //     Some(
  //       system.actorOf(AnalyticsStore.props(
  //         singleStores,
  //       settings

  //       )
  //     )
  //     )
  //   else None

  val dbHandler: ActorRef = system.actorOf(
    DBHandler.props(
      dbConnection,
      singleStores,
      callbackHandler,
      new CLIHelper(singleStores, dbConnection)
    )(settings),
    "database-handler"
  )

  val subscriptionManager: ActorRef = system.actorOf(
    SubscriptionManager.props(
      settings,
      singleStores,
      callbackHandler
    ),
    "subscription-handler"
  )


  val tempRequestInfoStore: ActorRef = system.actorOf( TemporaryRequestInfoStore.props )
  val requestHandler: ActorRef = system.actorOf(
    RequestHandler.props(
      singleStores,
      subscriptionManager,
      dbHandler,
      settings,
      tempRequestInfoStore,
    ),
    "request-handler"
  )

  val agentSystem: ActorRef = system.actorOf(
    AgentSystem.props(
      dbHandler,
      requestHandler,
      settings
    ),
    "agent-system"
  )

  val cliListener: ActorRef = system.actorOf(
    Props(
      new OmiNodeCLIListener(
        system,
        agentSystem,
        subscriptionManager,
        singleStores,
        dbConnection
      )),
    "omi-node-cli-listener"
  )

  saveSettingsOdf(system, requestHandler, settings)

  implicit val httpExt: HttpExt = Http()
  // create omi service actor
  val omiService = new OmiServiceImpl(
    system,
    materializer,
    subscriptionManager,
    settings,
    singleStores,
    requestHandler,
    callbackHandler,
    tempRequestInfoStore,
    metricsReporter
  )


  implicit val timeoutForBind: Timeout = Timeout(5.seconds)

}

trait OmiNode {
  implicit def system: ActorSystem

  implicit def materializer: ActorMaterializer

  def requestHandler: ActorRef

  def omiService: OmiService

  def settings: OmiConfigExtension

  def cliListener: ActorRef

  implicit def httpExt: HttpExt

  implicit val timeoutForBind: Timeout

  def bindTCP(): Unit= {
    IO(Tcp)  ? Tcp.Bind(cliListener,
      new InetSocketAddress("localhost", settings.cliPort))
  }

  /** Start a new HTTP server on configured port with our service actor as the handler.
    */
  def bindHTTP()(implicit ec: ExecutionContext): Future[ServerBinding] = {

    val bindingFuture =
      httpExt.bindAndHandle(omiService.myRoute, settings.interface, settings.webclientPort)
    bindingFuture.failed.foreach {
      case ex: Exception =>
        system.log.error(ex, "Failed to bind to {}:{}!", settings.interface, settings.webclientPort)

    }
    bindingFuture
  }

  def shutdown(): Future[akka.actor.Terminated] = {
    val f = system.terminate()
    f
  }

}

object OmiServer {
  def apply(): OmiServer = {

    new OmiServer()
  }

  def saveSettingsOdf(system: ActorSystem, requestHandler: ActorRef, settings: OmiConfigExtension): Unit = {
    if (settings.settingsOdfPath.nonEmpty) {
      import system.dispatcher// execution context for futures
      // Same timestamp for all OdfValues of the settings
      val date = new Date()
      val currentTime = new java.sql.Timestamp(date.getTime)

      // Save settings in db, this works also as a test for writing
      val numDescription =
        "Number of latest values (per sensor) that will be saved to the DB"
      system.log.info(s"$numDescription: ${settings.numLatestValues}")

      database.changeHistoryLength(settings.numLatestValues)

      system.log.info("Testing InputPusher...")

      system.log.info("Create testing object")
      val name = "num-latest-values-stored"
      val odf = ImmutableODF(Vector(
        InfoItem(
          name,
          settings.settingsOdfPath / name,
          values = Vector(Value(settings.numLatestValues, "xs:integer", currentTime)),
          descriptions = Set(Description(numDescription))
        )))
      system.log.info(s"Testing object created. $odf")

      implicit val timeout: Timeout = settings.journalTimeout
      val write = WriteRequest(odf, None,settings.startTimeout)
      system.log.info("Write created")
      val future: Future[ResponseRequest] = (requestHandler ? write).mapTo[ResponseRequest]
      system.log.info("Write started")
      future.foreach {
        response: ResponseRequest =>
          Results.unionReduce(response.results).forall {
            result: OmiResult =>
              result.returnValue match {
                case _: Successful =>
                  system.log.debug("O-MI InputPusher system working.")
                  true
                case _: OmiReturn =>
                  system.log.error(s"O-MI InputPusher system not working; $response")
                  false
              }
          }
      }

      future.failed.foreach {
        e: Throwable =>
          system.log.error(e, "O-MI InputPusher system not working; exception:")
      }
      Await.result(future, settings.startTimeout)
    }
  }
}


/**
  * Starting point of the stand-alone program.
  */
object Boot /*extends Starter */ {
  // with App{
  val log: Logger = LoggerFactory.getLogger("OmiServiceTest")


  def main(args: Array[String]): Unit = {
    Try {
      val server: OmiServer = OmiServer()
      import server.system.dispatcher
      server.bindTCP()
      server.bindHTTP()
    } match {
      case Failure(ex) => log.error("Error during startup", ex)
      case Success(_) => log.info("Server started successfully")
    }
  }

}


/**
  * Starting point of the servlet program.
  */
//class ServletBoot extends Starter with WebBoot {
//  override implicit val system = Boot.system
//  val serviceActor = start()
//  // bindHttp is not called
//}
