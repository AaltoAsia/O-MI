package database
import slick.driver.SQLiteDriver.api._
import slick.jdbc.StaticQuery.interpolation
import slick.lifted.ProvenShape
import parsing.Types._
import parsing.Types.Path._
import java.sql.Timestamp
import SQLite._
/**
 * DBSub class to represent subscription information
 * @param paths Array of paths representing all the sensors the subscription needs
 * @param ttl time to live. in seconds. subscription expires after ttl seconds
 * @param interval to store the interval value to DB
 * @param callback optional callback address. use None if no address is needed
 */
class DBSub(var paths: Array[Path],
            val ttl: Double,
            val interval: Double,
            val callback: Option[String],
            var startTimeOption: Option[Timestamp]
            ) extends SubLike {
  //this is assigned later when subscribtion is added to db
  var id: Int = 0
  val startTime: Timestamp =
    startTimeOption.getOrElse(
      new Timestamp(
        new java.util.Date().getTime
      )
    )

  if (callback == None) {
    paths.foreach {
      startBuffering(_)
    }
  }
}

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
case class DBSensor(pathto: Path, var value: String, var time: Timestamp) extends DBItem(pathto){
  override def toString=value
}

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
  def * : ProvenShape[(Path, String, java.sql.Timestamp)] = (path, value, timestamp)
  def pk = primaryKey("pk_DBData", (path, timestamp))
}

/**
 * class DBMetaData to store metadata for sensors as string e.g XML block as string
 */
class DBMetaData(tag: Tag)
  extends Table[(Path, String)](tag, "Metadata") {
  // This is the primary key column:
  def path = column[Path]("PATH", O.PrimaryKey)
  def data = column[String]("METADATA")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Path, String)] = (path, data)
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

/**
 * Storing paths that need to be buffered
 * i.e if path is found in the table it is being buffered
 * else only historyLength amount of values is stored
 */
class BufferedPath(tag: Tag)
  extends Table[(Path, Int)](tag, "Buffered") {
  // This is the primary key column:
  def path = column[Path]("PATH", O.PrimaryKey)
  def count = column[Int]("COUNT")
  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Path, Int)] = (path, count)
}
/**
 * Storing the subscription information to DB
 */
class DBSubscription(tag: Tag)
  extends Table[(Int, String, java.sql.Timestamp, Double, Double, Option[String])](tag, "subscriptions") {
  // This is the primary key column:
  def ID = column[Int]("ID", O.PrimaryKey)
  def paths = column[String]("PATHS")
  def start = column[java.sql.Timestamp]("START")
  def TTL = column[Double]("TTL")
  def interval = column[Double]("INTERVAL")
  def callback = column[Option[String]]("CALLBACK")
  def * : ProvenShape[(Int, String, java.sql.Timestamp, Double, Double, Option[String])] = (ID, paths, start, TTL, interval, callback)
}
