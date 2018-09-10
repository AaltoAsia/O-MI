package types

import scala.collection.immutable.SortedSet
import scala.util.Random
import org.specs2._
import org.specs2.matcher._

class PathTest extends mutable.Specification {
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
    "be ordered correctly with PathOrdering" in {
      val correct = Seq(
        Path("Objects"),
        Path("Objects/A"),
        Path("Objects/Test1"),
        Path("Objects/Test1/II"),
        Path("Objects/Test1/Obj"),
        Path("Objects/Test1/Obj/II"),

        Path("Objects/Test1/WII"),
        Path("Objects/Test1, a"),
        Path("Objects/Test1, a/II"),
        Path("Objects/Test2"),
        Path("Objects/Z")
      )
      val n = 40 
      val shuffles = (0 until n ).map{
        _ => Random.shuffle(correct)
      }
      val result = shuffles.map{
        seq: Seq[Path] =>
          seq.sorted(Path.PathOrdering) === correct
      }.reduce{
        (l,r) =>
          l and r
      }
      result
    }
  }
}
