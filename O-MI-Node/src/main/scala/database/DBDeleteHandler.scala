package database

import types.OmiTypes.{DeleteRequest, ResponseRequest, Results}

import scala.concurrent.Future

trait DBDeleteHandler extends DBHandlerBase {

  import context.dispatcher

  def handleDelete(delete: DeleteRequest): Future[ResponseRequest] = {
    val leafs = delete.odf.getLeafPaths.toSeq
    val removeFuture = removeHandler.handlePathRemove(leafs)
    removeFuture
      .map(res => ResponseRequest(Vector(Results
        .Success(description = Some(s"Successfully deleted ${res.sum} items")))))
  }
}
