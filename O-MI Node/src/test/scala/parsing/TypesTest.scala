package parsing

import org.specs2._
import parsing._

/* Test class for testing ODF Types */
class TypesTest extends Specification {

  def is = s2"""
  This is Specification to check inheritance for Types.

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
    """

  def e1 = {
    new ParseError("test error").isInstanceOf[ParseMsg]
  }

  def e2 = {
    new OneTimeRead("", Seq(), "", "", "", "", "", Seq()).isInstanceOf[ParseMsg]
  }

  def e3 = {
    new Write("", Seq(), "", Seq()).isInstanceOf[ParseMsg]
  }

  def e4 = {
    new Subscription("", "", Seq(), "", "", "", "", "", Seq()).isInstanceOf[ParseMsg]
  }

  def e5 = {
    new Result("", "", None, "", Seq()).isInstanceOf[ParseMsg]
  }

  def e6 = {
    new Cancel("", Seq()).isInstanceOf[ParseMsg]
  }
  
  def e10 = {
    !new OdfInfoItem(Seq(), Seq(), "").isInstanceOf[ParseMsg]
  }
  
  def e11 = {
    !new OdfObject(Seq(), Seq(), Seq(), "").isInstanceOf[ParseMsg]
  }
  
  def e100 = {
    new OdfInfoItem(Seq(), Seq(), "").isInstanceOf[OdfNode]
  }
  
  def e101 = {
    new OdfObject(Seq(), Seq(), Seq(), "").isInstanceOf[OdfNode]
  }
}