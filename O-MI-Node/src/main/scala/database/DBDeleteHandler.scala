package database
import agentSystem.AgentResponsibilities
import akka.actor.ActorRef
import responses.{CLIHelperT, CallbackHandler}
import types.OmiTypes.{DeleteRequest, ResponseRequest, Results}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait DBDeleteHandler extends DBHandlerBase {

  def handleDelete(delete: DeleteRequest): Future[ResponseRequest] = {
    val leafs = delete.odf.getLeafPaths.toSeq
    val removeFuture = removeHandler.handlePathRemove(leafs)
    removeFuture.map(res => ResponseRequest(Vector(Results.Success(description = Some(s"Successfully deleted ${res.sum} items")))))
  }
}
