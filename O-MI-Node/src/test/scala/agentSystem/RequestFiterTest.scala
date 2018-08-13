package agentSystem

import org.specs2.mutable.Specification

class RequestFilterTest extends Specification{

  val requestFilterTests: Vector[Tuple2[String,RequestFilter]]= Vector(
    ("r",ReadFilter()),
    ("w",WriteFilter()),
    ("c",CallFilter()),
    ("d",DeleteFilter()),
    ("rw",ReadWriteFilter()),
    ("rc",ReadCallFilter()),
    ("rd",ReadDeleteFilter()),
    ("wc",WriteCallFilter()),
    ("wd",WriteDeleteFilter()),
    ("cd",CallDeleteFilter()),
    ("rwc",ReadWriteCallFilter()),
    ("rwd",ReadWriteDeleteFilter()),
    ("wcd",WriteCallDeleteFilter()),
    ("rcd",ReadCallDeleteFilter()),
    ("rwcd",ReadWriteCallDeleteFilter())
  )
  "RequestFilter should parse correctly" >> {

    org.specs2.specification.core.Fragments.empty.append(
      requestFilterTests.map{
        case (str: String, rf: RequestFilter) => 
          s"$str" >> {
            RequestFilter(str) ===(rf)
          }
      }
    )
  }
}
