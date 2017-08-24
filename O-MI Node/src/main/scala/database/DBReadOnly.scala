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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

import org.slf4j.LoggerFactory
//import slick.driver.H2Driver.api._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types._
import http.OmiNodeContext

/**
 * Read only restricted interface methods for db tables
 */
trait DBReadOnly extends DBBase with OdfConversions with DBUtility with OmiNodeTables {
  import dc.driver.api._
  protected def singleStores : SingleStores
  protected[this] def findParentI(childPath: Path): DBIOro[Option[DBNode]] = findParentQ(childPath).result.headOption

  protected[this] def findParentQ(childPath: Path): Query[DBNodesTable, DBNode, Seq] =
    if (childPath.length == 0)
      hierarchyNodes filter (_.path === childPath)
    else
      hierarchyNodes filter (_.path === Path(childPath.init))
  
  private val log = LoggerFactory.getLogger("DBReadOnly")

/**
 * @param root Root of the tree
 * @param depth Maximum traverse depth relative to root
 */
protected[this] def getSubTreeQ(
  root: DBNode,
  depth: Option[Int] = None): Query[(DBNodesTable, Rep[Option[DBValuesTable]]), DBValueTuple, Seq] = {

    val depthConstraint: DBNodesTable => Rep[Boolean] = node =>
      depth match {
        case Some(depthLimit) =>
          node.depth <= root.depth + depthLimit
        case None =>
          true
      }
      val nodesQ = hierarchyNodes filter { node =>
        node.leftBoundary >= root.leftBoundary &&
        node.rightBoundary <= root.rightBoundary &&
        depthConstraint(node)
      }

      val nodesWithValuesQ =
        nodesQ joinLeft latestValues on (_.id === _.hierarchyId)

      nodesWithValuesQ sortBy {case (nodes, _) => nodes.leftBoundary.asc}
  }

}
