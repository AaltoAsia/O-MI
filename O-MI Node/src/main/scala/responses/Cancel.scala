package responses

import Common._
import parsing.Types._
import database._
import scala.xml._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

object OMICancel {

  def OMICancelResponse(request: Cancel): NodeSeq = {

    var omi_ttl = request.ttl
    var requestIds = request.requestId

    val response =
      <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl={ omi_ttl }>
        <omi:response>
          {
            var node = xml.NodeSeq.Empty
            for (idSeq <- requestIds) {
              for (id <- idSeq) {
                var result =
                  <omi:result msgformat="odf">
                    {
                      try {
                        // TODO: update somehow SubscriptionHandlerActor's internal memory
                        if (SQLite.removeSub(id.toInt)) {
                          <omi:return returnCode="200"></omi:return>
                        } else {
                          // No subscriptions found
                          <omi:return returnCode="404"></omi:return>
                        }
                      } catch {
                        case n: NumberFormatException =>
                          // Invalid ID
                          <omi:return returnCode="404"></omi:return>
                      }
                    }
                    <omi:requestId>{ id }</omi:requestId>
                    <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
                      <Objects>
                        <Object>
                          <id>CancelTest</id>
                        </Object>
                      </Objects>
                    </omi:msg>
                  </omi:result>
                node ++= result
              }
            }
            node
          }
        </omi:response>
      </omi:omiEnvelope>

    response
  }
}
