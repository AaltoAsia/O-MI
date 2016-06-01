/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package database

import scala.collection.SortedMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

import slick.driver.H2Driver.api._
import types._

trait DBUtility extends OmiNodeTables with OdfConversions {
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]

  // Used for compiler type trickery by causing type errors
  //trait Hole // TODO: RemoveMe!

  protected[this] def dbioDBInfoItemsSum(actions: Seq[DBIO[DBInfoItems]]): DBIO[DBInfoItems] =
    DBIO.fold(actions, SortedMap.empty[DBNode,Seq[DBValue]])(_ ++ _)

  protected[this] def dbioSeqSum[A]: Seq[DBIO[Seq[A]]] => DBIO[Seq[A]] = {
    seqIO =>
      def iosumlist(a: DBIO[Seq[A]], b: DBIO[Seq[A]]): DBIO[Seq[A]] = for {
        listA <- a
        listB <- b
      } yield (listA++listB)
      seqIO.foldRight(DBIO.successful(Seq.empty[A]):DBIO[Seq[A]])(iosumlist _)
  }

  //Helper for getting values with path
  protected[this] def getValuesQ(path: Path) =
    getWithHierarchyQ[DBValue, DBValuesTable](path, latestValues)

  protected[this] def getValuesQ(id: Int) =
    latestValues filter (_.hierarchyId === id)

  protected[this] def getValueI(path: Path) =
    getValuesQ(path).sortBy(
      _.timestamp.desc
    ).result.map(_.headOption)

  protected[this] def getDBInfoItemI(path: Path): DBIOro[Option[DBInfoItem]] = {

    val tupleDataI = joinWithHierarchyQ[DBValue, DBValuesTable](path, latestValues).result

    tupleDataI map toDBInfoItem
  }

  protected[this] def getWithExprI[ItemT, TableT <: HierarchyFKey[ItemT]](
    expr: Rep[ItemT] => Rep[Boolean],
    table: TableQuery[TableT]
  ): DBIOro[Option[ItemT]] =
    table.filter(expr).result.map(_.headOption)

  protected[this] def getWithHierarchyQ[ItemT, TableT <: HierarchyFKey[ItemT]](
    path: Path,
    table: TableQuery[TableT]
  ): Query[TableT,ItemT,Seq] = // NOTE: Does the Query table need (DBNodesTable, TableT) ?
    for {
      (hie, value) <- getHierarchyNodeQ(path) join table on (_.id === _.hierarchyId )
    } yield(value)

  protected[this] def joinWithHierarchyQ[ItemT, TableT <: HierarchyFKey[ItemT]](
    path: Path,
    table: TableQuery[TableT]
  ): Query[(DBNodesTable, Rep[Option[TableT]]),(DBNode, Option[ItemT]),Seq] =
    hierarchyNodes.filter(_.path === path) joinLeft table on (_.id === _.hierarchyId )

  protected[this] def getHierarchyNodeQ(path: Path) : Query[DBNodesTable, DBNode, Seq] =
    hierarchyNodes.filter(_.path === path)

  protected[this] def getHierarchyNodeQ(id: Int) : Query[DBNodesTable, DBNode, Seq] =
    hierarchyNodes.filter(_.id === id)

  protected[this] def getHierarchyNodeI(path: Path): DBIOro[Option[DBNode]] =
    hierarchyNodes.filter(_.path === path).result.map(_.headOption)

  protected[this] def getHierarchyNodesI(paths: Seq[Path]): DBIOro[Seq[DBNode]] =
  hierarchyNodes.filter(node => node.path.inSet( paths) ).result
   
  protected[this] def getHierarchyNodesQ(paths: Seq[Path]) :Query[DBNodesTable,DBNode,Seq]=
  hierarchyNodes.filter(node => node.path.inSet( paths) )

  protected[this] def getHierarchyNodeI(id: Int): DBIOro[Option[DBNode]] =
    hierarchyNodes.filter(_.id === id).result.map(_.headOption)



}
