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

import java.io.File

import http.Boot.settings
import org.prevayler.PrevaylerFactory
import slick.driver.H2Driver.api._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.Path
import collection.mutable.ArrayBuffer


package object database {

  private[this] var histLength = 15 //http.Boot.settings.numLatestValues
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
//import database.database._
import database.dbConfigName

sealed trait InfoItemEvent {
  val infoItem: OdfInfoItem
}

/*
 * Value of the InfoItem is changed and the new has newer timestamp. Event subs should be triggered.
 * Not a case class because pattern matching didn't work as expected.
 */
class ChangeEvent(val infoItem: OdfInfoItem) extends InfoItemEvent
object ChangeEvent {
  def apply(ii: OdfInfoItem) = new ChangeEvent(ii)
  def unapply(ce: ChangeEvent) = Some(ce.infoItem)
}


/*
 * New InfoItem (is also ChangeEvent)
 */
case class AttachEvent(override val infoItem: OdfInfoItem) extends ChangeEvent(infoItem) with InfoItemEvent

/**
 * Contains all stores that requires only one instance for interfacing
 */
object SingleStores {
    def createPrevayler[P](in: P, name: String) = {
      if(settings.writeToDisk) {
        val factory = new PrevaylerFactory[P]()

        // Change size thereshold so we can remove the old journal files as we take snapshots.
        // Otherwise it will continue to fill disk space
        factory.configureJournalFileSizeThreshold(100000000) // about 100M

        val directory = new File(settings.journalsDirectory++s"/$name")
        prevaylerDirectories += directory
        PrevaylerFactory.createPrevayler[P](in, directory.getAbsolutePath)
      } else {
        PrevaylerFactory.createTransientPrevayler[P](in)
      }
    }
    /** List of all prevayler directories. Currently used for removing unnecessary files in these directories */
    val prevaylerDirectories = ArrayBuffer[File]()

    val latestStore       = createPrevayler(LatestValues.empty, "latestStore")
    val hierarchyStore    = createPrevayler(OdfTree.empty, "hierarchyStore")
    val eventPrevayler    = createPrevayler(EventSubs.empty, "eventPrevayler")
    val intervalPrevayler = createPrevayler(IntervalSubs.empty, "intervalpPrevayler")
    val pollPrevayler     = createPrevayler(PolledSubs.empty, "pollPrevayler")
    val idPrevayler       = createPrevayler(SubIds(0), "idPrevayler")

    def buildOdfFromValues(items: Seq[(Path,OdfValue)]): OdfObjects = {

      val odfObjectsTrees = items map { case (path, value) =>
        val infoItem = OdfInfoItem(path, OdfTreeCollection(value))
        fromPath(infoItem)
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
    def valueShouldBeUpdated(oldValue: OdfValue, newValue: OdfValue): Boolean = {
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
    def processData(path: Path, newValue: OdfValue, oldValueOpt: Option[OdfValue]): Option[InfoItemEvent] = {

      // TODO: Replace metadata and description if given

      oldValueOpt match {
        case Some(oldValue) =>
          if (valueShouldBeUpdated(oldValue, newValue)) {
            val onChangeData =
              if (oldValue.value != newValue.value) {
                    Some(ChangeEvent(OdfInfoItem(path, Iterable(newValue))))
              } else None  // Value is same as the previous

            // NOTE: This effectively discards incoming data that is older than the latest received value
            latestStore execute SetSensorData(path, newValue)

            onChangeData
          } else None  // Newer data found

        case None =>  // no data was found => new sensor
          latestStore execute SetSensorData(path, newValue)
          val newInfo = OdfInfoItem(path, Iterable(newValue))
          Some(AttachEvent(newInfo))
      }

    }


  def getMetaData(path: Path) : Option[OdfMetaData] = {
    (hierarchyStore execute GetTree()).get(path).collect{ 
      case info : OdfInfoItem => info.metaData
    }.flatten
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


}
