

package responses

import scala.xml.{ NodeSeq, XML }
import types._
import OdfTypes._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.concurrent.duration._
import database._
trait RemoveHandler extends OmiRequestHandlerBase{
  def handlePathRemove(parentPath: Path): Boolean = {
    val objects = SingleStores.hierarchyStore execute GetTree()
    val node = objects.get(parentPath)
    node match {
      case Some(node) => {

        val leafs = getInfoItems(node).map(_.path)

        SingleStores.hierarchyStore execute TreeRemovePath(parentPath)

        leafs.foreach{path =>
          log.info(s"removing $path")
          SingleStores.latestStore execute EraseSensorData(path)
        }

        dbConnection.remove(parentPath)
        node match {
          case objs: OdfObjects => Await.ready(dbConnection.addRootR, 2.seconds)
          case _ => //noop
        }
        true

      }
      case None => false
    }
  }
}

