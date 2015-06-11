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
  type ReadWrite = Effect with Effect.Write with Effect.Read

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

    runSync(existingTables).headOption match {
      case Some(table) =>
        //noop
        println("Not creating tables, found table: " + table.name.name)
      case None =>
        // run transactionally so there are all or no tables
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
  protected def addObjectsI(path: Path, lastIsInfoItem: Boolean): DBIO[Unit] = {

    /** Query: Increase right and left values after value */
    def increaseAfterQ(value: Int) = {

      // NOTE: Slick 3.0.0 doesn't allow this query with its types, use sql instead
      //val rightValsQ = hierarchyNodes map (_.rightBoundary) filter (_ > value) 
      //val leftValsQ  = hierarchyNodes map (_.leftBoundary) filter (_ > value)
      //val rightUpdateQ = rightValsQ.map(_ + 2).update(rightValsQ)
      //val leftUpdateQ  =  leftValsQ.map(_ + 2).update(leftValsQ)

      DBIO.seq(
        sqlu"UPDATE HierarchyNodes SET rightBoundary = rightBoundary + 2 WHERE rightBoundary > ${value}",
        sqlu"UPDATE HierarchyNodes SET leftBoundary = leftBoundary + 2 WHERE leftBoundary > ${value}"
      )
    }


    def addNode(isInfoItem: Boolean)(fullpath: Path): DBIOAction[Unit, NoStream, ReadWrite] = {

      findParent(fullpath) flatMap { parentO =>
        val parent = parentO getOrElse {
          throw new RuntimeException(s"Didn't find root parent when creating objects, for path: $fullpath")
        }

        val insertRight = parent.rightBoundary
        val left        = insertRight + 1
        val right       = left + 1

        DBIO.seq(
          increaseAfterQ(insertRight),
          hierarchyNodes += DBNode(None, fullpath, left, right, fullpath.length, "", 0, isInfoItem)
        )
      }
    }

    val parentsAndPath = path.getParentsAndSelf

    val foundPathsQ   = hierarchyNodes filter (_.path inSet parentsAndPath) map (_.path) result
    // difference between all and found
    val missingPathsQ: DBIOAction[Seq[Path],NoStream,Effect.Read]  = foundPathsQ map (parentsAndPath diff _)

    // Combine DBIOActions as a single action
    val addingAction = missingPathsQ flatMap {(missingPaths: Seq[Path]) =>
      DBIO.seq(
        (missingPaths.init map addNode(false)) :+
        (missingPaths.lastOption map addNode(lastIsInfoItem) getOrElse (DBIO.successful(Unit))) : _*
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
  def set(path: Path, timestamp: Timestamp, value: String, valueType: String = ""): Unit = {
    val idQry = hierarchyNodes filter (_.path === path) map (n => (n.id, n.pollRefCount =!= 0)) result
    val count = getWithHierarchyQ[DBValue, DBValuesTable](path, latestValues).length.result
    val updateAction = idQry.headOption flatMap { qResult =>
      
      //Call hooks
      database.getSetHooks foreach { _(Seq(path))}
      
      qResult match{
        
        case None =>{
          addObjectsI(path, true)

          }
        
        case Some(existingPath) => {
          count.flatMap { valCount => {
            
          val addAction = (latestValues += DBValue(existingPath._1,timestamp,value,valueType))
          
          addAction.flatMap { x => {
            if(valCount > database.historyLength && !existingPath._2)
              removeExcessI(path)
            else
              DBIO.successful(0)
            
          }
          }
        }
        }
        }
      }

    }
    runSync(updateAction)
  }


  /**
   * Used to store metadata for a sensor to database
   * @param path path to sensor
   * @param data metadata to be stored as string e.g a XML block as string
   * 
   */
  def setMetaData(path: Path, data: String): Unit = {
    val idQry = hierarchyNodes filter (_.path === path) map (_.id) result

    val updateAction = idQry flatMap {
      _.headOption match {
        case Some(id) => 
          setMetaDataI(id, data)
        case None =>
          throw new RuntimeException("Tried to set metadata on unknown object.")
      }
    }
    runSync(updateAction)
  }

  def setMetaDataI(hierarchyId: Int, data: String): DBIOAction[Int, NoStream, Effect.Write with Effect.Read with Effect.Transactional] = {
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
   * Used to set many values efficiently to the database.
   * @param data list of tuples consisting of path and TimedValue.
   */
  /*
   * case class DBValue(
    hierarchyId: Int,
    timestamp: Timestamp,
    value: String,
    valueType: String
  )
   */
  
  def setMany(data: List[(Path, OdfValue)]): Boolean = {
    val idQry = data.map(n=> hierarchyNodes.filter(_.path === n._1).map(m => (m.id, m.pollRefCount =!= 0)).result.headOption)
    
//    val test = idQry.foldLeft(DBIO.successful(None))((b,n) =>b.andThen(n) )//DBIO.seq(idQry.head, idQry.last)//dbioSum[Option[(Int,Boolean)]](idQry)
    ???
//    data.map{case(path:Path, odfVal:OdfValue) => {
//      database.getSetHooks foreach {_(Seq(path))}
//      DBValue()
//    }}
////    data.foreach{
////      case(path:Path, v: OdfValue) =>{
////        //Call hooks
////      database.getSetHooks foreach (_(Seq(path)))
////      lazy val newTimestamp = new Timestamp(new java.util.Date().getTime)
////      
////      }
//        
//      //
//    }
//    ???
  } /*{
    var add = Seq[(Path,String,Timestamp)]()  // accumulator: dbobjects to add

    // Reformat data and add missing timestamps
    data.foreach {
      case (path: Path, v: OdfValue) =>

         // Call hooks
        val argument = Seq(path)
        getSetHooks foreach { _(argument) }

        lazy val newTimestamp = new Timestamp(new java.util.Date().getTime)
        add = add :+ (path, v.value, v.timestamp.getOrElse(newTimestamp))
    }

    // Add to latest values in a transaction
    runSync((latestValues ++= add).transactionally)

    // Add missing hierarchy and remove excess buffering
    var onlyPaths = data.map(_._1).distinct
    onlyPaths foreach{p =>
        val path = Path(p)

        var pathQuery = objects.filter(_.path === path)
        val len = runSync(pathQuery.result).length
        if (len == 0) {
          addObjects(path)
        }

        var buffering = runSync(buffered.filter(_.path === path).result).length > 0
        if (!buffering) {
          removeExcess(path)
        }
    }
  }*/


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
  private def removeExcessI(path: Path) = {
    val pathQuery = getWithHierarchyQ[DBValue, DBValuesTable](path, latestValues)
    val historyLen = database.historyLength
    val qLenI = pathQuery.length.result
    
    qLenI.flatMap { qLen => 
    if(qLen>historyLen){
      pathQuery.sortBy(_.timestamp).take(qLen-historyLen).delete
    } else{
      DBIO.successful(0)
    }
    }

  }
  /**
   * Used to clear excess data from database for given path
   * for example after stopping buffering we want to revert to using
   * historyLength
   * @param path path to sensor as Path object
   *
   */
  private def removeExcessQ(path: Path) = {
    val pathQuery = getWithHierarchyQ[DBValue, DBValuesTable](path, latestValues)
    val historyLen = database.historyLength
    val qLenI = pathQuery.length.result
    
    val removeAction = qLenI.flatMap { qLen => 
    if(qLen>historyLen){
      pathQuery.sortBy(_.timestamp).take(qLen-historyLen).delete
    } else{
      DBIO.successful(0)
    }
    }
    runSync(removeAction)

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
  
  /*{
      var pathQuery = latestValues.filter(_.path === path)

      pathQuery.sortBy(_.timestamp).result flatMap { qry =>
        var count = qry.length

        if (count > historyLength) {
          val oldtime = qry.drop(count - historyLength).head._3
          pathQuery.filter(_.timestamp < oldtime).delete
        } else
          DBIO.successful(())
      }
    }*/


  /**
   * put the path to buffering table if it is not there yet, otherwise
   * increases the count on that item, to prevent removing buffered data
   * if one subscription ends and other is still buffering.
   *
   * @param path path as Path object
   * 
   */
  // TODO: Is this needed at all?
  //protected def startBuffering(path: Path): Unit = ???
  /*{
    val pathQuery = buffered.filter(_.path === path)

    pathQuery.result flatMap { 
      case Seq() =>
        buffered += ((path, 1))
      case Seq(existingEntry) =>
        pathQuery.map(_.count) update (existingEntry.count + 1)
    }
  }*/


  /**
   * removes the path from buffering table or dimishes the count by one
   * also clear all buffered data if count is only 1
   * leaves only historyLength amount of data if count is only 1
   * 
   * @param path path as Path object
   */
  // TODO: Is this needed at all?
  //protected def stopBuffering(path: Path): Boolean = ??? 
  /*{
    val pathQuery = buffered.filter(_.path === path)

    pathQuery.result flatMap { existingEntry =>
      if (existingEntry.count > 1)
        pathQuery.map(_.count) update (existingEntry.count - 1)
      else
        pathQuery.delete
    }
      val pathQuery = buffered.filter(_.path === path)
      val str = runSync(pathQuery.result)
      var len = str.length
      if (len > 0) {
        if (str.head.count > 1) {
          runSync(pathQuery.map(_.count).update(len - 1))
          false
        } else {
          runSync(pathQuery.delete)
          removeExcess(path)
          true
        }
      } else {
        false
      }
  }*/





    


  /**
   * Check whether subscription with given ID has expired. i.e if subscription has been in database for
   * longer than its ttl value in seconds.
   *
   * @param id number that was generated during saving
   *
   * @return returns boolean whether subscription with given id has expired
   */
  // TODO: Is this needed at all?
  // def isExpired(id: Int): Boolean = ???
  /*
    {
      //gets time when subscibe was added,
      // adds ttl amount of seconds to it,
      //and compares to current time
        val sub = runSync(subs.filter(_.ID === id).result).headOption
        if(sub != None)
        {
        if (sub.get._4 > 0) {
          val endtime = new Timestamp(sub.get._3.getTime + (sub.get._4 * 1000).toLong)
          new java.sql.Timestamp(new java.util.Date().getTime).after(endtime)
        } else {
          true
        }
        }
        else
        {
          true
        }
    }*/



  /**
   * Removes subscription information from database for given ID.
   * Removes also related subscription items.
   * @param id id number that was generated during saving
   *
   */
  def removeSub(id: Int): Boolean ={
    val hIds = subItems.filter( _.hierarchyId === id )
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
                    removeExcessQ(node.path)
                  
                hierarchyNodes.update( 
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
  /*{
    
      var qry = subs.filter(_.ID === id)
      var toBeDeleted = runSync(qry.result)
      if (toBeDeleted.length > 0) {
        if (toBeDeleted.head._6 == None) {
          toBeDeleted.head._2.split(";").foreach { p =>
            stopBuffering(Path(p))
          }
        }
        db.run(qry.delete)
        return true
      } else {
        return false
      }
    
    false
  }*/
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

  private def dbioDBInfoItemsSum: Seq[DBIO[SortedMap[DBNode,Seq[DBValue]]]] => DBIO[SortedMap[DBNode,Seq[DBValue]]] = {
    seqIO =>
      def iosumlist(a: DBIO[SortedMap[DBNode,Seq[DBValue]]], b: DBIO[SortedMap[DBNode,Seq[DBValue]]]): DBIO[SortedMap[DBNode,Seq[DBValue]]] = for {
        listA <- a
        listB <- b
      } yield (listA++listB)
      seqIO.foldRight(DBIO.successful(SortedMap.empty[DBNode,Seq[DBValue]]):DBIO[SortedMap[DBNode,Seq[DBValue]]])(iosumlist _)
  }

  private def dbioSeqSum[A]: Seq[DBIO[Seq[A]]] => DBIO[Seq[A]] = {
    seqIO =>
      def iosumlist(a: DBIO[Seq[A]], b: DBIO[Seq[A]]): DBIO[Seq[A]] = for {
        listA <- a
        listB <- b
      } yield (listA++listB)
      seqIO.foldRight(DBIO.successful(Seq.empty[A]):DBIO[Seq[A]])(iosumlist _)
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
  def saveSub(sub: NewDBSub, dbItems: Seq[Path]): DBSub ={
    val subInsert: DBIOAction[Int, NoStream, Effect.Write with Effect.Read with Effect.Transactional] = (subs += sub)
    //XXX: runSync
    val id = runSync(subInsert)
    val hNodesI = getHierarchyNodesI(dbItems) 
    val itemsI = hNodesI.flatMap{
      hNodes =>{  
        dbioDBInfoItemsSum(
          hNodes.map{
             hNode =>{
              getSubTreeI( hNode.path ).map{
                toDBInfoItems( _ ).filter{ 
                  case (node, seqVals) => seqVals.nonEmpty 
                }.map{ 
                  case (node, seqVals) =>  (node, seqVals.sortBy( _.timestamp.getTime ).take(1) ) 
                } 
              }
            }
          }
        )
      }
    }
    val itemInsert = itemsI.flatMap{ infos => 
      val sItems = infos.map { case (hNode, values ) =>
          DBSubscriptionItem( id, hNode.id.get, values.headOption.map{ case value: DBValue => value.value } ) 
      }
      subItems ++= sItems 
    }
    //XXX: runSync
    runSync(itemInsert)
    if(!sub.hasCallback){
      //XXX: runSync
      runSync(
        hierarchyNodes.filter( 
          node => node.path.inSet( dbItems ) 
        ).result.flatMap{
          nodeSe => 
          DBIO.seq(
            nodeSe.map{
              node => 
              hierarchyNodes.update( 
                DBNode(
                  node.id,
                  node.path,
                  node.leftBoundary,
                  node.rightBoundary,
                  node.depth,
                  node.description,
                  node.pollRefCount + 1,
                  node.isInfoItem
                )
              )
            }:_*
          )
        }
      )
    }
    DBSub(
      id,
      sub.interval,
      sub.startTime,
      sub.ttl,
      sub.callback
    )
  }


}
