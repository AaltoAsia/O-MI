package types
package OmiTypes

import scala.concurrent.duration._
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}
import OmiTypes._

case class ResponseRequestBase(
  val results: OdfTreeCollection[OmiResult],
  val ttl: Duration = Duration.Inf
) extends ResponseRequest

object Responses{
  case class Success( requestID: Option[Long] = None, objects : Option[OdfObjects] = None, ttl: Duration = Duration.Inf ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.Success(requestID,objects))
  }
  case class NotImplemented( ttl: Duration = Duration.Inf) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotImplemented())
  }
  case class Unauthorized( ttl: Duration = Duration.Inf) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.Unauthorized())
  }
  case class InvalidRequest(msg: String = "", ttl: Duration = Duration.Inf) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InvalidRequest(msg))
  }
  case class InvalidCallback(callbackAddr: String, ttl: Duration = Duration.Inf ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InvalidCallback(callbackAddr))
  }
  case class NotFoundPaths( paths: Vector[Path], ttl: Duration = Duration.Inf ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotFoundPaths(paths))
  }

  case class NotFoundRequestIDs( requestIDs: Vector[Long], ttl: Duration = Duration.Inf ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.NotFoundRequestIDs(requestIDs))
  }
  case class ParseErrors( errors: Vector[ParseError], ttl: Duration = Duration.Inf ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.ParseErrors(errors))
  }

  case class InternalError( message: String, ttl: Duration = Duration.Inf ) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.InternalError(message))
  }
  object InternalError{
    def apply(e: Throwable, ttl: Duration ): InternalError = InternalError(e.getMessage(),ttl)
    def apply(e: Throwable ): InternalError = InternalError(e.getMessage(),Duration.Inf)
  }

  case class TimeOutError(message: String = "", ttl: Duration = Duration.Inf) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.TimeOutError(message))
  } 
  case class Poll( requestID: Long, objects: OdfObjects, ttl: Duration = Duration.Inf) extends ResponseRequest{
    override val results: OdfTreeCollection[OmiResult] = OdfTreeCollection(Results.Poll(requestID,objects))
  }
}
