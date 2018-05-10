package types 

import java.sql.Timestamp
import java.util.{Date, GregorianCalendar}
import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}

import scala.xml.Utility.trim
import scala.collection.immutable.HashMap
import org.specs2.matcher._
import org.specs2.matcher.XmlMatchers._

import org.specs2._
import types.Path._

class PathTest extends mutable.Specification{
  sequential
  "Path" should {
    "parse correctly without special character" in {
      Path( "Objects/Obj/test" ).toSeq === Vector("Objects","Obj","test") 
    }
    "parse \\/ correctly" in {
      Path( "Objects/Test\\/Obj/test" ).toSeq === Vector("Objects","Test\\/Obj","test") 
    }
    "print correctly without specila charchter" in {
      val str =  "Objects/Obj/test"
      Path(str).toString === str
    }
    "print \\/ correctly" in {
      val str =  "Objects/Test\\/Obj/test"
      Path(str).toString === str
    }
  }
}
