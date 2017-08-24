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

import parsing.xmlGen.xmlTypes.QlmIDType
import slick.backend.DatabaseConfig
//import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
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
  val dc : DatabaseConfig[JdbcProfile] //= DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.driver.api._
  val db: Database
  //protected[this] val db: Database
}

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
  def toOdf: OdfValue[Any] = OdfValue(value, valueType, timestamp)
}

case class SubValue(
                     subId: Long,
                     path: Path,
                     timestamp: Timestamp,
                     value: String,
                     valueType: String
                     ) {
  def toOdf: OdfValue[Any] = OdfValue(value, valueType, timestamp)
}

trait OmiNodeTables extends DBBase {
  import dc.driver.api._

  implicit lazy val pathColumnType = MappedColumnType.base[Path, String](
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
      OdfObject(Seq(OdfQlmID(path.last)),path, infoitems, objects, descriptionOdfOption, None)

    def toOdfObjects: OdfObjects = OdfObjects()


    def toOdfInfoItem: OdfInfoItem = toOdfInfoItem()
    def toOdfInfoItem(values: Iterable[OdfValue[Any]] = Iterable()): OdfInfoItem =
      OdfInfoItem(path, values, descriptionOdfOption, None)
  }

  implicit lazy val DBNodeOrdering = Ordering.by[DBNode, Int](_.leftBoundary)

  /**
   * (Boilerplate) Table to store object hierarchy.
   */
  class DBNodesTable(tag: Tag)
    extends Table[DBNode](tag, "HIERARCHYNODES") {
    import dc.driver.api._
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
  protected[this] lazy val hierarchyNodes = TableQuery[DBNodesTable] //table for storing hierarchy
  protected[this] lazy val hierarchyWithInsertId = hierarchyNodes returning hierarchyNodes.map(_.id)

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
    def idx1: Index = index("VALUEiDX", hierarchyId, unique = false) //index on hierarchyIDs
    def idx2: Index = index("TIMESTAMP", timestamp, unique = false)  //index on timestmaps
    /** Primary Key: (hierarchyId, timestamp) */

    def * : ProvenShape[DBValue] = (hierarchyId, timestamp, value, valueType, id.?) <> (DBValue.tupled, DBValue.unapply)
  }

  protected[this] lazy val latestValues = TableQuery[DBValuesTable] //table for sensor data



  protected[this] lazy val allTables =
    Seq( hierarchyNodes
       , latestValues
       )

  protected[this] lazy val allSchemas = allTables map (_.schema) reduceLeft (_ ++ _)

  /**
   * Empties all the data from the database
   * 
   */
  def clearDB(): Future[Int] = {
    val rootPath = Path("/Objects")
    db.run(
      DBIO.seq(
        (allTables map (_.delete)): _*
      ).andThen(hierarchyNodes += DBNode(None, rootPath, 1, 2, rootPath.length, "", 0, isInfoItem = false))
    )
  }

  def dropDB(): Future[Unit] = db.run( allSchemas.drop )
    
}
