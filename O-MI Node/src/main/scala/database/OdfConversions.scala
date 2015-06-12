package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.SortedMap

import parsing.Types._
import parsing.Types.OdfTypes._

import java.lang.RuntimeException

trait OdfConversions extends OmiNodeTables {
  type DBValueTuple= (DBNode, Option[DBValue])
  type DBInfoItem  = (DBNode, Seq[DBValue])
  type DBInfoItems = SortedMap[DBNode, Seq[DBValue]]


  def toDBInfoItems(input: Seq[DBValueTuple]): DBInfoItems =
    SortedMap(input groupBy (_._1) mapValues {values =>
      val empty = List[DBValue]()

      values.foldLeft(empty){
        case (others, (_, Some(dbValue))) => dbValue :: others
        case (others, (_, None)) => others
      }

      } toArray : _*
    )(DBNodeOrdering)

  def toDBInfoItem(tupleData: Seq[DBValueTuple]): Option[DBInfoItem] = {
      val items = toDBInfoItems(tupleData)
      assert(items.size <= 1, "Asked one infoitem, should contain max one infoitem")
      items.headOption
    }  

  /**
   * Conversion for a (sub)tree of hierarchy with value data.
   * @param treeData Hierarchy and value data joined, so contains InfoItem DBNodes and its values.
   */
  protected def odfConversion(treeData: Seq[DBValueTuple]): OdfObjects = {
    // Convert: Map DBNode -> Seq[DBValue]
    val nodeMap = toDBInfoItems(treeData)
    odfConversion(treeData)
  }

  protected def hasPathConversion: DBInfoItem => HasPath = {
    case (node, values) if node.isInfoItem  =>
      val odfValues      = values map (_.toOdf) toIterable
      val odfInfoItem    = node.toOdfInfoItem(odfValues)
      odfInfoItem
    case (node, values) if !node.isInfoItem =>
      node.toOdfObject
  }

  protected def odfConversion: DBInfoItem => OdfObjects =
    fromPath _ compose hasPathConversion

  protected def odfConversion(treeData: DBInfoItems): Option[OdfObjects] = {
    val odfObjectsTrees = treeData map odfConversion

    // safe version of reduce
    odfObjectsTrees.headOption map { head =>
        odfObjectsTrees.fold(head)(_ combine _)
    }
  }
}
