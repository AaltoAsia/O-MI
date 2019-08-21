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
import java.util.GregorianCalendar
import java.time.{ZoneId, OffsetDateTime}

import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}

import scala.collection.Iterable
import scala.collection.JavaConverters._
import scala.xml.NodeSeq

/**
  * Package containing classes presenting O-MI request internally.
  *
  */
package object omi {
  type OmiParseResult = Either[Iterable[ParseError], Iterable[OmiRequest]]
  type RequestID = Long

  def getPaths(request: OdfRequest): Seq[Path] = request.odf.getLeafPaths.toSeq

  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar = {
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
  }
  def timestampToDateTimeString(timestamp: Timestamp): String = {
    OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault()).toString
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

}

