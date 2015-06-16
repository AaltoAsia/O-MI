package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.SortedMap

import parsing.Types._
import parsing.Types.OdfTypes._




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
   *  Does not remove excess rows if path is set ot buffer
   *
   *  @param data sensordata, of type DBSensor to be stored to database.
   */
  def set(path: Path, timestamp: Timestamp, value: String, valueType: String = ""): Int = {
    
    val addObjectsAction = addObjectsI(path, true)
    
    val idQry = hierarchyNodes filter (_.path === path) map (n => (n.id, n.pollRefCount === 0)) result
    
    val updateAction = addObjectsAction.flatMap {  x=>
      
      idQry.headOption flatMap { qResult =>

      qResult match{
        case None =>{
          DBIO.successful(0)
          }
        
        case Some((id, buffering)) => {
          val addAction = (latestValues += DBValue(id,timestamp,value,valueType))
          
          addAction.flatMap { x => {
            if(buffering)
              removeExcessI(id)
            else
              DBIO.successful(1)
            
          }
          }
        }
      }
    }
  }
    val run = runSync(updateAction.transactionally)
    //Call hooks
    database.getSetHooks foreach { _(Seq(path))}
    println(s"RUN with $path:  $run")
    run
  }

  /**
   * Used to set many values efficiently to the database.
   * @param data list item to be added consisting of Path and OdfValue tuples.
   */
   def setMany(data: List[(Path, OdfValue)]): Unit = {
     
    val pathsData = data.groupBy(_._1).mapValues(v =>v.map(_._2))

    val addObjectsAction = DBIO.sequence(pathsData.keys.map(addObjectsI(_,true)))
    
    val idQry = getHierarchyNodesQ(pathsData.keys.toSeq).map { hNode =>
      (hNode.path, (hNode.id, hNode.pollRefCount === 0)) 
      } result
    
    val updateAction = addObjectsAction flatMap {unit =>
      idQry flatMap { qResult => 
        
        val idMap = qResult.toMap
        val pathsToIds = pathsData.map(n => (idMap(n._1)._1,n._2))
        val dbValues = pathsToIds.flatMap{
          
          case (key,value) =>value.map(odfVal =>{
            DBValue(
                key, 
                //create new timestamp if option is None
                odfVal.timestamp.fold(new Timestamp(new java.util.Date().getTime))( ts => ts), 
                odfVal.value, 
                odfVal.typeValue
                )  
          })
        }
        val addDataAction = latestValues ++= dbValues
        addDataAction.flatMap { x => 
          val remSeq = idMap.filter(n=> n._2._2).map(n=> n._2._1)
          DBIO.sequence(remSeq.map(removeExcessI(_)))
        }
      }
    }

    runSync(updateAction.transactionally)
    //Call hooks
    database.getSetHooks foreach {_(pathsData.keys.toSeq)}

  } 


  /**
   * Used to store metadata for a sensor to database
   * @param path path to sensor
   * @param data metadata to be stored as string e.g a XML block as string
   * 
   */
  def setMetaData(path: Path, data: String): Unit = {

    val updateAction = for {
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
   * @param path path to to-be-deleted sensor. If path doesn't end in sensor, does nothing.
   * @return boolean whether something was removed
   */
  // TODO: Is this needed at all?
  def remove(path: Path): Boolean = ??? /*{
    //search database for given path
    val pathQuery = latestValues.filter(_.path === path)
    var deleted = false
    //if found rows with given path remove else path doesn't exist and can't be removed
    if (runSync(pathQuery.result).length > 0) {
      runSync(pathQuery.delete)
      deleted = true;
    }
    if (deleted) {
      //also delete objects from hierarchy that are not used anymore.
      // start from sensors path and proceed upward in hierarchy until object that is shared by other sensor is found,
      //ultimately the root. path/to/sensor/temp -> path/to/sensor -> ..... -> "" (root)
      var testPath = path
      while (!testPath.isEmpty) {
        if (getChilds(testPath).length == 0) {
          //only leaf nodes have 0 childs. 
          var pathQueryObjects = objects.filter(_.path === testPath)
          runSync(pathQueryObjects.delete)
          testPath = testPath.dropRight(1)
        } else {
          //if object still has childs after we deleted one it is shared by other sensor, stop removing objects
          //exit while loop
          testPath = Path("")
        }
      }
    }
    return deleted
  }*/


  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private def removeExcessI(pathId: Int) = {
    val pathQuery = latestValues.filter(_.hierarchyId === pathId).sortBy(_.timestamp.asc)//getWithHierarchyQ[DBValue, DBValuesTable](path, latestValues)
    val historyLen = database.historyLength
    val qry = pathQuery.result
    val qLenI = pathQuery.length.result
    qLenI.flatMap { qLen => 
    if(qLen>historyLen){
      qry.flatMap { sortedVals =>
        val oldTime = sortedVals.drop(qLen - historyLen).head.timestamp
        latestValues.filter(_.timestamp < oldTime).delete
        }
    } else 
        DBIO.successful(0)
    }
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
    val hIds = subItems.filter( _.subId === id )
    val sub = subs.filter( _.id === id ) 
    //XXX: Is return value needed?
    if(runSync(sub.result).length == 0){
      false
    } else {
      val updates = hierarchyNodes.filter(
        node => 
        //XXX:
        node.id.inSet( runSync( hIds.map( _.hierarchyId ).result) )
      ).result.flatMap{
        nodeSe => 
          DBIO.seq(
            nodeSe.map{
              node => 
                val refCount = node.pollRefCount - 1 
                //XXX: heavy operation, but future doesn't help, new sub can be created middle of it
                if( refCount == 0) 
                    removeExcess(node.id.get)
                  
                hierarchyNodes.filter(_.id === node.id).update( 
                  DBNode(
                    node.id,
                    node.path,
                    node.leftBoundary,
                    node.rightBoundary,
                    node.depth,
                    node.description,
                    refCount,
                    node.isInfoItem
                  )
                )
            }:_*
          ) 
      }
      runSync(DBIO.seq(updates,hIds.delete,sub.delete))
      true 
    }
  }
  def removeSub(sub: DBSub): Boolean = removeSub(sub.id)




  /**
   * Method to modify start time and ttl values of a subscription based on id
   * 
   * @param id id number of the subscription to be modified
   * @param newTime time value to be set as start time
   * @param newTTL new TTL value to be set
   */
  def setSubStartTime(id:Int,newTime:Timestamp,newTTL:Double) ={
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

      newSubItems: DBInfoItems <- getInfoItemsI(subItemNodes)

      insertItems = newSubItems.map {
        case (hNode, values ) =>
          DBSubscriptionItem( subId, hNode.id.get, values.headOption.map{ case value: DBValue => value.value } ) 
        }
      _ <- subItems ++= insertItems 

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
