package database

import slick.driver.H2Driver.api._
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.iterableAsScalaIterable

import parsing.Types._
import parsing.Types.OdfTypes._

/**
 * Read only restricted interface methods for db tables
 */
trait DBReadOnly extends DBBase with OmiNodeTables {
  protected def findParent(childPath: Path): DBIOAction[DBNode,NoStream,Effect.Read] = (
    if (childPath.length == 0)
      hierarchyNodes filter (_.path === childPath)
    else
      hierarchyNodes filter (_.path === Path(childPath.init))
    ).result.head


  /**
   * Used to get metadata from database for given path
   * @param path path to sensor whose metadata is requested
   * 
   * @return metadata as Option[String], none if no data is found
   */
  def getMetaData(path: Path): Option[OdfMetaData] = runSync(
    getMetaDataI(path) map (_ map (OdfMetaData(_.metadata)))
  ) // TODO: clean codestyle

  protected def getMetaDataI(path: Path): DBIOAction[Option[DBMetaData], NoStream, Effect.Read] = {
    getWithHieracrhyQ[DBMetaData, DBMetaDatasTable](path, metadatas).result.map(_.headOption)
  }



  /**
   * Returns array of DBSensors for given subscription id.
   * Array consists of all sensor values after beginning of the subscription
   * for all the sensors in the subscription
   * returns empty array if no data or subscription is found
   *
   * @param id subscription id that is assigned during saving the subscription
   * @param testTime optional timestamp value to indicate end time of subscription,
   * should only be needed during testing. Other than testing None should be used
   *
   * @return Array of DBSensors
   */
  def getSubData(id: Int, testTime: Option[Timestamp]): OdfObjects = ???
  //OLD: def getSubData(id: Int, testTime: Option[Timestamp]): Array[DBSensor] = ???
    /*{
      var result = Buffer[DBSensor]()
      var subQuery = subs.filter(_.ID === id)
      var info: (Timestamp, Double) = (null, 0.0) //to gather only needed info from the query
      var paths = Array[String]()

      var str = runSync(subQuery.result)
      if (str.length > 0) {
        var sub = str.head
        info = (sub._3, sub._5)
        paths = sub._2.split(";")
      }
      paths.foreach {
        p =>
          result ++= DataFormater.FormatSubData(Path(p), info._1, info._2, testTime)
      }
      result.toArray
    }*/

  //ODL: def getSubData(id: Int): Array[DBSensor] = getSubData(id, None)
  def getSubData(id: Int): OdfObjects = getSubData(id, None)

  /**
   * Used to get data from database based on given path.
   * returns Some(DBSensor) if path leads to sensor and if
   * path leads to object returns Some(DBObject). DBObject has
   * variable childs of type Array[DBItem] which stores object's childs.
   * object.childs(0).path to get first child's path
   * if nothing is found for given path returns None
   *
   * @param path path to search data from
   *
   * @return either Some(DBSensor),Some(DBObject) or None based on where the path leads to
   */
  def get(path: Path): Option[ Either[ DBNode,DBValue ] ] = {
    val valueResult = runSync(
      getValuesQ(path).sortBy( _.timestamp.desc ).result
    )

    valueResult.headOption match {
      case Some(value) =>
        Some( Right(value) )
      case None =>
        val node = runSync( getHierarchyNodeI(path) )
        node.headOption map {value =>
          Left(node.get)
        }
    }
  }

  def getQ(single: OdfElement): OdfElement = ???

  // Used for compiler type trickery by causing type errors
  trait Hole // TODO: RemoveMe!

  //Helper for getting values with path
  protected def getValuesQ(path: Path) = getWithHieracrhyQ[DBValue, DBValuesTable](path, latestValues)

  protected def getWithHieracrhyQ[I, T <: HierarchyFKey[I]](path: Path, table: TableQuery[T]): Query[T,I,Seq] =
    for{
      (hie, value) <- hierarchyNodes.filter(_.path === path) join table on (_.id === _.hierarchyId )
    } yield(value)

  protected def getHierarchyNodeI(path: Path): DBIOAction[Option[DBNode], NoStream, Effect.Read] = 
    hierarchyNodes.filter(_.path === path).result.map(_.headOption)

  protected def getHierarchyNodeI(id: Int): DBIOAction[Option[DBNode], NoStream, Effect.Read] =
    hierarchyNodes.filter(_.id === id).result.map(_.headOption)


