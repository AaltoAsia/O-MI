package responses

import Common._
import parsing._
import parsing.Types._
import xml._

object ErrorResponse {

  def parseErrorResponse(parseError: ParseError): NodeSeq =
    parseErrorResponse(Seq(parseError))

  def parseErrorResponse(parseErrors: Iterable[ParseError]): NodeSeq =
    omiResult(
      returnCode(400, parseErrors.mkString(", "))
    )


  val notImplemented =
    omiResult(
      returnCode(501, "Not implemented.")
    )
  
  val intervalNotSupported = 
    omiResult {
      returnCode(501, "Interval not supported")
    }

  val ttlTimeOut =
    resultWrapper(
      returnCode(500, "TTL timeout, consider increasing TTL or is the server overloaded?")
    )

  def internalError(e: Throwable) =
    resultWrapper(
      returnCode(501, "Internal server error: " + e.getMessage())
    )

}
