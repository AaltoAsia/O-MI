package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable

import parsing.Types._
import parsing.Types.OdfTypes._

import java.lang.RuntimeException

/**
 * Read only restricted interface methods for db tables
 */
trait DBReadOnly extends DBBase with OmiNodeTables {
  protected def findParent(childPath: Path): DBIOAction[Option[DBNode],NoStream,Effect.Read] = (
    if (childPath.length == 0)
      hierarchyNodes filter (_.path === childPath)
    else
      hierarchyNodes filter (_.path === Path(childPath.init))
    ).result.headOption


  /**
   * Used to get metadata from database for given path
   * @param path path to sensor whose metadata is requested
   *
   * @return metadata as Option[String], none if no data is found
   */
  def getMetaData(path: Path): Option[OdfMetaData] = runSync(getMetaDataI(path))

  protected def getMetaDataI(path: Path): DBIOAction[Option[OdfMetaData], NoStream, Effect.Read] = {
    val queryResult = getWithHierarchyQ[DBMetaData, DBMetaDatasTable](path, metadatas).result
    queryResult map (
      _.headOption map (_.toOdf)
    )
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
  def getPollData(id: Int, testTime: Option[Timestamp]): OdfObjects = ???
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
  def getPollData(id: Int): OdfObjects = getPollData(id, None)

  /**
   * Used to get data from database based on given path.
   * returns Some(OdfInfoItem) if path leads to sensor and if
   * path leads to object returns Some(OdfObject).
   * OdfObject has childs as infoitems and objects.
   * if nothing is found for given path returns None
   *
   * @param path path to search data from
   *
   * @return either Some(OdfInfoItem),Some(OdfObject) or None based on where the path leads to
   */
  def get(path: Path): Option[ HasPath ] = {
    val valueNodePair = runSync(
      for {
        value <- getValueI(path)
        node <- getHierarchyNodeI(path)
      } yield (value, node)
    )

    valueNodePair match {
      case (Some(value), Some(node)) =>
        Some( node.toOdfInfoItem(Seq(value.toOdf)) )

      case (None, Some(node)) =>
        Some( node.toOdfObject )
      
      case _ => None
    }
  }

  def getQ(single: OdfElement): OdfElement = ???

  // Used for compiler type trickery by causing type errors
  trait Hole // TODO: RemoveMe!

  //Helper for getting values with path
  protected def getValuesQ(path: Path) =
    getWithHierarchyQ[DBValue, DBValuesTable](path, latestValues)

  protected def getValueI(path: Path) =
    getValuesQ(path).sortBy(
      _.timestamp.desc
    ).result.map(_.headOption)


  protected def getWithExprI[ItemT, TableT <: HierarchyFKey[ItemT]](
    expr: Rep[ItemT] => Rep[Boolean],
    table: TableQuery[TableT]
  ): DBIOAction[Option[ItemT], NoStream, Effect.Read] =
    table.filter(expr).result.map(_.headOption)

  protected def getWithHierarchyQ[ItemT, TableT <: HierarchyFKey[ItemT]](
    path: Path,
    table: TableQuery[TableT]
  ): Query[TableT,ItemT,Seq] = // NOTE: Does the Query table need (DBNodesTable, TableT) ?
    for {
      (hie, value) <- joinWithHierarchyQ[ItemT, TableT](path, table)
    } yield(value)

  protected def joinWithHierarchyQ[ItemT, TableT <: HierarchyFKey[ItemT]](
    path: Path,
    table: TableQuery[TableT]
  ): Query[(DBNodesTable, TableT),(DBNode, ItemT),Seq] =
    hierarchyNodes.filter(_.path === path) join table on (_.id === _.hierarchyId )

  protected def getHierarchyNodeI(path: Path): DBIOAction[Option[DBNode], NoStream, Effect.Read] =
    hierarchyNodes.filter(_.path === path).result.map(_.headOption)

  protected def getHierarchyNodesI(paths: Seq[Path]): DBIOAction[Seq[DBNode], NoStream, Effect.Read] =
  hierarchyNodes.filter(node => node.path.inSet( paths) ).result
    
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
  ): Query[DBValuesTable,DBValue,Seq] =
    nBetweenLogicQ(getValuesQ(path), begin, end, newest, oldest)


