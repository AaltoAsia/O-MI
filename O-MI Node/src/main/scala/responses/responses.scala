package responses

import database._
import parsing.xmlGen._
import parsing.xmlGen.scalaxb._
import parsing.Types.Path
import xml.XML
import xml.Node
import scala.language.existentials

object `package` {
 /*Ä 
  import OmiGenerator._
  import DBConversions._

  def successResponse = {
    omiEnvelope(
      1,
      "response",
      omiResponse(
        omiResult(
          omiReturn("200") 
        )
      ) 
    )
 }

  def readResponse(sensors: Seq[DBSensor])  = {
    omiEnvelope(
      1,
      "response",
      omiResponse(
        omiResult(
          omiReturn("200") 
        ),
        None,
        Some("odf"),
        Some( odfMsg( scalaxb.toXML[ObjectsType]( sensorsToObjects( sensors ) ) ) )
      ) 
    )
  }

  def pollResponse( requestId: String, sensors: Seq[DBSensor])  = {
    omiEnvelope(
      1,
      "response",
      omiResponse(
        omiResult(
          omiReturn("200") 
        ),
        Some(requestId),
        Some("odf"),
        Some( odfMsg( scalaxb.toXML[ObjectsType]( sensorsToObjects( sensors ) ) ) )
      ) 
    )
  }

  def subscriptionResponse( requestId: String, sensors: Seq[DBSensor])  = {
    omiEnvelope(
      1,
      "response",
      omiResponse(
        omiResult(
          omiReturn("200") 
        ),
        Some(requestId),
        Some("odf"),
        Some( odfMsg( scalaxb.toXML[ObjectsType]( sensorsToObjects( sensors ) ) ) )
      ) 
    )
  }

  def errorResponse(code: Int, description: String )  = {
    omiEnvelope(
      1,
      "response",
      omiResponse(
        omiResult(
          omiReturn(
            code.toString,
            Some(description)
          )
        )
      ) 
    )
  }*/
}
