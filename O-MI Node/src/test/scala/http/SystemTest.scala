package http
import org.specs2.mutable.Specification
import org.specs2.specification.Fragments

class SystemTest extends Specification{

  "test block" should {
    (1 to 500) foreach { i=>
      ("example " + i ) in {i === i}
      }
    
    (500 to 1000 by 2) foreach { i=>
      ("example " + i ) >> {i === i}
      }
  }
}