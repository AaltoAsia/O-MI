package types
package OmiTypes

import scala.concurrent.duration._
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}
import OmiTypes._

object Responses{
  def Success(
    objects : Option[OdfObjects], 
    ttl: Duration
    ) : ResponseRequest =ResponseRequest(
      OdfTreeCollection(
        Results.Success(
          odf = objects
        )
      ),
      ttl
    )
  def Success(
    requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty[RequestID], 
    objects : Option[OdfObjects] = None, 
    description: Option[String] = None,
    ttl: Duration = 10.seconds
    ) : ResponseRequest =ResponseRequest(
      OdfTreeCollection(
        Results.Success(
          requestIDs,
          objects,
          description
        )
      ),
      ttl
    )

  def NotImplemented( ttl: Duration = 10.seconds) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.NotImplemented()),
    ttl
  )
  def Unauthorized( ttl: Duration = 10.seconds) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.Unauthorized()),
    ttl
  )
  def InvalidRequest(msg: Option[String] = None, ttl: Duration = 10.seconds) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.InvalidRequest(msg)),
    ttl
  )
  def InvalidCallback(callbackAddr: Callback, reason: Option[String] =None, ttl: Duration = 10.seconds ) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.InvalidCallback(callbackAddr,reason)),
    ttl
  )
  def NotFoundPaths( objects: OdfObjects, ttl: Duration = 10.seconds ) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.NotFoundPaths(objects)),
    ttl
  )

  def NoResponse() : ResponseRequest = new ResponseRequest(OdfTreeCollection.empty, 0.seconds){
    override val asXML = xml.NodeSeq.Empty
    override val asOmiEnvelope: parsing.xmlGen.xmlTypes.OmiEnvelopeType =
      throw new AssertionError("This request is not an omiEnvelope")
  }

  def NotFound( description: String ) : ResponseRequest = NotFound( Some( description) )
  def NotFound( description: String, ttl: Duration ) : ResponseRequest = NotFound( Some( description),ttl )
  def NotFound( description: Option[String], ttl: Duration = 10.seconds ) : ResponseRequest = ResponseRequest(
    OdfTreeCollection(Results.NotFound(description)),
    ttl
  )

  def NotFoundRequestIDs( requestIDs: Vector[RequestID], ttl: Duration = 10.seconds ) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.NotFoundRequestIDs(requestIDs)),
    ttl
  )
  def ParseErrors( errors: Vector[ParseError], ttl: Duration = 10.seconds ) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.ParseErrors(errors)),
    ttl
  )

  def InternalError( message: Option[String] = None, ttl: Duration = 10.seconds ) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.InternalError(message)),
    ttl
  )
  def InternalError(e: Throwable, ttl: Duration): ResponseRequest = this.InternalError(Some(e.getMessage()),ttl)
  def InternalError(e: Throwable): ResponseRequest= this.InternalError(Some(e.getMessage()),10.seconds)

  def TTLTimeout(message: Option[String] = None, ttl: Duration = 10.seconds) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(Results.TTLTimeout(message)),
    ttl
  )
  def Poll( requestID: RequestID, objects: OdfObjects, ttl: Duration = 10.seconds) : ResponseRequest =ResponseRequest(
    OdfTreeCollection(
      Results.Poll(
        requestID,
        objects
      )
    ),
    ttl
  )
}
