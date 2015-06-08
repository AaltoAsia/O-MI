package database

import slick.driver.H2Driver.api._
import java.sql.Timestamp

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.collection.JavaConversions.iterableAsScalaIterable

import parsing.Types._
import parsing.Types.OdfTypes._
import parsing.Types.OmiTypes.SubLike
import parsing.Types.Path._
import database._

@deprecated("Old interface", "DB Interface refactor")
sealed abstract class DBItem(val path: Path)

@deprecated("Old interface", "DB Interface refactor")
case class DBSensor(pathto: Path, var value: String, var time: Timestamp) extends DBItem(pathto)

@deprecated("Old interface", "DB Interface refactor")
case class DBObject(pathto: Path) extends DBItem(pathto) {
    var childs = Array[DBItem]()
}

// TODO: are these useful?
trait IdProvider {
  def id: Int
}

trait hasPath {
  def path: Path
}

/**
 * Base trait for databases. Has basic private interface.
 */
trait DBBase{
  protected val db: Database


  def runSync[R]: DBIOAction[R, NoStream, Nothing] => R =
    io => Await.result(db.run(io), Duration.Inf)

  def runWait: DBIOAction[_, NoStream, Nothing] => Unit =
    io => Await.ready(db.run(io), Duration.Inf)

}



/**
 * Public datatypes
 */


sealed trait DBSubInternal
case class SubscriptionItem(
  val path: Path,
  val hierarchyId: Int,
  val lastValue: String // for event polling subs
)

/**
 * DBSub class to represent subscription information
 * @param ttl time to live. in seconds. subscription expires after ttl seconds
 * @param interval to store the interval value to DB
 * @param callback optional callback address. use None if no address is needed
 */
case class DBSub(
  val id: Int,
  val interval: Double,
  val startTime: Timestamp,
  val ttl: Double,
  val callback: Option[String]
) extends SubLike with DBSubInternal

case class NewDBSub(
  val interval: Double,
  val startTime: Timestamp,
  val ttl: Double,
  val callback: Option[String]
) extends SubLike with DBSubInternal


trait OmiNodeTables extends DBBase {

  implicit val pathColumnType = MappedColumnType.base[Path, String](
    { _.toString }, // Path to String
    { Path(_) }     // String to Path
    )



  /**
   * Implementation of the http://en.wikipedia.org/wiki/Nested_set_model
   * with depth.
   * @param id 
   * @param path
   * @param leftBoundary Nested set model: left value
   * @param rightBoundary Nested set model: right value
   * @param depth Extended nested set model: depth of this node in the tree
   * @param description for the corresponding odf node (Object or InfoItem)
   * @param pollRefCount Count of references to this node from active poll subscriptions
   */
  case class DBNode(
    id: Option[Int],
    path: Path,
    leftBoundary: Int,
    rightBoundary: Int,
    depth: Int,
    description: String,
    pollRefCount: Int
  ) {
    // TODO: possibility to insert infoitems into OdfObject
    def toOdfObject = OdfObject(path, Iterable(), Iterable(), Some(OdfDescription(description)), None)
    def toOdfInfoItem(values: Iterable[OdfValue]) = OdfInfoItem(path, values, Some(OdfDescription(description)), None)
  }


  /**
   * (Boilerplate) Table to store object hierarchy.
   */
  class DBNodesTable(tag: Tag)
    extends Table[DBNode](tag, "HierarchyNodes") {
    /** This is the PrimaryKey */
    def id            = column[Int]("hierarchyId", O.PrimaryKey, O.AutoInc)
    def path          = column[Path]("path")
    def leftBoundary  = column[Int]("leftBoundary")
    def rightBoundary = column[Int]("rightBoundary")
    def depth         = column[Int]("depth")
    def description   = column[String]("description")
    def pollRefCount  = column[Int]("pollRefCount")

    // Every table needs a * projection with the same type as the table's type parameter
    def * = (id.?, path, leftBoundary, rightBoundary, depth, description, pollRefCount) <> (
      DBNode.tupled,
      DBNode.unapply
    )
  }
  protected val hierarchyNodes = TableQuery[DBNodesTable] //table for storing hierarchy

  trait HierarchyFKey[A] extends Table[A] {
    def hierarchyId = column[Int]("hierarchyId")
    def hierarchy = foreignKey("hierarchy_fk", hierarchyId, hierarchyNodes)(
      _.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
  }





  /**
   * Represents one sensor value
   */
  case class DBValue(
    hierarchyId: Int,
    timestamp: Timestamp,
    value: String,
    valueType: String
  ) {
    def toOdf = OdfValue(value, valueType, Some(timestamp))
  }

