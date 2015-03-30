package responses

import parsing.Types._
import xml._

/**
 * Helpers and common response parts for all responses. See the types of these functions
 * for documentation.
 */
object Common {

  sealed case class OmiResponse(inner: OmiResult) {
    val xml =
      <omi:response>
        { inner.xml }
      </omi:response>
  }

  sealed case class OmiResult(inner: OmiResultChildren, format: String = "") {
    val xml: NodeSeq =
      if (format.isEmpty) {
        <omi:result>
          { inner.map(_.xml) : NodeSeq // if type is NodeSeq, xml library will insert it right
          }
        </omi:result>

      } else {
        <omi:result msgformat={ format }>
          { inner.map(_.xml) : NodeSeq }
        </omi:result>
      }

    // TODO: better way for multiple OmiResults
    /** Many <return> tags are allowed inside OmiResponse. This can be used to define them. */
    def ++(other: OmiResult) = {
      val ourxml = this.xml
      new OmiResult(Seq()){ // XXX: override hack
        override val xml: NodeSeq = ourxml ++ other.xml
      }
    }
  }

  type OmiResultChildren = Seq[OmiResultChild]

  /** Anything that can go inside <omi:return> */
  sealed trait OmiResultChild {
    val xml: Elem
  }

  sealed case class OmiRequestId(id: String) extends OmiResultChild {
    val xml = 
      <omi:requestId>{ id }</omi:requestId>
  }

  sealed case class OmiOdfMsg(innerxml: NodeSeq) extends OmiResultChild {
    val xml = 
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
          { innerxml }
      </omi:msg>
  }

  sealed case class OmiReturnCode(code: String, description: String = "")
      extends OmiResultChild {
    val xml =
      if (description.isEmpty) {
        <omi:return returnCode={ code.toString }></omi:return>
      } else {
        <omi:return returnCode={ code } description={ description }>
        </omi:return>
      }
  }



  /**
   * Wraps innerxml to O-MI Envelope
   */
  def omiEnvelope(ttl: Double)(innerxml: OmiResponse): NodeSeq = {
    <omi:omiEnvelope xmlns:omi="omi.xsd"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd"
	  	version="1.0" ttl={ ttl.toString }>
      { innerxml.xml }
    </omi:omiEnvelope>
  }

  def omiEnvelope: OmiResponse => NodeSeq = omiEnvelope(0)

  /**
   * Wraps innerxml to O-MI Envelope and response
   */
  def omiResponse(ttl: Double)(inner: OmiResult) = omiEnvelope(ttl)(OmiResponse(inner))

  def omiResponse: OmiResult => NodeSeq = omiResponse(0)
  def omiResponse(results: Seq[OmiResult]): NodeSeq = omiResponse(0)(results.reduceLeft(_ ++ _))

  def odfResultWrapper(inner: OmiResultChildren) = OmiResult(inner, "odf")
  def resultWrapper(inner: OmiResultChildren) = OmiResult(inner)

  /**
   * Wraps innerxml to O-MI Envelope, response and result with msgformat odf
   */
  def omiOdfResult(inner: OmiResultChildren): NodeSeq = omiResponse(odfResultWrapper(inner))

  /**
   * Wraps innerxml to O-MI Envelope, response and result
   */
  def omiResult(inner: OmiResultChildren): NodeSeq = omiResponse(resultWrapper(inner))

  /**
   * NOTE: Contains implementation specific "description" attribute
   * that can have a more detailed error message.
   */
  def returnCode(code: Int, description: String): Seq[OmiResultChild] =
    Seq(OmiReturnCode(code.toString, description))

  def returnCode(code: Int): Seq[OmiResultChild] = Seq(OmiReturnCode(code.toString))

  val returnCode200 = returnCode(200)

  def odfMsgWrapper(innerxml: NodeSeq): Seq[OmiResultChild] = Seq(OmiOdfMsg(innerxml))

  /**
   * Creates requestId element
   */
  def requestId(id: Int): Seq[OmiResultChild] = Seq(OmiRequestId(id.toString))

  /**
   * Creates many requestId elements
   * @param ids Takes ids exceptionally as Strings, but they should be integers
   */
  def requestIds(ids: Seq[String]): Seq[OmiResultChild] = {
    if (ids.nonEmpty)
      ids map {OmiRequestId(_)}
    else Seq()
  }
}

