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
import scala.concurrent.{Await, Future, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import analytics.AnalyticsStore
import org.slf4j.LoggerFactory

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.{HttpExt, Http}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.RouteResult // implicit route2HandlerFlow
//import akka.http.WebBoot
//import akka.http.javadsl.ServerBinding

import database._
import agentSystem._
import responses.{RequestHandler, SubscriptionManager, CallbackHandler}
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.{OmiReturn,OmiResult,Results,WriteRequest,ResponseRequest}
import types.OmiTypes.Returns.ReturnTypes._
import types.Path
import OmiServer._
import akka.stream.{ActorMaterializer, Materializer}

class OmiServer extends OmiNode{


  // we need an ActorSystem to host our application in
  implicit val system : ActorSystem = ActorSystem("on-core") 
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  import system.dispatcher // execution context for futures

  /**
   * Settings loaded by akka (typesafe config) and our [[OmiConfigExtension]]
   */
  val settings : OmiConfigExtension = OmiConfig(system)

  val singleStores = new SingleStores(settings)
  val dbConnection: DB = new DatabaseConnection()(
    system,
    singleStores,
    settings
  )

  val callbackHandler: CallbackHandler = new CallbackHandler(settings)( system, materializer)
  val analytics: Option[ActorRef] =
    if(settings.enableAnalytics)
      Some(
        system.actorOf(AnalyticsStore.props(
          singleStores,
        settings

        )
      )
      )
    else None

  val dbHandler = system.actorOf(
   DBHandler.props(
     dbConnection,
     singleStores,
     callbackHandler,
     analytics.filter(n => settings.enableWriteAnalytics)
   ),
   "database-handler"
  )
  
  val subscriptionManager = system.actorOf(
    SubscriptionManager.props(
      settings,
      singleStores,
      callbackHandler
    ),
    "subscription-handler"
  )


  val requestHandler : ActorRef = system.actorOf(
    RequestHandler.props(
      subscriptionManager,
      dbHandler,
      settings,
      analytics.filter(r => settings.enableReadAnalytics)
    ),
    "request-handler"
  )

  val agentSystem = system.actorOf(
   AgentSystem.props(
     analytics.filter(n => settings.enableWriteAnalytics),
     dbHandler,
     requestHandler,
     settings
   ),
   "agent-system"
  )

  val cliListener =system.actorOf(
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

  saveSettingsOdf(system,requestHandler,settings)

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
    analytics
  )


  implicit val timeoutForBind: Timeout = Timeout(5.seconds)

}
trait OmiNode {
  implicit def system : ActorSystem 
  implicit def materializer: ActorMaterializer
  def requestHandler : ActorRef
  def omiService : OmiService 
  def settings : OmiConfigExtension 
  def cliListener : ActorRef

  implicit def httpExt: HttpExt

  implicit val timeoutForBind : Timeout
  def bindTCP()(implicit ec: ExecutionContext): Unit= {
    IO(Tcp)  ? Tcp.Bind(cliListener,
      new InetSocketAddress("localhost", settings.cliPort))
  }

  /** Start a new HTTP server on configured port with our service actor as the handler.
   */
  def bindHTTP()(implicit ec: ExecutionContext): Future[ServerBinding] = {

    val bindingFuture =
      httpExt.bindAndHandle(omiService.myRoute, settings.interface, settings.webclientPort)
    
    bindingFuture.onFailure {
      case ex: Exception =>
        system.log.error(ex, "Failed to bind to {}:{}!", settings.interface, settings.webclientPort)

    }
    bindingFuture
  }
  def shutdown()(implicit ec: ExecutionContext): Future[akka.actor.Terminated] = {

    system.terminate()
  }

}

object OmiServer {
  def apply() : OmiServer = {
    
    new OmiServer()
  }

  def saveSettingsOdf(system: ActorSystem, requestHandler: ActorRef, settings: OmiConfigExtension) :Unit = {
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
      val future : Future[ResponseRequest]= (requestHandler ? write ).mapTo[ResponseRequest]
      future.onSuccess{
        case response: ResponseRequest=>
        Results.unionReduce(response.results).forall{
          case result : OmiResult => result.returnValue match {
            case s: Successful => 
              system.log.info("O-MI InputPusher system working.")
              true
            case f: OmiReturn => 
              system.log.error( s"O-MI InputPusher system not working; $response")
              false
          }
        }
      }

      future.onFailure{
        case e: Throwable => 
          system.log.error(e, "O-MI InputPusher system not working; exception:")
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
      import server.system.dispatcher
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
