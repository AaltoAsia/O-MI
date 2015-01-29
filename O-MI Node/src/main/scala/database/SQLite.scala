package database
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape
import java.io.File
import scala.collection.mutable.Map

object SQLite {

  //path where the file is stored
  val dbPath = "./sensorDB.sqlite3"
  //check if the file already exists
  val init = !new File(dbPath).exists()
  //tables for latest values and hierarchy
  val latestValues = TableQuery[DBData]
  val objects = TableQuery[DBNode]
  
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
      db withSession { implicit session =>
        //search database for sensor's path
        val pathQuery = for {
          d <- latestValues if d.path === data.path
        } yield (d.value, d.timestamp)
        //if found a row with same path update else add new data
        if (pathQuery.list.length > 0) {
          pathQuery.update(data.value, data.time)
          false
        } else {
          latestValues += (data.path, data.value, data.time)
          //also add missing objects for the hierarchy
          addObjects(data.path)
          true
        }
        
      }
    }

  /**
   * Remove is used to remove sensor given its path. Removes all unused objects along the path too.
   * 
   * 
   * @param path path to to-be-deleted sensor. If path doesn't end in sensor, does nothing.
   * @return boolean whether something was removed
   */
  def remove(path: String):Boolean ={
    db withSession { implicit session =>
      //search database for given path
      val pathQuery = latestValues.filter(_.path === path)
      //if found rows with given path remove else path doesn't exist and can't be removed
      if (pathQuery.list.length > 0) {
        pathQuery.delete
        //also delete objects from hierarchy that are not used anymore.
        // start from sensors path and proceed upward in hierarchy until object that is shared by other sensor is found,
        //ultimately the root. path/to/sensor/temp -> path/to/sensor -> ..... -> "" (root)
        var pathParts = path.split("/")
        while(!pathParts.isEmpty) {
          var testPath = pathParts.mkString("/")
          if (getChilds(testPath).length == 0) {
            //only leaf nodes have 0 childs. 
            var pathQueryObjects = objects.filter(_.path === testPath)
            pathQueryObjects.delete
            pathParts = pathParts.dropRight(1)
          } else {
            //if object still has childs after we deleted one it is shared by other sensor, stop removing objects
            //exit while loop
            pathParts = Array[String]() 
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
  def get(path: String): Option[DBItem] =
    {
      var result: Option[DBItem] = None
      
      db withSession { implicit session =>
        //search database for given path
        val pathQuery = latestValues.filter(_.path === path)
        //if path is found from latest values it must be Sensor otherwise check if it is an object
        if (pathQuery.list.length > 0) {
          //path is sensor
          //case class matching
          for (sensordata <- pathQuery) {
            sensordata match {
              case (path: String, value: String, time: java.sql.Timestamp) =>
                result = Some(DBSensor(path, value, time))
            }
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
  private def addObjects(path: String)(implicit session: Session) {
    var pathparts = path.split("/")
    var curpath = ""
    var fullpath = ""
    for (i <- 0 until pathparts.size) {
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
  //empties all content from the tables
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

/**
 * Abstract base class for sensors' data structure
 *
 * @param path to where node is. Last part is key for this.
 *
 */

sealed abstract class DBItem(val path: String)
/**
 * case class DBSensor for the actual sensor data
 * @param pathto path to sensor
 * @param value  actual value from sensor as String
 * @param time time stamp indicating when sensor data was read using java.sql.Timestamp
 *
 */
case class DBSensor(pathto: String, var value: String, var time: java.sql.Timestamp) extends DBItem(pathto)
/**
 * case class DBObject for object hierarchy
 * returned from get when path doesn't end in actual sensor
 * used to store hierarchy and to retrieve object's children for given path
 *
 * @param pathto path to object
 */
case class DBObject(pathto: String) extends DBItem(pathto) {
  var childs = Array[DBItem]()
}

/**
 * class DBData to store sensor data to database
 * used internally by the object SQLite
 */
class DBData(tag: Tag)
  extends Table[(String, String, java.sql.Timestamp)](tag, "Latestvalues") {
  // This is the primary key column:
  def path = column[String]("PATH", O.PrimaryKey)
  def value = column[String]("VALUE")
  def timestamp = column[java.sql.Timestamp]("TIME")
  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(String, String, java.sql.Timestamp)] = (path, value, timestamp)
}
/**
 * class DBNode to store object hierarchy
 * used internally by the object SQLite
 */
class DBNode(tag: Tag)
  extends Table[(String, String, String)](tag, "Objects") {
  // This is the primary key column:
  def path = column[String]("PATH", O.PrimaryKey)
  def parentPath = column[String]("PARENTPATH")
  def key = column[String]("KEY")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(String, String, String)] = (path, parentPath, key)
}
