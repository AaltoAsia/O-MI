/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package types


import java.sql.Timestamp
import java.util.{Date, GregorianCalendar}

import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}
import parsing.xmlGen._
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._

import scala.collection.Iterable
import scala.collection.JavaConverters._
import scala.xml.NodeSeq

/**
  * Package containing classes presenting O-MI request internally.
  *
  */
package object OmiTypes {
  type OmiParseResult = Either[Iterable[ParseError], Iterable[OmiRequest]]
  type RequestID = Long

  def getPaths(request: OdfRequest): Seq[Path] = request.odf.getLeafPaths.toSeq

  def requestToEnvelope(request: OmiEnvelopeTypeOption, ttl: Long): xmlTypes.OmiEnvelopeType = {
    val namespace = Some("omi.xsd")
    //val version = "1.0" //TODO remove unused?
    val datarecord = request match {
      case read: xmlTypes.ReadRequestType =>
        scalaxb.DataRecord[xmlTypes.ReadRequestType](namespace, Some("read"), read)
      case write: xmlTypes.WriteRequestType =>
        scalaxb.DataRecord[xmlTypes.WriteRequestType](namespace, Some("write"), write)
      case call: xmlTypes.CallRequestType =>
        scalaxb.DataRecord[xmlTypes.CallRequestType](namespace, Some("call"), call)
      case delete: xmlTypes.DeleteRequestType =>
        scalaxb.DataRecord[xmlTypes.DeleteRequestType](namespace, Some("delete"), delete)
      case cancel: xmlTypes.CancelRequestType =>
        scalaxb.DataRecord[xmlTypes.CancelRequestType](namespace, Some("cancel"), cancel)
      case response: xmlTypes.ResponseListType =>
        scalaxb.DataRecord[xmlTypes.ResponseListType](namespace, Some("response"), response)
    }
    xmlTypes.OmiEnvelopeType(datarecord, Map("@version" -> DataRecord("1.0"), "@ttl" -> DataRecord(ttl)))
  }

  def omiEnvelopeToXML(omiEnvelope: OmiEnvelopeType): NodeSeq = {
    scalaxb.toXML[OmiEnvelopeType](omiEnvelope, Some("omi"), Some("omiEnvelope"), omiDefaultScope)
  }

  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar = {
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
  }

  def requestIDsFromJava(requestIDs: java.lang.Iterable[java.lang.Long]): Vector[Long] = {
    requestIDs.asScala.map(Long2long).toVector
  }

  /** Wraps O-DF format to O-MI msg tag.
    *
    * @param odf O-DF Structure.
    * @return O-MI msg tag.
    */
  def odfMsg(odf: NodeSeq): NodeSeq = {
    <omi:msg xmlns="odf.xsd">
      {odf}
    </omi:msg>
  }

  def currentTimestamp: Timestamp = new Timestamp(new Date().getTime)
}

