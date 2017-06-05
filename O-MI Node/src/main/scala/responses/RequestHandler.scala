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


package responses

import scala.util.{Success, Failure }
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.xml.NodeSeq

import akka.actor.{ActorSystem, ActorRef, Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import akka.event.{LogSource, Logging, LoggingAdapter}

import analytics.AnalyticsStore
import database._
import types.OmiTypes._
import http.{ActorSystemContext, Actors, OmiConfigExtension }
import http.ContextConversion._
import scala.language.implicitConversions
import CallbackHandler._
import agentSystem.AgentResponsibilities
import agentSystem.AgentResponsibilities._
import agentSystem.AgentName
import agentSystem.AgentEvents._

object RequestHandler{
  def props(
    subscriptionManager : ActorRef,
    dbHandler : ActorRef,
    settings: OmiConfigExtension,
    analyticsStore: Option[ActorRef]
    ) = Props( 
      new RequestHandler(
        subscriptionManager,
        dbHandler,
        settings,
        analyticsStore
      )
    )

}

class RequestHandler(
  protected val subscriptionManager : ActorRef,
  protected val dbHandler : ActorRef,
  protected val settings: OmiConfigExtension,
  protected val analyticsStore: Option[ActorRef]
) extends Actor with ActorLogging
with SubscriptionHandler
with PollHandler
with CancelHandler
{
  import context.dispatcher
  case class AgentInformation( agentName: AgentName, running: Boolean, actorRef: ActorRef)
  private val agentResponsibilities: AgentResponsibilities = new AgentResponsibilities()
  private val agents: MutableMap[AgentName,AgentInformation] = MutableMap.empty
  def receive = {
    case read: ReadRequest => respond( handleReadRequest( read ))
    case write: WriteRequest => respond( handleWriteRequest( write ))
    case call: CallRequest => respond( handleCallRequest( call ))
    case response: ResponseRequest => respond(handleResponse(response)) 
    case omiRequest: OmiRequest => respond(handleNonOdfRequest( omiRequest ))
    case na: NewAgent => addAgent(na)
    case na: AgentStopped => agentStopped(na.agentName)
  }

  def handleReadRequest( read: ReadRequest) : Future[ResponseRequest] = {
    implicit val to = Timeout(read.handleTTL)
    log.debug("ReqeustHandler sending read to DBHandler.")
    val responseFuture = (dbHandler ? read).mapTo[ResponseRequest] 
    responseFuture.onComplete{
      case Failure(t) =>
        log.debug(s"ReqeustHandler failed to receive response from DBHandler: $t")
      case Success(response) =>
        log.debug(s"ReqeustHandler received successfully response from DBHandler.")
    }
    responseFuture
  }

  def handleWriteRequest( write: WriteRequest) : Future[ResponseRequest] = {
    log.info(s"ReqeustHandler handling OdfRequest...")
    val responsibleToRequest = agentResponsibilities.splitRequestToResponsible( write )
    val fSeq = Future.sequence(
      responsibleToRequest.map{
        case (None, subrequest) =>  
          implicit val to = Timeout(subrequest.handleTTL)
          log.info(s"Asking DBHandler to handle request parts that are not owned by an Agent.")
          (dbHandler ? subrequest).mapTo[ResponseRequest]
        case (Some(agentName), subrequest) => 
          log.info(s"Asking responsible Agent $agentName to handle part of request.")
          askAgent(agentName,subrequest)
          }.map{
            case future: Future[ResponseRequest] =>
              future.recover{
                case e: Exception =>
                  log.error( e,"DBHandler returned exception")
                  Responses.InternalError(e)
              }
          }
          )
    fSeq.map{
      case responses =>
        val results = responses.flatMap{
          case response => response.results
        }
        ResponseRequest(
          Results.unionReduce(results.toVector)
        )
    }

  }
  def handleCallRequest( call: CallRequest) : Future[ResponseRequest] = {
    log.info(s"ReqeustHandler handling OdfRequest...")
    val responsibleToRequest = agentResponsibilities.splitRequestToResponsible( call )
    val fSeq = Future.sequence(
      responsibleToRequest.map{
        case (None, subrequest) =>  
          //log.debug( "Call for serviceless path", subrequest.odf.asXML.toString)
          Future.successful{
            Responses.NotFound(
              "Call request for path that do not have responsible agent for service.")

          }
        case (Some(agentName), subrequest) => 
          log.info(s"Asking responsible Agent $agentName to handle part of request.")
          askAgent(agentName,subrequest)
          }.map{
            case future: Future[ResponseRequest] =>
              future.recover{
                case e: Exception =>
                  Responses.InternalError(e)
              }
          }
          )
    fSeq.map{
      case responses =>
        val results = responses.flatMap{
          case response => response.results
        }
        ResponseRequest(
          Results.unionReduce(results.toVector)
        )
    }

  }

  def handleResponse( response: ResponseRequest ): Future[ResponseRequest] ={
    handleNonOdfRequest(response.odfResultsToSingleWrite)
  }
  def handleNonOdfRequest( omiRequest: OmiRequest): Future[ResponseRequest] = {
    omiRequest match {
      case subscription: SubscriptionRequest => handleSubscription(subscription)
      case poll: PollRequest => handlePoll(poll)
      case cancel: CancelRequest => handleCancel(cancel)
    }
  }

  private def addAgent( newAgent: NewAgent) = {
    agentResponsibilities.add(newAgent.responsibilities)
    agents += (newAgent.agentName -> AgentInformation( newAgent.agentName, true, newAgent.actorRef))
  }

  private def agentStopped( agentName: AgentName ) ={
    agents -= agentName
    agentResponsibilities.removeAgent(agentName)
  }

  private def askAgent( agentName: AgentName, request: OmiRequest ): Future[ResponseRequest]={
    agents.get(agentName) match {
      case Some( ai: AgentInformation) if ai.running  =>  
        implicit val to = Timeout(request.handleTTL)
        val f = ai.actorRef ? ( request.withSenderInformation( ActorSenderInformation( self.path.name, self) ) )
        f.mapTo[ResponseRequest]

      case Some( ai: AgentInformation) if !ai.running  =>  
        Future.successful(
          ResponseRequest(
            Vector(
              Results.NotFound(
                Some(
                  "Not found responsible agent. Agent not running."
                )
              )
            )
          )
        )

      case None => 
        Future.successful(
          ResponseRequest(
            Vector(
              Results.NotFound(
                Some(
                  "Not found responsible agent. Agent missing from request handler."
                )
              )
            )
          )
        )
    } 
  }
  private def respond( futureResponse: Future[ResponseRequest] ) = {
    val senderRef = sender()
    futureResponse.recover{
      case e: Exception =>
        Responses.InternalError(e)
        }.map{
          response => 
            senderRef ! response
        }
  }
}
