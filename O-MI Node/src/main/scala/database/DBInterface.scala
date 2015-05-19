package database
import slick.driver.SQLiteDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import slick.jdbc.StaticQuery.interpolation
import slick.lifted.ProvenShape
import java.io.File
import scala.collection.mutable.Map
import scala.collection.mutable.Buffer
import java.sql.Timestamp
import slick.jdbc.StaticQuery
import slick.jdbc.meta.MTable

import parsing.Types._
import parsing.Types.OdfTypes._



package object database {

  private var histLength = 10
  private var setEventHooks: List[Seq[Path] => Unit] = List()

  implicit val pathColumnType = MappedColumnType.base[Path, String](
    { _.toString }, // Path to String
    { Path(_) }     // String to Path
    )
  
  def attachSetHook(f: Seq[Path] => Unit) =
    setEventHooks = f :: setEventHooks
  def getSetHooks = setEventHooks

  /**
   * sets the historylength to desired length
   * default is 10
   *
   * @param newLength new length to be used
   */
  def setHistoryLength(newLength: Int) {
    histLength = newLength
  }
  def historyLength = histLength

  /** compatibility method for [[DB.set]] */
  def set(implicit dbo: DB) = dbo.set _

  def startBuffering(path: Path)(implicit dbo: DB):Boolean = dbo.startBuffering(path)

}


/**
 * Changed to using single connection because of sqlite.
 * Might work also with many Database.forConfig calls but it's not tested.
 */
object singleConnection extends DB {
  val db = Database.forConfig("sqlite-conf")
  initialize()

  def destroy() = {
    db.close()
  }
}

/**
 * Database class for sqlite. Actually uses config parameters through forConfig in singleConnection.
 * To be used during actual runtime.
 */
class SQLiteConnection extends DB {
  override val db = singleConnection.db

  def destroy() = {
     db.close()

     val confUrl = slick.util.GlobalConfig.driverConfig("sqlite-conf").getString("url")
     // XXX: trusting string operations
     val dbPath = confUrl.split(":").last

     if (dbPath.split(".").lastOption.getOrElse("") == "sqlite3")
       new File(dbPath).delete()
  }
}



/**
 * Database class to be used during tests instead of object SQLite to prevent
 * problems caused by overlapping test data.
 * @param name name of the test database, optional. Data will be stored in memory
 */
class TestDB(val name:String = "") extends DB
{
  val db = Database.forURL("jdbc:sqlite::memory:", driver="org.sqlite.JDBC")
  initialize()
  def destroy() = {
     db.close()
  }
}




/**
 * base Database trait used in object SQLite and class testDB 
 */
trait DB {
  import database._
  protected val db: Database

  implicit val dbo = this

  //tables for latest values and hierarchy
  private val latestValues = TableQuery[DBData]//table for sensor data
  private val objects = TableQuery[DBNode]//table for storing hierarchy
  private val subs = TableQuery[DBSubscription]//table for storing subscription information
  private val buffered = TableQuery[BufferedPath]//table for aa currently buffered paths
  private val meta = TableQuery[DBMetaData]//table for metadata information


  private def runSync[R]: DBIOAction[R, NoStream, Nothing] => R =
    io => Await.result(db.run(io), Duration.Inf)

  private def runWait: DBIOAction[_, NoStream, Nothing] => Unit =
    io => Await.ready(db.run(io), Duration.Inf)


  /**
  * Initializing method, creates the file and tables. 
  */
  protected def initialize(){
    val setup = DBIO.seq(
      latestValues.schema.create,
      objects.schema.create,
      subs.schema.create,
      buffered.schema.create,
      meta.schema.create
    )
    
    val existingTables = MTable.getTables

    runSync(existingTables).headOption match {
      case Some(table) =>
        //noop
        println("Not creating tables, found table: " + table.name.name)
      case None =>
        // run transactionally so there are all or no tables
        runSync(setup.transactionally)
    }
  }


 /**
  * Metohod to completely remove database. Removes the actual database file.
  * Should not be called on object SQLite
  * Should be called after tests when using a database created from the testDB class
  */
  def destroy(): Unit


