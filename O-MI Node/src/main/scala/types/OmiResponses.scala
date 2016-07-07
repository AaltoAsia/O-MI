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
  case class Success( requestID: Option[Long] = None, objects : Option[OdfObjects] = None, description: Option[String] = None, ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.Success(requestID,objects,description))
  }
  case class NotImplemented( ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotImplemented())
  }
  case class Unauthorized( ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.Unauthorized())
  }
  case class InvalidRequest(msg: String = "", ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InvalidRequest(msg))
  }
  case class InvalidCallback(callbackAddr: String, ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InvalidCallback(callbackAddr))
  }
  case class NotFoundPaths( paths: Vector[Path], ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotFoundPaths(paths))
  }

  case class NotFoundRequestIDs( requestIDs: Vector[Long], ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotFoundRequestIDs(requestIDs))
  }
  case class ParseErrors( errors: Vector[ParseError], ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.ParseErrors(errors))
  }

  case class InternalError( message: String, ttl: Duration = 10.seconds ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InternalError(message))
  }
  object InternalError{
    def apply(e: Throwable, ttl: Duration ): InternalError = InternalError(e.getMessage(),ttl)
    def apply(e: Throwable ): InternalError = InternalError(e.getMessage(),10.seconds)
  }

  case class TimeOutError(message: String = "", ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.TimeOutError(message))
  } 
  case class Poll( requestID: Long, objects: OdfObjects, ttl: Duration = 10.seconds) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.Poll(requestID,objects))
  }
}
