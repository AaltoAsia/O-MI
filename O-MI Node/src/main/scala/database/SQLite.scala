package database
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.lifted.ProvenShape
import java.io.File
import scala.collection.mutable.Map
import scala.collection.mutable.Buffer
import java.sql.Timestamp

import parsing.Types._
import parsing.Types.Path._

object SQLite {
  implicit val pathColumnType = MappedColumnType.base[Path, String](
    { _.toString }, // Path to String
    { Path(_) } // String to Path
    )

  private var historyLength = 10
  //path where the file is stored
  private val dbPath = "./sensorDB.sqlite3"
  //check if the file already exists
  private val init = !new File(dbPath).exists()
  //tables for latest values and hierarchy
  private val latestValues = TableQuery[DBData]
  private val objects = TableQuery[DBNode]
  private val subs = TableQuery[DBSubscription]
  private val buffered = TableQuery[BufferedPath]

  private var setEventHooks: List[Seq[Path] => Unit] = List()
  def attachSetHook(f: Seq[Path] => Unit) =
    setEventHooks = f :: setEventHooks

  //initializing database
  private val db = Database.forURL("jdbc:sqlite:" + dbPath, driver = "org.sqlite.JDBC")
  db withTransaction { implicit session =>
    if (init) {
      initialize()
    }
  }

