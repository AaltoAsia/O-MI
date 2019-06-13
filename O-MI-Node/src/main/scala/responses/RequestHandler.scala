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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Future
import scala.util.{Failure, Success}

import database.SingleStores
import agentSystem.AgentEvents._
import agentSystem.{AgentName, AgentResponsibilities, Responsible, ResponsibleAgent, ResponsibleNode}
import http.OmiConfigExtension
import types.OmiTypes._
import types.odf.ImmutableODF
import types.Path
import http.TemporaryRequestInfoStore._
import utils._

object RequestHandler {
  def props(
             singleStores: SingleStores,
             subscriptionManager: ActorRef,
             dbHandler: ActorRef,
             settings: OmiConfigExtension,
             requestStore: ActorRef
           ): Props = Props(
    new RequestHandler(
      singleStores,
      subscriptionManager,
      dbHandler,
      settings,
      requestStore
    )
  )

}

class RequestHandler(
                      protected val singleStores: SingleStores,
                      protected val subscriptionManager: ActorRef,
                      protected val dbHandler: ActorRef,
                      protected val settings: OmiConfigExtension,
                      protected val requestStore: ActorRef
                    ) extends Actor with ActorLogging
  with SubscriptionHandler
  with PollHandler
  with CancelHandler {

  import context.dispatcher

  case class AgentInformation(agentName: AgentName, running: Boolean, actorRef: ActorRef)

  private val agentResponsibilities: AgentResponsibilities = new AgentResponsibilities(singleStores)
  private val agents: MutableMap[AgentName, AgentInformation] = MutableMap.empty

  def receive: PartialFunction[Any, Unit] = {
    case read: ReadRequest => respond(handleReadRequest(read))
    case write: WriteRequest => respond(handleWriteRequest(write))
    case delete: DeleteRequest => respond(handleDeleteRequest(delete))
    case call: CallRequest => respond(handleCallRequest(call))
    case response: ResponseRequest => respond(handleResponse(response))
    case omiRequest: OmiRequest => respond(handleNonOdfRequest(omiRequest))
    case na: NewAgent => addAgent(na)
    case na: AgentStopped => agentStopped(na.agentName)
  }

  def handleReadRequest(read: ReadRequest): Future[ResponseRequest] = {
    read.requestID.foreach{
      id =>
      requestStore ! AddInfos(id,Vector( 
        RequestStringInfo( "request-type", "read"),
        RequestStringInfo( "begin-attribute", read.begin.toString),
        RequestStringInfo( "end-attribute", read.end.toString),
        RequestStringInfo( "newest-attribute", read.newest.toString),
        RequestStringInfo( "oldest-attribute", read.oldest.toString),
        RequestStringInfo( "callback-attribute", read.callback.toString)
      ))
    } 
    val ottl: Timeout = Timeout(read.handleTTL)
    checkForNotFound(read).flatMap{
      case (Some(response),None) =>  
        Future.successful(response)
      case (notFoundResponse,Some(read)) => 
        splitAndHandle(read){
          request: OdfRequest =>
            log.debug("Handling shared paths: "+ request.odf.getLeafPaths.mkString("\n"))
            implicit val to: Timeout = Timeout(request.handleTTL)
            val responseFuture = (dbHandler ? request).mapTo[ResponseRequest]
            responseFuture.onComplete {
              case Failure(t) =>
                log.debug(s"RequestHandler failed to receive response from DBHandler: $t")
              case Success(response) =>
            }
            notFoundResponse match{
              case None => responseFuture
              case Some(nfResponse) =>
                responseFuture.map{
                  response => 
                    response.union(nfResponse)
                }
            }
            
        }
    }
  }
  def checkForNotFound(request: OdfRequest ): Future[Tuple2[Option[ResponseRequest],Option[OdfRequest]]] ={
    singleStores.getHierarchyTree().map{
      cachedOdf =>
        val notFoundPaths = request.odf.getLeafPaths.filterNot{
          path: Path =>
            cachedOdf.contains(path)
        }
        if( notFoundPaths.isEmpty ){
          (None,Some(request))
        } else if( notFoundPaths.toSet == request.odf.getLeafPaths.toSet){
          (Some(Responses.NotFoundPaths(request.odf)),None)
        } else {
          val nfOdf= ImmutableODF(notFoundPaths.flatMap{ 
            path: Path =>
              request.odf.get(path)
          }.toVector)
          (Some(Responses.NotFoundPaths(nfOdf)),Some(request.replaceOdf(request.odf.removePaths(notFoundPaths))))
        }
    }
  }
  def handleDeleteRequest( delete: DeleteRequest) : Future[ResponseRequest] = {
    checkForNotFound(delete).flatMap{
      case (Some(response),None) =>  Future.successful(response)
      case (notFoundResponse,Some(delete)) => 
        splitAndHandle(delete){
          request: OdfRequest =>
            implicit val to: Timeout = Timeout(request.handleTTL)
            log.debug(s"Asking DBHandler to handle request parts that are not owned by an Agent.")
            log.debug(s"Requested following paths: ${request.odf.getPaths.mkString("\n")}")
            val future = (dbHandler ? request).mapTo[ResponseRequest]
            notFoundResponse match{
              case None => future
              case Some(nfResponse) =>
                future.map{
                  response => 
                    response.union(nfResponse)
                }
            }
        }
    }
    
  }

  def handleWriteRequest( write: WriteRequest) : Future[ResponseRequest] = {
   splitAndHandle(write){
     request: OdfRequest =>
       implicit val to: Timeout = Timeout(request.handleTTL)
       write.requestID.foreach{
         id =>
           requestStore ! AddInfos(id,Vector( 
             RequestStringInfo( "request-type", "write"),
             RequestStringInfo( "callback-attribute", write.callback.toString)
           ))
       } 
       log.debug(s"Asking DBHandler to handle request parts that are not owned by an Agent.")
       (dbHandler ? request).mapTo[ResponseRequest]
   }
  }
  def splitAndHandle( request: OdfRequest )(f: OdfRequest => Future[ResponseRequest]): Future[ResponseRequest] ={
    val responsibleToRequestF = agentResponsibilities.splitRequestToResponsible( request )
    request.requestID.foreach{
      id =>
        requestStore ! AddInfos(id,Vector( 
          RequestStringInfo( "odf-version", request.odf.getRoot.flatMap{ obj => obj.version }.toString),
        ))
    } 
    val fSeq = responsibleToRequestF.flatMap{
      responsibleToRequest =>
        Future.sequence(
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
    }
    fSeq.map {
      responses =>
        log.debug( s"Received ${responses.size} responses: " + responses.mkString("\n"))
        val results = responses.flatMap{
          response => 
            log.debug( s"Received response with ${response.results.size} results: ")
            response.results
        }
        log.debug( s"Received ${results.size} results: " + results.mkString("\n"))
        ResponseRequest(
          Results.unionReduce(results.toVector)
        )
    }
  }

  def handleCallRequest( call: CallRequest) : Future[ResponseRequest] = {
    checkForNotFound(call).flatMap{
      case (Some(response),None) => Future.successful(response)
      case (notFoundResponse,Some(call)) => 
      splitAndHandle(call){
        request: OdfRequest =>
          val f = Future.successful{
            Responses.NotFound(
              "Call request for path that do not have responsible agent for service.")
          }
          notFoundResponse match{
            case None => f
            case Some(nfResponse) => f.map{ response => response.union(nfResponse)}
          }
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
        val responseF = f.mapTo[ResponseRequest]
        responseF

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
