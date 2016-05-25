/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  **/
package responses

import types._
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.scalaxb._
import scala.xml.NodeSeq
import scala.language.existentials
import OmiGenerator._
/** 
  * Object containing helper mehtods for generating scalaxb generated O-MI types.
  * Used to generate response messages when received a request.
  */
object OmiGenerator {

  /** Generates O-MI OmiEnvelope
    * @param ttl ttl attribute of omiEnvelope
    * @param requestName name of request: "read", "write", "cancel", "response".
    * @param request Request with requestName tag as root.
    * @param version of O-MI
    * @return O-MI omiEnvelope tag.
    */
  def omiEnvelope[ R <: OmiEnvelopeOption : CanWriteXML ](ttl: Double, requestName: String, request: R , version: String = "1.0") : OmiEnvelope= {
    OmiEnvelope( DataRecord[R](Some("omi.xsd"), Some(requestName), request), version, ttl)
  }

  /** Generates O-MI ResponseListType
    * @param results O-MI result tags.
    * @return O-MI response tag.
    */
  def omiResponse( results: RequestResultType*) : ResponseListType = {
    ResponseListType(
      results:_*
    )
}

  /** Generates O-MI RequestResultType.
    * @param returnType O-MI return tag.
    * @param requestID O-MI requestID tag's value.
    * @param msgformat value for omiEnvelope tag's msgformat attribute.
    * @param msg msg tag.
    * @return O-MI result tag.
    */
  def omiResult(
    returnType: ReturnType, 
    requestID: Option[String] = None,
    msgformat: Option[String] = None,
    msg: Option[NodeSeq] = None ) : RequestResultType = {
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

  /** Generates O-MI ReturnType 
    * @param returnCode HTTP return code.
    * @param description Description of return code.
    * @param value Value in omi:return tag.
    * @return O-MI return tag.
    */
  def omiReturn( returnCode: String, description: Option[String] = None, value: String = "") : ReturnType={
    ReturnType(value, returnCode, description, attributes = Map.empty)
  }

  /** Wraps O-DF format to O-MI msg tag.
    * @param odf O-DF Structure.
    * @return O-MI msg tag.
    */
  def odfMsg( odf: NodeSeq ):  NodeSeq ={
    <omi:msg xmlns="odf.xsd">
      {odf}
    </omi:msg>
  }

  /** Generates O-MI response tag from O-MI result tags.
    * @param ttl attribute for omiEnvelope.
    * @param results O-MI result tags.
    * @return O-MI omiEnvelope tag.
    */
  def wrapResultsToResponseAndEnvelope(ttl: Double, results: RequestResultType*) : OmiEnvelope= {
    omiEnvelope(ttl, "response", omiResponse(results: _*))
  }

  /** Generates OmiEnvelope from O-MI results.
    * @param ttl in secods as Double.
    * @param results used in generation.
    * @return O-MI omiEnvelo tag as xml.NodeSeq.
    */
  def xmlFromResults(ttl: Double, results: RequestResultType*): xml.NodeSeq = {
    xmlMsg(wrapResultsToResponseAndEnvelope(ttl, results: _*))
  }

  /**
    * Generates xml from OmiEnvelope
    *
    * @param envelope xmlType for OmiEnvelope containing response
    * @return xml.NodeSeq containing response
    */
  def xmlMsg(envelope: OmiEnvelope): NodeSeq = {
    scalaxb.toXML[OmiEnvelope](envelope, Some("omi.xsd"), Some("omiEnvelope"), defaultScope)
  }

  /** O-MI response for not found paths/subscription. */
  def notFound : NodeSeq= xmlFromResults(
    1.0,
    Results.notFound)

  /** O-MI response for successfully handled request with out any other information.*/
  def success : NodeSeq = xmlFromResults(
    1.0,
    Results.success)

  /** O-MI response for request which permission was denied.*/
  def unauthorized : NodeSeq = xmlFromResults(
    1.0,
    Results.unauthorized)

  /** O-MI response for features not implemented.*/
  def notImplemented : NodeSeq = xmlFromResults(
    1.0,
    Results.notImplemented)

  /** O-MI response for invalid request.
    * @param msg Message defining invalidation.
    */
  def invalidRequest(msg: String = "") : NodeSeq = xmlFromResults(
    1.0,
    Results.invalidRequest(msg))

  /** O-MI response for request which malformed content.
    * @param err ParseErrors
    */
  def parseError(err: ParseError*) : NodeSeq =
  xmlFromResults(
    1.0,
    Results.simple("400",
      Some(err.map { e => e.msg }.mkString("\n"))))

  /** O-MI response for request which callback address was malformed.
    * @param callback Invalid callback address
    */
  def invalidCallback(callback: String) : NodeSeq =
  xmlFromResults(
    1.0,
    Results.simple("400",
      Some("Invalid callback address: " + callback)))

  /** O-MI response for request that caused an exception.
    * @param e Exception caught.
    */
  def internalError(e: Throwable) : NodeSeq =
  xmlFromResults(
    1.0, Results.internalError(e)
  )

  /** O-MI response for request which ttl timedout. */
  def timeOutError(message: String = "") : NodeSeq = xmlFromResults(
    1.0, Results.timeOutError(message)
  )
}

