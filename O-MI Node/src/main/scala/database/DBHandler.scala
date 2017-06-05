package database

import java.lang.{Iterable => JavaIterable}



import scala.collection.immutable.IndexedSeq
import scala.collection.mutable.{Map => MutableMap}
import scala.collection.immutable.Map
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import akka.actor.{Actor, ActorRef, ActorSystem, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import database._
import responses.CallbackHandler
import responses.CallbackHandler._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes._
import types.Path
import analytics.{AddWrite, AnalyticsStore}
import agentSystem.{AgentName, AgentResponsibilities}
import agentSystem.AgentEvents._

trait DBHandlerBase extends Actor 
  with ActorLogging{
  protected implicit def dbConnection: DB
  protected implicit def singleStores: SingleStores
  protected implicit def callbackHandler: CallbackHandler
  protected implicit def analyticsStore: Option[ActorRef]
  protected def agentResponsibilities: AgentResponsibilities 
}

object DBHandler{
  def props(
    dbConnection: DB,
    singleStores: SingleStores,
    callbackHandler: CallbackHandler,
    analyticsStore: Option[ActorRef]
  ) = Props(
    new DBHandler(
      dbConnection,
      singleStores,
      callbackHandler,
      analyticsStore
    )
  )
}


class DBHandler(
  protected  val dbConnection: DB,
  protected  val singleStores: SingleStores,
  protected  val callbackHandler: CallbackHandler,
  protected  val analyticsStore: Option[ActorRef]
  
  ) extends DBReadHandler
  with DBWriteHandler
{
  import context.{system, dispatcher}
  def receive = {
    case na: NewAgent => addAgent(na)
    case na: AgentStopped => agentStopped(na.agentName)
    case write: WriteRequest => 
      respond( 
        permissionCheckFuture(write).flatMap{
          case false => Future.successful( permissionFailureResponse )
          case true =>  handleWrite( write ) 
        }
      )
    case read: ReadRequest => 
      log.debug(s"DBHandler received read.")
      respond( 
        handleRead( read )
      )
  }

  protected val agentResponsibilities: AgentResponsibilities = new AgentResponsibilities()
  case class AgentInformation( agentName: AgentName, running: Boolean, actorRef: ActorRef)
  private val agents: MutableMap[AgentName,AgentInformation] = MutableMap.empty

  private def respond( futureResponse: Future[ResponseRequest] ) = {
    val senderRef = sender()
    futureResponse.onComplete{
      case Failure(t) =>
        log.debug(s"RBHandler failed to process read/write: $t")
      case Success(response) =>
        log.debug(s"DBHandler successfully processed read/write.")
    }
    futureResponse.recover{
      case e: Exception =>
        log.error(e, "DBHandler caught exception.")
        Responses.InternalError(e)
    }.map{
      response => 
        senderRef ! response
    }
  }

  def permissionCheck( request: OdfRequest)={
    if( request.senderInformation.isEmpty ){
      true
    } else {
      request.senderInformation.forall{
        case ActorSenderInformation( actorName, actorRef ) =>
          if( agentKnownAndRunning( actorName ) )
            agentResponsibilities.checkResponsibilityFor( Some(actorName), request)
          else 
            agentResponsibilities.checkResponsibilityFor( None, request)
      }
    }
  }
  def permissionCheckFuture( request: OdfRequest)= {
    Future{
      permissionCheck(request)
    }
  }
  def permissionFailureResponse = ResponseRequest(
    Vector(
      Results.InvalidRequest(
        Some("Agent doesn't have permissions to access some part of O-DF.")
      )
    )
  )
  private def agentKnownAndRunning(agentName: AgentName) : Boolean = agents.get(agentName).map(_.running).getOrElse(false)
  private def addAgent( newAgent: NewAgent) = {
    agentResponsibilities.add(newAgent.responsibilities)
    agents += (newAgent.agentName -> AgentInformation( newAgent.agentName, true, newAgent.actorRef))
  }
  private def agentStopped( agentName: AgentName ) ={
    agents -= agentName
    agentResponsibilities.removeAgent( agentName )
  }
}
