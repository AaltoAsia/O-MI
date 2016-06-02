

package responses

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

import database._
import types.OdfTypes._
import types._

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

        val dbRemoveFuture: Future[Int] = node match {
          case objs: OdfObjects => dbConnection.removeRoot(parentPath)
          case _ => dbConnection.remove(parentPath)
        }

        dbRemoveFuture.onComplete{
          case Success(res) => log.info(s"Database successfully deleted $res nodes")
          case Failure(error) => log.error(error, s"Failure when trying to remove $parentPath")
        }

        true

      }
      case None => false
    }
  }
}

