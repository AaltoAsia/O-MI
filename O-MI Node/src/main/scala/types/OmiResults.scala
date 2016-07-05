package types
package OmiTypes

import scala.concurrent.duration._
import types.OdfTypes.OdfObjects

object Results{
  case class Success(requestID: Option[Long] = None, objects: Option[OdfObjects] = None, description: Option[String] = None) extends OmiResult(Returns.Success(description),requestID.toVector, objects)
  case class NotImplemented() extends OmiResult( Returns.NotImplemented())
  case class Unauthorized() extends OmiResult( Returns.Unauthorized() )
  case class InvalidRequest(msg: String = "") extends OmiResult( Returns.InvalidRequest(msg) )
  case class InvalidCallback(callback: String ) extends OmiResult( Returns.InvalidCallback(callback))
  case class NotFoundPaths( paths: Vector[Path] ) extends OmiResult(Returns.NotFoundPaths(paths))

  case class NotFoundRequestIDs( requestIDs: Vector[Long] ) extends OmiResult(Returns.NotFoundRequestIDs(requestIDs))
  case class ParseErrors( errors: Vector[ParseError] ) extends OmiResult(Returns.ParseErrors(errors))

  object InternalError{
    def apply(e: Throwable): InternalError = InternalError(e.getMessage())
  }

  case class InternalError( message: String ) extends OmiResult(Returns.InternalError(message))
  case class TimeOutError(message: String = "") extends OmiResult(Returns.TimeOutError(message))
  case class Poll( requestID: Long, objects: OdfObjects) extends OmiResult( Returns.Success(), Vector(requestID), Some(objects))
  case class Read( objects: OdfObjects) extends OmiResult( Returns.Success(), Vector.empty, Some(objects))
  case class Subscription( requestID: Long, interval: Option[Duration] = None) extends OmiResult( 
    Returns.Success(
      interval.map{ dur => s"Successfully started subscription. Interval was set to $dur"}),
      Vector(requestID)
  )
}
