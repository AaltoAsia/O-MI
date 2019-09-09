package database

import java.sql.Timestamp
import java.util.Date

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import http.OmiConfigExtension
import org.slf4j.{Logger, LoggerFactory}
import types.omi.{OmiReturn, ReturnCode}
import types.Path
import types.odf._

import scala.concurrent.Future

trait CacheSupportDB{ db: DB =>
  protected def singleStores: SingleStores
  protected val system: ActorSystem
  protected def settings: OmiConfigExtension
  import system.dispatcher
  def readLatestFromCache(leafPaths: Seq[Path], maxLevels: Option[Int]): Future[Option[ImmutableODF]] = {
    // NOTE: Might go off sync with tree or values if the request is large,
    // but it shouldn't be a big problem
    val p2iisF: Future[Map[Path, InfoItem]] = singleStores.getHierarchyTree().map{
      odf => 
        val t = odf.subTreePaths(leafPaths.toSet,maxLevels)
        t.filterNot{
            path => t.exists{
              op => path.isAncestorOf(op)
            }
        }.toIterator.flatMap{
          path => odf.get(path).collect{
            case ii: InfoItem => ii.path -> ii
          }
        }.toMap
    }

    for {
      p2iis <- p2iisF
      pathToValue: Seq[(Path, Value[Any])] <- singleStores.readValues(p2iis.keys.toSeq)
        .mapTo[Seq[(Path, Value[Any])]]
      objectsWithValues = Some(ImmutableODF(pathToValue.flatMap { //why option??
        case (path: Path, value) =>
          p2iis.get(path).map {
            ii: InfoItem =>
              ii.copy(
                names = Vector.empty,
                descriptions = Set.empty,
                metaData = None,
                values = Vector(value)
              )
          }
      }.toVector))
    } yield objectsWithValues

  }
  def readLatestFromCache(requestedOdf: ODF,
                  maxLevels: Option[Int]): Future[Option[ImmutableODF]] = {
    readLatestFromCache(requestedOdf.getLeafPaths.toSeq, maxLevels)
  }
}
