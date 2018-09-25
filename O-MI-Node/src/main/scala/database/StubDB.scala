package database

import java.io.File
import java.sql.Timestamp

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import http.OmiConfigExtension
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import types.OmiTypes.{OmiReturn, ReturnCode}
import types.Path
import types.odf._

import scala.concurrent.Future

class StubDB(val singleStores: SingleStores, val system: ActorSystem, val settings: OmiConfigExtension) extends DB {

  import scala.concurrent.ExecutionContext.Implicits.global
  protected val log: Logger = LoggerFactory.getLogger("Stub DB")

  def initialize(): Unit = Unit

  val dbmaintainer: ActorRef = system.actorOf(SingleStoresMaintainer.props(singleStores, settings))


  /**
    * Used to get result values with given constrains in parallel if possible.
    * first the two optional timestamps, if both are given
    * search is targeted between these two times. If only start is given,all values from start time onwards are
    * targeted. Similarly if only end is given, values before end time are targeted.
    * Then the two Int values. Only one of these can be present. fromStart is used to select fromStart number
    * of values from the beginning of the targeted area. Similarly from ends selects fromEnd number of values from
    * the end.
    * All parameters except the first are optional, given only the first returns all requested data
    *
    * @param requests SINGLE requests in a list (leafs in request O-DF); InfoItems, Objects and MetaDatas
    * @param begin    optional start Timestamp
    * @param end      optional end Timestamp
    * @param newest   number of values to be returned from start
    * @param oldest   number of values to be returned from end
    * @return Combined results in a O-DF tree
    */
  def getNBetween(requests: Iterable[Node],
                  begin: Option[Timestamp],
                  end: Option[Timestamp],
                  newest: Option[Int],
                  oldest: Option[Int])(implicit timeout: Timeout): Future[Option[ODF]] = {
    readLatestFromCache(requests.map {
      node => node.path
    }.toSeq).map(Some(_))
  }

  def readLatestFromCache(requestedOdf: ODF)(implicit timeout: Timeout): Future[ImmutableODF] = {
    readLatestFromCache(requestedOdf.getLeafPaths.toSeq)
  }

  def readLatestFromCache(leafPaths: Seq[Path])(implicit timeout: Timeout): Future[ImmutableODF] = {
    // NOTE: Might go off sync with tree or values if the request is large,
    // but it shouldn't be a big problem
    val fp2iis = singleStores.getHierarchyTree().map(_.getInfoItems.collect {
      case ii: InfoItem if leafPaths.exists { path: Path => path.isAncestorOf(ii.path) || path == ii.path } =>
        ii.path -> ii
    }.toMap)
    val objectsWithValues: Future[ImmutableODF] = for {
      p2iis <- fp2iis
      pathToValue  <-
        singleStores.readValues(p2iis.keys.toVector).mapTo[Seq[(Path, Value[Any])]]
      objectsWithValues = ImmutableODF(pathToValue.flatMap {
        case (path: Path, value: Value[Any]) =>
          val temp = p2iis.get(path).map {
            ii: InfoItem =>
              ii.copy(
                names = Vector.empty,
                descriptions = Set.empty,
                metaData = None,
                values = Vector(value)
              )
          }
          temp
      }.toVector)
    } yield objectsWithValues

    objectsWithValues
  }

  /**
    * Used to set many values efficiently to the database.
    *
    * @param data list item to be added consisting of Path and OdfValue[Any] tuples.
    */
  def writeMany(data: Seq[InfoItem]): Future[OmiReturn] = {
    Future.successful(OmiReturn.apply(ReturnCode.Success))
  }

  /**
    * Used to remove given path and all its descendants from the database.
    *
    * @param path Parent path to be removed.
    */
  def remove(path: Path)(implicit timeout: Timeout): Future[Seq[Int]] = Future.successful(Seq())
}
