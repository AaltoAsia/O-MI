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

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import http.OmiConfigExtension
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import types.omi.OmiReturn
import types.Path
import types.odf._

import scala.concurrent.Future

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
case class SameValueEvent(override val infoItem: InfoItem) extends ChangeEvent(infoItem)

/*
 * New InfoItem (is also ChangeEvent)
 */
case class AttachEvent(override val infoItem: InfoItem) extends ChangeEvent(infoItem) with InfoItemEvent



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
                   oldest: Option[Int],
                   maxLevels: Option[Int]
                 )(implicit timeout: Timeout): Future[Option[ODF]]

  /**
    * Used to set many values efficiently to the database.
    *
    * @param data list item to be added consisting of Path and OdfValue[Any] tuples.
    */
  def writeMany(data: Seq[InfoItem]): Future[OmiReturn]

  def writeMany(odf: ImmutableODF): Future[OmiReturn] = {
    writeMany(odf.getNodes.collect { case ii: InfoItem => ii }.toSeq)
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