  /**
   * Used to get sensor values with given constrains. first the two optional timestamps, if both are given
   * search is targeted between these two times. If only start is given,all values from start time onwards are
   * targeted. Similiarly if only end is given, values before end time are targeted.
   *    Then the two Int values. Only one of these can be present. fromStart is used to select fromStart number
   * of values from the begining of the targeted area. Similiarly from ends selects fromEnd number of values from
   * the end.
   * All parameters except path are optional, given only path returns all values in the database for that path
   *
   * @param path path as Path object
   * @param start optional start Timestamp
   * @param end optional end Timestamp
   * @param fromStart number of values to be returned from start
   * @param fromEnd number of values to be returned from end
   * @return query for the requested values
   */
  protected def getNBetweenInfoItemQ(
    path: Path,
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]
  ): Query[DBValuesTable,DBValue,Seq] = {
    val timeFrame = ( end, begin ) match {
      case (None, Some(startTime)) => 
        getValuesQ(path).filter{ value =>
          value.timestamp >= startTime
        }
      case (Some(endTime), None) => 
        getValuesQ(path).filter{ value =>
          value.timestamp <= endTime
        }
      case (Some(endTime), Some(startTime)) => 
        getValuesQ(path).filter{ value =>
          value.timestamp >= startTime &&
          value.timestamp <= endTime
        }
      case (None, None) =>
        getValuesQ(path)
    }
    val query = 
      if( newest.nonEmpty ) {
        timeFrame.sortBy( _.timestamp.desc ).take(newest.get)
      } else if ( oldest.nonEmpty ) {
        timeFrame.sortBy( _.timestamp.asc ).take( oldest.get ) // XXX: Will have unconsistent ordering
      } else {
        timeFrame.sortBy( _.timestamp.desc )
      }
    query
  }

  /**
   * Used to get sensor values with given constrains. first the two optional timestamps, if both are given
   * search is targeted between these two times. If only start is given,all values from start time onwards are
   * targeted. Similiarly if only end is given, values before end time are targeted.
   *    Then the two Int values. Only one of these can be present. fromStart is used to select fromStart number
   * of values from the begining of the targeted area. Similiarly from ends selects fromEnd number of values from
   * the end.
   * All parameters except the first are optional, given only the first returns all requested data
   *
   * @param requests SINGLE requests in a list (leafs in request O-DF); InfoItems, Objects and MetaDatas
   * @param start optional start Timestamp
   * @param end optional end Timestamp
   * @param fromStart number of values to be returned from start
   * @param fromEnd number of values to be returned from end
   * @return Combined results in a O-DF tree
   */
  def getNBetween(
    requests: Iterable[OdfElement],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]
  ): OdfObjects = {
    require( ! (newest.isDefined && oldest.isDefined),
      "Both newest and oldest at the same time not supported!")
    //val futureResults: Iterable[Future[Seq[OdfElement]]] = requests map {
    val futureResults = requests map {

      case obj @ OdfObject(path,items,objects,_,_) =>
        require(items.isEmpty && objects.isEmpty,
          "getNBetween requires leaf OdfElements from the request")

        db.run(
          getNBetweenSubTreeQ(path, begin, end, newest, oldest).result
        )
        ???

      case OdfInfoItem(path, values, _, _) =>
        val futureSeq = db.run(
          getNBetweenInfoItemQ(path, begin, end, newest, oldest).result
        )
        futureSeq map (_ map (_.toOdfValue))

        val futureOption = db.run(
          getMetaDataI(path)
        )
        futureOption map (_.toSeq)
        ???

      case odf: OdfElement =>
        assert(false, s"Non-supported query parameter: $odf")
        ???
        //case OdfObjects(_, _) => 
        //case OdfDesctription(_, _) => 
        //case OdfValue(_, _, _) => 
    }

    ???
  }

  def getSubTreeQ(path: Path): Query[Hole,Hole,Seq] = ???

  protected def getNBetweenSubTreeQ(
    path: Path,
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]
  ): Query[Hole,DBValue,Seq] = {
    ???
  }
    
  /**
   * Used to get childs of an object with given path
   * @param path path to object whose childs are needed
   * @return Array[DBItem] of DBObjects containing childs
   *  of given object. Empty if no childs found or invalid path.
   */
  def getChilds(path: Path): Array[DBItem] = ???
    /*{
      var childs = Array[DBItem]()
      val objectQuery = for {
        c <- objects if c.parentPath === path
      } yield (c.path)
      var str = runSync(objectQuery.result)
      childs = Array.ofDim[DBItem](str.length)
      var index = 0
      str foreach {
        case (cpath: Path) =>
          childs(Math.min(index, childs.length - 1)) = DBObject(cpath)
          index += 1
      }
      childs
    }
    */





  /**
   * Checks whether given path exists on the database
   * @param path path to be checked
   * @return boolean whether path was found or not
   */
  protected def hasObject(path: Path): Boolean =
    runSync(hierarchyNodes.filter(_.path === path).exists.result)




  /**
   * getAllSubs is used to search the database for subscription information
   * Can also filter subscriptions based on whether it has a callback address
   * @param hasCallBack optional boolean value to filter results based on having callback address
   * 
   * None -> all subscriptions
   * Some(True) -> only with callback
   * Some(False) -> only without callback
   *
   * @return DBSub objects for the query as Array
   */
  def getAllSubs(hasCallBack: Option[Boolean]): Array[DBSub] = ??? 
  /*
    {
      val all = runSync(hasCallBack match {
        case Some(true) =>
          subs.filter(!_.callback.isEmpty).result
        case Some(false) =>
          subs.filter(_.callback.isEmpty).result
        case None =>
          subs.result
      })
      all map { elem =>
          val paths = elem._2.split(";").map(Path(_)).toVector
          DBSub(elem._1, paths, elem._4, elem._5, elem._6, Some(elem._3))
      }
    }
    */



  /**
   * Returns DBSub object wrapped in Option for given id.
   * Returns None if no subscription data matches the id
   * @param id number that was generated during saving
   *
   * @return returns Some(BDSub) if found element with given id None otherwise
   */
  def getSub(id: Int): Option[DBSub] = runSync(getSubQ(id))

  protected def getSubQ(id: Int): DBIOAction[Option[DBSub],NoStream,Effect.Read] =
    subs.filter(_.id === id).result.map(_.headOption)
}