  /**
   * Used to set values to database. If data already exists for the path, appends until historyLength
   * is met, otherwise creates new data and all the missing objects to the hierarchy.
   *  Does not remove excess rows if path is set ot buffer
   *
   *  @param data sensordata, of type DBSensor to be stored to database.
   *  @return boolean whether added data was new
   */
  def set(data: DBSensor) = {
      var count = 0
      var buffering = false
      //search database for sensor's path
      val pathQuery = latestValues.filter(_.path === data.path)
      buffering = runSync(buffered.filter(_.path === data.path).result).length > 0
      //appends a row to the latestvalues table
      count = runSync(pathQuery.result).length
      runSync(DBIO.seq(latestValues += (data.path, data.value, data.time)))
      // Call hooks
      val argument = Seq(data.path)
      getSetHooks foreach { _(argument) }

      if (count > historyLength && !buffering) {
        //if table has more than historyLength and not buffering, remove excess data
        removeExcess(data.path)
        false
      } else if (count == 0) {
        //add missing objects for the hierarchy since this is a new path
        addObjects(data.path)
        true
      } else {
        //existing path and less than history length of data or buffering.
        false
      }
  }


  /**
   * Used to store metadata for a sensor to database
   * @param path path to sensor
   * @param data metadata to be stored as string e.g a XML block as string
   * 
   */
  def setMetaData(path:Path,data:String) = {
    val qry = meta.filter(_.path === path).map(_.data)
    val count = runSync(qry.result).length
    if(count == 0)
    {
      runSync(meta += (path,data))
    }
    else
    {
      runSync(qry.update(data))
    }
  }


  /**
   * Used to get metadata from database for given path
   * @param path path to sensor whose metadata is requested
   * 
   * @return metadata as Option[String], none if no data is found
   */
  def getMetaData(path:Path):Option[String]=
  {
    val qry = meta.filter(_.path === path).map(_.data)
    runSync(qry.result).headOption
  }
  def RemoveMetaData(path:Path)=
  {
    val qry = meta.filter(_.path === path)
    runSync(qry.delete)
  }


  /**
   * Used to set many values efficiently to the database.
   * @param data list of tuples consisting of path and TimedValue.
   */
  def setMany(data: List[(Path, OdfValue)]): Unit = {
    var add = Seq[(Path,String,Timestamp)]()  // accumulator: dbobjects to add

    // Reformat data and add missing timestamps
    data.foreach {
      case (path: Path, v: OdfValue) =>

         // Call hooks
        val argument = Seq(path)
        getSetHooks foreach { _(argument) }

        lazy val newTimestamp = new Timestamp(new java.util.Date().getTime)
        add = add :+ (path, v.value, v.timestamp.getOrElse(newTimestamp))
    }

    // Add to latest values in a transaction
    runSync((latestValues ++= add).transactionally)

    // Add missing hierarchy and remove excess buffering
    var onlyPaths = data.map(_._1).distinct
    onlyPaths foreach{p =>
        val path = Path(p)

        var pathQuery = objects.filter(_.path === path)
        val len = runSync(pathQuery.result).length
        if (len == 0) {
          addObjects(path)
        }

        var buffering = runSync(buffered.filter(_.path === path).result).length > 0
        if (!buffering) {
          removeExcess(path)
        }
    }
  }


  /**
   * Remove is used to remove sensor given its path. Removes all unused objects from the hierarchcy along the path too.
   *
   *
   * @param path path to to-be-deleted sensor. If path doesn't end in sensor, does nothing.
   * @return boolean whether something was removed
   */
  def remove(path: Path): Boolean = {
    //search database for given path
    val pathQuery = latestValues.filter(_.path === path)
    var deleted = false
    //if found rows with given path remove else path doesn't exist and can't be removed
    if (runSync(pathQuery.result).length > 0) {
      runSync(pathQuery.delete)
      deleted = true;
    }
    if (deleted) {
      //also delete objects from hierarchy that are not used anymore.
      // start from sensors path and proceed upward in hierarchy until object that is shared by other sensor is found,
      //ultimately the root. path/to/sensor/temp -> path/to/sensor -> ..... -> "" (root)
      var testPath = path
      while (!testPath.isEmpty) {
        if (getChilds(testPath).length == 0) {
          //only leaf nodes have 0 childs. 
          var pathQueryObjects = objects.filter(_.path === testPath)
          runSync(pathQueryObjects.delete)
          testPath = testPath.dropRight(1)
        } else {
          //if object still has childs after we deleted one it is shared by other sensor, stop removing objects
          //exit while loop
          testPath = Path("")
        }
      }
    }
    return deleted
  }


