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

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult // implicit route2HandlerFlow
//import akka.http.WebBoot
//import akka.http.javadsl.ServerBinding

import database._
import agentSystem._
import responses.{RequestHandler, SubscriptionManager}
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import types.Path

/**
 * Initialize functionality with [[Starter.init]] and then start standalone app with [[Starter.start]],
 * seperated for testing purposes and for easier implementation of different starting methods (standalone, servlet)
 */
trait Starter {
  // we need an ActorSystem to host our application in
  implicit val system : ActorSystem = ActorSystem("on-core")
  /**
   * Settings loaded by akka (typesafe config) and our [[OmiConfigExtension]]
   */
  val settings = Settings(system)

  val subManager = system.actorOf(SubscriptionManager.props(), "subscription-handler")
  

  import scala.concurrent.ExecutionContext.Implicits.global
  def saveSettingsOdf(agentSystem: ActorRef) :Unit = {
    if ( settings.settingsOdfPath.nonEmpty ) {
      // Same timestamp for all OdfValues of the settings
      val date = new Date()
      val currentTime = new java.sql.Timestamp(date.getTime)

      // Save settings in db, this works also as a test for writing
      val numDescription =
        "Number of latest values (per sensor) that will be saved to the DB"
      system.log.info(s"$numDescription: ${settings.numLatestValues}")

      database.changeHistoryLength(settings.numLatestValues)

      system.log.info("Testing InputPusher...")

      val objects = createAncestors(
        OdfInfoItem(
          Path(settings.settingsOdfPath + "num-latest-values-stored"), 
          Iterable(OdfValue(settings.numLatestValues.toString, "xs:integer", currentTime)),
          Some(OdfDescription(numDescription))
        ))
      
      val write = WriteRequest( 60  seconds, objects)
      implicit val timeout = Timeout( 60 seconds)
      val future : Future[ResponsibleAgentResponse]= (agentSystem ? ResponsibilityRequest( "InitializationTest", write )).mapTo[ResponsibleAgentResponse]
      future.onSuccess{
        case _=>
        system.log.info("O-MI InputPusher system working.")
      }

      future.onFailure{
        case e: Throwable => system.log.error(e, "O-MI InputPusher system not working; exception:")
      }
      Await.result(future, 60 seconds)
    }
  }


  /**
   * Start as stand-alone server.
   * Creates single Actors.
   * Binds to configured external agent interface port.
   *
   * @return O-MI Service actor which is not yet bound to the configured http port
   */
  def start(dbConnection: DB = new DatabaseConnection): OmiServiceImpl = {

    // create and start sensor data listener
    // TODO: Maybe refactor to an internal agent!

    val agentManager = system.actorOf(
      AgentSystem.props(dbConnection, subManager),
      "agent-system"
    )

    saveSettingsOdf(agentManager)
    val requestHandler = new RequestHandler(subManager, agentManager)(dbConnection)

    val omiNodeCLIListener =system.actorOf(
      Props(new OmiNodeCLIListener(  agentManager, subManager, requestHandler)),
      "omi-node-cli-listener"
    )

    // create omi service actor
    val omiService = new OmiServiceImpl(requestHandler)


    implicit val timeoutForBind = Timeout(5.seconds)

    IO(Tcp)  ? Tcp.Bind(omiNodeCLIListener,
      new InetSocketAddress("localhost", settings.cliPort))

    omiService
  }



  /** Start a new HTTP server on configured port with our service actor as the handler.
   */
  def bindHttp(service: OmiServiceImpl): Unit = {

    implicit val timeoutForBind = Timeout(5.seconds)
    implicit val materializer = ActorMaterializer()

    val bindingFuture =
      Http().bindAndHandle(service.myRoute, settings.interface, settings.webclientPort)

    bindingFuture.onFailure {
      case ex: Exception =>
        system.log.error(ex, "Failed to bind to {}:{}!", settings.interface, settings.webclientPort)

    }
  }
}



/**
 * Starting point of the stand-alone program.
 */
object Boot extends Starter {// with App{
  def main(args: Array[String]) : Unit= {
  system.log.info(this.getClass.toString)
  Try {
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
//class ServletBoot extends Starter with WebBoot {
//  override implicit val system = Boot.system
//  val serviceActor = start()
//  // bindHttp is not called
//}
