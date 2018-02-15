

/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package responses
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import akka.event.{LogSource, Logging, LoggingAdapter}
import akka.actor.ActorSystem
import database._
import types.OdfTypes._
import types._
import http.{ActorSystemContext, OmiServer, Storages}

trait CLIHelperT{
  def handlePathRemove(parentPath: Path): Boolean
  def getAllData(): Future[Option[OdfObjects]]
  def writeOdf(odf: OdfObjects): Unit
  }
class CLIHelper(val singleStores: SingleStores, dbConnection: DB )(implicit system: ActorSystem) extends CLIHelperT{

  implicit val logSource: LogSource[CLIHelper]= new LogSource[CLIHelper] {
      def genString( handler:  CLIHelper) = handler.toString
    }
  protected val log: LoggingAdapter = Logging( system, this)

  def handlePathRemove(parentPath: Path): Boolean = {
    val objects = singleStores.hierarchyStore execute GetTree()
    val node = objects.get(parentPath)
    node match {
      case Some(_node) => {

        val leafs = getInfoItems(_node).map(_.path)

        singleStores.hierarchyStore execute TreeRemovePath(parentPath)

        leafs.foreach{path =>
          log.info(s"removing $path")
          singleStores.latestStore execute EraseSensorData(path)
        }

        val dbRemoveFuture: Future[Int] = dbConnection.remove(parentPath).map( _.length )

        dbRemoveFuture.onComplete{
          case Success(res) => log.info(s"Database successfully deleted $res nodes")
          case Failure(error) => log.error(error, s"Failure when trying to remove $parentPath")
        }

        true

      }
      case None => false
    }
  }

  /**
    * method of getting all the data available from hierarchystore and dagabase, includes all metadata and descriptions
    *
    * method in this class because it has viisbility to singleStores and Database
    * @return
    */
  def getAllData(): Future[Option[OdfObjects]] = {
    val odf: OdfObjects = singleStores.hierarchyStore execute GetTree()
    val leafs = getLeafs(odf)
    dbConnection.getNBetween(leafs,None,None,Some(100),None).map(objs => objs.map(_.union(odf)))
  }

  def writeOdf(odf: OdfObjects): Unit = {
    val infoItems: Seq[OdfInfoItem] = odf.infoItems
    dbConnection.writeMany(infoItems)
    singleStores.hierarchyStore execute Union(odf)
    val latestValues: Seq[(Path, OdfValue[Any])] =
      infoItems.collect{
        case OdfInfoItem(path,values,_,_,_,_) if values.nonEmpty => (path, values.maxBy(_.timestamp.getTime()))
      }
    latestValues.foreach(pv => singleStores.latestStore execute SetSensorData(pv._1,pv._2))

  }

}

