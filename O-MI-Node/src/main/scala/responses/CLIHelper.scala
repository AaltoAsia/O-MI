

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
import types.odf._
import types._
import http.{ActorSystemContext, OmiServer}
import journal.Models.{ErasePathCommand, GetTree, UnionCommand, WriteCommand}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

trait CLIHelperT{
  def handlePathRemove(parentPathS: Seq[Path]): Future[Seq[Int]]
  def getAllData(): Future[Option[ODF]]
  def writeOdf(odf: ImmutableODF): Future[Unit]
  }
class CLIHelper(val singleStores: SingleStores, dbConnection: DB )(implicit system: ActorSystem) extends CLIHelperT{
  implicit val timeout: Timeout = Timeout(2 minutes)

  implicit val logSource: LogSource[CLIHelper]= new LogSource[CLIHelper] {
      def genString( handler:  CLIHelper): String = handler.toString
    }
  protected val log: LoggingAdapter = Logging( system, this)

  def handlePathRemove(parentPaths: Seq[Path]): Future[Seq[Int]] = {
    val odfF = (singleStores.hierarchyStore ? GetTree).mapTo[ImmutableODF]

    Future.sequence(parentPaths.map { parentPath =>
      val nodeO = odfF.map(_.get(parentPath))
      nodeO.flatMap {
        case Some(node) => {

          val leafs = odfF.map(_.getPaths.filter {
            p: Path =>
              node.path.isAncestorOf(p)
          }.foreach{path =>
            log.info(s"removing $path")
            singleStores.latestStore ! ErasePathCommand(path)
          })

          singleStores.hierarchyStore ! ErasePathCommand(parentPath)

          val dbRemoveFuture: Future[Int] = dbConnection.remove(parentPath).map(_.length)

          dbRemoveFuture.onComplete {
            case Success(res) => log.debug(s"Database successfully deleted $res nodes")
            case Failure(error) => log.error(error, s"Failure when trying to remove $parentPath")
          }

          dbRemoveFuture

        }
        case None => Future.successful(0)
      }
    })
  }



  /**
    * method of getting all the data available from hierarchystore and database, includes all metadata and descriptions
    *
    * method in this class because it has visibility to singleStores and Database
    * @return
    */
  def getAllData(): Future[Option[ODF]] = {
    val odfF: Future[ImmutableODF] = (singleStores.hierarchyStore ? GetTree).mapTo[ImmutableODF]
    for{
      odf <- odfF
      leafs = odf.getLeafs
      o: Option[ODF] <- dbConnection.getNBetween(leafs,None,None,Some(100),None)
      res = o.map(_.union(odf))
    } yield res
  }

  def writeOdf(odf: ImmutableODF): Future[Unit] = {
    val infoItems: Seq[InfoItem] = odf.getInfoItems
    for{
      dbc <- dbConnection.writeMany(infoItems)
      ret <- (singleStores.hierarchyStore ? UnionCommand(odf.immutable))
      latestValues: Map[Path, Value[Any]] = infoItems.collect{
        case ii: InfoItem if ii.values.nonEmpty => ii.path -> ii.values.maxBy(_.timestamp.getTime())
      }.toMap//.map(pv => singleStores.latestStore ? SetSensorData(pv._1,pv._2))
      res <- (singleStores.latestStore ? WriteCommand(latestValues)).mapTo[Unit]
    } yield res
  }

}

