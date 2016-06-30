/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
package database

import java.sql.Timestamp

import scala.concurrent.Future
import scala.concurrent.duration._

import parsing.xmlGen.xmlTypes.QlmID
import slick.driver.H2Driver.api._
import slick.lifted.{Index, ForeignKeyQuery, ProvenShape}
//import scala.collection.JavaConversions.iterableAsScalaIterable
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.SubLike
import types._
//import types.Path._


/**
 * Base trait for databases. Has basic protected interface.
 */
trait DBBase{
  protected[this] val db: Database
}




/**
 * Public datatypes
 */


case class SubscriptionItem(
  val subId: Long,
  val path: Path,
  val lastValue: Option[String] // for event polling subs
)

// For db table type, match to and use subclasses of this
sealed trait DBSubInternal

/**
 * DBSub class to represent subscription information
 * @param ttl time to live. in seconds. subscription expires after ttl seconds
 * @param interval to store the interval value to DB
 * @param callback optional callback address. use None if no address is needed
 */
case class DBSub(
  val id: Long,
  val interval: Duration,
  val startTime: Timestamp,
  val ttl: Duration,
  val callback: Option[String]
) extends SubLike with DBSubInternal

case class NewDBSub(
  val interval: Duration,
  val startTime: Timestamp,
  val ttl: Duration,
  val callback: Option[String]
) extends SubLike with DBSubInternal


/**
 * Represents one sensor value
 */
case class DBValue(
  hierarchyId: Int,
  timestamp: Timestamp,
  value: String,
  valueType: String,
    valueId: Option[Long] = None
) {
  def toOdf: OdfValue = OdfValue(value, valueType, timestamp)
}

case class SubValue(
                     subId: Long,
                     path: Path,
                     timestamp: Timestamp,
                     value: String,
                     valueType: String
                     ) {
  def toOdf: OdfValue = OdfValue(value, valueType, timestamp)
}



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
   * @param isInfoItem Boolean indicating if this Node is an InfoItem
   */
  case class DBNode(
    id: Option[Int],
    path: Path,
    leftBoundary: Int,
    rightBoundary: Int,
    depth: Int,
    description: String,
    pollRefCount: Int,
    isInfoItem: Boolean 
  ) {
    def descriptionOdfOption: Option[OdfDescription] =
      if (description.nonEmpty) Some(OdfDescription(description))
      else None


    def toOdfObject: OdfObject = toOdfObject()
    def toOdfObject(infoitems: Iterable[OdfInfoItem] = Iterable(), objects: Iterable[OdfObject] = Iterable()): OdfObject =
      OdfObject(Seq(QlmID(path.last)),path, infoitems, objects, descriptionOdfOption, None)

    def toOdfObjects: OdfObjects = OdfObjects()


    def toOdfInfoItem: OdfInfoItem = toOdfInfoItem()
    def toOdfInfoItem(values: Iterable[OdfValue] = Iterable()): OdfInfoItem =
      OdfInfoItem(path, values, descriptionOdfOption, None)
  }

  implicit val DBNodeOrdering = Ordering.by[DBNode, Int](_.leftBoundary)

  /**
   * (Boilerplate) Table to store object hierarchy.
   */
  class DBNodesTable(tag: Tag)
    extends Table[DBNode](tag, "HIERARCHYNODES") {
    /** This is the PrimaryKey */
    def id: Rep[Int] = column[Int]("HIERARCHYID", O.PrimaryKey, O.AutoInc)
    def path: Rep[Path] = column[Path]("PATH")
    def leftBoundary: Rep[Int] = column[Int]("LEFTBOUNDARY")
    def rightBoundary: Rep[Int] = column[Int]("RIGHTBOUNDARY")
    def depth: Rep[Int] = column[Int]("DEPTH")
    def description: Rep[String] = column[String]("DESCRIPTION")
    def pollRefCount: Rep[Int] = column[Int]("POLLREFCOUNT")
    def isInfoItem: Rep[Boolean] = column[Boolean]("ISINFOITEM")

    // Every table needs a * projection with the same type as the table's type parameter
    def * : ProvenShape[DBNode] = (id.?, path, leftBoundary, rightBoundary, depth, description, pollRefCount, isInfoItem) <> (
      DBNode.tupled,
      DBNode.unapply
    )
  }
  protected[this] val hierarchyNodes = TableQuery[DBNodesTable] //table for storing hierarchy
  protected[this] val hierarchyWithInsertId = hierarchyNodes returning hierarchyNodes.map(_.id)

  trait HierarchyFKey[A] extends Table[A] {
    val hierarchyfkName: String
    def hierarchyId: Rep[Int] = column[Int]("HIERARCHYID")
    def hierarchy: ForeignKeyQuery[DBNodesTable, DBNode] = foreignKey(hierarchyfkName, hierarchyId, hierarchyNodes)(
      _.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
  }






  /**
   * (Boilerplate) Table for storing latest sensor data to database
   */
  class DBValuesTable(tag: Tag)
    extends Table[DBValue](tag, "SENSORVALUES") with HierarchyFKey[DBValue] {
    val hierarchyfkName = "VALUESHIERARCHY_FK"
    // from extension:
    def id: Rep[Long] = column[Long]("VALUEID", O.PrimaryKey, O.AutoInc)
    def timestamp: Rep[Timestamp] = column[Timestamp]("TIME",O.SqlType("TIMESTAMP(3)"))
    def value: Rep[String] = column[String]("VALUE")
    def valueType: Rep[String] = column[String]("VALUETYPE")
    def idx1: Index = index("valueIdx", hierarchyId, unique = false) //index on hierarchyIDs
    def idx2: Index = index("timestamp", timestamp, unique = false)  //index on timestmaps
    /** Primary Key: (hierarchyId, timestamp) */

    def * : ProvenShape[DBValue] = (hierarchyId, timestamp, value, valueType, id.?) <> (DBValue.tupled, DBValue.unapply)
  }

  protected[this] val latestValues = TableQuery[DBValuesTable] //table for sensor data

///////////////////////////////////////////////////


  class PollSubsTable(tag: Tag)
    extends Table[SubValue](tag, "POLLSUBVALUES") {
    /** This is the PrimaryKey */
    def subId: Rep[Long] = column[Long]("SUBID")
    def path: Rep[Path] = column[Path]("PATH")
    def timestamp: Rep[Timestamp] = column[Timestamp]("TIME")
    def value: Rep[String] = column[String]("VALUE")
    def valueType: Rep[String] = column[String]("VALUETYPE")

    // Every table needs a * projection with the same type as the table's type parameter
    def * : ProvenShape[SubValue] = ( subId, path, timestamp, value, valueType) <> (
      SubValue.tupled,
      SubValue.unapply
      )
  }

  protected[this] val pollSubs = TableQuery[PollSubsTable]
  ///////////////////////////////////////////////////



  protected[this] val allTables =
    Seq( hierarchyNodes
       , latestValues
       , pollSubs
       )

  protected[this] val allSchemas = allTables map (_.schema) reduceLeft (_ ++ _)

  /**
   * Empties all the data from the database
   * 
   */
  def clearDB(): Future[Int] = {
    val rootPath = Path("/Objects")
    db.run(
      DBIO.seq(
        (allTables map (_.delete)): _*
      ).andThen(hierarchyNodes += DBNode(None, rootPath, 1, 2, rootPath.length, "", 0, false))
    )
  }

  def dropDB(): Future[Unit] = db.run( allSchemas.drop )
    
}
