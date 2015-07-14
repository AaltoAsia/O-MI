package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._

import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.SortedMap

import types.OdfTypes._



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
  protected def odfConversion(treeData: Seq[DBValueTuple]): Option[OdfObjects] = {
    // Convert: Map DBNode -> Seq[DBValue]
    val nodeMap = toDBInfoItems(treeData)
    odfConversion(nodeMap)
  }

  protected def hasPathConversion: DBInfoItem => HasPath = {
    case (infoItemNode, values) if infoItemNode.isInfoItem  =>
      val odfValues      = values map (_.toOdf) toIterable
      val odfInfoItem    = infoItemNode.toOdfInfoItem(odfValues)
      odfInfoItem
    case (objectNode, values) if !objectNode.isInfoItem && objectNode.depth > 1 =>
      objectNode.toOdfObject
    case (objectNode, values) if !objectNode.isInfoItem && objectNode.depth == 1 =>
      objectNode.toOdfObjects
  }

  /**
   * For url discovery get path.
   * Version for which Object children are supported for one level of the hierarchy.
   * @param items input data for single object (objects and infoitems for the first level of children)
   * @return Single object or infoItem extracted from items
   */
  protected def singleObjectConversion(items: DBInfoItems): Option[HasPath] = {
    //require(items.size > 0, "singleObjectConversion requires data!")
    if (items.size == 0) return None

    val nodes = items.keys

    // search element with the lowest depth and take only the node
    val theObject = items minBy (_._1.depth)

    theObject match {
      case (objectNode, _) if !objectNode.isInfoItem =>
        val allChildren =
          nodes filter (item =>
              item.leftBoundary > objectNode.leftBoundary &&
              item.leftBoundary < objectNode.rightBoundary
              )

        val (infoItemChildren, objectChildren) = allChildren partition (_.isInfoItem)

        val odfInfoItemChildren = infoItemChildren map (_.toOdfInfoItem)
        val odfObjectChildren   = objectChildren   map (_.toOdfObject)


        require(allChildren.nonEmpty, s"should have children, has $items")

        if (objectNode.depth == 1){
          val odfObjects = OdfObjects(odfObjectChildren)
          Some(odfObjects)
        } else {
          val odfObject = objectNode.toOdfObject(odfInfoItemChildren, odfObjectChildren)
          Some(odfObject)
        }

      case infoItem @ (infoItemNode, _) if infoItemNode.isInfoItem  =>
        Some(hasPathConversion(infoItem))
    }
  }

  protected def odfConversion: DBInfoItem => OdfObjects =
    fromPath _ compose hasPathConversion

  protected def odfConversion(treeData: DBInfoItems): Option[OdfObjects] = {
    val odfObjectsTrees = treeData map odfConversion

    // safe version of reduce
    odfObjectsTrees.headOption map { head =>
        odfObjectsTrees.reduce(_ combine _)
    }
  }
}
