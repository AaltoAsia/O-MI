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

import scala.collection.SortedMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

import slick.driver.H2Driver.api._
import types._

trait DBUtility extends OmiNodeTables with OdfConversions {
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]

  protected[this] def getHierarchyNodeQ(path: Path) : Query[DBNodesTable, DBNode, Seq] =
    hierarchyNodes.filter(_.path === path)

  protected[this] def getHierarchyNodeQ(id: Int) : Query[DBNodesTable, DBNode, Seq] =
    hierarchyNodes.filter(_.id === id)

  protected[this] def getHierarchyNodesI(paths: Seq[Path]): DBIOro[Seq[DBNode]] =
  hierarchyNodes.filter(node => node.path.inSet( paths) ).result
   
  protected[this] def getHierarchyNodesQ(paths: Seq[Path]) :Query[DBNodesTable,DBNode,Seq]=
  hierarchyNodes.filter(node => node.path.inSet( paths) )

}