  /**
   * Makes a Query which filters, limits and sorts as limited by the parameters.
   * See [[getNBetween]].
   * @param getter Gets DBValue from some ValueType for filtering and sorting
   */
  protected def nBetweenLogicQ(
    values: Query[DBValuesTable,DBValue,Seq],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]
  ): Query[DBValuesTable,DBValue,Seq] = {
    val timeFrame = values filter betweenLogic(begin, end)
    val query =
      if( newest.nonEmpty ) {
        timeFrame sortBy ( _.timestamp.desc ) take (newest.get)
      } else if ( oldest.nonEmpty ) {
        timeFrame sortBy ( _.timestamp.asc ) take (oldest.get) sortBy (_.timestamp.desc)
      } else {
        timeFrame sortBy ( _.timestamp.desc )
      }
    query
  }

  protected def betweenLogic(
    begin: Option[Timestamp],
    end: Option[Timestamp]
  ): DBValuesTable => Rep[Boolean] =
    ( end, begin ) match {
      case (None, Some(startTime)) =>
        { value =>
          value.timestamp >= startTime
        }
      case (Some(endTime), None) =>
        { value =>
          value.timestamp <= endTime
        }
      case (Some(endTime), Some(startTime)) =>
        { value =>
          value.timestamp >= startTime &&
          value.timestamp <= endTime
        }
      case (None, None) =>
        { value =>
          true: Rep[Boolean]
        }
    }




  /**
   * Used to get result values with given constrains in parallel if possible.
   * first the two optional timestamps, if both are given
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
    requests: Iterable[HasPath],
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

        val actions = getHierarchyNodeI(path) flatMap {rootNodeO =>
          rootNodeO match {
            case Some(rootNode) =>
              val subTreeDataQ = getSubTreeQ(rootNode)

              // NOTE: We can only apply "between" logic here because of the subtree query
              // basicly we fetch too much data if "newest" or "oldest" is set
              val timeFrameFilter = betweenLogic(begin, end)
              val subTreeDataI = (
                subTreeDataQ filter {
                  case (node, value) => timeFrameFilter(value)
                }
              ).result

              // Odf conversion, TODO: move to own method
              subTreeDataI map {data => odfConversion(rootNode, data)}

            
            case None => Seq()
          }
          ???
        }

        db.run( actions )

      case OdfInfoItem(path, rvalues, _, metadata) =>
        val futureSeq = db.run(
          getNBetweenInfoItemQ(path, begin, end, newest, oldest).result
        )
        futureSeq map (_ map (_.toOdf))

        /*
        val metaDataRequested = metadata.isDefined
        if (metaDataRequested)
        val futureOption = db.run(
          getMetaDataI(path)
        )
        futureOption map (_.toSeq)*/
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

  /**
   * Conversion for a (sub)tree of hierarchy with value data
   * @param root Root of the subtree
   * @param treeData Hierarchy and value data joined and sorted by leftBoundary.
   */
  protected def odfConversion(root: DBNode, treeData: Seq[(DBNode, DBValue)]): OdfObject = ??? /*{
    // Convert: Map DBNode -> Seq[DBValue]
    val nodeMap = treeData groupBy (_._1) mapValues (_ map (_._2)) 

    def genOdfRoot(root: DBNode, treeData: Map[DBNode, Seq[DBValue]]): Iterable[OdfObject] = {
      treeData.headOption match {
        case Some((headNode, )) =>
          treeData span { case (node, _) => node.depth == head.depth }
            .toIterable map {
            case (node, Seq()) if !node.isInfoItem =>
              val innerRoot = node
              val innerRootOdfs = innerGenOdf(innerRoot, ???)

              innerRoot.toOdfObject(Iterable(), innerRootOdfs)

              // TODO: How to do the objects
            case (node, values) if node.isInfoItem =>
              node.toOdfInfoItem(values)
          }

      }
    }

    // FIXME: when to compute lazy values to Seq etc
    genOdfRoot(root, nodeMap)

  }*/

  protected def getSubTreeQ(
    root: DBNode
  ): Query[(DBNodesTable, DBValuesTable), (DBNode, DBValue), Seq] = {
    val nodesQ = hierarchyNodes filter { node =>
      node.leftBoundary >= root.leftBoundary &&
      node.rightBoundary <= root.rightBoundary
    }

    val nodesWithValuesQ =
      nodesQ join latestValues on (_.id === _.hierarchyId)

    nodesWithValuesQ sortBy (_._1.leftBoundary.asc)
  }


  protected def getSubTreeI(
    path: Path
  ): DBIOAction[Seq[(DBNode, DBValue)], NoStream, Effect.Read] = {

    val subTreeRoot = getHierarchyNodeI(path)

    subTreeRoot flatMap {
      case Some(root) =>

        getSubTreeQ(root).result

      case None => DBIO.successful(Seq()) // TODO: What if not found?
    }
  }

  /**
   * Used to get childs of an object with given path
   * @param path path to object whose childs are needed
   * @return Array[DBItem] of DBObjects containing childs
   *  of given object. Empty if no childs found or invalid path.
   */
  def getChilds(path: Path): Array[DBItem] = {
    
    ???
  }
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
   * @return DBSub objects for the query as Seq
   */
  def getAllSubs(hasCallBack: Option[Boolean]): Seq[DBSub] = {
    val all = runSync(hasCallBack match{
      case Some(true)   => subs.filter(!_.callback.isEmpty).result
      case Some(false)  => subs.filter(_.callback.isEmpty).result
      case None         => subs.result
    })
    all.collect({case x: DBSub =>x})

  }


  def getSubscribtedPaths( id: Int) : Array[Path] = {
    val pathsQ = for{
      (subI, hie) <- subItems.filter( _.subId === id ) join hierarchyNodes on ( _.hierarchyId === _.id )
    }yield( hie.path )
    runSync( pathsQ.result ).toArray
  }
  def getSubscribtedItems( id: Int) : Array[DBSubscriptionItem] = {
    runSync( subItems.filter( _.subId === id ).result ).toArray
  }


  /**
   * Returns DBSub object wrapped in Option for given id.
   * Returns None if no subscription data matches the id
   * @param id number that was generated during saving
   *
   * @return returns Some(BDSub) if found element with given id None otherwise
   */
  def getSub(id: Int): Option[DBSub] = runSync(getSubQ(id))

  protected def getSubQ(id: Int): DBIOAction[Option[DBSub],NoStream,Effect.Read] =
    subs.filter(_.id === id).result.map{
      _.headOption map {
        case sub: DBSub => sub
        case _ => throw new RuntimeException("got wrong or unknown sub class???")
      }
    }
}
