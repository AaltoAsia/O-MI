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

import agentSystem.AgentEvents._
import agentSystem.{AgentName, AgentResponsibilities, Responsible, ResponsibleAgent, ResponsibleNode}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import http.OmiConfigExtension
import types.OmiTypes._

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Future
import scala.util.{Failure, Success}
import utils._

object RequestHandler {
  def props(
             subscriptionManager: ActorRef,
             dbHandler: ActorRef,
             settings: OmiConfigExtension
           ): Props = Props(
    new RequestHandler(
      subscriptionManager,
      dbHandler,
      settings
    )
  )

}

class RequestHandler(
                      protected val subscriptionManager: ActorRef,
                      protected val dbHandler: ActorRef,
                      protected val settings: OmiConfigExtension
                    ) extends Actor with ActorLogging
  with SubscriptionHandler
  with PollHandler
  with CancelHandler {

  import context.dispatcher

  case class AgentInformation(agentName: AgentName, running: Boolean, actorRef: ActorRef)

  private val agentResponsibilities: AgentResponsibilities = new AgentResponsibilities()
  private val agents: MutableMap[AgentName, AgentInformation] = MutableMap.empty

  def receive: PartialFunction[Any, Unit] = {
    case read: ReadRequest => respond(handleReadRequest(read))
    case write: WriteRequest => respond(handleWriteRequest(write))
    case call: CallRequest => respond(handleCallRequest(call))
    case response: ResponseRequest => respond(handleResponse(response))
    case omiRequest: OmiRequest => respond(handleNonOdfRequest(omiRequest))
    case na: NewAgent => addAgent(na)
    case na: AgentStopped => agentStopped(na.agentName)
  }

  def handleReadRequest(read: ReadRequest): Future[ResponseRequest] = {
    implicit val to: Timeout = Timeout(read.handleTTL)
    val responseFuture = (dbHandler ? read).mapTo[ResponseRequest]
    responseFuture.onComplete {
      case Failure(t) =>
        log.debug(s"RequestHandler failed to receive response from DBHandler: $t")
      case Success(response) =>
    }
    responseFuture
  }
  def handleWriteRequest( delete: DeleteRequest) : Future[ResponseRequest] = {
   splitAndHandle(delete){
     request: OdfRequest =>
       implicit val to: Timeout = Timeout(request.handleTTL)
       log.debug(s"Asking DBHandler to handle request parts that are not owned by an Agent.")
       (dbHandler ? request).mapTo[ResponseRequest]
   }
    
  }

  def handleWriteRequest( write: WriteRequest) : Future[ResponseRequest] = {
    /*
    log.debug(s"RequestHandler handling write OdfRequest...")
    val responsibleToRequest = agentResponsibilities.splitRequestToResponsible(write)
    val responsesFromResponsible = responsibleToRequest.map {
      case (None, subrequest) =>
        implicit val to: Timeout = Timeout(subrequest.handleTTL)
        log.debug(s"Asking DBHandler to handle request parts that are not owned by an Agent.")
        (dbHandler ? subrequest).mapTo[ResponseRequest]
      case (Some(agentName), subrequest) =>
        log.debug(s"Asking responsible Agent $agentName to handle part of request.")
        askAgent(agentName, subrequest)
    }
    val fSeq = Future.sequence(
      responsesFromResponsible.map {
        future: Future[ResponseRequest] =>

          val recovered = future.recover {
            case e: Exception =>
              log.error(e, "DBHandler returned exception")
              Responses.InternalError(e)
          }
          recovered
      }
    )
    fSeq.map {
      responses: Iterable[ResponseRequest] =>
        val results = responses.flatMap(response => response.results)
        ResponseRequest(
          Results.unionReduce(results.toVector)
        )
    }
    */
   splitAndHandle(write){
     request: OdfRequest =>
       implicit val to: Timeout = Timeout(request.handleTTL)
       log.debug(s"Asking DBHandler to handle request parts that are not owned by an Agent.")
       (dbHandler ? request).mapTo[ResponseRequest]
   }
  }
  def splitAndHandle( request: OdfRequest )(f: OdfRequest => Future[ResponseRequest]): Future[ResponseRequest] ={
    val responsibleToRequest = agentResponsibilities.splitRequestToResponsible( request )
    val fSeq = Future.sequence(
      responsibleToRequest.map{
        case (None, subrequest) =>  
          f(subrequest)
        case (Some(responsible), subrequest: OmiRequest) => 
          askResponsible(responsible,subrequest)
      }.map{
        future: Future[ResponseRequest] =>
          future.recover {
            case e: Exception =>
              Responses.InternalError(e)
          }
      }
    )
    fSeq.map {
      responses =>
        val results = responses.flatMap(response => response.results)
        ResponseRequest(
          Results.unionReduce(results.toVector)
        )
    }
  }

  def handleCallRequest( call: CallRequest) : Future[ResponseRequest] = {
   splitAndHandle(call){
     request: OdfRequest =>
          Future.successful{
            Responses.NotFound(
              "Call request for path that do not have responsible agent for service.")
          }
   }

  }

  def handleResponse(response: ResponseRequest): Future[ResponseRequest] = {
    handleWriteRequest(response.odfResultsToSingleWrite)
  }

  def handleNonOdfRequest(omiRequest: OmiRequest): Future[ResponseRequest] = {
    omiRequest match {
      case subscription: SubscriptionRequest => handleSubscription(subscription)
      case poll: PollRequest => handlePoll(poll)
      case cancel: CancelRequest => handleCancel(cancel)
      case delete: DeleteRequest => {
        implicit val to: Timeout = Timeout(delete.handleTTL)
        (dbHandler ? delete).mapTo[ResponseRequest]
      }
      case other => {
        log.warning(s"Unexpected non-O-DF request: $other")
        Future.failed(new Exception(s"Unexpected non-O-DF request"))
      }
    }
  }

  private def addAgent(newAgent: NewAgent) = {
    agentResponsibilities.add(newAgent.responsibilities)
    agents += (newAgent.agentName -> AgentInformation(newAgent.agentName, running = true, newAgent.actorRef))
  }

  private def agentStopped(agentName: AgentName) = {
    agents -= agentName
    agentResponsibilities.removeAgent(agentName)
  }

  private def askResponsible(responsible: Responsible, request: OmiRequest): Future[ResponseRequest] = {
    responsible match {
      case ResponsibleAgent( agentName: AgentName ) => 
        askAgent( agentName, request )
      case ResponsibleNode( uri ) => 
        Future.successful{
          Responses.InternalError("Responsible external addresses not yet supported")
        }
    }
  }
  private def askAgent(agentName: AgentName, request: OmiRequest): Future[ResponseRequest] = {
    log.debug(s"Asking responsible Agent $agentName to handle part of request.")
    agents.get(agentName) match {
      case Some(ai: AgentInformation) if ai.running =>
        implicit val to: Timeout = Timeout(request.handleTTL)
        val f = ai.actorRef ? request.withSenderInformation(ActorSenderInformation(self.path.name, self))
        f.mapTo[ResponseRequest]

      case Some(ai: AgentInformation) if !ai.running =>
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

  private def respond(futureResponse: Future[ResponseRequest]) = {
    val senderRef = sender()
    futureResponse.recover {
      case e: Exception =>
        Responses.InternalError(e)
    }.map {
      response =>
        senderRef ! response
    }
  }
}
