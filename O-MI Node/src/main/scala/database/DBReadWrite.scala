package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.iterableAsScalaIterable

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

    val setup = allSchemas.create    

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
  protected def addObjects(path: Path) {

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


    def addNode(fullpath: Path): DBIOAction[Unit, NoStream, ReadWrite] =
        for {
          parent <- findParent(fullpath)

          insertRight = parent.rightBoundary
          left        = insertRight + 1
          right       = left + 1

          _ <- increaseAfterQ(insertRight)

          _ <- hierarchyNodes += DBNode(None, fullpath, left, right, fullpath.length, "", 0)

        } yield ()

    val parentsAndPath = path.getParentsAndSelf

    val foundPathsQ   = hierarchyNodes filter (_.path inSet parentsAndPath) map (_.path) result
    // difference between all and found
    val missingPathsQ: DBIOAction[Seq[Path],NoStream,Effect.Read]  = foundPathsQ map (parentsAndPath diff _)

    // Combine DBIOActions as a single action
    val addingAction = missingPathsQ flatMap {(missingPaths: Seq[Path]) =>
      DBIO.seq(
        missingPaths map addNode : _*
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
   *  @return boolean whether added data was new
   */
  def set(path: Path, timestam: Timestamp, value: String, valueType: String = ""): Boolean = ???
  /*{
      //search database for sensor's path
      val pathQuery = latestValues.filter(_.path === data.path)
      val buffering = runSync(buffered.filter(_.path === data.path).result).length > 0

      //appends a row to the latestvalues table
      val count = runSync(pathQuery.result).length
      runSync(DBIO.seq(latestValues += (data.path, data.value, data.time)))
      // Call hooks
      val argument = Seq(data.path)
      getSetHooks foreach { _(argument) }

      if (count > historyLength && !buffering) {
        //if table has more than historyLength and not buffering, remove excess data
        removeExcess(data.path)
        false
      } else if (count == 0) {
        //add missing objects for the hierarchy since this is a new path
        addObjects(data.path)
        true
      } else {
        //existing path and less than history length of data or buffering.
        false
      }
  }*/


  /**
   * Used to store metadata for a sensor to database
   * @param path path to sensor
   * @param data metadata to be stored as string e.g a XML block as string
   * 
   */
  def setMetaData(path:Path,data:String): Unit = ???
  def setMetaData(hierarchyId:Int,data:String): Unit = ???
  /*{
    val qry = meta.filter(_.path === path).map(_.data)
    val count = runSync(qry.result).length
    if(count == 0)
    {
      runSync(meta += (path,data))
    }
    else
    {
      runSync(qry.update(data))
    }
  }
  */


  def RemoveMetaData(path:Path): Unit= ???
  /*{
    val qry = meta.filter(_.path === path)
    runSync(qry.delete)
  }*/


  /**
   * Used to set many values efficiently to the database.
   * @param data list of tuples consisting of path and TimedValue.
   */
  def setMany(data: List[(Path, OdfValue)]): Boolean = ??? /*{
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
  private def removeExcess(path: Path) = ??? /*{
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
  protected def startBuffering(path: Path): Unit = ??? /*{
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
  protected def stopBuffering(path: Path): Boolean = ??? /*{
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
  def removeSub(id: Int): Boolean = ??? /*{
    
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
  def setSubStartTime(id:Int,newTime:Timestamp,newTTL:Double) = ??? 
  /*{
    runWait(subs.filter(_.ID === id).map(p => (p.start,p.TTL)).update((newTime,newTTL)))
  }*/


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
  def saveSub(sub: NewDBSub, dbItems: Seq[Paths]): DBSub = ??? 
  /*{
        val id = getNextId()
        if (sub.callback.isEmpty) {
          sub.paths.foreach {
            runSync(startBuffering(_))
          }
        }
        val insertSub =
          subs += (id, sub.paths.mkString(";"), sub.startTime, sub.ttl, sub.interval, sub.callback)
        runSync(DBIO.seq(
          insertSub
        ))

        //returns the id for reference
        id
    }
    */


}

