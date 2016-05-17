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

import java.sql.Timestamp

import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types._
import http.Boot.system.log
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
 * Read-write interface methods for db tables.
 */
trait DBReadWrite extends DBReadOnly with OmiNodeTables {
  type ReadWrite = Effect with Effect.Write with Effect.Read with Effect.Transactional
  type DBIOrw[Result] = DBIOAction[Result, NoStream, ReadWrite]

  /**
   * Initializing method, creates the file and tables.
   * This method blocks everything else in this object.
   *
   * Tries to guess if tables are not yet created by checking existing tables
   * This gives false-positive only when there is other tables present. In that case
   * manually clean the database.
   */
  def initialize() = this.synchronized {

    val setup = DBIO.seq(
      allSchemas.create,
      addRoot)

    val existingTables = MTable.getTables
    val existed = runSync(existingTables)
    if (existed.length > 0) {
      //noop
      log.info(
        "Found tables: " +
          existed.map { _.name.name }.mkString(", ") +
          "\n Not creating new tables.")
    } else {
      //run transactionally so there are all or no tables

      log.info("Creating new tables: " + allTables.map(_.baseTableRow.tableName).mkString(", "))
      runSync(setup.transactionally)
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
   * Used to set values to database. If data already exists for the path, appends until historyLength
   * is met, otherwise creates new data and all the missing objects to the hierarchy.
   *  Does not remove excess rows if path is set or buffer
   *
   *  @param data sensordata, of type DBSensor to be stored to database.
   *  @return hierarchy id
   */
  def set(path: Path, timestamp: Timestamp, value: String, valueType: String = ""): (Path, Int) = {
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

    val returnId = runSync(updateAction.transactionally)

    val infoitem = OdfInfoItem( path, Iterable( OdfValue(value, valueType, timestamp ) ) ) 

    //Call hooks
    database.getSetHooks foreach { _(Seq(infoitem)) }
    returnId
  }



  /**
   * Used to set many values efficiently to the database.
   * @param data list item to be added consisting of Path and OdfValue tuples.
   */
  def setMany(data: Seq[(Path, OdfValue)]): Seq[(Path, Int)] = {

    val pathsData: Map[Path, Seq[OdfValue]] =
      data.groupBy(_._1).mapValues(
        v => v.map(_._2).sortBy(
          _.timestamp.getTime
        ))

    val writeAction = for {
      addObjectsAction <- DBIO.sequence(
        pathsData.keys map (addObjectsI(_, lastIsInfoItem = true)))

      idQry <- getHierarchyNodesQ(pathsData.keys.toSeq) map { hNode =>
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
            odfVal.value,
            odfVal.typeValue)
        }
      }
      updateAction <- latestValues ++= dbValues
        
    } yield idMap.toSeq

    val pathIdRelations = runSync(writeAction.transactionally)

    val infoitems = pathsData.collect{
      case (path: Path, values : Seq[OdfValue] ) if values.nonEmpty =>
        OdfInfoItem(
          path,
          values.map{ va => 
            OdfValue(
              va.value,
              va.typeValue,
              va.timestamp
            )
          }.toIterable
      )
    }
    //Call hooks
    database.getSetHooks foreach { _(infoitems.toSeq) }

    pathIdRelations
  }

  /**
   * Remove is used to remove sensor given its path. Removes all unused objects from the hierarchcy along the path too.
   *
   *
   * @param path path to to-be-deleted sub tree.
   * @return boolean whether something was removed
   */
  //@deprecated("For testing only.", "Since implemented.")
  def remove(path: Path): Boolean = {
    val hNode = runSync(hierarchyNodes.filter(_.path === path).result).headOption
    if (hNode.isEmpty) return false //require( hNode.nonEmpty, s"No such item found. Cannot remove. path: $path")  

    val removedLeft = hNode.getOrElse(throw new UninitializedError).leftBoundary
    val removedRight = hNode.getOrElse(throw new UninitializedError).rightBoundary
    val subTreeQ = getSubTreeQ(hNode.get)
    val subTree = runSync(subTreeQ.result)
    val removedIds = subTree.map { _._1.id.getOrElse(throw new UninitializedError) }
    val removeActions = DBIO.seq(
      latestValues.filter { _.hierarchyId.inSet(removedIds) }.delete,
      hierarchyNodes.filter { _.id.inSet(removedIds) }.delete)
    val removedDistance = removedRight - removedLeft + 1 // one added to fix distance to rigth most boundary before removed left ( 14-11=3, 15-3=12 is not same as removed 11 ) 
    val updateActions = DBIO.seq(
      sqlu"""UPDATE HIERARCHYNODES SET RIGHTBOUNDARY =  RIGHTBOUNDARY - ${removedDistance}
        WHERE RIGHTBOUNDARY > ${removedLeft}""",
      sqlu"""UPDATE HIERARCHYNODES SET LEFTBOUNDARY = LEFTBOUNDARY - ${removedDistance} 
        WHERE LEFTBOUNDARY > ${removedLeft}""")
    runSync(DBIO.seq(removeActions, updateActions))
    true
  }
  //add root node when removed or when first started
  private def addRoot = {
    hierarchyNodes += DBNode(None, Path("/Objects"), 1, 2, Path("/Objects").length, "", 0, false)
  }
  def addRootR = {
    db.run(addRoot)
  }

