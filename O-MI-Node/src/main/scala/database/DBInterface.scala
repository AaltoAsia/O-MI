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
package database

import java.io.File
import java.sql.Timestamp

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import http.OmiConfigExtension
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import org.prevayler.{PrevaylerFactory}
import slick.basic.DatabaseConfig
import types.OmiTypes.ReturnCode
import slick.jdbc.JdbcProfile
import types.odf._
import types.OmiTypes.OmiReturn
import types.Path
import journal.Models.GetTree
import journal.Models.SingleReadCommand
import journal.Models.MultipleReadCommand
import akka.pattern.ask

package object database {

  private[this] var histLength = 15 //http.Boot.settings.numLatestValues
  /**
    * Sets the historyLength to desired length
    * default is 10
    *
    * @param newLength new length to be used
    */
  def changeHistoryLength(newLength: Int): Unit = {
    histLength = newLength
  }

  def historyLength: Int = histLength

  val dbConfigName = "slick-config"

}

//import database.database._
//import database.database.dbConfigName
import database.dbConfigName

sealed trait InfoItemEvent {
  val infoItem: InfoItem
}

/**
  * Value of the InfoItem is changed and the new has newer timestamp. Event subs should be triggered.
  * Not a case class because pattern matching didn't work as expected (this class is extended).
  */
class ChangeEvent(val infoItem: InfoItem) extends InfoItemEvent {
  override def toString: String = s"ChangeEvent($infoItem)"

  override def hashCode: Int = infoItem.hashCode
}

object ChangeEvent {
  def apply(ii: InfoItem): ChangeEvent = new ChangeEvent(ii)

  def unapply(ce: ChangeEvent): Option[InfoItem] = Some(ce.infoItem)
}

/**
  * Received new value with newer timestamp but value is the same as the previous
  */
case class SameValueEvent(infoItem: InfoItem) extends InfoItemEvent

/*
 * New InfoItem (is also ChangeEvent)
 */
case class AttachEvent(override val infoItem: InfoItem) extends ChangeEvent(infoItem) with InfoItemEvent

/**
  * Contains all stores that requires only one instance for interfacing
  */
class SingleStores(protected val settings: OmiConfigExtension)(implicit val system: ActorSystem) {

  import system.dispatcher

  private[this] def createPrevayler[P](in: P, name: String) = {
    if (settings.writeToDisk) {
      val journalFileSizeLimit = settings.maxJournalSizeBytes
      val factory = new PrevaylerFactory[P]()

      val directory = new File(settings.journalsDirectory ++ s"/$name")
      prevaylerDirectories += directory

      // Configure factory settings
      // Change size threshold so we can remove the old journal files as we take snapshots.
      // Otherwise it will continue to fill disk space
      factory.configureJournalFileSizeThreshold(journalFileSizeLimit) // about 100M
      factory.configurePrevalenceDirectory(directory.getAbsolutePath)
      factory.configurePrevalentSystem(in)
      // Create factory
      factory.create()
    } else {
      PrevaylerFactory.createTransientPrevayler[P](in)
    }
  }

  /** List of all prevayler directories. Currently used for removing unnecessary files in these directories */
  val prevaylerDirectories: ArrayBuffer[File] = ArrayBuffer[File]()

  val latestStore: ActorRef = system.actorOf(Props[journal.LatestStore])
  val hierarchyStore: ActorRef = system.actorOf(Props[journal.HierarchyStore])
  val subStore: ActorRef = system.actorOf(Props[journal.SubStore])
  val pollDataPrevayler: ActorRef = system.actorOf(Props[journal.PollDataStore])

  //val latestStore: Prevayler[LatestValues] = createPrevayler(LatestValues.empty, "latestStore")
  //val hierarchyStore: Prevayler[OdfTree] = createPrevayler(OdfTree.empty, "hierarchyStore")
  //val subStore: Prevayler[Subs] = createPrevayler(Subs.empty,"subscriptionStore")
  //val pollDataPrevayler: Prevayler[PollSubData] = createPrevayler(PollSubData.empty, "pollDataPrevayler")

  // ???subStore execute RemoveWebsocketSubs()

