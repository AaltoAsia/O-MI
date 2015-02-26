package responses

import Common._
import parsing.Types._
import database._
import scala.xml._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

object OmiCancel {

  def OMICancelResponse(request: Cancel): NodeSeq = {
    omiResult{
      for (id <- request.requestId) {
       // TODO: Send ids to database for canceling; if database returns true append success message, 
       // if false append error message? also update somehow
       // SubscriptionHandlerActor's internal memory
      }
      // Success: <omi:return returnCode="200"></omi:return>
      // Error: <omi:return returnCode="404"></omi:return>
      ???
    }
  }
}
