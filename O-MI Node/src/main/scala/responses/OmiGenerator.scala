package responses

import database._
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.scalaxb
import parsing.xmlGen.scalaxb._
import types.Path
import parsing.xmlGen.scalaxb.DataRecord._
import parsing.xmlGen.scalaxb.XMLStandardTypes._
import xml.XML
import xml.NodeSeq

/** Object containing helper mehtods for generating scalaxb generated O-MI types. Used to generate response messages when received a request.
  *
  **/
object OmiGenerator {
  
  def omiEnvelope[ R <: OmiEnvelopeOption : CanWriteXML ](ttl: Double, requestName: String, request: R , version: String = "1.0") = {
      OmiEnvelope( DataRecord[R](Some("omi.xsd"), Some(requestName), request), version, ttl)
  }
  
  def omiResponse( results: RequestResultType*) : ResponseListType = {
    ResponseListType(
      results:_*
    )
  }
  
  def omiResult(returnType: ReturnType, requestID: Option[String] = None, msgformat: Option[String] = None, msg: Option[NodeSeq] = None) : RequestResultType = {
    RequestResultType(
        returnType,
        requestID match{
          case Some(id) => Some(IdType(id))
          case None => None
        },
        if(msgformat.nonEmpty && msg.nonEmpty)
          Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), msg.get) )
        else
          None,
        None,
        None,
        if(msgformat.nonEmpty && msg.nonEmpty)
          msgformat
        else
          None
      )
  } 

  def omiReturn( returnCode: String, description: Option[String] = None, value: String = "") : ReturnType={
    ReturnType(value, returnCode, description, attributes = Map.empty)
  }

  def odfMsg( value: NodeSeq )={
    <omi:msg xmlns="odf.xsd">
      {value}
    </omi:msg>
  }
}

