package parsing

import scala.collection.JavaConversions.seqAsJavaList
import scala.concurrent.duration._

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
    """

  def e1 = {
    !new ParseError("test error").isInstanceOf[OmiRequest]
  }

  def e2 = {
    new ReadRequest(10.seconds, OdfObjects()).isInstanceOf[OmiRequest]
  }

  def e3 = {
    new WriteRequest(10.seconds, OdfObjects()).isInstanceOf[OmiRequest]
  }

  def e4 = {
    new SubscriptionRequest(0.seconds, 0.seconds, OdfObjects()).isInstanceOf[OmiRequest]
  }

  def e5 = {
    new ResponseRequest(Seq(OmiResult(OmiReturn("200")))).isInstanceOf[OmiRequest]
  }

  def e6 = {
    new CancelRequest(10.seconds, Seq()).isInstanceOf[OmiRequest]
  }
  
  def e10 = {
    !new OdfInfoItem(Seq("Objects", "Typestest","t"), Seq()).isInstanceOf[OmiRequest]
  }
  
  def e11 = {
    !new OdfObject(Seq(),Path("Objects/TypesTest"), Seq(), Seq()).isInstanceOf[OmiRequest]
  }
  
  def e100 = {
    new OdfInfoItem(Seq("Ojects", "Typestest", "t"), Seq()).isInstanceOf[OdfNode]
  }
  
  def e101 = {
    new OdfObject(Seq(),Path("Objects/TypesTest"), Seq(), Seq()).isInstanceOf[OdfNode]
  }
  
  def e200 = {
    Path("test/test2").toSeq should be equalTo (Path(Seq("test", "test2")))
  }
  
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
    val path1 = new Path("test1/test2")
    val path2 = new Path("test3/test4/test5")
    
    (path1 / path2).toSeq should be equalTo (new Path("test1/test2/test3/test4/test5").toSeq)
  }
  
  def e301 = {
    val path1 = new Path("test1/test2")
    
    (path1 / "test3/test4/test5").toSeq should be equalTo (new Path("test1/test2/test3/test4/test5").toSeq)
  }
}