  /**
   * Returns array of DBSensors for given subscription id.
   * Array consists of all sensor values after beginning of the subscription
   * for all the sensors in the subscription
   * returns empty array if no data or subscription is found
   *
   * @param id subscription id that is assigned during saving the subscription
   * @param testTime optional timestamp value to indicate end time of subscription,
   * should only be needed during testing. Other than testing None should be used
   *
   * @return Array of DBSensors
   */
  def getSubData(id: Int, testTime: Option[Timestamp]): Array[DBSensor] =
    {
      var result = Buffer[DBSensor]()
      var subQuery = subs.filter(_.ID === id)
      var info: (Timestamp, Double) = (null, 0.0) //to gather only needed info from the query
      var paths = Array[String]()

      var str = runSync(subQuery.result)
      if (str.length > 0) {
        var sub = str.head
        info = (sub._3, sub._5)
        paths = sub._2.split(";")
      }
      paths.foreach {
        p =>
          result ++= DataFormater.FormatSubData(Path(p), info._1, info._2, testTime)
      }
      result.toArray
    }

  def getSubData(id: Int): Array[DBSensor] = getSubData(id, None)

  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private def removeExcess(path: Path) =
    {
      var pathQuery = latestValues.filter(_.path === path)
      var qry = runSync(pathQuery.sortBy(_.timestamp).result)
      var count = qry.length
      if (count > historyLength) {
        val oldtime = qry.drop(count - historyLength).head._3
        runSync(pathQuery.filter(_.timestamp < oldtime).delete)
      }
    }


  /**
   * put the path to buffering table if it is not there yet, otherwise
   * increases the count on that item, to prevent removing buffered data
   * if one subscription ends and other is still buffering.
   *
   * @param path path as Path object
   * 
   */
  def startBuffering(path: Path):Boolean = {
    val pathQuery = buffered.filter(_.path === path)
    var len = runSync(pathQuery.result).length
    if (len == 0) {
      runSync(buffered += (path, 1))
      true
    } else {
      runSync(pathQuery.map(_.count).update(len + 1))
      false
    }
  }


  /**
   * removes the path from buffering table or dimishes the count by one
   * also clear all buffered data if count is only 1
   * leaves only historyLength amount of data if count is only 1
   * 
   * @param path path as Path object
   */
  def stopBuffering(path: Path):Boolean = {
      val pathQuery = buffered.filter(_.path === path)
      val str = runSync(pathQuery.result)
      var len = str.length
      if (len > 0) {
        if (str.head._2 > 1) {
          runSync(pathQuery.map(_.count).update(len - 1))
          false
        } else {
          runSync(pathQuery.delete)
          removeExcess(path)
          true
        }
      } else {
        false
      }
  }


  /**
   * Used to get data from database based on given path.
   * returns Some(DBSensor) if path leads to sensor and if
   * path leads to object returns Some(DBObject). DBObject has
   * variable childs of type Array[DBItem] which stores object's childs.
   * object.childs(0).path to get first child's path
   * if nothing is found for given path returns None
   *
   * @param path path to search data from
   *
   * @return either Some(DBSensor),Some(DBObject) or None based on where the path leads to
   */
  def get(path: Path): Option[DBItem] =
  {
    //search database for given path
    val pathQuery = latestValues.filter(_.path === path)
    //if path is found from latest values it must be Sensor otherwise check if it is an object
    val qry = runSync(pathQuery.sortBy(_.timestamp).result)
    if (qry.length > 0) {
      //path is sensor
      //case class matching
      qry.last match {
        case (path: Path, value: String, time: java.sql.Timestamp) =>
          return Some(DBSensor(path, value, time))
      }
    } else {
      //LatestValues  only contains sensor values, so if empty either path doesn't exist or is object
      val hierarchyQuery = objects.filter(_.path === path)
      val hierQry = runSync(hierarchyQuery.result) 
      //query is empty if no object is found in path
      if(hierQry.nonEmpty){
        var childs = getChilds(path)
        //therefore childs is only empty if given path doesn't exist
        //path is an object
        //create object and give it reference to its childs
        var obj = DBObject(path)

        if (!childs.isEmpty) 
          obj.childs = childs
        
        return Some(obj)
      }
      return None
    }
  }


