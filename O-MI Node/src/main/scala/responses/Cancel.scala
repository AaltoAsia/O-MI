package responses

import parsing.Types._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

object OmiCancel {

  def OMICancelResponse(requests: List[ParseMsg]): String = {

    val xml = <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
                <omi:response>
                  <omi:result>
                    {
                      var requestIds = requests.collect {
                        case Cancel(ttl: String,
                          requestId: Seq[String]) => requestId
                      }
                      for (id <- requestIds) {
                    	 // TODO: Send ids to database for canceling; if database returns true append success message, 
                    	 // if false append error message?
                      }
                      // Success: <omi:return returnCode="200"></omi:return>
                      // Error: <omi:return returnCode="404"></omi:return>
                    }
                  </omi:result>
                </omi:response>
              </omi:omiEnvelope>

    xml.toString
  }
}
