package responses

import parsing.Types._
import xml._

/**
 * Helpers and common response parts for all responses. See the types of these functions
 * for documentation.
 */
object Common {
  /**
   * Wraps innerxml to O-MI Envelope
   */
  def omiEnvelope(ttl: Int)(innerxml: NodeSeq): NodeSeq = {
    <omi:omiEnvelope xmlns:omi="omi.xsd"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd"
	  	version="1.0" ttl={ ttl.toString }>
      { innerxml }
    </omi:omiEnvelope>
  }

  def omiEnvelope: NodeSeq => NodeSeq = omiEnvelope(0)


  /**
   * Wraps innerxml to O-MI Envelope and response
   */
  def omiResponse(ttl: Int)(innerxml: NodeSeq) = {
    omiEnvelope(ttl)(
      <omi:response>
        { innerxml }
      </omi:response>
    )
  }

  def omiResponse: NodeSeq => NodeSeq = omiResponse(0)



  def result(innerxml: NodeSeq): NodeSeq = {
    <omi:result>
      { innerxml }
    </omi:result>
  }

  /**
   * Wraps innerxml to O-MI Envelope, response and result with msgformat odf
   */
  def omiOdfResult(innerxml: NodeSeq): NodeSeq = omiResponse(
      <omi:result msgformat="odf">
        { innerxml }
      </omi:result>
    )

  /**
   * Wraps innerxml to O-MI Envelope, response and result
   */
  def omiResult(innerxml: NodeSeq): NodeSeq = omiResponse(result(innerxml))


  def returnCode(code: Int): Elem =
    <omi:return returnCode={ code.toString }></omi:return>

  /**
   * NOTE: Contains implementation specific "description" attribute
   * that can have a more detailed error message.
   */
  def returnCode(code: Int, description: String): Elem =
    <omi:return returnCode={ code.toString } description={ description }>
    </omi:return>

  val returnCode200: Elem = returnCode(200)

  def odfMsgWrapper(innerxml: NodeSeq): Elem = {
    <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
        { innerxml }
    </omi:msg>
  }

  // Should not be called directly because id is supposed to be Int
  private def requestId(id: String): Elem = {
    <omi:requestId>
      { id }
    </omi:requestId>
  }
  def requestId(id: Int): Elem = requestId(id.toString)

  def requestIds(ids: Seq[String]): NodeSeq = {
    if (ids.nonEmpty)
      ids map {requestId(_)}
    else NodeSeq.Empty
  }
}

