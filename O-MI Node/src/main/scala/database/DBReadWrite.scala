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
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

import org.slf4j.LoggerFactory
//import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.OmiReturn
import types._

/**
 * Read-write interface methods for db tables.
 */
trait DBReadWrite extends DBReadOnly with OmiNodeTables {
  import dc.driver.api._
  type ReadWrite = Effect with Effect.Write with Effect.Read with Effect.Transactional
  type DBIOrw[Result] = DBIOAction[Result, NoStream, ReadWrite]

  private val log = LoggerFactory.getLogger("DBReadWrite")


  /**
   * Initializing method, creates the file and tables.
   * This method blocks everything else in this object.
   *
   * Tries to guess if tables are not yet created by checking existing tables
   * This gives false-positive only when there is other tables present. In that case
   * manually clean the database.
   */
  def initialize(): Unit = this.synchronized {

    val setup = DBIO.seq(
      allSchemas.create,
      addRoot)

    val existingTables = MTable.getTables
    val existed = Await.result(db.run(existingTables), 5 minutes)
    if (existed.nonEmpty) {
      //noop
      log.info(
        "Found tables: " +
          existed.map { _.name.name }.mkString(", ") +
          "\n Not creating new tables.")
    } else {
      //run transactionally so there are all or no tables

      log.info("Creating new tables: " + allTables.map(_.baseTableRow.tableName).mkString(", "))
      Await.result(db.run(setup.transactionally), 5 minutes)
    }
  }

  /**
   * Metohod to completely remove database. Tries to remove the actual database file.
   */
  def destroy(): Unit

  /**
   * Adds missing objects(if any) to hierarchy based on given path
   * @param path path whose hierarchy is to be stored to database
   * @return Inserted (path, id) tuples
   */
  protected[this] def addObjectsI(path: Path, lastIsInfoItem: Boolean): DBIOrw[Seq[(Path, Int)]] = {

    /** Query: Increase right and left values after value */
    def increaseAfterQ(value: Int) = {

      // NOTE: Slick 3.0.0 doesn't allow this query with its types, use sql instead
      //val rightValsQ = hierarchyNodes map (_.rightBoundary) filter (_ > value) 
      //val leftValsQ  = hierarchyNodes map (_.leftBoundary) filter (_ > value)
      //val rightUpdateQ = rightValsQ.map(_ + 2).update(rightValsQ)
      //val leftUpdateQ  =  leftValsQ.map(_ + 2).update(leftValsQ)

      DBIO.seq(
        sqlu"UPDATE HIERARCHYNODES SET RIGHTBOUNDARY = RIGHTBOUNDARY + 2 WHERE RIGHTBOUNDARY >= ${value}",
        sqlu"UPDATE HIERARCHYNODES SET LEFTBOUNDARY = LEFTBOUNDARY + 2 WHERE LEFTBOUNDARY > ${value}")
    }

    // @return insertId
    def addNode(isInfoItem: Boolean)(fullpath: Path): DBIOrw[(Path, Int)] = (for {

      parentO <- findParentI(fullpath)
      parent = parentO getOrElse {
        throw new RuntimeException(s"Didn't find root parent when creating objects, for path: $fullpath")
      }

      parentRight = parent.rightBoundary
      left = parentRight
      right = left + 1

      _ <- increaseAfterQ(parentRight)
      
      insertId <- hierarchyWithInsertId += DBNode(None, fullpath, left, right, fullpath.length, "", 0, isInfoItem)
    } yield (fullpath, insertId) ).transactionally

    val parentsAndPath = path.getParentsAndSelf

    val foundPathsI = hierarchyNodes filter (_.path inSet parentsAndPath) map (_.path) result
    // difference between all and found
    val missingPathsI: DBIOro[Seq[Path]] = foundPathsI map (parentsAndPath diff _)

    // Combine DBIOActions as a single action
    val addingAction = missingPathsI flatMap { (missingPaths: Seq[Path]) =>

      // these will not break when empty
      val init = missingPaths.dropRight(1)
      val last = missingPaths.takeRight(1)

      DBIO.sequence(
        (init map addNode(false)) ++
          (last map addNode(lastIsInfoItem)))
    }

    // NOTE: transaction level probably could be reduced to increaseAfter + DBNode insert
    addingAction.transactionally
  }


  /**
   * NOTE: This function is not used at the moment - 2016/06.
   * Used to write values to database. If data already exists for the path, appends until historyLength
   * is met, otherwise creates new data and all the missing objects to the hierarchy.
   *  Does not remove excess rows if path is set or buffer
   */
  def write(path: Path, timestamp: Timestamp, value: String, valueType: String = ""): Future[(Path, Int)] = {
    val updateAction = for {

      _ <- addObjectsI(path, true)

      nodeIdSeq <- getHierarchyNodeQ(path).map(
        node => node.id).result

      updateResult <- nodeIdSeq.headOption match {
        case None =>
          // shouldn't happen
          throw new RuntimeException("Didn't get nodeIds from query when they were just checked/added")

        case Some(id) => for {
          _ <- (latestValues += DBValue(id, timestamp, value, valueType))
        } yield (path, id)
      }
    } yield updateResult

    val returnId = db.run(updateAction.transactionally)

    //val infoitem = OdfInfoItem( path, Iterable( OdfValue[Any](value, valueType, timestamp ) ) )

    //Call hooks
    returnId
  }



