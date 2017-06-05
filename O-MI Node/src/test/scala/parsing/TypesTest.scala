package parsing

import scala.collection.JavaConversions.seqAsJavaList
import scala.concurrent.duration._
import xml._

import org.specs2._
import types.OdfTypes.OdfTreeCollection._
import types.OdfTypes._
import types.OmiTypes._
import types.Path._
import types._


/* Test class for testing ODF Types */
class TypesTest extends Specification {

  def is = s2"""
  This is Specification to check inheritance for Types. Also testing Path Object

  Types should specify type inheritance for
    OmiRequest inherited by
      ReadRequest         $e2
      WriteRequest			      $e3
      SubscriptionRequest	      $e4
      ResponseRequest	          $e5
      CancelRequest		      $e6
    OmiRequest not inherited by
      ParseError        $e1
      OdfInfoItem		  $e10
      OdfObject		      $e11
    OdfElement inherited by
      OdfInfoItem		  $e100
      OdfObject			  $e101
      
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
    """

  def e1 = !ParseErrorList("test error").isInstanceOf[OmiRequest]

  def e2 = ReadRequest(OdfObjects(), None, None, None, None, None, 0.seconds).isInstanceOf[OmiRequest]

  def e3 = WriteRequest( OdfObjects(), None, 10.seconds).isInstanceOf[OmiRequest]

  def e4 = SubscriptionRequest(1.seconds, OdfObjects(), None, None, None,  0.seconds).isInstanceOf[OmiRequest]

  def e5 = {
    ResponseRequest(Seq(OmiResult(Returns.Success()))).isInstanceOf[OmiRequest]
  }

  def e6 = CancelRequest(Seq(), 10.seconds).isInstanceOf[OmiRequest]
  
  def e10 = {
    !  OdfInfoItem(Path("Objects/obj1/sensor")).isInstanceOf[OmiRequest]
  }

  def e11 = !OdfObject(Seq(),Path("Objects/TypesTest"), Seq(), Seq()).isInstanceOf[OmiRequest]
  
  def e100 = {
      OdfInfoItem(Path("Objects/obj1/sensor")).isInstanceOf[OdfNode]
  }

  def e101 = OdfObject(Seq(),Path("Objects/TypesTest"), Seq(), Seq()).isInstanceOf[OdfNode]
  
  def e200 = Path("test/test2").toSeq should be equalTo (Path(Seq("test", "test2")))
  
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
    path1.hashCode() should be equalTo path2.hashCode()
  }
  
  def e300 = {
    val path1 =   Path("test1/test2")
    val path2 =   Path("test3/test4/test5")
    
    (path1 / path2).toSeq should be equalTo (  Path("test1/test2/test3/test4/test5").toSeq)
  }
  
  def e301 = {
    val path1 =   Path("test1/test2")
    
    (path1 / "test3/test4/test5").toSeq should be equalTo (  Path("test1/test2/test3/test4/test5").toSeq)
  }

  val testOdfMsg: NodeSeq ={
    <msg xmlns = "omi.xsd">
      <Objects xmlns="odf.xsd">
      { for (i <- 0 to 10000)
        yield <Object><id>TestObject</id><InfoItem name="i"></InfoItem></Object>
      }
      </Objects>
    </msg>
  }

  def xmlOmi(ttl: String, verb: NodeSeq) = <omiEnvelope xmlns="omi.xsd" ttl={ttl}> {verb} </omiEnvelope>
  def xmlRead = <read xmlns="omi.xsd" msgformat="odf"> {testOdfMsg} </read>
  val xmlReadFinite: NodeSeq = xmlOmi("10", xmlRead)
  def xmlReadInfinite: NodeSeq = xmlOmi("-1", xmlRead)
  def xmlWrite: NodeSeq = xmlOmi("10", <write xmlns="omi.xsd" msgformat="odf"> {testOdfMsg} </write>)
  def xmlCancel: NodeSeq = xmlOmi("10", <cancel xmlns="omi.xsd" msgformat="odf"> <requestID>0</requestID> </cancel>)
  def xmlResponse: NodeSeq = xmlOmi("10", <response xmlns="omi.xsd" msgformat="odf"> {testOdfMsg} </response>)

  def newRawRequestWrapper(xml: NodeSeq) = RawRequestWrapper(xml.toString, UserInfo())

  def pFiniteTTL   = newRawRequestWrapper(xmlReadFinite).ttl mustEqual 10.seconds
  def pInfiniteTTL = newRawRequestWrapper(xmlReadInfinite).ttl mustEqual Duration.Inf

  import RawRequestWrapper.MessageType._

  def pRead     = newRawRequestWrapper(xmlReadFinite).messageType mustEqual Read
  def pWrite    = newRawRequestWrapper(xmlWrite).messageType mustEqual Write
  def pCancel   = newRawRequestWrapper(xmlCancel).messageType mustEqual Cancel
  def pResponse = newRawRequestWrapper(xmlResponse).messageType mustEqual Response

}
