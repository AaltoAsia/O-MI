package responses

import database._
import parsing.xmlGen._
import parsing.xmlGen.scalaxb
import parsing.Types.Path
import xml.XML
import xml.Node
import scala.language.existentials

object Result{
  import OmiGenerator._
  import DBConversions._
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

  def readResult(sensors: Array[DBSensor])(implicit dbConnection: DB) : RequestResultType =  odfResult( "200", None, None, sensors)

  def pollResult( requestId: String, sensors: Array[DBSensor])(implicit dbConnection: DB) : RequestResultType =
    odfResult( "200", None, Some(requestId), sensors)

  def subDataResult( requestId: String, sensors: Array[DBSensor])(implicit dbConnection: DB) : RequestResultType =
    odfResult( "200", None, Some(requestId), sensors)  
    
  def subscriptionResult( requestId: String): RequestResultType ={
    omiResult(
      omiReturn(
        "200",
        Some("Successfully started subcription")
      ),
      Some(requestId)
    )
  }

  def odfResult( returnCode: String, returnDescription: Option[String], requestId: Option[String], sensors: Array[DBSensor])(implicit dbConnection: DB): RequestResultType  = {
    omiResult(
      omiReturn(
        returnCode,
        returnDescription
      ),
      requestId,
      Some("odf"),
      Some( odfMsg( scalaxb.toXML[ObjectsType]( sensorsToObjects( sensors ), Some("odf"), Some("Objects"), scope ) ) )
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
