/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  https://github.com/AaltoAsia/O-MI/blob/master/LICENSE.txt

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable}
import scala.collection.SortedMap

import types._
import types.OdfTypes._

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
      hierarchyNodes += DBNode(None, Path("/Objects"), 1, 2, Path("/Objects").length, "", 0, false))

    val existingTables = MTable.getTables
    val existed = runSync(existingTables)
    if (existed.length > 0) {
      //noop
      println(
        "Found tables: " +
          existed.map { _.name.name }.mkString(", ") +
          "\n Not creating new tables.")
    } else {
      // run transactionally so there are all or no tables

      println("Creating new tables: " + allTables.map(_.baseTableRow.tableName).mkString(", "))
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

  def addNodes(odfNodes: Seq[OdfNode] ) :Seq[(Path,Int)] = {
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

    val addingAction = DBIO.sequence(
      odfNodes.sortBy(_.path.length).collect{
        case objs : OdfObjects=> 
          addNode(false)(objs.path)
        case obj : OdfObject=> 
          addNode(false)(obj.path)
        case info : OdfInfoItem=> 
          addNode(true)(info.path)
      }
    ) 

    // NOTE: transaction level probably could be reduced to increaseAfter + DBNode insert
    runSync( addingAction.transactionally )
  
  }



  /**
   * Used to remove data before given timestamp
   * @param path path to sensor as Path object
   * @param timestamp that tells how old data will be removed, exclusive.
   */
  private[this] def removeBefore(paths: SortedMap[DBNode, Seq[DBValue]], timestamp: Timestamp) = {

    val infoitems = paths.keySet.collect{
      case DBNode(Some(id),_,_,_,_,_,_,isInfoItem) if (isInfoItem)=> id
    }.toSeq
    
    val historyLen = database.historyLength

    val removeBeforeActions = infoitems.map { iItem =>
      val items = for {
        subscriptionitems <- subItems filter (subItem => subItem.hierarchyId === iItem) result

        earliest <- subs.filter { sub => sub.id.inSet(subscriptionitems.map(_.subId)) }.map(_.startTime).sorted.result

        time = earliest.headOption

        pathQuery = latestValues filter (_.hierarchyId === iItem) sortBy (_.timestamp.asc)

        qLen <- pathQuery.length.result

        removeAction <- if (qLen > historyLen) for {

          sortedVals <- pathQuery.result
          oldTime = sortedVals.drop(qLen - historyLen).head.timestamp

          cutOffTime = time.fold(oldTime)(subtime => if (subtime.before(oldTime)) subtime else oldTime)

          result <- latestValues.filter(value =>
            value.hierarchyId === iItem && value.timestamp < cutOffTime).delete

          asd <- latestValues.filter(value =>
            value.hierarchyId === iItem).map(_.value).result

          //           debug={
          ////            println("removedebug:vvvvvvvvvvvvvvv")
          ////            asd.foreach(println(_))
          //         }

          } yield result
        else DBIO.successful((0))

        //       debug = {
        //         println(s"""
        //earliest: $earliest
        //time:     $time
        //pathQuery:$pathQuery
        //qLen:     $qLen
        //removeAction: $removeAction
        //timestamp:$timestamp
        //
        //""")
        //       }

      } yield (removeAction)
      //     println(iItem)
      //     println("\n" + runSync(items))
      items

    }

    DBIO.sequence(removeBeforeActions)

  }

  /**
   * Get poll data for subscription without a callback.
   * This operation by definition returns the requested data that is accumulated since the start of
   * the subscription or since last poll request.
   * @param subId Subscription id
   * @param newTime Timestamp for the poll time, might be the new start time for the subscription
   * @return Some results or None if something important was not found
   */
  def getPollData(subId: Long, newTime: Timestamp): Option[OdfObjects] = {

    // lastValues: map from hierarchyId to its lastValue (for this subscription)
    def handleEventPoll(dbsub: DBSub, lastValues: Map[Int, Option[String]], sortedValues: Seq[DBValue]): Seq[DBValue] = {
      //GET all vals
      val timeframedVals = sortedValues filter betweenLogic(Some(dbsub.startTime), Some(newTime))

      val lastValO: Option[String] = for {
        head <- timeframedVals.headOption
        lastValRes <- lastValues.get(head.hierarchyId)
        lastVal <- lastValRes
      } yield lastVal

      val sameAsLast = lastValO match {
        case Some(lastVal) =>
          (testVal: DBValue) =>
            lastVal == testVal.value
            case None =>
          (_: DBValue) => false
      }

      val sorted = timeframedVals.sortBy(_.timestamp.getTime)

      val empty = Seq.empty[DBValue]

      // drops values from start that are same as before
      val dbvals = (sorted dropWhile sameAsLast).foldLeft(empty)(
        // Reduce subsequences with the same value to one element
        (accumulator, b) =>
          if (accumulator.lastOption.exists(_.value == b.value))
            accumulator
          else
            accumulator :+ b)

      dbvals
    }

    // option checking easier at top level because of the "return" 
    val sub = getSub(subId) match {
      case None =>
        return None
      case Some(s) => s
    }

    val items = for {
      subsItems <- subItems filter (_.subId === subId) result

      subData <- getSubItemDataI(subsItems)

      // sort time ascending; newest last
      dataSorted = subData mapValues (
        _.sortBy(_.timestamp.getTime))

      lastValues = subsItems groupBy (_.hierarchyId) map {
        case (hId, valueSeq) => (hId, valueSeq.headOption flatMap (_.lastValue))
      }

      // Get right data for each infoitem
      data = dataSorted mapValues { seqVals =>
        if (sub.isEventBased)
          handleEventPoll(sub, lastValues, seqVals)
        else // Normal interval poll
          getByIntervalBetween(seqVals, sub.startTime, newTime, sub.interval)
      }

      // Update SubItems lastValues
      lastValUpdates <- if (sub.isEventBased) {
        val actions = data map {
          case (node, vals) if vals.nonEmpty =>
            val lastVal = vals.lastOption.map { _.value }
            val subItemQ = subItems.filter { subItem =>
              subItem.hierarchyId === node.id &&
                subItem.subId === sub.id
            }
            subItemQ.update(
              DBSubscriptionItem(sub.id, node.id.get, lastVal))
          case _ => DBIO.successful(())
        }
        DBIO.sequence(actions)

      } else DBIO.successful(()) // noop

      // Update subscription, move begin time forward

      elapsedTime: Duration = (newTime.getTime - sub.startTime.getTime) milliseconds

      // Move begin and ttl to virtually delete the old data that is given now as a response
      (newBegin, newTTL) = if (sub.isEventBased) {
        (newTime, sub.ttl - elapsedTime)

      } else {
        // How many intervals are in the past?:
        val intervalsPast = (elapsedTime / sub.interval).toInt // floor

        (new Timestamp(
          sub.startTime.getTime + (intervalsPast * sub.interval).toMillis),
          sub.ttl - intervalsPast * sub.interval // Intervals between 
          )
      }

      updatedSub = sub.copy(startTime = newBegin, ttl = newTTL)

      subQ = subs.filter(_.id === sub.id)

      updateSub <- subQ update updatedSub

      removeOld <- DBIO.sequence(subData.keys.map(key => removeExcessI(key.id.get)))

      //      removeOld <- removeBefore(dataSorted, newTime)

      //      debug = {
      //        println(dataSorted)
      //        println(removeOld)
      //        println("AAAAAAAAAAAAAAAAAAAAAAAAAA")
      //      }

    } yield data
    odfConversion(
      runSync(items.transactionally))
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
        node => (node.id, node.pollRefCount === 0)).result

      updateResult <- nodeIdSeq.headOption match {
        case None =>
          // shouldn't happen
          throw new RuntimeException("Didn't get nodeIds from query when they were just checked/added")

        case Some((id, buffering)) => for {
          _ <- (latestValues += DBValue(id, timestamp, value, valueType))


          _ <- if (buffering)
              removeExcessI(id)
            else
              DBIO.successful(())
        } yield (path, id)
      }
    } yield updateResult

    val returnId = runSync(updateAction.transactionally)

    val infoitem = OdfInfoItem( path, Iterable( OdfValue(value, valueType, Some(timestamp) ) ) ) 

    //Call hooks
    database.getSetHooks foreach { _(Seq(infoitem)) }
    //    println(s"RUN with $path:  $run")
    returnId
  }

  /**
   * Used to set many values efficiently to the database.
   * @param data list item to be added consisting of Path and OdfValue tuples.
   */
  def setMany(data: List[(Path, OdfValue)]): Seq[(Path, Int)] = {

    val pathsData: Map[Path, Seq[OdfValue]] =
      data.groupBy(_._1).mapValues(
        v => v.map(_._2).sortBy(
          _.timestamp match {
            case Some(time) => time.getTime
            case None       => 0
          }))

    val writeAction = for {
      addObjectsAction <- DBIO.sequence(
        pathsData.keys map (addObjectsI(_, lastIsInfoItem = true)))

      idQry <- getHierarchyNodesQ(pathsData.keys.toSeq) map { hNode =>
        (hNode.path, (hNode.id, hNode.pollRefCount === 0))
      } result

      idMap = idQry.toMap: Map[Path, (Int, Boolean)]

      pathsToIds = pathsData map {
        case (path, odfValues) => (idMap(path)._1, odfValues)
      }

      dbValues = pathsToIds flatMap {
        case (id, odfValues) => odfValues map { odfVal =>
          DBValue(
            id,
            //create new timestamp if option is None
            odfVal.timestamp.getOrElse {
              new Timestamp(new java.util.Date().getTime)
            },
            odfVal.value,
            odfVal.typeValue)
        }
      }
      updateAction <- latestValues ++= dbValues

      remSeq = idMap.map{
        case (_,(nodeId, _ )) => nodeId
      }
      
////remove excess refactor
//      .filter {
//        case (_, (_, zeroPollRefs)) => zeroPollRefs
//      }.map {
//        case (_, (nodeId, _)) => nodeId
//      }

      removeAction <- DBIO.sequence(
        remSeq.map(removeExcessI(_)))
        
//        debug = {
//        println(s"""
//removeAction: $removeAction
//""")
//      }
    } yield (idMap map { case (path, (id, _)) => (path, id) }).toSeq

    val pathIdRelations = runSync(writeAction.transactionally)

    val infoitems = pathsData.collect{
      case (path: Path, values : Seq[OdfValue] ) if values.nonEmpty =>
        OdfInfoItem(
          path,
          values.map{ va => 
            OdfValue(
              va.value,
              va.typeValue,
              Some(va.timestamp.getOrElse{
                new Timestamp(new java.util.Date().getTime)
              })
            )
          }.toIterable
      )
    }
    //Call hooks
    database.getSetHooks foreach { _(infoitems.toSeq) }

    pathIdRelations
  }

  def setDescription(hasPath: OdfNode): Unit = {

    val path = hasPath.path
    val description = hasPath.description.getOrElse(
      throw new IllegalArgumentException("Tried to set unexisting description."))

    val updateAction = for {
      _ <- hasPath match {
        case objs: OdfObjects  => addObjectsI(path, lastIsInfoItem = false)
        case obj: OdfObject    => addObjectsI(path, lastIsInfoItem = false)
        case info: OdfInfoItem => addObjectsI(path, lastIsInfoItem = true)
        case matcherror        => throw new MatchError(matcherror)
      }

      nodeO <- getHierarchyNodeI(path)

      result <- nodeO match {
        case Some(hNode) =>
          val newNode = hNode.copy(description = description.value)
          hierarchyNodes.filter(_.id === hNode.id).update(newNode)
        case _ =>
          throw new RuntimeException("Tried to set description on unknown object.")
      }
    } yield result
    runSync(updateAction.transactionally)
  }
  /**
   * Used to store metadata for a sensor to database
   * @param path path to sensor
   * @param data metadata to be stored as string e.g a XML block as string
   *
   */
  def setMetaData(path: Path, data: String): Unit = {

    val updateAction = for {
      _ <- addObjectsI(path, lastIsInfoItem = true) // Only InfoItems can have MetaData in O-DF v1.0

      nodeO <- getHierarchyNodeI(path)

      result <- nodeO match {
        case Some(DBNode(Some(id), _, _, _, _, _, _, _)) =>
          setMetaDataI(id, data)
        case _ =>
          throw new RuntimeException("Tried to set metadata on unknown object.")
      }
    } yield result
    runSync(updateAction.transactionally)
  }

  def setMetaDataI(hierarchyId: Int, data: String): DBIOrw[Int] = {
    val qry = metadatas filter (_.hierarchyId === hierarchyId) map (_.metadata)
    val qryres = qry.result map (_.headOption)
    qryres flatMap {
      case None =>
        metadatas += DBMetaData(hierarchyId, data)
      case Some(_) =>
        qry.update(data)
    } transactionally
  }

  def RemoveMetaData(path: Path): Unit = {
    // TODO: Is this needed at all?
    val firstNode: Option[DBNode] = runSync(hierarchyNodes.filter(_.path === path).result.headOption)
    firstNode.foreach { node => 
      val qry = metadatas.filter(_.hierarchyId === node.id)
      runSync(qry.delete)
    }
  }

  /**
   * Remove is used to remove sensor given its path. Removes all unused objects from the hierarchcy along the path too.
   *
   *
   * @param path path to to-be-deleted sub tree.
   * @return boolean whether something was removed
   */
  @deprecated("For testing only.", "Since implemented.")
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
      subItems.filter { _.hierarchyId.inSet(removedIds) }.delete,
      metadatas.filter { _.hierarchyId.inSet(removedIds) }.delete,
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
  /*
  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private[this] def removeExcessI(pathId: Int): DBIO[Int] = {
    val pathQuery = 
      latestValues filter (_.hierarchyId === pathId) sortBy (_.timestamp.asc)

    val historyLen = database.historyLength

    val removeAction = for {
      queryLen <- pathQuery.length.result

      removeCount <-
        if(queryLen > historyLen) for {

          sortedVals <- pathQuery.result
          oldTime = sortedVals.drop(queryLen - historyLen).head.timestamp

          result <- latestValues.filter( value =>
            value.hierarchyId === pathId && value.timestamp < oldTime
          ).delete

          } yield result
        else DBIO.successful(0)

    } yield removeCount

    removeAction.transactionally
  }*/
  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param pathId pathId to the node that needs cleaning
   *
   */
  private[this] def removeExcessI(pathId: Int): DBIO[Int] = {
    val pathQuery =
      latestValues filter (_.hierarchyId === pathId) sortBy (_.timestamp.asc)

    val historyLen = database.historyLength

    val removeAction = for {
      queryLen <- pathQuery.length.result

      removeCount <- if (queryLen > historyLen) for {

        subscriptionitems <- subItems filter (subItem => subItem.hierarchyId === pathId) result

        earliest <- subs.filter { sub => sub.id.inSet(subscriptionitems.map(_.subId)) }.map(_.startTime).sorted.result

        time = earliest.headOption

        sortedVals <- pathQuery.result
        oldTime = sortedVals.drop(queryLen - historyLen).headOption.getOrElse(throw new UninitializedError).timestamp

        cutOffTime = time.fold(oldTime)(subtime => if (subtime.before(oldTime)) subtime else oldTime)

//        debug = {
//          println(s"""
//sortedvals: $sortedVals
//earliest: $earliest
//time: $time
//oldTime: $oldTime
//cutOffTime: $cutOffTime
//
//""")
//        }
        result <- 
          latestValues.filter(value =>
            value.hierarchyId === pathId && value.timestamp < cutOffTime).delete
        
//       debug = {
//          println(s"""
//result: $result
//""")
//}

      } yield result
      else DBIO.successful(0)

    } yield removeCount

    removeAction.transactionally
  }
  def removeN(idNTuples: Seq[(Int,Int)])={
    val removes = DBIO.sequence(
    idNTuples.map{
      case (id: Int, n: Int) =>
        latestValues.filter(_.hierarchyId === id ).sortBy(_.timestamp.asc).take(n).delete
    })
    runSync(removes.transactionally)
  }
  def removeBefore(idTimeTuples: Seq[(Int,Timestamp)] ) = {
    val removes = DBIO.sequence(
    idTimeTuples.map{
      case (id: Int, time: Timestamp) =>
      latestValues.filter(_.hierarchyId === id ).filter{value => value.timestamp < time}.delete
    })
    runSync(removes.transactionally)
  
  }

  //  private[this] def removeBefodre(paths: SortedMap[DBNode, Seq[DBValue]], timestamp: Timestamp) ={
  //
  //     val infoitems = paths.keySet.filter(_.isInfoItem).map(_.id.get).toSeq
  //     val historyLen = database.historyLength
  //
  //     val removeBeforeActions = infoitems.map { iItem => 
  //     val items = for {
  //       subscriptionitems <- subItems filter (subItem => subItem.hierarchyId === iItem) result
  //       
  //       earliest <- subs.filter { sub => sub.id.inSet(subscriptionitems.map(_.subId)) }.map(_.startTime).sorted.result
  //       
  //       time = earliest.headOption
  //       
  //       pathQuery = latestValues filter (_.hierarchyId === iItem) sortBy (_.timestamp.asc)
  //       
  //       qLen <- pathQuery.length.result
  //       
  //       removeAction <- if( qLen > historyLen) for {
  //         
  //         sortedVals <- pathQuery.result
  //         oldTime = sortedVals.drop(qLen - historyLen).head.timestamp
  //         
  //         cutOffTime = time.fold(oldTime)(subtime => if( subtime.before(oldTime) ) subtime else oldTime)
  //         
  //         result <- latestValues.filter(value => 
  //           value.hierarchyId === iItem && value.timestamp < cutOffTime).delete
  //           
  //         asd <- latestValues.filter(value => 
  //           value.hierarchyId === iItem).map(_.value).result
  //           
  //           debug={
  //            println("removedebug:vvvvvvvvvvvvvvv")
  //            asd.foreach(println(_))
  //         }
  //           
  //       } yield result
  //       else DBIO.successful((0))
  //       
  //       debug = {
  //         println(s"""
  //earliest: $earliest
  //time:     $time
  //pathQuery:$pathQuery
  //qLen:     $qLen
  //removeAction: $removeAction
  //timestamp:$timestamp
  //
  //""")
  //       }
  //       
  //     } yield (removeAction) 
  ////     println(iItem)
  ////     println("\n" + runSync(items))
  //     items
  //     
  //     }
  //
  //     DBIO.sequence(removeBeforeActions)
  //
  //  }
  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private[this] def removeExcess(pathId: Int) = {
    runSync(removeExcessI(pathId))
  }

  /**
   * Removes subscription information from database for given ID.
   * Removes also related subscription items.
   * @param id id number that was generated during saving
   *
   */
  def removeSub(id: Long): Boolean = {

    val result = for {
      subO <- getSubI(id)

      subQ = subs filter (_.id === id)
      subItemsQ = subItems filter (_.subId === id)

      isSuccess <- subO match {
        case None =>
          DBIO.successful(false)

        case Some(sub) => for {
          subItemNodeIds <- subItemsQ.map(_.hierarchyId).result

          subItemNodes <- hierarchyNodes filter (
            _.id inSet subItemNodeIds) result

          

          _ <- subItemsQ.delete

          _ <- subQ.delete
          
          _ <- if (!sub.hasCallback) {
//            DBIO.sequence(subItemNodeIds.map { x => removeExcessI(x) })
            val subItemRefCountUpdates = subItemNodes map {
              case node @ DBNode(Some(nodeId),_,_,_,_,_,_,_) =>
                val newRefCount = node.pollRefCount - 1 

                val updatedNode = node.copy( pollRefCount = newRefCount )
                getHierarchyNodeQ(nodeId) update updatedNode
                
                removeExcessI(nodeId)
                
//                if ( newRefCount == 0 ) {
//                  removeExcessI(nodeId)
//                }else {
//                  val updatedNode = node.copy( pollRefCount = newRefCount )
//
//                  getHierarchyNodeQ(nodeId) update updatedNode
//                }
            }
            DBIO.sequence(subItemRefCountUpdates)
          } else {
            DBIO.successful(Unit)
          }
          

        } yield true
      }
    } yield isSuccess

    runSync(result.transactionally)
  }

  def removeSub(sub: DBSub): Boolean = removeSub(sub.id)

  /**
   * Method to modify start time and ttl values of a subscription based on id
   *
   * @param id id number of the subscription to be modified
   * @param newTime time value to be set as start time
   * @param newTTL new TTL value to be set
   */
  def setSubStartTime(id: Long, newTime: Timestamp, newTTL: Double) = {
    runWait(subs.filter(_.id === id).map(p => (p.startTime, p.ttl)).update((newTime, newTTL)))
  }

  /**
   * Saves subscription information to database
   * adds timestamp at current time to keep track of expiring
   * adds unique id number to differentiate between elements and
   * to provide easy query parameter
   *
   * @param sub DBSub object to be stored
   *
   * @return id number that is used for querying the elements
   */
  def saveSub(sub: NewDBSub, dbItems: Seq[Path]): DBSub = {
    val subInsertI = for {
      subId <- subsWithInsertId += sub
      subItemNodes <- getHierarchyNodesI(dbItems)

      _ = require(subItemNodes.length >= dbItems.length,
        s"Invalid path, no such item found, requested $dbItems, found ${subItemNodes.map(_.path)}")

      newSubItems = subItemNodes map
        (node => DBSubscriptionItem(subId, node.id.get, None))

      _ <- subItems ++= newSubItems

      _ <- if (!sub.hasCallback) {
        DBIO.sequence(
          subItemNodes map { node =>
            val updatedNode = node.copy(
              pollRefCount = node.pollRefCount + 1)
            hierarchyNodes filter (_.id === node.id) update updatedNode
          })
      } else {
        DBIO.successful(Seq())
      }
    } yield subId

    val subIdResult = runSync(subInsertI.transactionally)

    DBSub(
      subIdResult,
      sub.interval,
      sub.startTime,
      sub.ttl,
      sub.callback)
  }
}
