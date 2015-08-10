package responses

import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.scalaxb
import types.OdfTypes.OdfObjects
import scala.xml.{XML, Node}
import scala.language.existentials
import OmiGenerator.{omiResult, omiReturn, odfMsg}

/** Object containing helper mehtods for generating RequestResultTypes. Used to generate results  for requests.
  *
  **/
object Result{
  private[this] val scope = scalaxb.toScope(
    None -> "",
    Some("omi") -> "omi.xsd",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  def internalError(msg: String = "Internal error") : RequestResultType = simple( "500", Some(msg) )
  def notImplemented : RequestResultType = simple( "501", Some("Not implemented") )
  def unauthorized : RequestResultType = simple( "401", Some("Unauthorized") )
  def notFound: RequestResultType = simple( "404", Some("Such item/s not found.") )
  def notFoundSub: RequestResultType = simple( "404", Some("A subscription with this id has expired or doesn't exist"))
  def notFoundSub(requestID: String): RequestResultType =
    omiResult(
      omiReturn( "404", Some("A subscription with this id has expired or doesn't exist")),
      Some(requestID)
    )
    
  def success : RequestResultType = simple( "200", None)
  def invalidRequest(msg: String = ""): RequestResultType = simple( "400", Some("Bad request: " + msg) )


  /**  for normal O-MI Read request.
    *
    * @param objects objects contains O-DF data read from database
    * @return  containing the O-DF data. 
    **/
  def read( objects: OdfObjects) : RequestResultType =  odf( "200", None, None, objects)

  /**  for poll request, O-MI Read request with requestID.
    *
    * @param requestID  requestID of subscription
    * @param objects objects contains O-DF data read from database
    * @return  containing the requestID and the O-DF data. 
    **/
  def poll( requestID: String, objects: OdfObjects) : RequestResultType =
    odf( "200", None, Some(requestID), objects)

  /**  for interval Subscription to use when automatily sending responses to callback address.
    *
    * @param requestID  requestID of subscription
    * @param objects objects contains O-DF data read from database
    * @return  containing the requestID and the O-DF data. 
    **/
  def subData( requestID: String, objects: OdfObjects) : RequestResultType =
    odf( "200", None, Some(requestID), objects)  
    
  /**  for subscripton request, O-MI Read request with interval.
    *
    * @param requestID  requestID of created subscription
    * @return  containing the requestID 
    **/
  def subscription( requestID: String): RequestResultType ={
    omiResult(
      omiReturn(
        "200",
        Some("Successfully started subscription")
      ),
      Some(requestID)
    )
  }

  /**  containing O-DF data.
    *
    **/
  def odf( returnCode: String, returnDescription: Option[String], requestID: Option[String], objects: OdfObjects): RequestResultType  = {
    omiResult(
      omiReturn(
        returnCode,
        returnDescription
      ),
      requestID,
      Some("odf"),
      Some( odfMsg( scalaxb.toXML[ObjectsType]( objects.asObjectsType , None, Some("Objects"), scope ) ) ) 
    )
  }

  /** Simple result containing only status code and optional description.
    *
    **/
  def simple(code: String, description: Option[String] ) : RequestResultType = {
    omiResult(
      omiReturn(
        code,
        description
      )
    )
  }

}
