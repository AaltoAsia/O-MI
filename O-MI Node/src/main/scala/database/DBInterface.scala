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
//import database.database._
import database.dbConfigName

sealed trait InfoItemEvent {
  val infoItem: OdfInfoItem
}

/*
 * Value of the InfoItem is changed and the new has newer timestamp. Event subs should be triggered.
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
        PrevaylerFactory.createPrevayler[P](in, settings.journalsDirectory++s"/$name")
      } else {
        PrevaylerFactory.createTransientPrevayler[P](in)
      }
    }
    lazy val latestStore       = createPrevayler(LatestValues.empty, "latestStore")//PrevaylerFactory.createPrevayler(LatestValues.empty, settings.journalsDirectory++"/latestStore")
    lazy val hierarchyStore    = createPrevayler(OdfTree.empty, "hierarchyStore")//PrevaylerFactory.createPrevayler(OdfTree.empty,      settings.journalsDirectory++"/hierarchyStore")
    lazy val eventPrevayler    = createPrevayler(EventSubs.empty, "eventPrevayler")//PrevaylerFactory.createPrevayler(EventSubs.empty,    settings.journalsDirectory++"/eventPrevayler")
    lazy val intervalPrevayler = createPrevayler(IntervalSubs.empty, "intervalpPrevayler")//PrevaylerFactory.createPrevayler(IntervalSubs.empty, settings.journalsDirectory++"/intervalPrevayler")
    lazy val pollPrevayler     = createPrevayler(PolledSubs.empty, "pollPrevayler")//PrevaylerFactory.createPrevayler(PolledSubs.empty,   settings.journalsDirectory++"/pollPrevayler")
    lazy val idPrevayler       = createPrevayler(SubIds(0), "idPrevayler")//)PrevaylerFactory.createPrevayler(SubIds(0),          settings.journalsDirectory++"/idPrevayler")

    def buildOdfFromValues(items: Seq[(Path,OdfValue)]): OdfObjects = {

      val odfObjectsTrees = items map { case (path, value) =>
        val infoItem = OdfInfoItem(path, OdfTreeCollection(value))
        fromPath(infoItem)
      }
      // safe version of reduce, might be a bit slow way to construct the result
      //val valueOdfTree =
      odfObjectsTrees.headOption map { head =>
        odfObjectsTrees.par.reduce(_ union _)
      } getOrElse (OdfObjects())

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
          if (oldValue.timestamp before newValue.timestamp) {
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

  /**
   * Fast latest values storage interface
   */
  //val latestStore: LatestStore = SingleStores.latestStore
  //val eventPrevayler = SingleStores.eventPrevayler
  //val intervalPrevayler = SingleStores.intervalPrevayler
  //val idPrevayler: LatestStore = SingleStores.idPrevayler

}
