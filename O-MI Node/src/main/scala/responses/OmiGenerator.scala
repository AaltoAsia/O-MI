/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  https://github.com/AaltoAsia/O-MI/blob/master/LICENSE.txt

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package responses


import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.scalaxb
import parsing.xmlGen.scalaxb._
import scala.xml.NodeSeq

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

