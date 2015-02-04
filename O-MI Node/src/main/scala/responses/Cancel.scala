package responses

import parsing._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

object OmiCancel {
  
  def OMICancelResponse(requests: List[ParseMsg]) : String = {
    val error = <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
    			  <omi:response>
    				<omi:result>
    				  <omi:return returnCode="404"></omi:return>
    				</omi:result>
    			  </omi:response>
    			</omi:omiEnvelope>
      
    val xml = <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
    			    <omi:response>
    				  <omi:result>
    				    <omi:return returnCode="200"></omi:return>
    				  </omi:result>
    			    </omi:response>
    			  </omi:omiEnvelope> 
      
    var requestIds = requests.collect {
                  case Cancel(ttl : String,
                      requestId : Seq[String]) => requestId
                }
    for(id <- requestIds){
      
    }    
    ???
  }
}