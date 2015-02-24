package responses

import parsing.Types._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

object OmiCancel {

  def OMICancelResponse(requests: List[ParseMsg]): String = {

    val response =
      <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
        <omi:response>
          {
            var node = xml.NodeSeq.Empty
            var requestIds = requests.collect {
              case Cancel(ttl: String, requestId: Seq[String]) => requestId
            }
            for (idSeq <- requestIds) {
              for (id <- idSeq) {
                var result =
                  <omi:result>
                    {
                      try {
                        if (SQLite.removeSub(id.toInt)) {
                          <omi:return returnCode="200"></omi:return>
                        } else {
                          <omi:return returnCode="404"></omi:return>
                        }
                      } catch {
                        case n: NumberFormatException =>
                          <omi:return returnCode="404"></omi:return>
                      }
                    }
                  </omi:result>
                node ++= result
              }
            }
            node
          }
        </omi:response>
      </omi:omiEnvelope>

    response.toString
  }
}
