package database
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape
import java.io.File
import scala.collection.mutable.Map
import scala.collection.mutable.Buffer

import parsing.Path
import parsing.Path._

object SQLite {
  implicit val pathColumnType = MappedColumnType.base[Path, String](
    {_.toString}, // Path to String
    {Path(_)}     // String to Path
  )

  private var historyLength = 10
  //path where the file is stored
  private val dbPath = "./sensorDB.sqlite3"
  //check if the file already exists
  val init = !new File(dbPath).exists()
  //tables for latest values and hierarchy
  private val latestValues = TableQuery[DBData]
  private val objects = TableQuery[DBNode]
  
  //initializing database
  val db = Database.forURL("jdbc:sqlite:" + dbPath, driver = "org.sqlite.JDBC")
  db withSession { implicit session =>
    if (init) {
      initialize()
    }
  }
  
  /**
   * Used to set values to database. If data already exists for the path, updates existing data,
   *  otherwise creates new data and all the missing objects to the hierarchy.
   *  
   *  @param data data is of type DBSensor
   *  @return boolean whether added data was new
   */
  def set(data: DBSensor) =
    {
    var count = 0
    //try{
      db withSession { implicit session =>
        //search database for sensor's path
       val pathQuery = latestValues.filter(_.path === data.path)
        //if found a row with same path update else add new data
        count = pathQuery.list.length
        if (count >= historyLength) {
          latestValues += (data.path, data.value, data.time)
          val oldtime = pathQuery.sortBy(_.timestamp).first._3
          pathQuery.filter(_.timestamp === oldtime).delete
          false
        } else {
          latestValues += (data.path, data.value, data.time)
          //also add missing objects for the hierarchy
          addObjects(data.path)
          true
        }
        
      }
    //}
    //catch{
      //case e: Exception => 
        //just fail silently when something bad happens
        //e.g does nothing when trying to add same path and timestamp multiple times
    //}
    
    }
   def setHistoryLength(newLength:Int)
   {
     historyLength=newLength
   }
  /**
   * Remove is used to remove sensor given its path. Removes all unused objects along the path too.
   * 
   * 
   * @param path path to to-be-deleted sensor. If path doesn't end in sensor, does nothing.
   * @return boolean whether something was removed
   */
  def remove(path: Path):Boolean ={
    db withSession { implicit session =>
      //search database for given path
      val pathQuery = latestValues.filter(_.path === path)

      //if found rows with given path remove else path doesn't exist and can't be removed
      if (pathQuery.list.length > 0) {
        pathQuery.delete
        //also delete objects from hierarchy that are not used anymore.
        // start from sensors path and proceed upward in hierarchy until object that is shared by other sensor is found,
        //ultimately the root. path/to/sensor/temp -> path/to/sensor -> ..... -> "" (root)
        var testPath = path

        while(!testPath.isEmpty) {
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
        return true
      }
      else
      {
        return false
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
      
      db withSession { implicit session =>
        //search database for given path
        val pathQuery = latestValues.filter(_.path === path)

        //if path is found from latest values it must be Sensor otherwise check if it is an object
        var count = pathQuery.list.length
        if (count > 0) {
          //path is sensor
          //case class matching
          val latest = pathQuery.sortBy(_.timestamp).drop(count-1)
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
   * getInterval returns Array of DBSensors that are on given path and between given timestamps
   * @param path path to sensor whose values are of interest
   * @param start 
   */
  def getInterval(path: Path, start: java.sql.Timestamp, end: java.sql.Timestamp): Array[DBSensor] = {
    var result = Buffer[DBSensor]()
    db withSession { implicit session =>
      val pathQuery = latestValues.filter(_.path === path)
        //if path is found from latest values it must be Sensor otherwise check if it is an object
        var count = pathQuery.list.length
        if (count > 0) {
         val sorted = pathQuery.sortBy(_.timestamp)
         pathQuery foreach {
           case (dbpath: Path, dbvalue: String, dbtime: java.sql.Timestamp) =>
             if(dbtime.after(start) && dbtime.before(end))
               result+=new DBSensor(dbpath,dbvalue,dbtime)     
         }
        }
    }
    result.toArray
  }
  /**
   * Adds missing objects(if any) to hierarchy based on given path
   * @param path path whose hierarchy is to be stored to database
   *
   */
  private def addObjects(path: Path)(implicit session: Session) {
    val pathAndParents: Seq[Path] = path.tail.scanLeft(path.head)(_ / _)
    for (i) {
      if (fullpath != "") {
        fullpath += "/"
      }
      fullpath += pathparts(i)
      if (!hasObject(fullpath)) {
        objects += (fullpath, curpath, pathparts(i))
      }
      curpath = fullpath
    }
  }
  /**
   * returns n latest values from sensor at given path as Array[DBSensor]
   * returns all stored values if n is greater than number of values stored
   * @param path path to sensor
   * @param n number of values to return
   * @param return returns Array[DBSensor]
   */
  def getNLatest(path:String,n:Int) = getN(path,n,true):Array[DBSensor]
  /**
   * returns n oldest values from sensor at given path as Array[DBSensor]
   * returns all stored values if n is greater than number of values stored
   * @param path path to sensor
   * @param n number of values to return
   * @param return returns Array[DBSensor]
   */
  def getNOldest(path:String,n:Int) = getN(path,n,false):Array[DBSensor]
  /**
   * returns n latest or oldest values from sensor at given path as Array[DBSensor]
   * returns all stored values if n is greater than number of values stored
   * @param path path to sensor
   * @param n number of values to return
   * @param latest boolean return latest? if false returns oldest
   * @param return returns Array[DBSensor]
   */
  private def getN(path:String,n:Int,latest:Boolean):Array[DBSensor]=
  {
   var result = Buffer[DBSensor]()
    db withSession { implicit session =>
      val pathQuery = latestValues.filter(_.path === path)
        var count = pathQuery.list.length
        if (count > 0) {
         val sorted = pathQuery.sortBy(_.timestamp)
         val limited = if(latest){sorted.drop(math.max(count-n,0))}else{sorted.take(math.min(count,n))}
         limited foreach{
           case (dbpath: String, dbvalue: String, dbtime: java.sql.Timestamp) =>
               result+=new DBSensor(dbpath,dbvalue,dbtime)     
         }
        }
    }
    result.toArray
  }
  

  /**
   * Empties all the data from the database
   * 
   */
  def clearDB()={
    db withSession { implicit session =>
    latestValues.delete
    objects.delete
    }
  }

  /**
   * Used to get childs of an object with given path
   * @param path path to object whose childs are needed
   * @return Array[DBItem] of DBObjects containing childs
   *  of given object. Empty if no childs found or invalid path.
   */
  private def getChilds(path: String)(implicit session: Session): Array[DBItem] =
    {
      var childs = Array[DBItem]()
      val objectQuery = for {
        c <- objects if c.parentPath === path
      } yield (c.path)
      childs = Array.ofDim[DBItem](objectQuery.list.length)
      var index = 0
      objectQuery foreach {
        case (path: String) =>
          childs(index) = DBObject(path)
          index += 1
      }

      childs
    }
  /**
   * Checks whether given path exists on the database
   * @param path path to be checked
   * @return boolean whether path was found or not
   */
  private def hasObject(path: String)(implicit session: Session): Boolean =
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
    }
}


import SQLite._


/**
 * Abstract base class for sensors' data structure
 *
 * @param path to where node is. Last part is key for this.
 *
 */

sealed abstract class DBItem(val path: Path)
/**
 * case class DBSensor for the actual sensor data
 * @param pathto path to sensor
 * @param value  actual value from sensor as String
 * @param time time stamp indicating when sensor data was read using java.sql.Timestamp
 *
 */
case class DBSensor(pathto: Path, var value: String, var time: java.sql.Timestamp) extends DBItem(pathto)
/**
 * case class DBObject for object hierarchy
 * returned from get when path doesn't end in actual sensor
 * used to store hierarchy and to retrieve object's children for given path
 *
 * @param pathto path to object
 */
case class DBObject(pathto: Path) extends DBItem(pathto) {
  var childs = Array[DBItem]()
}

/**
 * class DBData to store sensor data to database
 * used internally by the object SQLite
 */
class DBData(tag: Tag)
  extends Table[(Path, String, java.sql.Timestamp)](tag, "Values") {
  // This is the primary key column:
  def path = column[Path]("PATH")
  def value = column[String]("VALUE")
  def timestamp = column[java.sql.Timestamp]("TIME")
  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(String, String, java.sql.Timestamp)] = (path, value, timestamp)
  def pk = primaryKey("pk_DBData",(path,timestamp))
}
/**
 * class DBNode to store object hierarchy
 * used internally by the object SQLite
 */
class DBNode(tag: Tag)
  extends Table[(Path, Path, String)](tag, "Objects") {
  // This is the primary key column:
  def path = column[Path]("PATH", O.PrimaryKey)
  def parentPath = column[Path]("PARENTPATH")
  def key = column[String]("KEY")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Path, Path, String)] = (path, parentPath, key)
}