  /**
   * (Boilerplate) Table for storing latest sensor data to database
   */
  class DBValuesTable(tag: Tag)
    extends Table[DBValue](tag, "Values") with HierarchyFKey[DBValue] {
    // from extension:
    //def hierarchyId = column[Int]("hierarchyId")
    def timestamp = column[Timestamp]("time")
    def value = column[String]("value")
    def valueType = column[String]("valueType")

    /** Primary Key: (hierarchyId, timestamp) */
    def pk = primaryKey("pk_DBData", (hierarchyId, timestamp))

    def * = (hierarchyId, timestamp, value, valueType) <> (DBValue.tupled, DBValue.unapply)
  }

  protected val latestValues = TableQuery[DBValuesTable] //table for sensor data







  case class DBMetaData(
    val hierarchyId: Int,
    val metadata: String
  ) {
    def toOdf = OdfMetaData(metadata)
  }

  /**
   * (Boilerplate) Table for storing metadata for sensors as string e.g XML block as string
   */
  class DBMetaDatasTable(tag: Tag)
    extends Table[DBMetaData](tag, "Metadata") with HierarchyFKey[DBMetaData] {
    /** This is the PrimaryKey */
    override def hierarchyId = column[Int]("hierarchyId", O.PrimaryKey)
    def metadata    = column[String]("metadata")

    def * = (hierarchyId, metadata) <> (DBMetaData.tupled, DBMetaData.unapply)
  }
  protected val metadatas = TableQuery[DBMetaDatasTable]//table for metadata information







  /**
   * (Boilerplate) Table for O-MI subscription information
   */
  class DBSubsTable(tag: Tag)
    extends Table[DBSubInternal](tag, "Subscriptions") {
    /** This is the PrimaryKey */
    def id        = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def interval  = column[Double]("interval")
    def startTime = column[Timestamp]("start")
    def ttl       = column[Double]("ttl")
    def callback  = column[Option[String]]("callback")

    private def dbsubTupled:
      ((Option[Int], Double, Timestamp, Double, Option[String])) => DBSubInternal = {
        case (None, interval_, startTime_, ttl_, callback_) =>
          NewDBSub(interval_, startTime_, ttl_, callback_)
        case (Some(id_), interval_, startTime_, ttl_, callback_) =>
          DBSub(id_, interval_, startTime_, ttl_, callback_)
      }
    private def dbsubUnapply: 
      DBSubInternal => Option[(Option[Int], Double, Timestamp, Double, Option[String])] = {
        case DBSub(id_, interval_, startTime_, ttl_, callback_) =>
          Some((Some(id_), interval_, startTime_, ttl_, callback_))
        case NewDBSub(interval_, startTime_, ttl_, callback_) =>
          Some((None, interval_, startTime_, ttl_, callback_))
        case _ => None
      }

    def * =
      (id.?, interval, startTime, ttl, callback).shaped <> (
      dbsubTupled, dbsubUnapply
    )
  }

  protected val subs = TableQuery[DBSubsTable]

  trait SubFKey[A] extends Table[A] {
    def subId = column[Int]("subId")
    def sub   = foreignKey("sub_fk", subId, subs)(
      _.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade
    )
  }





  case class DBSubscriptionItem(
    val subId: Int,
    val hierarchyId: Int,
    val lastValue: String // for event polling subs
  )
  /**
   * Storing paths of subscriptions
   */
  class DBSubscribedItemsTable(tag: Tag)
      extends Table[DBSubscriptionItem](tag, "SubItems")
      with SubFKey[DBSubscriptionItem]
      with HierarchyFKey[DBSubscriptionItem] {
    // from extension:
    //def subId = column[Int]("subId")
    //def hierarchyId = column[Int]("hierarchyId")
    def lastValue = column[String]("lastValue")
    def pk = primaryKey("pk_subItems", (subId, hierarchyId))
    def * = (subId, hierarchyId, lastValue) <> (DBSubscriptionItem.tupled, DBSubscriptionItem.unapply)
  }

  protected val subItems = TableQuery[DBSubscribedItemsTable]

  protected val allTables =
    Seq( hierarchyNodes
       , latestValues
       , metadatas
       , subs
       , subItems
       )

  protected val allSchemas = allTables map (_.schema) reduceLeft (_ ++ _)

  /**
   * Empties all the data from the database
   * 
   */
  def clearDB() = runWait(
    DBIO.seq(
      (allTables map (_.delete)): _* 
    )
  )

  def dropDB() = runWait( allSchemas.drop )
    
}
