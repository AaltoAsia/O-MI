package types
package OmiTypes

import scala.concurrent.duration._
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}
import OmiTypes._

object Responses{
case class Success( requestID: Option[Long] = None, objects : Option[OdfObjects] = None, ttl: Duration = Duration.Inf ) extends ResponseRequest( OdfTreeCollection(Results.Success(requestID,objects)),ttl)
case class NotImplemented( ttl: Duration = Duration.Inf) extends ResponseRequest( OdfTreeCollection(Results.NotImplemented()),ttl)
case class Unauthorized( ttl: Duration = Duration.Inf) extends ResponseRequest( OdfTreeCollection(Results.Unauthorized()) ,ttl)
case class InvalidRequest(msg: String = "", ttl: Duration = Duration.Inf) extends ResponseRequest( OdfTreeCollection(Results.InvalidRequest(msg)),ttl )
case class InvalidCallback(callback: String, ttl: Duration = Duration.Inf ) extends ResponseRequest( OdfTreeCollection(Results.InvalidCallback(callback)),ttl)
case class NotFoundPaths( paths: Vector[Path], ttl: Duration = Duration.Inf ) extends ResponseRequest(OdfTreeCollection(Results.NotFoundPaths(paths)),ttl)

case class NotFoundRequestIDs( requestIDs: Vector[Long], ttl: Duration = Duration.Inf ) extends ResponseRequest(OdfTreeCollection(Results.NotFoundRequestIDs(requestIDs)),ttl)
case class ParseErrors( errors: Vector[ParseError], ttl: Duration = Duration.Inf ) extends ResponseRequest(OdfTreeCollection(Results.ParseErrors(errors)),ttl)

case class InternalError( message: String, ttl: Duration = Duration.Inf ) extends ResponseRequest(OdfTreeCollection(Results.InternalError(message)),ttl)
object InternalError{
  def apply(e: Throwable, ttl: Duration ): InternalError = InternalError(e.getMessage(),ttl)
  def apply(e: Throwable ): InternalError = InternalError(e.getMessage(),Duration.Inf)
}

case class TimeOutError(message: String = "", ttl: Duration = Duration.Inf) extends ResponseRequest(OdfTreeCollection(Results.TimeOutError(message)),ttl) 
case class Poll( requestID: Long, objects: OdfObjects, ttl: Duration = Duration.Inf) extends ResponseRequest( OdfTreeCollection(Results.Poll(requestID,objects)),ttl)
}
