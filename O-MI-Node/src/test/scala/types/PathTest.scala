package types

import org.specs2._

class PathTest extends mutable.Specification {
  sequential
  "Path" should {
    "parse correctly without special character" in {
      Path("Objects/Obj/test").toSeq === Vector("Objects", "Obj", "test")
    }
    "parse \\/ correctly" in {
      Path( """Objects/Test\/Obj/test""").toSeq === Vector("Objects", "Test/Obj", "test")
    }
    "print correctly without special character" in {
      val str = "Objects/Obj/test"
      Path(str).toString === str
    }
    "print \\/ correctly" in {
      val str = """Objects/Test\/Obj/test"""
      Path(str).toString === str
    }
  }
}