  /**
   * Adds missing objects(if any) to hierarchy based on given path
   * @param path path whose hierarchy is to be stored to database
   *
   */
  private def addObjects(path: Path) {
    val parentsAndPath: Seq[Path] = path.tail.scanLeft(Path(path.head))(Path(_) / _)
    var parent = Path("")
      for (fullpath <- parentsAndPath) {
        if (!hasObject(fullpath)) {
          runSync(objects += (fullpath, parent, fullpath.last))
        }
        parent = fullpath
      }
    
  }


  /**
   * Used to get sensor values with given constrains. first the two optional timestamps, if both are given
   * search is targeted between these two times. If only start is given,all values from start time onwards are
   * targeted. Similiarly if only end is given, values before end time are targeted.
   *    Then the two Int values. Only one of these can be present. fromStart is used to select fromStart number
   * of values from the begining of the targeted area. Similiarly from ends selects fromEnd number of values from
   * the end.
   * All parameters except path are optional, given only path returns all values in the database for that path
   *
   * @param path path as Path object
   * @param start optional start Timestamp
   * @param end optional end Timestamp
   * @param fromStart number of values to be returned from start
   * @param fromEnd number of values to be returned from end
   *
   * @return Array of DBSensors
   */
  def getNBetween(
      path: Path,
      start: Option[Timestamp],
      end: Option[Timestamp],
      fromStart: Option[Int],
      fromEnd: Option[Int]): Array[DBSensor] = {
    var result = Array[DBSensor]()
    var query = latestValues.filter(_.path === path)
    if (start != None) {
      query = query.filter(_.timestamp >= start.get)
    }
    if (end != None) {
      query = query.filter(_.timestamp <= end.get)
    }
    query = query.sortBy(_.timestamp)
      var str = runSync(query.result)
      if (fromStart != None && fromEnd != None) {
        //does not compute
        //can't have query from two different parts in one go
      } else if (fromStart != None) {
        var amount = Math.max(0, Math.min(str.length, fromStart.get))
        str = str.take(amount)
      } else if (fromEnd != None) {
        var amount = Math.max(0, Math.min(str.length, fromEnd.get))
        str = str.drop(str.length - amount)
      }
      result = Array.ofDim[DBSensor](str.length)
      var index = 0
      str foreach {
        case (dbpath: Path, dbvalue: String, dbtime: java.sql.Timestamp) =>
          result(index) = new DBSensor(dbpath, dbvalue, dbtime)
          index += 1
      }
      result
    
  }


  /**
   * Empties all the data from the database
   * 
   */
  def clearDB() = {
    runWait(DBIO.seq(
      latestValues.delete,
      objects.delete,
      subs.delete,
      buffered.delete))
  }


  /**
   * Used to get childs of an object with given path
   * @param path path to object whose childs are needed
   * @return Array[DBItem] of DBObjects containing childs
   *  of given object. Empty if no childs found or invalid path.
   */
  def getChilds(path: Path): Array[DBItem] =
    {
      var childs = Array[DBItem]()
      val objectQuery = for {
        c <- objects if c.parentPath === path
      } yield (c.path)
      var str = runSync(objectQuery.result)
      childs = Array.ofDim[DBItem](str.length)
      var index = 0
      str foreach {
        case (cpath: Path) =>
          childs(Math.min(index, childs.length - 1)) = DBObject(cpath)
          index += 1
      }
      childs
    }


  /**
   * Checks whether given path exists on the database
   * @param path path to be checked
   * @return boolean whether path was found or not
   */
  private def hasObject(path: Path): Boolean =
    {
      runSync(objects.filter(_.path === path).result).length > 0
    }


