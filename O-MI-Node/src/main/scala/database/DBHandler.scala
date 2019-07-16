package database

import http.OmiConfigExtension
import agentSystem.AgentEvents._
import agentSystem.{AgentName, AgentResponsibilities}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import responses.{CLIHelperT, CallbackHandler}
import types.OmiTypes._

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait DBHandlerBase extends Actor
  with ActorLogging {
  protected implicit def dbConnection: DB

  protected implicit def singleStores: SingleStores

  protected implicit def callbackHandler: CallbackHandler

  protected implicit def removeHandler(): CLIHelperT

  protected def agentResponsibilities: AgentResponsibilities
  protected implicit def settings: OmiConfigExtension
}

object DBHandler {
  def props(
             dbConnection: DB,
             singleStores: SingleStores,
             callbackHandler: CallbackHandler,
             removeHandler: CLIHelperT
            )(
             implicit settings: OmiConfigExtension
           ): Props = Props(
    new DBHandler(
      dbConnection,
      singleStores,
      callbackHandler,
      removeHandler
    )
  )
}


class DBHandler(
                 protected val dbConnection: DB,
                 protected val singleStores: SingleStores,
                 protected val callbackHandler: CallbackHandler,
                 protected val removeHandler: CLIHelperT
                )(
                 protected implicit val settings: OmiConfigExtension
               ) extends DBReadHandler
  with DBWriteHandler with DBDeleteHandler {

  import context.dispatcher

  def receive: PartialFunction[Any, Unit] = {
    case na: NewAgent => addAgent(na)
    case na: AgentStopped => agentStopped(na.agentName)
    case write: WriteRequest =>
      respond(
        permissionCheckFuture(write).flatMap {
          case false => Future.successful(permissionFailureResponse)
          case true => handleWrite(write)
        }
      )
    case read: ReadRequest =>
      respond(
        handleRead(read)
      )
    case delete: DeleteRequest =>
      respond(
        handleDelete(delete)
      )
  }

  protected val agentResponsibilities: AgentResponsibilities = new AgentResponsibilities(singleStores)

  case class AgentInformation(agentName: AgentName, running: Boolean, actorRef: ActorRef)

  private val agents: MutableMap[AgentName, AgentInformation] = MutableMap.empty

  private def respond(futureResponse: Future[ResponseRequest]) = {
    val senderRef = sender()
    futureResponse.onComplete {
      case Failure(t) =>
        log.debug(s"DBHandler failed to process read/write: $t")
      case Success(response) =>
    }
    futureResponse.recover {
      case e: Exception =>
        log.error(e, "DBHandler caught exception.")
        Responses.InternalError(e)
    }.map {
      response =>
        senderRef ! response
    }
  }

  def permissionCheck(request: OdfRequest): Boolean = {
    if (request.senderInformation.isEmpty) {
      true
    } else {
      request.senderInformation.forall {
        case ActorSenderInformation(actorName, actorRef) =>
          if (agentKnownAndRunning(actorName))
            agentResponsibilities.checkResponsibilityFor(Some(actorName), request)
          else
            agentResponsibilities.checkResponsibilityFor(None, request)
      }
    }
  }

  def permissionCheckFuture(request: OdfRequest): Future[Boolean] = {
    Future {
      permissionCheck(request)
    }
  }

  def permissionFailureResponse: ResponseRequest = ResponseRequest(
    Vector(
      Results.InvalidRequest(
        Some("Agent doesn't have permissions to access some part of O-DF.")
      )
    )
  )

  private def agentKnownAndRunning(agentName: AgentName): Boolean = agents.get(agentName).exists(_.running)

  private def addAgent(newAgent: NewAgent) = {
    agentResponsibilities.add(newAgent.responsibilities)
    agents += (newAgent.agentName -> AgentInformation(newAgent.agentName, running = true, newAgent.actorRef))
  }

  private def agentStopped(agentName: AgentName) = {
    agents -= agentName
    agentResponsibilities.removeAgent(agentName)
  }
}