  def buildODFFromValues(items: Seq[(Path, Value[Any])]): ODF = {
    ImmutableODF(items map { case (path, value) =>
      InfoItem(path, Vector(value))
    })
  }


  /**
    * Logic for updating values based on timestamps.
    * If timestamp is same or the new value timestamp is after old value return true else false
    *
    * @param oldValue old value(from latestStore)
    * @param newValue the new value to be added
    * @return
    */
  def valueShouldBeUpdated(oldValue: Value[Any], newValue: Value[Any]): Boolean = {
    oldValue.timestamp before newValue.timestamp
  }


  /**
    * Main function for handling incoming data and running all event-based subscriptions.
    * As a side effect, updates the internal latest value store.
    * Event callbacks are not sent for each changed value, instead event results are returned
    * for aggregation and other extra functionality.
    *
    * @param path     Path to incoming data
    * @param newValue Actual incoming data
    * @return Triggered responses
    */
  def processData(path: Path, newValue: Value[Any], oldValueOpt: Option[Value[Any]]): Option[InfoItemEvent] = {

    // TODO: Replace metadata and description if given

    oldValueOpt match {
      case Some(oldValue) =>
        if (valueShouldBeUpdated(oldValue, newValue)) {
          // NOTE: This effectively discards incoming data that is older than the latest received value
          if (oldValue.value != newValue.value) {
            Some(ChangeEvent(InfoItem(path, Vector(newValue))))
          } else {
            // Value is same as the previous
            Some(SameValueEvent(InfoItem(path, Vector(newValue))))
          }

        } else None // Newer data found

      case None => // no data was found => new sensor
        val newInfo = InfoItem(path, Vector(newValue))
        Some(AttachEvent(newInfo))
    }

  }


  def getMetaData(path: Path)(implicit timeout: Timeout): Future[Option[MetaData]] = {
    (hierarchyStore ? GetTree).mapTo[ImmutableODF].map(_.get(path).collect {
      case ii: InfoItem if ii.metaData.nonEmpty => ii.metaData
    }.flatten)
  }

  def getSingle(path: Path)(implicit timeout: Timeout): Future[Option[Node]] = {
    val ftree = (hierarchyStore ? GetTree).mapTo[ImmutableODF]
    ftree.flatMap(tree => tree.get(path).map {
      case info: InfoItem =>
        (latestStore ? SingleReadCommand(path)).mapTo[Option[Value[Any]]].map {
          case Some(value) =>
            InfoItem(path.last, path, values = Vector(value), attributes = info.attributes)
          case None =>
            info
        }
      case objs: Objects => Future.successful(objs)
      //objs.copy(objects = objs.objects map (o => OdfObject(o.id, o.path,typeValue = o.typeValue)))
      case obj: Object => Future.successful(obj)
    } match {
      case Some(f) => f.map(Some(_))
      case None => Future.successful(None)
    }
      /*
      val (ciis,cobjs) = tree.getChilds(obj.path).map{
        case childObj: Object => childObj.copy( descriptions = Vector.empty)
        case childII: InfoItem => childII.copy( descriptions = Vector.empty, metaData = None)
      }.partition{
        case childObj: Object => true
        case childII: InfoItem => false
      }
      obj.copy(
        objects = obj.objects map (o => OdfObject(o.id, o.path, typeValue = o.typeValue)),
        infoItems = obj.infoItems map (i => InfoItem(i.path, attributes = i.attributes)),
        typeValue = obj.typeValue
      )*/

    )
  }
}


/**
  * Database class for sqlite. Actually uses config parameters through forConfig.
  * To be used during actual runtime.
  */
class DatabaseConnection()(
  protected val system: ActorSystem,
  protected val singleStores: SingleStores,
  protected val settings: OmiConfigExtension
) extends OdfDatabase with DB {

  //val dc = DatabaseConfig.forConfig[JdbcProfile](dbConfigName)
  val dc: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  val db = dc.db
  //val db = Database.forConfig(dbConfigName)
  initialize()

  val dbmaintainer: ActorRef = system.actorOf(DBMaintainer.props(
    this,
    singleStores,
    settings
  ), "db-maintainer")

  def destroy(): Unit = {
    dropDB()
    db.close()

    // Try to remove the db file
    val confUrl = slick.util.GlobalConfig.profileConfig(dbConfigName).getString("url")
    // XXX: trusting string operations
    val dbPath = confUrl.split(":").lastOption.getOrElse("")

    val fileExt = dbPath.split(".").lastOption.getOrElse("")
    if (fileExt == "sqlite3" || fileExt == "db")
      new File(dbPath).delete()
  }
}

