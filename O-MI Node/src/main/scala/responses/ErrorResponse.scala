package responses

import Common._
import parsing.Types._
import xml._

/**
 * Contains some premade error responses for different error cases
 */
object ErrorResponse {

  /**
   * Helper method for the other parseErrorResponse method,
   * creates sequence of ParseErrors
   * 
   * @param parseError parse error to be made into Seq and passed to parseErrorResponse
   * @return omiResult message with the error response
   */
  def parseErrorResponse(parseError: ParseError): NodeSeq =
    parseErrorResponse(Seq(parseError))
    
    /**
     * Method that generates error response from multiple ParseErrors
     * 
     * @param parseErrors sequence of parse errors to handle
     * @return omiResult with the ParseErrors
     */
  def parseErrorResponse(parseErrors: Iterable[ParseError]): NodeSeq =
    omiResult(
      returnCode(400, parseErrors.mkString(", "))
    )


  val notImplemented =
    omiResult(
      returnCode(501, "Not implemented.")
    )
  
  val intervalNotSupported =
    resultWrapper {
      returnCode(501, "Interval not supported")
    }

  val ttlTimeOut =
    resultWrapper(
      returnCode(500, "TTL timeout, consider increasing TTL or is the server overloaded?")
    )

    /**
     * Generates omiresult from throwable error
     * 
     * @param e Throwable to be converted
     * @return omiresult with the Throwable error 
     */
  def internalError(e: Throwable) =
    resultWrapper(
      returnCode(501, "Internal server error: " + e.getMessage())
    )

}
