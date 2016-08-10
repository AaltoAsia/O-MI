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

import org.slf4j.LoggerFactory

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
import responses.{RequestHandler, SubscriptionManager, CallbackHandler}
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import types.Path
import OmiServer._
import akka.stream.{ActorMaterializer, Materializer}

class OmiServer extends OmiNodeContext {

  // we need an ActorSystem to host our application in
  implicit val system : ActorSystem = ActorSystem("on-core") 
  /**
   * Settings loaded by akka (typesafe config) and our [[OmiConfigExtension]]
   */
  implicit val settings : OmiConfigExtension = OmiConfig(system)
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  import system.dispatcher // execution context for futures
  implicit val dbConnection: DBReadWrite = new DatabaseConnection()(this)
  val singleStores = new SingleStores()(settings)
  val callbackHandler: CallbackHandler = new CallbackHandler()( this )
  override val subscriptionManager = system.actorOf(SubscriptionManager.props()(this), "subscription-handler")

  override val agentSystem = system.actorOf(
    AgentSystem.props()(this),
    "agent-system"
  )

  override val requestHandler : RequestHandler = new RequestHandler()(this)

  override val cliListener =system.actorOf(
    Props(new OmiNodeCLIListener()(this)),
    "omi-node-cli-listener"
  )
  saveSettingsOdf(this)


  // create omi service actor
  val omiService = new OmiServiceImpl()(this)


  implicit val timeoutForBind = Timeout(5.seconds)
  def bindTCP() : Unit= {
    IO(Tcp)  ? Tcp.Bind(cliListener,
      new InetSocketAddress("localhost", settings.cliPort))
  }

  /** Start a new HTTP server on configured port with our service actor as the handler.
   */
  def bindHTTP(): Unit = {

    implicit val timeoutForBind = Timeout(5.seconds)
    implicit val materializer = ActorMaterializer()

    val bindingFuture =
      Http().bindAndHandle(omiService.myRoute, settings.interface, settings.webclientPort)

    bindingFuture.onFailure {
      case ex: Exception =>
        system.log.error(ex, "Failed to bind to {}:{}!", settings.interface, settings.webclientPort)

    }
  }

}
/**
 * Initialize functionality with [[Starter.init]] and then start standalone app with [[Starter.start]],
 * seperated for testing purposes and for easier implementation of different starting methods (standalone, servlet)
 */
object OmiServer {
  def apply() : OmiServer = {
    
    new OmiServer()
  }

  def saveSettingsOdf(implicit nodeContext: OmiNodeContext) :Unit = {
    import nodeContext.{settings, system,dbConnection, agentSystem}
    if ( settings.settingsOdfPath.nonEmpty ) {
      import system.dispatcher // execution context for futures
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
      
      val write = WriteRequest( objects, None,  60  seconds)
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
}



/**
 * Starting point of the stand-alone program.
 */
object Boot /*extends Starter */{// with App{
  val log = LoggerFactory.getLogger("OmiServiceTest")

  def main(args: Array[String]) : Unit= {
    Try{
      val server: OmiServer = OmiServer()
      server.bindTCP()
      server.bindHTTP()
    }match {
      case Failure(ex)  =>  log.error( "Error during startup", ex)
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
