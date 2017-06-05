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
import com.typesafe.config.{ConfigFactory,Config}
import akka.actor.{ActorRef,ActorSystem}
import org.slf4j.LoggerFactory
import org.prevayler.PrevaylerFactory
import parsing.xmlGen.xmlTypes.MetaDataType
import slick.backend.DatabaseConfig
//import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.OmiReturn
import types.Path
import http.{ActorSystemContext, Settings, Storages}


package object database {

  private[this] var histLength = 15 //http.Boot.settings.numLatestValues
  /**
   * Sets the historylength to desired length
   * default is 10
   * @param newLength new length to be used
   */
  def changeHistoryLength(newLength: Int): Unit = {
    histLength = newLength
  }
  def historyLength: Int = histLength

  val dbConfigName = "dbconf"

}
//import database.database._
//import database.database.dbConfigName
import database.dbConfigName
sealed trait InfoItemEvent {
  val infoItem: OdfInfoItem
}

/**
 * Value of the InfoItem is changed and the new has newer timestamp. Event subs should be triggered.
 * Not a case class because pattern matching didn't work as expected (this class is extended).
 */
class ChangeEvent(val infoItem: OdfInfoItem) extends InfoItemEvent {
  override def toString: String = s"ChangeEvent($infoItem)"
  override def hashCode: Int = infoItem.hashCode
}
object ChangeEvent {
  def apply(ii: OdfInfoItem): ChangeEvent = new ChangeEvent(ii)
  def unapply(ce: ChangeEvent): Option[OdfInfoItem] = Some(ce.infoItem)
}

/**
 * Received new value with newer timestamp but value is the same as the previous
 */
case class SameValueEvent(infoItem: OdfInfoItem) extends InfoItemEvent

/*
 * New InfoItem (is also ChangeEvent)
 */
case class AttachEvent(override val infoItem: OdfInfoItem) extends ChangeEvent(infoItem) with InfoItemEvent

/**
 * Contains all stores that requires only one instance for interfacing
 */
