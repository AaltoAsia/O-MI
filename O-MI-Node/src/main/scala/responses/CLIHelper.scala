

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

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.{LogSource, Logging, LoggingAdapter}
import akka.util.Timeout
import database._
import types._
import types.odf._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait CLIHelperT {
  def handlePathRemove(parentPathS: Iterable[Path]): Future[Iterable[Int]]

  def takeSnapshot(): Future[Any]

  def getAllData(): Future[Option[ODF]]

  def writeOdf(odf: ImmutableODF): Future[Unit]

  def trimJournal(journal: String, seqNr: String): Future[Any]
}

class CLIHelper(val singleStores: SingleStores, dbConnection: DB)(implicit system: ActorSystem) extends CLIHelperT {
  implicit val timeout: Timeout = system.settings.config
    .getDuration("omi-service.journal-ask-timeout",TimeUnit.MILLISECONDS).milliseconds

  implicit val logSource: LogSource[CLIHelper] = (handler: CLIHelper) => handler.toString
  protected val log: LoggingAdapter = Logging(system, this)

  def takeSnapshot(): Future[Any] = singleStores.takeSnapshot

  def trimJournal(journal: String, seqNr: String): Future[Any] = for {
    number <- Future.fromTry(Try{seqNr.toLong})
    res <- singleStores.trimJournal(journal, number)
  } yield res

  def handlePathRemove(parentPaths: Iterable[Path]): Future[Iterable[Int]] = {
    val odfF = singleStores.getHierarchyTree()

    Future.sequence(parentPaths.map { parentPath =>
      odfF.flatMap {(odf) =>

          odf.subTreePaths(Set(parentPath)).foreach(singleStores.erasePathData)

          val dbRemoveFuture: Future[Int] = dbConnection.remove(parentPath).map(_.length).flatMap{
            n =>
              singleStores.erasePathHierarchy(parentPath).map( _ => n)
          }

          dbRemoveFuture.onComplete {
            case Success(res) => log.debug(s"Database successfully deleted $res nodes")
            case Failure(error) => log.error(error, s"Failure when trying to remove $parentPath")
          }

          dbRemoveFuture

      }
    })
  }


  /**
    * method of getting all the data available from hierarchystore and database, includes all metadata and descriptions
    *
    * method in this class because it has visibility to singleStores and Database
    *
    * @return
    */
  def getAllData(): Future[Option[ODF]] = {
    val odfF: Future[ImmutableODF] = singleStores.getHierarchyTree()
    for {
      odf <- odfF
      leafs = odf.getLeafs
      o: Option[ODF] <- dbConnection.getNBetween(leafs, None, None, Some(100), None, None)
      res = o.map(_.union(odf))
    } yield res
  }

  def writeOdf(odf: ImmutableODF): Future[Unit] = {
    val infoItems: Iterable[InfoItem] = odf.getInfoItems
    for {
      dbc <- dbConnection.writeMany(infoItems.toSeq)
      ret <- singleStores.updateHierarchyTree(odf.toImmutable)
      latestValues: Map[Path, Value[Any]] = infoItems.collect {
        case ii: InfoItem if ii.values.nonEmpty => ii.path -> ii.values.maxBy(_.timestamp.getTime())
      }.toMap //.map(pv => singleStores.latestStore ? SetSensorData(pv._1,pv._2))
      res <- singleStores.writeValues(latestValues)
    } yield res
  }

}