  /**
   * Check whether subscription with given ID has expired. i.e if subscription has been in database for
   * longer than its ttl value in seconds.
   *
   * @param id number that was generated during saving
   *
   * @return returns boolean whether subscription with given id has expired
   */
  def isExpired(id: Int): Boolean =
    {
      //gets time when subscibe was added,
      // adds ttl amount of seconds to it,
      //and compares to current time
        val sub = runSync(subs.filter(_.ID === id).result).headOption
        if(sub != None)
        {
        if (sub.get._4 > 0) {
          val endtime = new Timestamp(sub.get._3.getTime + (sub.get._4 * 1000).toLong)
          new java.sql.Timestamp(new java.util.Date().getTime).after(endtime)
        } else {
          true
        }
        }
        else
        {
          true
        }
    }


  /**
   * Removes subscription information from database for given ID
   * @param id id number that was generated during saving
   *
   */
  def removeSub(id: Int): Boolean = {
    
      var qry = subs.filter(_.ID === id)
      var toBeDeleted = runSync(qry.result)
      if (toBeDeleted.length > 0) {
        if (toBeDeleted.head._6 == None) {
          toBeDeleted.head._2.split(";").foreach { p =>
            stopBuffering(Path(p))
          }
        }
        db.run(qry.delete)
        return true
      } else {
        return false
      }
    
    false
  }


  /**
   * getAllSubs is used to search the database for subscription information
   * Can also filter subscriptions based on whether it has a callback address
   * @param hasCallBack optional boolean value to filter results based on having callback address
   * 
   * None -> all subscriptions
   * Some(True) -> only with callback
   * Some(False) -> only without callback
   *
   * @return DBSub objects for the query as Array
   */
  def getAllSubs(hasCallBack: Option[Boolean]): Array[DBSub] =
    {
      var res = Array[DBSub]()
      var all = runSync(hasCallBack match {
        case Some(true) =>
          subs.filter(!_.callback.isEmpty).result
        case Some(false) =>
          subs.filter(_.callback.isEmpty).result
        case None =>
          subs.result
      })
          res = Array.ofDim[DBSub](all.length)
          var index = 0
          all foreach {
            elem =>
              res(index) = new DBSub(Array(), elem._4, elem._5, elem._6, Some(elem._3))
              res(index).paths = elem._2.split(";").map(Path(_))
              res(index).id = elem._1
              index += 1
      }
      res
    }


  /**
   * Method to modify start time and ttl values of a subscription based on id
   * 
   * @param id id number of the subscription to be modified
   * @param newTime time value to be set as start time
   * @param newTTL new TTL value to be set
   */
  def setSubStartTime(id:Int,newTime:Timestamp,newTTL:Double) = {
    runWait(subs.filter(_.ID === id).map(p => (p.start,p.TTL)).update((newTime,newTTL)))
  }


  /**
   * Returns DBSub object wrapped in Option for given id.
   * Returns None if no subscription data matches the id
   * @param id number that was generated during saving
   *
   * @return returns Some(BDSub) if found element with given id None otherwise
   */
  def getSub(id: Int): Option[DBSub] =
    {
      var res: Option[DBSub] = None
      val query = runSync(subs.filter(_.ID === id).result)
        if (query.length > 0) {
          //creates DBSub object based on saved information
          var head = query.head
          var sub = new DBSub(Array(), head._4, head._5, head._6, Some(head._3))
          sub.paths = head._2.split(";").map(Path(_))
          sub.id = head._1
          res = Some(sub)
        }
      res
    }


  /**
   * Saves subscription information to database
   * adds timestamp at current time to keep track of expiring
   * adds unique id number to differentiate between elements and
   * to provide easy query parameter
   *
   * @param sub DBSub object to be stored
   *
   * @return id number that is used for querying the elements
   */
  def saveSub(sub: DBSub): Int = {
        val id = getNextId()
        sub.id = id
        runSync(DBIO.seq(
          subs += (sub.id, sub.paths.mkString(";"), sub.startTime, sub.ttl, sub.interval, sub.callback)
        ))

        //returns the id for reference
        id
    }


  /**
   * Private helper method to find next free id number
   * @return the next free id number
   */
  private def getNextId(): Int = {
    var res = runSync(subs.result)
    res = res.sortBy(_._1)
    var len = res.length
    if (len > 0) {
      //find the element with greatest id value and add 1 to it
      res.last._1 + 1
    } else {
      0
    }
  }
}

