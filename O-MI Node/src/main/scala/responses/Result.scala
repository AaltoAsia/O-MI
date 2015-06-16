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

object Result{
  import OmiGenerator._
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
  def success : RequestResultType = simpleResult( "200", None)

  def readResult( objects: OdfObjects) : RequestResultType =  odfResult( "200", None, None, objects)

  def pollResult( requestId: String, objects: OdfObjects) : RequestResultType =
    odfResult( "200", None, Some(requestId), objects)

  def subDataResult( requestId: String, objects: OdfObjects) : RequestResultType =
    odfResult( "200", None, Some(requestId), objects)  
    
  def subscriptionResult( requestId: String): RequestResultType ={
    omiResult(
      omiReturn(
        "200",
        Some("Successfully started subcription")
      ),
      Some(requestId)
    )
  }

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

  def simpleResult(code: String, description: Option[String] ) : RequestResultType = {
    omiResult(
      omiReturn(
        code,
        description
      )
    )
  }

}
