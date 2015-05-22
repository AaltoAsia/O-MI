package responses

import database._
import parsing.xmlGen._
import parsing.xmlGen.scalaxb
import parsing.Types.Path
import xml.XML
import xml.Node
import scala.language.existentials

class Responses(implicit dbConnection: DB){
  import OmiGenerator._
  import DBConversions._
  private val scope = scalaxb.toScope(
    None -> "odf",
    Some("omi") -> "omi.xsd",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  def successResponse = {
    omiEnvelopeForResponse(
      1,
      omiResponse(
        omiResult(
          omiReturn("200") 
        )
      ) 
    )
 }

  def readResponse(sensors: Array[DBSensor])  = {
    omiEnvelopeForResponse(
      1,
      omiResponse(
        omiResult(
          omiReturn("200"),
          None,
          Some("odf"),
          Some( odfMsg( scalaxb.toXML[ObjectsType]( sensorsToObjects( sensors ), Some("odf"), Some("Objects"), scope )  ) )
        )
      ) 
    )
  }

  def pollResponse( requestId: String, sensors: Array[DBSensor])  = {
    omiEnvelopeForResponse(
      1,
      omiResponse(
        omiResult(
          omiReturn("200"),
          Some(requestId),
          Some("odf"),
          Some( odfMsg( scalaxb.toXML[ObjectsType]( sensorsToObjects( sensors ), Some("odf"), Some("Objects"), scope ) ) )
        )
      ) 
    )
  }

  def subscriptionResponse( requestId: String, sensors: Array[DBSensor])  = {
    omiEnvelopeForResponse(
      1,
      omiResponse(
        omiResult(
          omiReturn("200"),
          Some(requestId),
          Some("odf"),
          Some( odfMsg( scalaxb.toXML[ObjectsType]( sensorsToObjects( sensors ), Some("odf"), Some("Objects"), scope ) ) )
        )
      ) 
    )
  }

  def errorResponse(code: String, description: String )  = {
    omiEnvelopeForResponse(
      1,
      omiResponse(
        omiResult(
          omiReturn(
            code,
            Some(description)
          )
        )
      ) 
    )
  }
}