  /**
   * Used to set many values efficiently to the database.
   */
  def writeMany(infos: Seq[OdfInfoItem]): Future[OmiReturn] = {
    val pathsData: Map[Path, Seq[OdfValue[Any]]] = infos.map(ii => (ii.path -> ii.values.sortBy(_.timestamp.getTime))).toMap

    val writeAction = for {
      addObjectsAction <- DBIO.sequence(
        pathsData.keys map (addObjectsI(_, lastIsInfoItem = true))) // NOTE: Heavy operation

      idQry <- getHierarchyNodesQ(pathsData.keys.toSeq) map { hNode => // NOTE: Heavy operation
        (hNode.path, hNode.id)
      } result

      idMap = idQry.toMap: Map[Path, Int]

      pathsToIds = pathsData map {
        case (path, odfValues) => (idMap(path), odfValues)
      }

      dbValues = pathsToIds flatMap {
        case (id, odfValues) => odfValues map { odfVal =>
          DBValue(
            id,
            //create new timestamp if option is None
            odfVal.timestamp,
            odfVal.value.toString,
            odfVal.typeValue)
        }
      }
      updateAction <- latestValues ++= dbValues
        
    } yield idMap.toSeq

    val pathIdRelations : Future[Seq[(types.Path, Int)]] = db.run(writeAction.transactionally)

    //Call hooks
    pathIdRelations.map{ 
      case seq : Seq[(types.Path, Int)] if seq.nonEmpty => OmiReturn("200")
      case seq : Seq[(types.Path, Int)] if seq.isEmpty =>
        OmiReturn("500",Some("Using old database. Should use Warp 10."))
    }
  }


  //@deprecated("For testing only.", "Since implemented.")
  private def removeQ(path: Path) = {// : Future[DBIOrw[Seq[Any]]] ?
    val resultAction = for{
      hNode <-  hierarchyNodes.filter(_.path === path).result.map(_.headOption)
      resOpt =  hNode.map{ node =>
        val removedLeft = node.leftBoundary
        val removedRight = node.rightBoundary
        for{
          removedValues <- getSubTreeQ(node).result
          removedIds = removedValues.map{case (_node, _) => _node.id.getOrElse(throw new UninitializedError)}.distinct
          removeOp = DBIO.fold(Seq(
           latestValues.filter { _.hierarchyId.inSet(removedIds) }.delete,
           hierarchyNodes.filter { _.id.inSet(removedIds) }.delete
          ), 0)((delete1, delete2) => delete1 + delete2)
          removedDistance = removedRight - removedLeft + 1
          updateActions = DBIO.seq(
            sqlu"""UPDATE HIERARCHYNODES SET RIGHTBOUNDARY =  RIGHTBOUNDARY - ${removedDistance}
              WHERE RIGHTBOUNDARY > ${removedLeft}""",
            sqlu"""UPDATE HIERARCHYNODES SET LEFTBOUNDARY = LEFTBOUNDARY - ${removedDistance}
              WHERE LEFTBOUNDARY > ${removedLeft}""").map(_ => 0)
          numDel <- DBIO.fold(Seq(removeOp, updateActions), 0)((start, next) => start + next)

        } yield numDel//FIX

      }
      res <- resOpt.getOrElse(DBIO.failed(new Exception))
    } yield res
    resultAction
  }

  /**
   * Remove is used to remove sensor given its path. Removes all unused objects from the hierarchcy along the path too.
   *
   *
   * @param path path to to-be-deleted sub tree.
   * @return boolean whether something was removed
   */
  def remove(path: Path): Future[Int] = {
    if(path.length == 1){
      removeRoot(path)
    }else{
      db.run(removeQ(path).transactionally)
    }
  }

  /**
   * remove the root Objects from the database and add empty root  back to database
   * this is to help executing the removing and adding operation transactionally
   */
  def removeRoot(path: Path): Future[Int] = {
    db.run(
      DBIO.sequence(Seq(removeQ(path),addRoot.map(res => 0))).transactionally
    ).map(_.sum)
  }
  //add root node when removed or when first started
  private def addRoot = {
    hierarchyNodes += DBNode(None, Path("/Objects"), 1, 2, Path("/Objects").length, "", 0, false)
  }
  def addRootR: Future[Int] = {
    db.run(addRoot)
  }

  def trimDB(): Future[Seq[Int]] = {
    val historyLen = database.historyLength
    val hIdQuery = (for(h <- hierarchyNodes) yield h.id).result

    val startT = System.currentTimeMillis()
    log.info(s"trimming database to $historyLen newest values")
    val trimQuery = for{
      idList <- hIdQuery
      res <- DBIO.sequence(idList.map(id => sqlu"""DELETE FROM SENSORVALUES
                   WHERE VALUEID <= (
                     SELECT VALUEID
                     FROM (
                       SELECT VALUEID
                       FROM SENSORVALUES
                       WHERE HIERARCHYID = $id
                       ORDER BY TIME DESC
                       LIMIT 1 OFFSET $historyLen
                     ) foo
                    ) AND HIERARCHYID = $id;
            """))
    } yield res

    db.run(trimQuery)

    //val endT = System.currentTimeMillis()
    //log.info(s"Deleting took ${endT - startT} milliseconds")

    //runQ
    /*val historyLen = 50//database.historyLength
   val qry = sqlu"""DELETE FROM SENSORVALUES
                     WHERE VALUEID NOT IN (SELECT a.VALUEID FROM SENSORVALUES AS a
                       LEFT JOIN SENSORVALUES AS a2
                         ON a.HIERARCHYID = a2.HIERARCHYID AND a.TIME <= a2.TIME
                     GROUP BY a.VALUEID
                     HAVING COUNT(*) <= ${historyLen});"""

    val runQ = db.run(qry)
   runQ
  */}

}