  def removePollSub(id: Long): Int = {
    val q = pollSubs filter(_.subId === id)
    val action = q.delete
    val result = runSync(action)
    result
  }

  /*def removeDataAndUpdateLastValues(id: Long, lastValues: Seq[SubValue]) = {
    runSync(removeDataAndUpdateLastValuesI(id, lastValues))
  }
  private def removeDataAndUpdateLastValuesI(id: Long, lastValues: Seq[SubValue]) = {
    val subData = pollSubs filter (_.subId === id)
    val updateAction = for {
      _ <- subData.delete
      added <- pollSubs ++= lastValues
    } yield added
    updateAction
  }*/
  /**
   * Method used for polling subsription data from database.
   * Returns and removes
   * @param id
   * @return
   */
  def pollEventSubscription(id: Long): Seq[SubValue] = {
    runSync(pollEventSubscriptionI(id))
  }
  def debugMethod = {
    runSync(pollSubs.result) //TODO remove
  }
  private def pollEventSubscriptionI(id: Long) = {
    val subData = pollSubs filter (_.subId === id)
    for{
      data <- subData.result
      _ <- subData.delete
    } yield data
  }
  
  def pollIntervalSubscription(id: Long): Seq[SubValue] = {
    runSync(pollIntervalSubscriptionI(id))
  }
  
  private def pollIntervalSubscriptionI(id: Long) = {
    val subData = pollSubs filter (_.subId === id)
    for{
      data <- subData.result
      lastValues = data.groupBy(_.path).flatMap{ //group by path
        case (iPath, pathData) =>
        //pathData.maxBy(_.timestamp.getTime) maxBy can produce nullpointer exception
        pathData.foldLeft[Option[SubValue]](None){(col, next) => //find value with newest timestamp
          col.fold(Option(next)){c => //compare
            if (c.timestamp.before(next.timestamp)) Option(next) else Option(c)
         }
        }
      }
      _ <- subData.delete
      _ <- pollSubs ++= lastValues
    //_<- //(pollSubs ++= groupedData.map(_._2).flatten)
    } yield data
  }

  def addNewPollData(newData: Seq[SubValue]) = {
    runSync(pollSubs ++= newData)
  }

  def trimDB() = {
    val historyLen = database.historyLength
    val hIdQuery = (for(h <- hierarchyNodes) yield h.id).result

    val startT = System.currentTimeMillis()
    log.info(s"trimming database to $historyLen newest values")

    val idList = runSync(hIdQuery)
    val qry = idList.map(id => sqlu"""DELETE FROM SENSORVALUES
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
            """)


    val runQ = runSync(DBIO.sequence(qry)).sum

    val endT = System.currentTimeMillis()
    log.info(s"Deleting took ${endT - startT} milliseconds")

    runQ
    /*val historyLen = 50//database.historyLength
   val qry = sqlu"""DELETE FROM SENSORVALUES
                     WHERE VALUEID NOT IN (SELECT a.VALUEID FROM SENSORVALUES AS a
                       LEFT JOIN SENSORVALUES AS a2
                         ON a.HIERARCHYID = a2.HIERARCHYID AND a.TIME <= a2.TIME
                     GROUP BY a.VALUEID
                     HAVING COUNT(*) <= ${historyLen});"""

    val runQ = runSync(qry)
   runQ
  */}

}
