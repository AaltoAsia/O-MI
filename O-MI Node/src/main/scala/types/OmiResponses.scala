package types
package OmiTypes

import scala.concurrent.duration._
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}
import OmiTypes._

case class ResponseRequestBase(
  val results: OdfTreeCollection[OmiResult],
  val ttl: Duration = 10.seconds
) extends ResponseRequest

object Responses{
  case class Success(
    requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty[RequestID], 
    objects : Option[OdfObjects] = None, 
    description: Option[String] = None,
    ttl: Duration = 10.seconds
  ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(
      Results.Success(
        requestIDs,
        objects,
        description
      )
    )
  }
  case class NotImplemented( ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotImplemented())
  }
  case class Unauthorized( ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.Unauthorized())
  }
  case class InvalidRequest(msg: Option[String] = None, ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InvalidRequest(msg))
  }
  case class InvalidCallback(callbackAddr: Callback, reason: Option[String] =None, ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InvalidCallback(callbackAddr,reason))
  }
  case class NotFoundPaths( objects: OdfObjects, ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotFoundPaths(objects))
  }

  case class NoResponse() extends ResponseRequest{
    val ttl = 0.seconds
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection()
    override val asXML = xml.NodeSeq.Empty
    override val asOmiEnvelope: parsing.xmlGen.xmlTypes.OmiEnvelope =
      throw new AssertionError("This request is not an omiEnvelope")
  }

  case class NotFoundRequestIDs( requestIDs: Vector[RequestID], ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotFoundRequestIDs(requestIDs))
  }
  case class ParseErrors( errors: Vector[ParseError], ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.ParseErrors(errors))
  }

  case class InternalError( message: Option[String] = None, ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InternalError(message))
  }
  object InternalError{
    def apply(e: Throwable, ttl: Duration): InternalError = InternalError(Some(e.getMessage()),ttl)
    def apply(e: Throwable): InternalError = InternalError(Some(e.getMessage()),10.seconds)
  }

  case class TimeOutError(message: Option[String] = None, ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.TimeOutError(message))
  } 
  case class Poll( requestID: RequestID, objects: OdfObjects, ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(
      Results.Poll(
        requestID,
        objects
      )
    )
  }
}
