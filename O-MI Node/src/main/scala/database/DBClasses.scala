package database
import slick.driver.H2Driver.api._
import slick.jdbc.StaticQuery.interpolation
import slick.lifted.ProvenShape
import parsing.Types._
import parsing.Types.OmiTypes.SubLike
import parsing.Types.Path._
import java.sql.Timestamp

import database._

// TODO: are these useful?
trait IdProvider {
  def id: Int
}

trait hasPath {
  def path: Path
}



// TODO: doc

trait OmiNodeTables {

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
  )

  /**
   * (Boilerplate) Table to store object hierarchy.
   */
  class DBNodesTable(tag: Tag)
    extends Table[DBNode](tag, "Objects") {
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
      DBNode.mapped,
      DBNode.unapply
    )
  }
  protected val hierarchyNodes = TableQuery[DBNodesTable] //table for storing hierarchy

  trait HierarchyFKey {
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
  )

  /**
   * (Boilerplate) Table for storing latest sensor data to database
   */
  class DBValuesTable(tag: Tag)
    extends Table[DBValue](tag, "Values") with HierarchyFKey {
    def hierarchyId = column[Int]("hierarchyId")
    def timestamp = column[Timestamp]("time")
    def value = column[String]("value")
    def valueType = column[String]("valueType")

    /** Primary Key: (hierarchyId, timestamp) */
    def pk = primaryKey("pk_DBData", (hierarchyId, timestamp))

    def * = (hierarchyId, timestamp, value, valueType) <> (DBValue.mapped, DBValue.unapply)
  }

  protected val latestValues = TableQuery[DBValuesTable] //table for sensor data







  case class DBMetaData(
    hierarchyId: Int,
    metadata: String
  )

  /**
   * (Boilerplate) Table for storing metadata for sensors as string e.g XML block as string
   */
  class DBMetaDatasTable(tag: Tag)
    extends Table[DBMetaData](tag, "Metadata") with HierarchyFKey {
    /** This is the PrimaryKey */
    def hierarchyId = column[Int]("hierarchyId", O.PrimaryKey)
    def metadata    = column[String]("metadata")

    def * = (hierarchId, metadata) <> (DBMetaData.mapped, DBMetaData.unapply)
  }
  protected val metadatas = TableQuery[DBMetaDatasTable]//table for metadata information






  /**
   * DBSub class to represent subscription information
   * @param paths Array of paths representing all the sensors the subscription needs
   * @param ttl time to live. in seconds. subscription expires after ttl seconds
   * @param interval to store the interval value to DB
   * @param callback optional callback address. use None if no address is needed
   */
  case class DBSub(
    val id: Option[Int] = None,
    val interval: Double,
    val startTime: Timestamp,
    val ttl: Double,
    val callback: Option[String],
    val lastValue: String // for event polling subs
  ) extends SubLike with IdProvider

  // val paths: Vector[Path],

  /**
   * (Boilerplate) Table for O-MI subscription information
   */
  class DBSubsTable(tag: Tag)
    extends Table[DBSub](tag, "Subscriptions") {
    /** This is the PrimaryKey */
    def id        = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def interval  = column[Double]("interval")
    def startTime = column[Timestamp]("start")
    def ttl       = column[Double]("ttl")
    def callback  = column[Option[String]]("callback")
    def lastValue = column[String]("lastValue")
    def * = (id.?, interval, startTime, ttl, callback, lastValue) <> (DBSub.tuppled, DBSub.unapply)
  }

  protected val subs = TableQuery[DBSubsTable]

  trait SubFKey {
    def subId = column[Int]("subId")
    def sub   = foreignKey("sub_fk", hierarchyId, hierarchy)(
      _.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
  }





  case class DBSubscriptionItem(
    subId: Int,
    hierarchyId: Int
  )
  /**
   * Storing paths of subscriptions
   */
  class DBSubscribedItemsTable(tag: Tag)
    extends Table[DBSubscriptionItem](tag, "SubItems") with SubFKey with HierarchyFKey {
    def subId = column[Int]("subId")
    def hierarchyId = column[Int]("hierarchyId")
    def * = (subId, hierarchyId) <> (DBSubscriptionItem.mapped, DBSubscriptionItem.unapply)
  }

  protected val subItems = TableQuery[DBSubscribedItemsTable]

  protected val allTables =
    Seq( hierarchy
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
