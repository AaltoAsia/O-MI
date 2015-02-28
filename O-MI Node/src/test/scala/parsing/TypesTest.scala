package parsing

import org.specs2._
import parsing._
import parsing.Types._
import parsing.Types.Path._

/* Test class for testing ODF Types */
class TypesTest extends Specification {

  def is = s2"""
  This is Specification to check inheritance for Types. Also testing Path Object

  Types should specify type inheritance for
    ParseMsg inherited by
      ParseError  		  $e1
      OneTimeRead         $e2
      Write			      $e3
      Subscription	      $e4
      Result	          $e5
      Cancel		      $e6
    ParseMsg not inherited by
      OdfInfoItem		  $e10
      OdfObject		      $e11
    OdfNode inherited by
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
    new ParseError("test error").isInstanceOf[ParseMsg]
  }

  def e2 = {
    new OneTimeRead("", Seq()).isInstanceOf[ParseMsg]
  }

  def e3 = {
    new Write("", Seq()).isInstanceOf[ParseMsg]
  }

  def e4 = {
    new Subscription("", 0, Seq()).isInstanceOf[ParseMsg]
  }

  def e5 = {
    new Result("", "", None).isInstanceOf[ParseMsg]
  }

  def e6 = {
    new Cancel("", Seq()).isInstanceOf[ParseMsg]
  }
  
  def e10 = {
    !new OdfInfoItem(Seq(), Seq(), "").isInstanceOf[ParseMsg]
  }
  
  def e11 = {
    !new OdfObject(Seq(), Seq(), Seq()).isInstanceOf[ParseMsg]
  }
  
  def e100 = {
    new OdfInfoItem(Seq(), Seq(), "").isInstanceOf[OdfNode]
  }
  
  def e101 = {
    new OdfObject(Seq(), Seq(), Seq()).isInstanceOf[OdfNode]
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
