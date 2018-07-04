package types
package OmiTypes

import scala.concurrent.duration._
import types.odf._
import OmiTypes._

import scala.xml.NodeSeq

object Responses {
  def Success(
               objects: Option[ODF],
               ttl: Duration
             ): ResponseRequest = ResponseRequest(
    OdfCollection(
      Results.Success(
        odf = objects
      )
    ),
    ttl
  )

  def Success(
               requestIDs: OdfCollection[RequestID] = OdfCollection.empty[RequestID],
               objects: Option[ODF] = None,
               description: Option[String] = None,
               ttl: Duration = 10.seconds
             ): ResponseRequest = ResponseRequest(
    OdfCollection(
      Results.Success(
        requestIDs,
        objects,
        description
      )
    ),
    ttl
  )

  def NotImplemented(ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.NotImplemented()),
    ttl
  )

  def Unauthorized(ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.Unauthorized()),
    ttl
  )

  def InvalidRequest(msg: Option[String] = None, ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.InvalidRequest(msg)),
    ttl
  )

  def InvalidCallback(callbackAddr: Callback,
                      reason: Option[String] = None,
                      ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.InvalidCallback(callbackAddr, reason)),
    ttl
  )

  def NotFoundPaths(objects: ODF, ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.NotFoundPaths(objects)),
    ttl
  )

  def NoResponse(): ResponseRequest = new ResponseRequest(OdfCollection.empty, 0.seconds) {
    override val asXML: NodeSeq = xml.NodeSeq.Empty
    override val asOmiEnvelope: parsing.xmlGen.xmlTypes.OmiEnvelopeType =
      throw new AssertionError("This request is not an omiEnvelope")
  }

  def NotFound(description: String): ResponseRequest = NotFound(Some(description))

  def NotFound(description: String, ttl: Duration): ResponseRequest = NotFound(Some(description), ttl)

  def NotFound(description: Option[String], ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.NotFound(description)),
    ttl
  )

  def NotFoundRequestIDs(requestIDs: Vector[RequestID], ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.NotFoundRequestIDs(requestIDs)),
    ttl
  )

  def ParseErrors(errors: Vector[ParseError], ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.ParseErrors(errors)),
    ttl
  )

  def InternalError(message: Option[String] = None, ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.InternalError(message)),
    ttl
  )

  def InternalError(e: Throwable, ttl: Duration): ResponseRequest = this.InternalError(Some(e.getMessage()), ttl)

  def InternalError(e: Throwable): ResponseRequest = this.InternalError(Some(e.getMessage()), 10.seconds)

  def TTLTimeout(message: Option[String] = None, ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(Results.TTLTimeout(message)),
    ttl
  )

  def Poll(requestID: RequestID, objects: ODF, ttl: Duration = 10.seconds): ResponseRequest = ResponseRequest(
    OdfCollection(
      Results.Poll(
        requestID,
        objects
      )
    ),
    ttl
  )
}
