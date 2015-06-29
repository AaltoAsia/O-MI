package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.iterableAsScalaIterable
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
      hierarchyNodes += DBNode(None, Path("/Objects"), 1, 2, Path("/Objects").length, "", 0, false)
    )    

    val existingTables = MTable.getTables
    val existed = runSync(existingTables)
    if( existed.length > 0 ){
        //noop
        println(
          "Found tables: " +
          existed.map{_.name.name}.mkString(", ") +
          "\n Not creating new tables."
        )
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
   */
  protected def addObjectsI(path: Path, lastIsInfoItem: Boolean): DBIOrw[Seq[Unit]] = {

    /** Query: Increase right and left values after value */
    def increaseAfterQ(value: Int) = {

      // NOTE: Slick 3.0.0 doesn't allow this query with its types, use sql instead
      //val rightValsQ = hierarchyNodes map (_.rightBoundary) filter (_ > value) 
      //val leftValsQ  = hierarchyNodes map (_.leftBoundary) filter (_ > value)
      //val rightUpdateQ = rightValsQ.map(_ + 2).update(rightValsQ)
      //val leftUpdateQ  =  leftValsQ.map(_ + 2).update(leftValsQ)

      DBIO.seq(
        sqlu"UPDATE HIERARCHYNODES SET RIGHTBOUNDARY = RIGHTBOUNDARY + 2 WHERE RIGHTBOUNDARY >= ${value}",
        sqlu"UPDATE HIERARCHYNODES SET LEFTBOUNDARY = LEFTBOUNDARY + 2 WHERE LEFTBOUNDARY > ${value}"
      )
    }


    def addNode(isInfoItem: Boolean)(fullpath: Path): DBIOrw[Unit] = {

      findParentI(fullpath) flatMap { parentO =>
        val parent = parentO getOrElse {
          throw new RuntimeException(s"Didn't find root parent when creating objects, for path: $fullpath")
        }

        val parentRight = parent.rightBoundary
        val left        = parentRight
        val right       = left + 1

        DBIO.seq(
          increaseAfterQ(parentRight),
          hierarchyNodes += DBNode(None, fullpath, left, right, fullpath.length, "", 0, isInfoItem)
        ).transactionally
      }
    }

    val parentsAndPath = path.getParentsAndSelf

    val foundPathsI   = hierarchyNodes filter (_.path inSet parentsAndPath) map (_.path) result
    // difference between all and found
    val missingPathsI: DBIOro[Seq[Path]]  = foundPathsI map (parentsAndPath diff _)

    // Combine DBIOActions as a single action
    val addingAction = missingPathsI flatMap {(missingPaths: Seq[Path]) =>

      // these will not break when empty
      val init = missingPaths.dropRight(1)
      val last = missingPaths.takeRight(1)

      DBIO.sequence(
        (init map addNode(false) ) ++
        (last map addNode(lastIsInfoItem))
      )
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
   */
  def set(path: Path, timestamp: Timestamp, value: String, valueType: String = ""): Int = {
    val updateAction = for {

      _ <- addObjectsI(path, true)
    
      nodeIdSeq <- getHierarchyNodeQ(path).map (
        node => (node.id, node.pollRefCount === 0)
      ).result
    
      updateResult <- nodeIdSeq.headOption match {
        case None =>
          DBIO.successful(0)
        
        case Some((id, buffering)) => for {
          _ <- (latestValues += DBValue(id,timestamp,value,valueType))

          result <- if(buffering)
                 removeExcessI(id)
               else
                 DBIO.successful(1)
        } yield result
      }
    } yield updateResult

    val run = runSync(updateAction.transactionally)

    //Call hooks
    database.getSetHooks foreach { _(Seq(path))}
//    println(s"RUN with $path:  $run")
    run
  }

  /**
   * Used to set many values efficiently to the database.
   * @param data list item to be added consisting of Path and OdfValue tuples.
   */
   def setMany(data: List[(Path, OdfValue)]): Unit = {
     
    val pathsData: Map[Path, Seq[OdfValue]] =
      data.groupBy(_._1).mapValues(v => v.map(_._2))

    val writeAction = for {
      addObjectsAction <- DBIO.sequence(
        pathsData.keys map ( addObjectsI(_, lastIsInfoItem=true) )
      )
      
      idQry <- getHierarchyNodesQ(pathsData.keys.toSeq) map { hNode =>
        (hNode.path, (hNode.id, hNode.pollRefCount === 0)) 
      } result
      
      idMap = idQry.toMap : Map[Path, (Int, Boolean)]

      pathsToIds = pathsData map {
        case (path, odfValues) => (idMap(path)._1, odfValues)
      }

      dbValues = pathsToIds flatMap {
        case (id, odfValues) => odfValues map { odfVal =>
          DBValue(
              id, 
              //create new timestamp if option is None
              odfVal.timestamp.getOrElse{
                new Timestamp(new java.util.Date().getTime)
              }, 
              odfVal.value, 
              odfVal.typeValue
          )  
        }
      }
      updateAction <- latestValues ++= dbValues

      remSeq = idMap.filter{
        case (_, (_, zeroPollRefs)) => zeroPollRefs
      }.map{
        case (_, (nodeId, _)) => nodeId
      }

      removeAction <- DBIO.sequence(
        remSeq.map(removeExcessI(_))
      )
    } yield ()

    runSync(writeAction.transactionally)

    //Call hooks
    database.getSetHooks foreach {_(pathsData.keys.toSeq)}
  } 

  def setDescription(hasPath: HasPath) : Unit  = {

    val path = hasPath.path
    val description = if(hasPath.description.nonEmpty) 
      hasPath.description.get.value
    else 
      throw new RuntimeException("Tried to set unexisting description.")

    val updateAction = for {
      _ <- hasPath match {
        case objs : OdfObjects => addObjectsI(path, lastIsInfoItem=false) 
        case obj : OdfObject => addObjectsI(path,  lastIsInfoItem=false) 
        case info : OdfInfoItem => addObjectsI(path,  lastIsInfoItem=true) 
      }

      nodeO <- getHierarchyNodeI(path)
      
      result <- nodeO match {
        case Some(hNode) => 
          hierarchyNodes.filter( _.id === hNode.id).update(DBNode(hNode.id,hNode.path, hNode.leftBoundary, hNode.rightBoundary, hNode.depth, description, hNode.pollRefCount, hNode.isInfoItem))
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
      _ <- addObjectsI(path, lastIsInfoItem=true)  // Only InfoItems can have MetaData in O-DF v1.0

      nodeO <- getHierarchyNodeI(path)
      
      result <- nodeO match {
        case Some(DBNode(Some(id),_,_,_,_,_,_,_)) => 
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
    qryres flatMap[Int, NoStream, Effect.Write] {
      case None => 
        metadatas += DBMetaData(hierarchyId, data)
      case Some(_) =>
        qry.update(data)
    } transactionally
  }


  def RemoveMetaData(path:Path): Unit={
  // TODO: Is this needed at all?
    val node = runSync( hierarchyNodes.filter( _.path === path ).result.headOption )
    if( node.nonEmpty ){
      val qry = metadatas.filter( _.hierarchyId === node.get.id )
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
    val hNode = runSync( hierarchyNodes.filter( _.path === path).result ).headOption
    if(hNode.isEmpty) return false //require( hNode.nonEmpty, s"No such item found. Cannot remove. path: $path")  
    
    val removedLeft = hNode.get.leftBoundary
    val removedRight = hNode.get.rightBoundary
    val subTreeQ = getSubTreeQ(hNode.get)
    val subTree = runSync( subTreeQ.result )
    val removedIds =  subTree.map{ _._1.id.get } 
    val removeActions = DBIO.seq(
      latestValues.filter{ _.hierarchyId.inSet( removedIds ) }.delete,
      subItems.filter{ _.hierarchyId.inSet( removedIds  ) }.delete,
      metadatas.filter{ _.hierarchyId.inSet( removedIds ) }.delete,
      hierarchyNodes.filter{ _.id.inSet( removedIds ) }.delete
    )
    val removedDistance = removedRight - removedLeft + 1 // one added to fix distance to rigth most boundary before removed left ( 14-11=3, 15-3=12 is not same as removed 11 ) 
    val updateActions = DBIO.seq(
        sqlu"""UPDATE HIERARCHYNODES SET RIGHTBOUNDARY =  RIGHTBOUNDARY - ${ removedDistance }
        WHERE RIGHTBOUNDARY > ${removedLeft}""",
        sqlu"""UPDATE HIERARCHYNODES SET LEFTBOUNDARY = LEFTBOUNDARY - ${ removedDistance } 
        WHERE LEFTBOUNDARY > ${removedLeft}"""
    )
    runSync( DBIO.seq(removeActions, updateActions) )
    true
  } 

  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private def removeExcessI(pathId: Int): DBIO[Int] = {
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
  }
  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private def removeExcess(pathId: Int) = {
    runSync(removeExcessI(pathId))
  }


  /**
   * Used to remove data before given timestamp
   * @param path path to sensor as Path object
   * @param timestamp that tells how old data will be removed, exclusive.
   */
   private def removeBefore(path:Path, timestamp: Timestamp) ={
    //check for other subs?, check historylen?
    val pathQuery = getWithHierarchyQ[DBValue,DBValuesTable](path,latestValues)
//    val historyLen = database.historyLength
    pathQuery.filter(_.timestamp < timestamp).delete
  }

  




  /**
   * Removes subscription information from database for given ID.
   * Removes also related subscription items.
   * @param id id number that was generated during saving
   *
   */
  def removeSub(id: Int): Boolean ={
    val result = for {
      subO <- getSubI(id)

      subQ      = subs filter (_.id === id)
      subItemsQ = subItems filter( _.subId === id )

      isSuccess <- subO match {
        case None =>
          DBIO.successful(false)

        case Some(sub) => for {
          subItemNodeIds <- subItemsQ.map( _.hierarchyId ).result

          subItemNodes <- hierarchyNodes filter (
            _.id inSet subItemNodeIds
          ) result

          _ <- if (!sub.hasCallback) { 
            val subItemRefCountUpdates = subItemNodes map {
              case node @ DBNode(Some(nodeId),_,_,_,_,_,_,_) =>
                val newRefCount = node.pollRefCount - 1 

                if ( newRefCount == 0 ) 
                    removeExcessI(nodeId)
                else {
                  val updatedNode = node.copy( pollRefCount = newRefCount )

                  getHierarchyNodeQ(nodeId) update updatedNode
                }
            }
            DBIO.sequence(subItemRefCountUpdates)
          } else {
            DBIO.successful(Unit)
          }

          _ <- subItemsQ.delete

          _ <- subQ.delete

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
  def setSubStartTime(id: Int, newTime: Timestamp, newTTL: Double) ={
    runWait(subs.filter(_.id === id).map(p => (p.startTime,p.ttl)).update((newTime,newTTL)))
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

      subInfoItems: DBInfoItems <- getInfoItemsI(subItemNodes)


      _ = require( subItemNodes.length >= dbItems.length, "Invalid path, no such item found")

      newSubItems = subInfoItems map {
        case (hNode, values) =>
          val lastValue: Option[String] = for {
            dbValue <- values.headOption
            if (sub.isEventBased)
          } yield dbValue.value

          DBSubscriptionItem( subId, hNode.id.get, lastValue) 
        }
      _ <- subItems ++= newSubItems 

      _ <- if (!sub.hasCallback) {
        DBIO.sequence(
          subItemNodes map { node => 
            val updatedNode = node.copy(
              pollRefCount = node.pollRefCount + 1
            )
            hierarchyNodes filter (_.id === node.id) update updatedNode
          }
        )
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
      sub.callback
    )
  }
}
