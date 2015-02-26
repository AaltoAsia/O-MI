package responses

import parsing.Types._
import xml._

/**
 * Helpers and common response parts for all responses. See the types of these functions
 * for documentation.
 */
object Common {
  def omiEnvelope(ttl: Int)(innerxml: NodeSeq): NodeSeq = {
    <omi:omiEnvelope xmlns:omi="omi.xsd"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd
        omi.xsd" version="1.0" ttl={ ttl.toString }>
      { innerxml }
    </omi:omiEnvelope>
  }

  def omiEnvelope: NodeSeq => NodeSeq = omiEnvelope(0)


  def omiResponse(ttl: Int)(innerxml: NodeSeq) = {
    omiEnvelope(ttl)(
      <omi:response>
        { innerxml }
      </omi:response>
    )
  }

  def omiResponse: NodeSeq => NodeSeq = omiResponse(0)

  def omiOdfResult(innerxml: NodeSeq): NodeSeq = omiResponse(
      <omi:result msgformat="odf">
        { innerxml }
      </omi:result>
    )
  def omiResult(innerxml: NodeSeq): NodeSeq = omiResponse(
      <omi:result>
        { innerxml }
      </omi:result>
    )


  def returnCodeWrapper(code: Int): Elem =
    <omi:return returnCode={ code.toString }></omi:return>

  /**
   * NOTE: Contains implementation specific "error" attribute
   * that can have a more detailed error message.
   */
  def returnCodeWrapper(code: Int, error: String): Elem =
    <omi:return returnCode={ code.toString } error={ error }>
    </omi:return>

  val returnCode200: Elem = returnCodeWrapper(200)

  def odfmsgWrapper(innerxml: NodeSeq): NodeSeq = {
    <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
        { innerxml }
    </omi:msg>
  }

  def genErrorResponse(parseError: ParseError): xml.NodeSeq = {
    omiResponse(
      returnCodeWrapper(400, parseError.toString)
    )
  }

  def genErrorResponse(parseErrors: Iterable[ParseError]): xml.NodeSeq = {
    omiResponse(
      returnCodeWrapper(400, parseErrors.mkString(", "))
    )
  }
}

