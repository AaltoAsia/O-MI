package parsing

import java.net.URI

import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.stream.scaladsl
import org.specs2._
import types.odf._
import types.odf.OdfCollection._
import types.omi._
import types.Path._
import types._
import testHelpers._

import scala.concurrent.duration._
import scala.xml._


/* Test class for testing ODF Types */
class TypesTest extends Specification {

  implicit val system = Actorstest.createAs()
  implicit val materializer = ActorMaterializer()
  def is =
    s2"""
  This is Specification to check inheritance for Types. Also testing Path Object

  Path 
    Path object should
      create same instance from string and seq	$e200
      be same as the Seq it was created with 	$e201
      seq as path								$e202
      have same hashcodes when equal $e203
    Path class instance should
      join with another path correctly			$e300
      join with another path string				$e301

  RawRequestWrapper
    Pre-parsing should
      parse finite ttl            $pFiniteTTL
      parse infinite ttl          $pInfiniteTTL
      parse message type read     $pRead
      parse message type write    $pWrite
      parse message type cancel   $pCancel
      parse message type response $pResponse

  OmiTypes test
    $omiTypes1
    """

  def e200 = Path("test", "test2").toSeq should be equalTo (Path(Seq("test", "test2")))

  def e201 = {
    val seq = Seq("test", "test2")
    val path = Path(seq)
    Path.PathAsSeq(path) should be equalTo (seq)
  }

  def e202 = {
    val seq = Seq("test", "test2")
    val path = Path(seq)
    Path.SeqAsPath(seq).toString should be equalTo (path.toString)
  }

  def e203 = {
    val path1 = Path("Objects")
    val path2 = Path(Seq("Objects"))
    path1 should be equalTo path2
    path1.hashCode should be equalTo path2.hashCode
  }

  def e300 = {
    val path1 = Path("test1", "test2")
    val path2 = Path("test3", "test4", "test5")

    (path1 / path2).toSeq should be equalTo (Path("test1", "test2", "test3", "test4", "test5").toSeq)
  }

  def e301 = {
    val path1 = Path("test1", "test2")

    (path1 / "test3" / "test4" / "test5").toSeq should
      be equalTo
      (Path("test1", "test2", "test3", "test4", "test5").toSeq)
  }

  def omiTypes1 = {
    val reg1 = ReadRequest(ImmutableODF(), None, None, None, None,None,  None)
    val reg2 = ReadRequest(ImmutableODF(),
      None,
      None,
      None,
      None,None, 
      Some(HTTPCallback(Uri("Http://google.com"))))
    reg1.hasCallback should be equalTo (false) and (
      reg2.hasCallback should be equalTo (true)) and (
      reg2.callbackAsUri must beSome.like { case a => a.isInstanceOf[URI] }) and (
      reg2.parsed must beRight) and (
      reg2.unwrapped must beSuccessfulTry) 
  }

  def omiTypes2 = {
    val reg1 = ReadRequest(ImmutableODF(), None, None, None, None,None,  None, 0 seconds)
    val reg2 = ReadRequest(ImmutableODF(), None, None, None, None,None,  None, 5 seconds)
    val reg3 = ReadRequest(ImmutableODF(), None, None, None, None,None,  None, Duration.Inf)

    reg1.handleTTL must be equalTo (2 minutes) and (
      reg2.handleTTL must be equalTo (5 seconds)) and (
      reg3.handleTTL must be equalTo (FiniteDuration(Int.MaxValue, MILLISECONDS)))
  }

  val testOdfMsg: NodeSeq = {
    <msg xmlns="omi.xsd">
      <Objects xmlns="odf.xsd">
        {for (i <- 0 to 10000)
        yield <Object>
          <id>TestObject</id> <InfoItem name="i"></InfoItem>
        </Object>}
      </Objects>
    </msg>
  }

  def xmlOmi(ttl: String, verb: NodeSeq) = <omiEnvelope xmlns="omi.xsd" ttl={ttl}>
    {verb}
  </omiEnvelope>

  def xmlRead = <read xmlns="omi.xsd" msgformat="odf">
    {testOdfMsg}
  </read>

  val xmlReadFinite: NodeSeq = xmlOmi("10", xmlRead)

  def xmlReadInfinite: NodeSeq = xmlOmi("-1", xmlRead)

  def xmlWrite: NodeSeq = xmlOmi("10", <write xmlns="omi.xsd" msgformat="odf">
    {testOdfMsg}
  </write>)

  def xmlCancel: NodeSeq = xmlOmi("10", <cancel xmlns="omi.xsd" msgformat="odf">
    <requestID>0</requestID>
  </cancel>)

  def xmlResponse: NodeSeq = xmlOmi("10", <response xmlns="omi.xsd" msgformat="odf">
    {testOdfMsg}
  </response>)

  def newRawRequestWrapper(xml: NodeSeq) = RawRequestWrapper(scaladsl.Source.single(xml.toString), UserInfo())

  def pFiniteTTL = newRawRequestWrapper(xmlReadFinite).ttl mustEqual 10.seconds

  def pInfiniteTTL = newRawRequestWrapper(xmlReadInfinite).ttl mustEqual Duration.Inf

  import RawRequestWrapper.MessageType._

  def pRead = newRawRequestWrapper(xmlReadFinite).requestVerb mustEqual Read

  def pWrite = newRawRequestWrapper(xmlWrite).requestVerb mustEqual Write

  def pCancel = newRawRequestWrapper(xmlCancel).requestVerb mustEqual Cancel

  def pResponse = newRawRequestWrapper(xmlResponse).requestVerb mustEqual Response

}
