package responses

import parsing._
import parsing.Types._
import xml._

object ErrorResponse {
  def parseErrorResponse( errors: Seq[ParseError]) : NodeSeq = {
    <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
      <omi:response>
        <omi:result>
          <omi:return returnCode="400" description={errors.map{err => err.msg}.mkString("\n")}></omi:return>
        </omi:result>
      </omi:response>
    </omi:omiEnvelope>
  }
  def ttlTimeOut = {
    <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
      <omi:response>
        <omi:result>
          <omi:return returnCode="500" description="Could not produce response with in ttl."></omi:return>
        </omi:result>
      </omi:response>
    </omi:omiEnvelope>
  }
  def notImplemented = {
    <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
      <omi:response>
        <omi:result>
          <omi:return returnCode="501"></omi:return>
        </omi:result>
      </omi:response>
    </omi:omiEnvelope>
  }

}
