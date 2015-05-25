package responses

import database._
import parsing.xmlGen._
import parsing.xmlGen.scalaxb._
import parsing.Types.Path
import xml.XML
import xml.NodeSeq
import parsing.xmlGen.scalaxb.DataRecord._
import parsing.xmlGen.scalaxb.XMLStandardTypes._

object OmiGenerator {
  
  import DBConversions._
  
  def omiEnvelope[ R <: OmiEnvelopeOption : CanWriteXML ](ttl: Double, request: R , version: String = "1.0") = {
      OmiEnvelope( DataRecord[R]( request), version, ttl)
  }
  
  def omiResponse( results: RequestResultType*) : ResponseListType = {
    ResponseListType(
      results:_*
    )
  }
  
  def omiResult(returnType: ReturnType, requestId: Option[String] = None, msgformat: Option[String] = None, msg: Option[NodeSeq] = None) : RequestResultType = {
    RequestResultType(
        returnType,
        requestId match{
          case Some(id) => Some(IdType(id))
          case None => None
        },
        if(msgformat.nonEmpty && msg.nonEmpty)
          Some( scalaxb.DataRecord(msg.get) )
        else
          None,
        None,
        None,
        if(msgformat.nonEmpty && msg.nonEmpty)
          msgformat
        else
          None,
        targetType = DeviceValue
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

