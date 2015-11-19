/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package database

import types.OdfTypes.OdfInfoItem
import slick.driver.H2Driver.api._
import org.prevayler.PrevaylerFactory

import java.io.File

import LatestValues.LatestStore
import http.Boot.settings
import types.OdfTypes.OdfValue
import types.Path
import types.OdfTypes.fromPath

import collection.JavaConversions.asJavaIterable


package object database {

  private[this] var setEventHooks: List[Seq[OdfInfoItem] => Unit] = List()

  /**
   * Set hooks are run when new data is saved to database.
   * @param f Function that takes the updated paths as parameter.
   */
  def attachSetHook(f: Seq[OdfInfoItem] => Unit) =
    setEventHooks = f :: setEventHooks
  def getSetHooks = setEventHooks

  private[this] var histLength = 10
  /**
   * Sets the historylength to desired length
   * default is 10
   * @param newLength new length to be used
   */
  def setHistoryLength(newLength: Int) {
    histLength = newLength
  }
  def historyLength = histLength

  val dbConfigName = "h2-conf"

}
import database._


/**
 * Contains all stores that requires only one instance for interfacing
 */
object SingleStores {
    val latestStore = PrevaylerFactory.createPrevayler(LatestValues.empty, settings.journalsDirectory)
    val eventPrevayler = PrevaylerFactory.createPrevayler(EventSubs.empty, settings.journalsDirectory)
    val intervalPrevayler = PrevaylerFactory.createPrevayler(IntervalSubs.empty, settings.journalsDirectory)
    val idPrevayler = PrevaylerFactory.createPrevayler(SubIds(0), settings.journalsDirectory)

    /**
     * Main function for handling incoming data and running all event-based subscriptions.
     *  As a side effect, updates the internal latest value store.
     *  Event callbacks are sent seperately for each *changed* value and .
     * @param path Path to incoming data
     * @param newValue Actual incoming data
     * @return Triggered responses
     */
    def processEvents(path: Path, newValue: OdfValue): Seq[(EventSub, parsing.xmlGen.xmlTypes.RequestResultType)] = {
      lazy val esubs = eventPrevayler execute LookupEventSubs(path)
      // TODO: attach or other events
      // lazy val onChange = esubs.filter{???} 

      val oldValueOpt = latestStore execute LookupSensorData(path)

      oldValueOpt match {
        case Some(oldValue) =>
          if (oldValue.timestamp before newValue.timestamp) {
            val onChangeResponses =
              if (oldValue.value != newValue.value) {
                esubs.map{ esub =>
                  val resp = responses.Results.odf("200", None, Some(esub.id.toString),
                    fromPath(OdfInfoItem(path, Iterable(newValue)))
                  )
                  (esub, resp)
                }
              } else Seq.empty

            latestStore execute SetSensorData(path, newValue)

            onChangeResponses
          } else Seq.empty
        case None => 
          // TODO: Attach events
          Seq.empty
      }

    }

}


/**
 * Database class for sqlite. Actually uses config parameters through forConfig.
 * To be used during actual runtime.
 */
class DatabaseConnection extends DB {
  val db = Database.forConfig(dbConfigName)
  initialize()

  def destroy() = {
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
class TestDB(val name:String = "") extends DB
{
  println("Creating TestDB: " + name)
  val db = Database.forURL(s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver",
    keepAliveConnection=true)
  initialize()

  /**
  * Should be called after tests.
  */
  def destroy() = {
    println("Removing TestDB: " + name)
    db.close()
  }
}




/**
 * Database trait used by db classes.
 * Contains a public high level read-write interface for the database tables.
 */
trait DB extends DBReadWrite with DBBase {
  /**
   * These are old ideas about reducing access to read only
   */
  def asReadOnly: DBReadOnly = this
  def asReadWrite: DBReadWrite = this

  /**
   * Fast latest values storage interface
   */
  //val latestStore: LatestStore = SingleStores.latestStore
  //val eventPrevayler = SingleStores.eventPrevayler
  //val intervalPrevayler = SingleStores.intervalPrevayler
  //val idPrevayler: LatestStore = SingleStores.idPrevayler

}