class SingleStores(protected val settings: OmiConfigExtension) {
  private[this] def createPrevayler[P](in: P, name: String) = {
    if(settings.writeToDisk) {
      val journalFileSizeLimit = settings.maxJournalSizeBytes
      val factory = new PrevaylerFactory[P]()

      val directory = new File(settings.journalsDirectory++s"/$name")
      prevaylerDirectories += directory

      // Configure factory settings
      // Change size thereshold so we can remove the old journal files as we take snapshots.
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
  val prevaylerDirectories = ArrayBuffer[File]()

  val latestStore       = createPrevayler(LatestValues.empty, "latestStore")
  val hierarchyStore    = createPrevayler(OdfTree.empty, "hierarchyStore")
  val subStore          = createPrevayler(Subs.empty,"subscriptionStore")
  val pollDataPrevayler = createPrevayler(PollSubData.empty, "pollDataPrevayler")
  val idPrevayler       = createPrevayler(SubIds(0), "idPrevayler")
  subStore execute RemoveWebsocketSubs()

  def buildOdfFromValues(items: Seq[(Path,OdfValue[Any])]): OdfObjects = {

    val odfObjectsTrees = items map { case (path, value) =>
      val infoItem = OdfInfoItem(path, OdfTreeCollection(value))
      createAncestors(infoItem)
    }
  odfObjectsTrees.par.reduceOption(_ union _).getOrElse(OdfObjects())
  }


  /**
   * Logic for updating values based on timestamps.
   * If timestamp is same or the new value timestamp is after old value return true else false
   *
   * @param oldValue old value(from latestStore)
   * @param newValue the new value to be added
   * @return
   */
  def valueShouldBeUpdated(oldValue: OdfValue[Any], newValue: OdfValue[Any]): Boolean = {
    oldValue.timestamp before newValue.timestamp
  }


  /**
   * Main function for handling incoming data and running all event-based subscriptions.
   *  As a side effect, updates the internal latest value store.
   *  Event callbacks are not sent for each changed value, instead event results are returned 
   *  for aggregation and other extra functionality.
   * @param path Path to incoming data
   * @param newValue Actual incoming data
   * @return Triggered responses
   */
  def processData(path: Path, newValue: OdfValue[Any], oldValueOpt: Option[OdfValue[Any]]): Option[InfoItemEvent] = {

    // TODO: Replace metadata and description if given

    oldValueOpt match {
      case Some(oldValue) =>
        if (valueShouldBeUpdated(oldValue, newValue)) {
          // NOTE: This effectively discards incoming data that is older than the latest received value
          if (oldValue.value != newValue.value) {
            Some(ChangeEvent(OdfInfoItem(path, Iterable(newValue))))
          } else {
            // Value is same as the previous
            Some(SameValueEvent(OdfInfoItem(path, Iterable(newValue))))
          }

        } else None  // Newer data found

      case None =>  // no data was found => new sensor
        val newInfo = OdfInfoItem(path, Iterable(newValue))
        Some(AttachEvent(newInfo))
    }

  }


  def getMetaData(path: Path) : Option[OdfMetaData] = {
    (hierarchyStore execute GetTree()).get(path).collect{
      case OdfInfoItem(_,_,_,Some(mData),_,_) => mData
    }
  }

  def getSingle(path: Path) : Option[OdfNode] ={
    (hierarchyStore execute GetTree()).get(path).map{
      case info : OdfInfoItem => 
        latestStore execute LookupSensorData(path) match {
          case Some(value) =>
            OdfInfoItem(path, Iterable(value) ) 
          case None => 
            info
        }
          case objs : OdfObjects => 
            objs.copy(objects = objs.objects map (o => OdfObject(o.id, o.path,typeValue = o.typeValue)))
          case obj : OdfObject => 
            obj.copy(
              objects = obj.objects map (o => OdfObject(o.id, o.path, typeValue = o.typeValue)),
              infoItems = obj.infoItems map (i => OdfInfoItem(i.path)),
              typeValue = obj.typeValue
            )
    }
  } 
}


/**
 * Database class for sqlite. Actually uses config parameters through forConfig.
 * To be used during actual runtime.
 */
class DatabaseConnection()(
  protected val system : ActorSystem,
  protected val singleStores : SingleStores,
  protected val settings : OmiConfigExtension
  ) extends DBCachedReadWrite with DBBase with DB {

  //val dc = DatabaseConfig.forConfig[JdbcProfile](dbConfigName)
  val dc : DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  val db = dc.db
  //val db = Database.forConfig(dbConfigName)
  initialize()

  val dbmaintainer = system.actorOf(DBMaintainer.props(
    this,
    singleStores,
    settings
    ), "db-maintainer")

  def destroy(): Unit = {
     dropDB()
     db.close()

     // Try to remove the db file
     val confUrl = slick.util.GlobalConfig.driverConfig(dbConfigName).getString("url")
     // XXX: trusting string operations
     val dbPath = confUrl.split(":").lastOption.getOrElse("")

     val fileExt = dbPath.split(".").lastOption.getOrElse("")
     if (fileExt == "sqlite3" || fileExt == "db")
       new File(dbPath).delete()
  }
}


/**
 * Database class to be used during tests instead of production db to prevent
 * problems caused by overlapping test data.
 * Uses h2 named in-memory db
 * @param name name of the test database, optional. Data will be stored in memory
 */
class UncachedTestDB(
  val name:String = "", 
  useMaintainer: Boolean = true, 
  val config: Config = ConfigFactory.load(
    ConfigFactory.parseString("""
dbconf {
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
  val configName: String = "dbconf")(
  protected val system : ActorSystem,
  protected val singleStores : SingleStores,
  protected val settings : OmiConfigExtension
) extends DBReadWrite with DB {

  override protected val log = LoggerFactory.getLogger("UncachedTestDB")
  log.debug("Creating UncachedTestDB: " + name)
  override val dc = DatabaseConfig.forConfig[JdbcProfile](configName,config)
  import dc.driver.api._
  val db = dc.db
   // Database.forURL(url, driver = driver,
   // keepAliveConnection=true)
  initialize()

  val dbmaintainer = if( useMaintainer) {
    system.actorOf(DBMaintainer.props(
    this,
    singleStores,
    settings
    ), "uncached-db-maintainer")
  } else ActorRef.noSender
  /**
  * Should be called after tests.
  */
  def destroy(): Unit = {
    if(useMaintainer )system.stop(dbmaintainer)
    log.debug("Removing UncachedTestDB: " + name)
    db.close()
  }
}


/**
 * Database class to be used during tests instead of production db to prevent
 * problems caused by overlapping test data.
 * Uses h2 named in-memory db
 * @param name name of the test database, optional. Data will be stored in memory
 */
class TestDB(
  val name:String = "", 
  useMaintainer: Boolean = true, 
  val config: Config = ConfigFactory.load(
    ConfigFactory.parseString("""
dbconf {
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
  val configName: String = "dbconf")(
  protected val system : ActorSystem,
  protected val singleStores : SingleStores,
  protected val settings : OmiConfigExtension
) extends DBCachedReadWrite with DB {

  override protected val log = LoggerFactory.getLogger("TestDB")
  log.debug("Creating TestDB: " + name)
  override val dc = DatabaseConfig.forConfig[JdbcProfile](configName,config)
  import dc.driver.api._
  val db = dc.db
   // Database.forURL(url, driver = driver,
   // keepAliveConnection=true)
  initialize()

  val dbmaintainer = if( useMaintainer) { system.actorOf(DBMaintainer.props(
    this,
    singleStores,
    settings
    ), "db-maintainer")
  } else ActorRef.noSender
  /**
  * Should be called after tests.
  */
  def destroy(): Unit = {
    if(useMaintainer )system.stop(dbmaintainer)
    log.debug("Removing TestDB: " + name)
    db.close()
  }
}




/**
 * Database trait used by db classes.
 * Contains a public high level read-write interface for the database tables.
 */
trait DB {
  /**
   * Used to get result values with given constrains in parallel if possible.
   * first the two optional timestamps, if both are given
   * search is targeted between these two times. If only start is given,all values from start time onwards are
   * targeted. Similiarly if only end is given, values before end time are targeted.
   *    Then the two Int values. Only one of these can be present. fromStart is used to select fromStart number
   * of values from the begining of the targeted area. Similiarly from ends selects fromEnd number of values from
   * the end.
   * All parameters except the first are optional, given only the first returns all requested data
   *
   * @param requests SINGLE requests in a list (leafs in request O-DF); InfoItems, Objects and MetaDatas
   * @param begin optional start Timestamp
   * @param end optional end Timestamp
   * @param newest number of values to be returned from start
   * @param oldest number of values to be returned from end
   * @return Combined results in a O-DF tree
   */
  def getNBetween(
    requests: Iterable[OdfNode],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]): Future[Option[OdfObjects]]

  /**
   * Used to set many values efficiently to the database.
   * @param data list item to be added consisting of Path and OdfValue[Any] tuples.
   */
  def writeMany(data: Seq[OdfInfoItem]): Future[OmiReturn]

  /**
   * Used to remove given path and all its descendants from the databas.
   * @param path Parent path to be removed.
   */
  def remove(path: Path): Future[Seq[Int]]
}