class StubDB(val singleStores: SingleStores, val system: ActorSystem, val settings: OmiConfigExtension) extends DB {

  import scala.concurrent.ExecutionContext.Implicits.global

  def initialize(): Unit = Unit;

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
    val fp2iis = (singleStores.hierarchyStore ? GetTree).mapTo[ImmutableODF].map(_.getInfoItems.collect {
      case ii: InfoItem if leafPaths.exists { path: Path => path.isAncestorOf(ii.path) || path == ii.path } =>
        ii.path -> ii
    }.toMap)
    val objectsWithValues: Future[ImmutableODF] = for {
      p2iis <- fp2iis
      pathToValue: Seq[(Path, Value[Any])] <- (singleStores.latestStore ? MultipleReadCommand(p2iis.keys.toVector))
        .mapTo[Seq[(Path, Value[Any])]]
      objectsWithValues = ImmutableODF(pathToValue.flatMap {
        case (path: Path, value: Value[Any]) =>
          p2iis.get(path).map {
            ii: InfoItem =>
              ii.copy(
                names = Vector.empty,
                descriptions = Set.empty,
                metaData = None,
                values = Vector(value)
              )
          }
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


/**
  * Database class to be used during tests instead of production db to prevent
  * problems caused by overlapping test data.
  * Uses h2 named in-memory db
  *
  * @param name name of the test database, optional. Data will be stored in memory
  */
class TestDB(
              val name: String = "",
              useMaintainer: Boolean = true,
              val config: Config = ConfigFactory.load(
                ConfigFactory.parseString(
                  """
slick-config {
  driver = "slick.driver.H2Driver$"
  db {
    url = "jdbc:h2:mem:test1"
    driver = org.h2.Driver
    connectionPool = disabled
    keepAliveConnection = true
    connectionTimeout = 15s
  }
}
"""
                )).withFallback(ConfigFactory.load()),
              val configName: String = "slick-config")(
              protected val system: ActorSystem,
              protected val singleStores: SingleStores,
              protected val settings: OmiConfigExtension
            ) extends OdfDatabase with DB {

  override protected val log: Logger = LoggerFactory.getLogger("TestDB")
  log.debug("Creating TestDB: " + name)
  override val dc = DatabaseConfig.forConfig[JdbcProfile](configName, config)
  val db = dc.db

  initialize()

  val dbmaintainer: ActorRef = if (useMaintainer) {
    system.actorOf(DBMaintainer.props(
      this,
      singleStores,
      settings
    ), "db-maintainer")
  } else ActorRef.noSender

  /**
    * Should be called after tests.
    */
  def destroy(): Unit = {
    if (useMaintainer) system.stop(dbmaintainer)
    log.debug("Removing TestDB: " + name)

    db.close()
  }
}


/**
  * Database trait used by db classes.
  * Contains a public high level read-write interface for the database tables.
  */
trait DB {
  def initialize(): Unit

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
  def getNBetween(
                   requests: Iterable[Node],
                   begin: Option[Timestamp],
                   end: Option[Timestamp],
                   newest: Option[Int],
                   oldest: Option[Int])(implicit timeout: Timeout): Future[Option[ODF]]

  /**
    * Used to set many values efficiently to the database.
    *
    * @param data list item to be added consisting of Path and OdfValue[Any] tuples.
    */
  def writeMany(data: Seq[InfoItem]): Future[OmiReturn]

  def writeMany(odf: ImmutableODF): Future[OmiReturn] = {
    writeMany(odf.getNodes.collect { case ii: InfoItem => ii })
  }

  /**
    * Used to remove given path and all its descendants from the database.
    *
    * @param path Parent path to be removed.
    */
  def remove(path: Path)(implicit timeout: Timeout): Future[Seq[Int]]
}

trait TrimmableDB {
  def trimDB(): Future[Seq[Int]]
}
