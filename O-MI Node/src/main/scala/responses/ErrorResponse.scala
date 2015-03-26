package responses

import Common._
import parsing._
import parsing.Types._
import xml._

object ErrorResponse {
  
  def parseErrorResponse(parseError: ParseError): NodeSeq =
    parseErrorResponse(Seq(parseError))
    
  def parseErrorResponse(parseErrors: Iterable[ParseError]): NodeSeq = {
    omiResult(
      returnCode(400, parseErrors.mkString(", ")))
  }

  def ttlTimeOut = {
    omiResult(
      returnCode(500, "Could not produce response with in ttl."))
  }
  
  def notImplemented = {
    omiResult(
      returnCode(501, "Not implemented."))
  }
  
  def intervalNotSupported: xml.NodeSeq = {
    omiResult {
      returnCode(501, "Interval not supported")
    }
  }
}
