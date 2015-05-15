package parsing

import org.specs2._
import parsing._
import parsing.Types._
import parsing.Types.Path._
import parsing.Types.OmiTypes._
import parsing.Types.OdfTypes._
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable

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
    Path class instance should
      join with another path correctly			$e300
      join with another path string				$e301
    """

  def e1 = {
    !new ParseError("test error").isInstanceOf[OmiRequest]
  }

  def e2 = {
    new ReadRequest(10, OdfObjects()).isInstanceOf[OmiRequest]
  }

  def e3 = {
    new WriteRequest(10, OdfObjects()).isInstanceOf[OmiRequest]
  }

  def e4 = {
    new SubscriptionRequest(0, 0, OdfObjects()).isInstanceOf[OmiRequest]
  }

  def e5 = {
    new ResponseRequest(Seq(OmiResult("1","200"))).isInstanceOf[OmiRequest]
  }

  def e6 = {
    new CancelRequest(10, Seq()).isInstanceOf[OmiRequest]
  }
  
  def e10 = {
    !new OdfInfoItem(Seq(), Seq()).isInstanceOf[OmiRequest]
  }
  
  def e11 = {
    !new OdfObject(Seq(), Seq(), Seq()).isInstanceOf[OmiRequest]
  }
  
  def e100 = {
    new OdfInfoItem(Seq(), Seq()).isInstanceOf[OdfElement]
  }
  
  def e101 = {
    new OdfObject(Seq(), Seq(), Seq()).isInstanceOf[OdfElement]
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