  /**
   * Used to set values to database. If data already exists for the path, appends until historyLength
   * is met, otherwise creates new data and all the missing objects to the hierarchy.
   *  Does not remove excess rows if path is set ot buffer
   *
   *  @param data data is of type DBSensor
   *  @return boolean whether added data was new
   */
  def set(data: DBSensor) =
    {
      var count = 0
      db withTransaction { implicit session =>
        //search database for sensor's path
        val pathQuery = latestValues.filter(_.path === data.path)
        var buffering = buffered.filter(_.path === data.path).list.length > 0
        //appends a row to the latestvalues table
        count = pathQuery.length.run
        latestValues += (data.path, data.value, data.time)
        
        // Call hooks
        val argument = Seq(data.path)
        setEventHooks foreach {_(argument)}

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
    }
  /**
   * Used to set many values efficiently to the database.
   * Currently works only for list of tuples consisting of path and value.
   */
  def setMany(data: List[(String, String)]) {
    db withTransaction { implicit session =>
      var path = Path("")
      var len = 0
      data.foreach {
        case (p: String, v: String) =>
          path = Path(p)
          var pathQuery = latestValues.filter(_.path === path)
          len = pathQuery.length.run
          if (len == 0) {
            addObjects(path)
          }
          var buffering = buffered.filter(_.path === path).list.length > 0
          latestValues += (path, v, new Timestamp(new java.util.Date().getTime))

          // Call hooks
          val argument = Seq(path)
          setEventHooks foreach {_(argument)}

          if (len >= historyLength) {
            removeExcess(path)
          }
      }
    }
  }
  /**
   * sets the historylength to desired length
   * default is 10
   * 
   * @param newLength new length to be used
   */
  def setHistoryLength(newLength: Int) {
    historyLength = newLength
  }
  /**
   * Remove is used to remove sensor given its path. Removes all unused objects along the path too.
   *
   *
   * @param path path to to-be-deleted sensor. If path doesn't end in sensor, does nothing.
   * @return boolean whether something was removed
   */
  def remove(path: Path): Boolean = {

    db withSession { implicit session =>
      //search database for given path
      var deleted = false
      val pathQuery = latestValues.filter(_.path === path)
      //if found rows with given path remove else path doesn't exist and can't be removed
      if (pathQuery.list.length > 0) {
        pathQuery.delete
        deleted = true;
        //also delete objects from hierarchy that are not used anymore.
        // start from sensors path and proceed upward in hierarchy until object that is shared by other sensor is found,
        //ultimately the root. path/to/sensor/temp -> path/to/sensor -> ..... -> "" (root)

        var testPath = path

        while (!testPath.isEmpty) {
          if (getChilds(testPath).length == 0) {
            //only leaf nodes have 0 childs. 
            var pathQueryObjects = objects.filter(_.path === testPath)
            pathQueryObjects.delete

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

  }
  /**
   * Returns array of DBSensors for given subscription id.
   * Array consists of all sensor values after beginning of the subscription
   * for all the sensors in the subscription
   * returns empty array if no data or subscription is found
   * 
   * @param id subscription id that is assigned during saving the subscription
   * 
   * @return Array of DBSensors
   */
  def getSubData(id: Int): Array[DBSensor] =
    {
      db withTransaction { implicit session =>
        var result = Buffer[DBSensor]()
        var subQuery = subs.filter(_.ID === id)
        if (subQuery.length.run > 0) {
          var sub = subQuery.first
          var paths = sub._2.split(";")
          paths.foreach {
            p =>
              result ++= DataFormater.FormatSubData(Path(p), sub._3, sub._5)
          }
        }
        result.toArray
      }
    }
  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private def removeExcess(path: Path)(implicit session: Session) =
    {
      val pathQuery = latestValues.filter(_.path === path)
      var count = pathQuery.length.run
      if (count > historyLength) {
        val oldtime = pathQuery.sortBy(_.timestamp).drop(count - historyLength).first._3
        pathQuery.filter(_.timestamp < oldtime).delete
      }
    }
  /**
   * put the path to buffering table if it is not there yet, otherwise
   * increases the count on that item, to prevent removing buffered data
   * if one subscription ends and other is still buffering.
   * 
   * @param path path as Path object
   */
  def startBuffering(path: Path) {
    db withTransaction { implicit session =>
      val pathQuery = buffered.filter(_.path === path)
      var len = pathQuery.length.run
      if (len == 0) {
        buffered += (path, 1)
        true
      } else {
        val counts = for {
          c <- pathQuery
        } yield (c.count)
        counts.update(len + 1)
        false
      }
    }
  }
  /**
   * removes the path from buffering table or dimishes the count by one
   * also clear all buffered data if count is only 1
   * leaves only historyLength amount of data if count is only 1
   * @param path path as Path object
   */
  def stopBuffering(path: Path) {
    db withSession { implicit session =>
      val pathQuery = buffered.filter(_.path === path)
      var len = pathQuery.length.run
      if (len > 0) {
        if (pathQuery.first._2 > 1) {
          val counts = for {
            c <- pathQuery
          } yield (c.count)
          counts.update(len - 1)
          false
        } else {
          pathQuery.delete
          removeExcess(path)
          true
        }
      } else {
        false
      }
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
      var result: Option[DBItem] = None

      db withTransaction { implicit session =>
        //search database for given path
        val pathQuery = latestValues.filter(_.path === path)
        //if path is found from latest values it must be Sensor otherwise check if it is an object
        var count = pathQuery.length.run
        if (count > 0) {
          //path is sensor
          //case class matching
          val latest = pathQuery.sortBy(_.timestamp).drop(count - 1)
          latest.first match {
            case (path: Path, value: String, time: java.sql.Timestamp) =>
              result = Some(DBSensor(path, value, time))
          }
        } else {
          var childs = getChilds(path)
          //childs is empty only if given path does not exist or ends in sensor.
          //But code here is never executed if path ends in sensor 
          //therefore childs is only empty if given path doesn't exist
          if (!childs.isEmpty) {
            //path is an object
            //create object and give it reference to its childs
            var obj = DBObject(path)
            obj.childs = childs
            result = Some(obj)
          }
        }
      }
      result
    }
  /**
   * Adds missing objects(if any) to hierarchy based on given path
   * @param path path whose hierarchy is to be stored to database
   *
   */
  private def addObjects(path: Path)(implicit session: Session) {
    val parentsAndPath: Seq[Path] = path.tail.scanLeft(Path(path.head))(Path(_) / _)
    var parent = Path("")
    for (fullpath <- parentsAndPath) {
      if (!hasObject(fullpath)) {
        objects += (fullpath, parent, fullpath.last)
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
   * All parameters except path are optional, given only path returns all values in DB for that path
   *
   * @param path path as Path object
   * @param start optional start Timestamp
   * @param start optional end Timestamp
   * @param fromStart number of values to be returned from start
   * @param fromEnd number of values to be returned from end
   *
   * @param return Array of DBSensors
   */

  def getNBetween(path: Path, start: Option[Timestamp], end: Option[Timestamp], fromStart: Option[Int], fromEnd: Option[Int]): Array[DBSensor] = {
    var result = Array[DBSensor]()
    db withTransaction { implicit session =>
      var query = latestValues.filter(_.path === path)
      if (start != None) {
        query = query.filter(_.timestamp >= start.get)
      }
      if (end != None) {
        query = query.filter(_.timestamp <= end.get)
      }
      if (fromStart != None && fromEnd != None) {
        //does not compute
        //can't have query from two different parts in one go
      } else if (fromStart != None) {
        var amount = Math.max(0, Math.min(query.length.run, fromStart.get))
        query = query.take(amount)
      } else if (fromEnd != None) {
        var amount = Math.max(0, Math.min(query.length.run, fromEnd.get))
        query = query.drop(query.length.run - amount)
      }
      result = Array.ofDim[DBSensor](query.length.run)
      query.sortBy(_.timestamp)
      var index = 0
      query foreach {
        case (dbpath: Path, dbvalue: String, dbtime: java.sql.Timestamp) =>
          result(index) = new DBSensor(dbpath, dbvalue, dbtime)
          index += 1
      }
      result
    }
  }

  /**
   * Empties all the data from the database
   *
   */
  def clearDB() = {
    db withTransaction { implicit session =>
      latestValues.delete
      objects.delete
      subs.delete
      buffered.delete
    }
  }

  /**
   * Used to get childs of an object with given path
   * @param path path to object whose childs are needed
   * @return Array[DBItem] of DBObjects containing childs
   *  of given object. Empty if no childs found or invalid path.
   */
  private def getChilds(path: Path)(implicit session: Session): Array[DBItem] =
    {
      var childs = Array[DBItem]()
      val objectQuery = for {
        c <- objects if c.parentPath === path
      } yield (c.path)
      childs = Array.ofDim[DBItem](objectQuery.list.length)
      var index = 0
      objectQuery foreach {
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
  private def hasObject(path: Path)(implicit session: Session): Boolean =
    {
      var objectQuery = objects.filter(_.path === path)
      objectQuery.list.length > 0
    }

  /**
   * Initializes tables to the database file
   * called only when the database file doesn't exist at startup
   */
  private def initialize()(implicit session: Session) =
    {
      latestValues.ddl.create
      objects.ddl.create
      subs.ddl.create
      buffered.ddl.create
    }
  /**
   * Check whether subscription with given ID has expired. i.e if subscription has been in database for
   * longer than its ttl value in seconds.
   *
   * @param id id number that was generated during saving
   *
   * @param return returns boolean whether subscription with given id has expired
   */
  def isExpired(id: Int): Boolean =
    {
      //gets time when subscibe was added,
      // adds ttl amount of seconds to it,
      //and compares to current time
      db withTransaction { implicit session =>
        val sub = subs.filter(_.ID === id).first
        if (sub._4 > 0) {
          var cal = java.util.Calendar.getInstance()
          cal.setTimeInMillis(sub._3.getTime())
          cal.add(java.util.Calendar.SECOND, sub._4)
          var endtime = new java.sql.Timestamp(cal.getTime().getTime())
          new java.sql.Timestamp(new java.util.Date().getTime).after(endtime)
        } else {
          true
        }
      }
    }
  /**
   * Removes subscription information from database for given ID
   * @param id id number that was generated during saving
   *
   */
  def removeSub(id: Int): Boolean = {
    db withSession { implicit session =>
      var toBeDeleted = subs.filter(_.ID === id)
      if(toBeDeleted.length.run > 0) { 
//        println("\nlist:\n "+ toBeDeleted.list+ "\nend") //Debug print
        if(toBeDeleted.list.head._6 == None) {
          toBeDeleted.first._2.split(";").foreach { p =>
            stopBuffering(Path(p))
          }
        }
        toBeDeleted.delete
        return true
      } else {
        return false
      }
    }
    true
  }
  /**
   * Returns DBSub object wrapped in Option for given id.
   * Returns None if no subscription data matches the id
   * @param id id number that was generated during saving
   *
   * @param return returns Some(BDSub) if found element with given id None otherwise
   */
  def getSub(id: Int): Option[DBSub] =
    {
      var res: Option[DBSub] = None
      db withTransaction { implicit session =>
        val query = subs.filter(_.ID === id)
        if (query.list.length > 0) {
          //creates DBSub object based on saved information
          var head = query.first
          var sub = new DBSub(Array(), head._4, head._5, head._6, Some(head._3))
          sub.paths = head._2.split(";").map(Path(_))
          sub.id = head._1
          res = Some(sub)
        }

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
   * @param return id number that is used for querying the elements
   */
  def saveSub(sub: DBSub): Int =
    {
      db withTransaction { implicit session =>
        val id = getNextId()
        sub.id = id
        subs += (sub.id, sub.paths.mkString(";"), sub.startTime.get, sub.ttl, sub.interval, sub.callback)
        //returns the id for reference
        id
      }
    }
  /**
   * Private helper method to find next free id number
   * @param return the next free id number
   */
  private def getNextId()(implicit session: Session): Int = {
    var len = subs.list.length
    if (len > 0) {
      //find the element with greatest id value and add 1 to it
      subs.sortBy(_.ID).drop(len - 1).first._1 + 1
    } else {
      0
    }
  }
}

