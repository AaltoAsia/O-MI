package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.SortedMap

import types._
import types.OdfTypes._
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable

trait DBUtility extends OmiNodeTables with OdfConversions {
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]

  protected def dbioDBInfoItemsSum(actions: Seq[DBIO[DBInfoItems]]): DBIO[DBInfoItems] =
    DBIO.fold(actions, SortedMap.empty[DBNode,Seq[DBValue]])(_ ++ _)

  protected def dbioSeqSum[A]: Seq[DBIO[Seq[A]]] => DBIO[Seq[A]] = {
    seqIO =>
      def iosumlist(a: DBIO[Seq[A]], b: DBIO[Seq[A]]): DBIO[Seq[A]] = for {
        listA <- a
        listB <- b
      } yield (listA++listB)
      seqIO.foldRight(DBIO.successful(Seq.empty[A]):DBIO[Seq[A]])(iosumlist _)
  }

  // Used for compiler type trickery by causing type errors
  trait Hole // TODO: RemoveMe!

  //Helper for getting values with path
  protected def getValuesQ(path: Path) =
    getWithHierarchyQ[DBValue, DBValuesTable](path, latestValues)

  protected def getValuesQ(id: Int) =
    latestValues filter (_.hierarchyId === id)

  protected def getValueI(path: Path) =
    getValuesQ(path).sortBy(
      _.timestamp.desc
    ).result.map(_.headOption)

  protected def getDBInfoItemI(path: Path): DBIOro[Option[DBInfoItem]] = {

    val tupleDataI = joinWithHierarchyQ[DBValue, DBValuesTable](path, latestValues).result

    tupleDataI map toDBInfoItem
  }

  protected def getWithExprI[ItemT, TableT <: HierarchyFKey[ItemT]](
    expr: Rep[ItemT] => Rep[Boolean],
    table: TableQuery[TableT]
  ): DBIOro[Option[ItemT]] =
    table.filter(expr).result.map(_.headOption)

  protected def getWithHierarchyQ[ItemT, TableT <: HierarchyFKey[ItemT]](
    path: Path,
    table: TableQuery[TableT]
  ): Query[TableT,ItemT,Seq] = // NOTE: Does the Query table need (DBNodesTable, TableT) ?
    for {
      (hie, value) <- getHierarchyNodeQ(path) join table on (_.id === _.hierarchyId )
    } yield(value)

  protected def joinWithHierarchyQ[ItemT, TableT <: HierarchyFKey[ItemT]](
    path: Path,
    table: TableQuery[TableT]
  ): Query[(DBNodesTable, Rep[Option[TableT]]),(DBNode, Option[ItemT]),Seq] =
    hierarchyNodes.filter(_.path === path) joinLeft table on (_.id === _.hierarchyId )

  protected def getHierarchyNodeQ(path: Path) : Query[DBNodesTable, DBNode, Seq] =
    hierarchyNodes.filter(_.path === path)

  protected def getHierarchyNodeQ(id: Int) : Query[DBNodesTable, DBNode, Seq] =
    hierarchyNodes.filter(_.id === id)

  protected def getHierarchyNodeI(path: Path): DBIOro[Option[DBNode]] =
    hierarchyNodes.filter(_.path === path).result.map(_.headOption)

  protected def getHierarchyNodesI(paths: Seq[Path]): DBIOro[Seq[DBNode]] =
  hierarchyNodes.filter(node => node.path.inSet( paths) ).result
   
  protected def getHierarchyNodesQ(paths: Seq[Path]) :Query[DBNodesTable,DBNode,Seq]=
  hierarchyNodes.filter(node => node.path.inSet( paths) )

  protected def getHierarchyNodeI(id: Int): DBIOro[Option[DBNode]] =
    hierarchyNodes.filter(_.id === id).result.map(_.headOption)


  protected def getSubI(id: Int): DBIOro[Option[DBSub]] =
    subs.filter(_.id === id).result map {
      _.headOption map {
        case sub: DBSub => sub
        case _ => throw new RuntimeException("got wrong or unknown sub class???")
      }
    }
  protected def getSubItemHierarchyIdsI(subId: Int) =
    subItems filter (
      _.subId === subId
    ) map ( _.hierarchyId ) result


}
