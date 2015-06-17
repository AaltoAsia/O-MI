package responses

import database._
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.scalaxb
import types.Path
import types.OdfTypes
import types.OdfTypes._
import xml.XML
import xml.Node
import scala.language.existentials
import OmiGenerator._

/** Object containing helper mehtods for generating RequestResultTypes. Used to generate results  for requests.
  *
  **/
object Result{
  private val scope = scalaxb.toScope(
    None -> "odf",
    Some("omi") -> "omi.xsd",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  def internalError(msg: String = "Internal error") : RequestResultType = simpleResult( "500", Some(msg) )
  def notImplemented : RequestResultType = simpleResult( "501", Some("Not implemented") )
  def unauthorized : RequestResultType = simpleResult( "401", Some("Unauthorized") )
  def notFound: RequestResultType = simpleResult( "404", Some("Such item/s not found.") )
  def notFoundSub: RequestResultType = simpleResult( "404", Some("A subscription with this id has expired or doesn't exist"))
  def success : RequestResultType = simpleResult( "200", None)
  def invalidRequest(msg: String = ""): RequestResultType = simpleResult( "400", Some("Bad request: " + msg) )


  /** Result for normal O-MI Read request.
    *
    * @param objects objects contains O-DF data read from database
    * @return Result containing the O-DF data. 
    **/
  def readResult( objects: OdfObjects) : RequestResultType =  odfResult( "200", None, None, objects)

  /** Result for poll request, O-MI Read request with requestId.
    *
    * @param requestId  requestId of subscription
    * @param objects objects contains O-DF data read from database
    * @return Result containing the requestId and the O-DF data. 
    **/
  def pollResult( requestId: String, objects: OdfObjects) : RequestResultType =
    odfResult( "200", None, Some(requestId), objects)

  /** Result for interval Subscription to use when automatily sending responses to callback address.
    *
    * @param requestId  requestId of subscription
    * @param objects objects contains O-DF data read from database
    * @return Result containing the requestId and the O-DF data. 
    **/
  def subDataResult( requestId: String, objects: OdfObjects) : RequestResultType =
    odfResult( "200", None, Some(requestId), objects)  
    
  /** Result for subscripton request, O-MI Read request with interval.
    *
    * @param requestId  requestId of created subscription
    * @return Result containing the requestId 
    **/
  def subscriptionResult( requestId: String): RequestResultType ={
    omiResult(
      omiReturn(
        "200",
        Some("Successfully started subcription")
      ),
      Some(requestId)
    )
  }

  /** Result containing O-DF data.
    *
    **/
  def odfResult( returnCode: String, returnDescription: Option[String], requestId: Option[String], objects: OdfObjects): RequestResultType  = {
    omiResult(
      omiReturn(
        returnCode,
        returnDescription
      ),
      requestId,
      Some("odf"),
      Some( odfMsg( scalaxb.toXML[ObjectsType]( OdfTypes.OdfObjectsAsObjectsType(objects) , Some("odf.xsd"), Some("Objects"), scope ) ) ) 
    )
  }

  /** Simple result containing only status code and optional description.
    *
    **/
  def simpleResult(code: String, description: Option[String] ) : RequestResultType = {
    omiResult(
      omiReturn(
        code,
        description
      )
    )
  }

}
