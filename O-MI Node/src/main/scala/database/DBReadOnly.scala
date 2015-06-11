package database

import scala.language.postfixOps

import slick.driver.H2Driver.api._
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.SortedMap

import parsing.Types._
import parsing.Types.OdfTypes._

import java.lang.RuntimeException

/**
 * Read only restricted interface methods for db tables
 */
trait DBReadOnly extends DBBase with OmiNodeTables {

  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]

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

  protected def dbioDBInfoItemsSum: Seq[DBIO[SortedMap[DBNode,Seq[DBValue]]]] => DBIO[SortedMap[DBNode,Seq[DBValue]]] = {
    seqIO =>
      def iosumlist(a: DBIO[SortedMap[DBNode,Seq[DBValue]]], b: DBIO[SortedMap[DBNode,Seq[DBValue]]]): DBIO[SortedMap[DBNode,Seq[DBValue]]] = for {
        listA <- a
        listB <- b
      } yield (listA++listB)
      seqIO.foldRight(DBIO.successful(SortedMap.empty[DBNode,Seq[DBValue]]):DBIO[SortedMap[DBNode,Seq[DBValue]]])(iosumlist _)
  }

  protected def dbioSeqSum[A]: Seq[DBIO[Seq[A]]] => DBIO[Seq[A]] = {
    seqIO =>
      def iosumlist(a: DBIO[Seq[A]], b: DBIO[Seq[A]]): DBIO[Seq[A]] = for {
        listA <- a
        listB <- b
      } yield (listA++listB)
      seqIO.foldRight(DBIO.successful(Seq.empty[A]):DBIO[Seq[A]])(iosumlist _)
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
  def getSubData(id: Int, testTime: Option[Timestamp]): Option[OdfObjects] ={
    val subitemHieIds = subItems.filter( _.hierarchyId === id ).map( _.hierarchyId )
    val subItemNodesQ = hierarchyNodes.filter(
      //XXX:
      _.id.inSet( runSync( subitemHieIds.result ) )
    )
    val hierarchy = subItemNodesQ.result.flatMap{
      nodeseq =>{
        dbioDBInfoItemsSum(
          nodeseq.map{
            node =>{
              getSubTreeI(node.path).map{
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

   runSync( hierarchy.map{ odfConversion( _ ) } )
  }
  def getPollData(id: Int, newTime: Timestamp): Option[OdfObjects] ={
    val sub  = getSub( id )
    val subitems = subItems.filter( _.hierarchyId === id )
    val subitemHieIds = subItems.filter( _.hierarchyId === id ).map( _.hierarchyId )
    val subItemNodesQ = hierarchyNodes.filter(
      //XXX:
      _.id.inSet( runSync( subitemHieIds.result ) )
    )
    /*
     *
     *
     *  FIXME: TODO: XXX:
     *  This is horrible a thing.
     *
     *
     */
    val hierarchy = subItemNodesQ.result.flatMap{
      nodeseq =>{
        dbioDBInfoItemsSum(
          nodeseq.map{
            node =>{
              getSubTreeI(node.path).map{
                toDBInfoItems( _ ).filter{
                  //filter valueless infoitems
                  case (node, seqVals) => seqVals.nonEmpty
                }.map{
                  case (node, seqVals) =>
                    (
                      node,
                      //Get right data for each infoitem
                      sub match {
                        case Some(dbsub : DBSub) =>{
                          val vals = seqVals.sortBy( _.timestamp.getTime )
                          if( dbsub.isEventBased ){//Event Poll
                            //GET all vals
                            val dbvals = getBetween(vals, dbsub.startTime, newTime).dropWhile(
                              value =>
                              //XXX:
                                runSync(//drop unchanged values
                                  //Should reduce all sub sequences of same values to one value
                                  subitems.filter( _.hierarchyId === node.id ).result.map{
                                    _.headOption match{
                                      case Some( subitem ) => value.value == subitem.lastValue
                                      case None => false
                                    }
                                  }
                                )
                              )
                            //UPDATE LASTVALUES
                            //UPDATE SUB STARTTIME
                            dbvals
                          } else {//Normal poll
                            //GET VALUES FOR EACH INTERVAL
                            getByIntervalBetween(vals, dbsub.startTime, newTime, dbsub.interval.toLong )
                          }
                        }
                        case None => Seq.empty
                      }
                    )
                }
              }
            }
          }
        )
      }
    }

    runSync( hierarchy.map{ odfConversion( _ ) } )
  }

  def getBetween( values: Seq[DBValue], after: Timestamp, before: Timestamp ) = {
    values.filter( value => value.timestamp.before( before ) && value.timestamp.after( after ) )
  }
  def getByIntervalBetween(values: Seq[DBValue] , beginTime: Timestamp, endTime: Timestamp, interval: Long ) = {
    var intervalTime =
      endTime.getTime - (endTime.getTime - beginTime.getTime)%interval // last interval before poll
    var timeframe  = values.sortBy(
        value =>
        value.timestamp.getTime
      ) //ascending
   
    var intervalValues : Seq[DBValue] = Seq.empty
    var index = 1

    while( index > -1 && intervalTime >= beginTime.getTime ){
      index = timeframe.lastIndexWhere( value => value.timestamp.getTime <= intervalTime )
      if( index > -1) {
        intervalValues = intervalValues :+ timeframe( index )
        intervalTime -= interval
      }
    }   
    intervalValues.reverse
  }
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
  def getSubData(id: Int): Option[OdfObjects] = getSubData(id, None)

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

    val subTreeDataI = getSubTreeI(path, depth=Some(1))

    val dbInfoItemI  = subTreeDataI map toDBInfoItem

    val resultI = dbInfoItemI map (_ map hasPathConversion)

    runSync(resultI)
  }

  def getQ(single: OdfElement): OdfElement = ???

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
  ): DBIOAction[Option[ItemT], NoStream, Effect.Read] =
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

  protected def getHierarchyNodeI(path: Path): DBIOAction[Option[DBNode], NoStream, Effect.Read] =
    hierarchyNodes.filter(_.path === path).result.map(_.headOption)

  protected def getHierarchyNodesI(paths: Seq[Path]): DBIOAction[Seq[DBNode], NoStream, Effect.Read] =
  hierarchyNodes.filter(node => node.path.inSet( paths) ).result
   
    protected def getHierarchyNodesQ(paths: Seq[Path]) :Query[DBNodesTable,DBNode,Seq]=
  hierarchyNodes.filter(node => node.path.inSet( paths) )

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
  protected def getNBetweenDBInfoItemQ(
    id: Int,
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]
  ): Query[DBValuesTable,DBValue,Seq] =
    nBetweenLogicQ(getValuesQ(id), begin, end, newest, oldest)


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
    val timeFrame = values filter betweenLogicR(begin, end)
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

  protected def betweenLogicR(
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
  protected def betweenLogic(
    begin: Option[Timestamp],
    end: Option[Timestamp]
  ): DBValue => Boolean =
    ( end, begin ) match {
      case (None, Some(startTime)) =>
        { _.timestamp.getTime >= startTime.getTime }

      case (Some(endTime), None) =>
        { _.timestamp.getTime <= endTime.getTime }

      case (Some(endTime), Some(startTime)) =>
        { value =>
          value.timestamp.getTime >= startTime.getTime &&
          value.timestamp.getTime <= endTime.getTime
        }
      case (None, None) =>
        { value => true }
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
              val subTreeDataI = getSubTreeQ(rootNode).result

              // NOTE: We can only apply "between" logic here because of the subtree query
              // basicly we fetch too much data if "newest" or "oldest" is set
             
              val timeframedTreeDataI =
                subTreeDataI map { _ filter {
                  case (node, Some(value)) => betweenLogic(begin, end)(value)
                  case (node ,None) => true
                }}

              val dbInfoItemsI = timeframedTreeDataI map {toDBInfoItems(_)}

              // Odf conversion
              dbInfoItemsI map {items => odfConversion(items)}

            case None =>  // Requested object was not found, TODO: think about error handling
              DBIO.successful(Seq())
          }
        }

        db.run( actions )

      case OdfInfoItem(path, rvalues, _, metadata) =>
        val odfInfoItemI = getHierarchyNodeI(path) flatMap {nodeO =>
          nodeO match {
            case Some(node @ DBNode(Some(nodeId),_,_,_,_,_,_,_)) =>
              val dataI = getNBetweenDBInfoItemQ(nodeId, begin, end, newest, oldest).result

              dataI flatMap {data =>
                odfConversion((node, data))// match {
                  //case Some(dbInfoItem) => // info item has values
                  //}
                ???
              }

            case _ => DBIO.successful(Seq())  // Requested object was not found, TODO: think about error handling
          }
        }

        db.run(odfInfoItemI)

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

  type DBValueTuple= (DBNode, Option[DBValue])
  type DBInfoItem  = (DBNode, Seq[DBValue])
  type DBInfoItems = SortedMap[DBNode, Seq[DBValue]]

  protected implicit val DBNodeOrdering = Ordering.by[DBNode, Int](_.leftBoundary)

  def toDBInfoItems(input: Seq[DBValueTuple]): DBInfoItems =
    SortedMap(input groupBy (_._1) mapValues {values =>
      val empty = List[DBValue]()

      values.foldLeft(empty){
        case (others, (_, Some(dbValue))) => dbValue :: others
        case (others, (_, None)) => others
      }

      } toArray : _*
    )

  def toDBInfoItem(tupleData: Seq[DBValueTuple]): Option[DBInfoItem] = {
      val items = toDBInfoItems(tupleData)
      assert(items.size <= 1, "Asked one infoitem, should contain max one infoitem")
      items.headOption
    }  

  /**
   * Conversion for a (sub)tree of hierarchy with value data.
   * @param treeData Hierarchy and value data joined, so contains InfoItem DBNodes and its values.
   */
  protected def odfConversion(treeData: Seq[DBValueTuple]): OdfObjects = {
    // Convert: Map DBNode -> Seq[DBValue]
    val nodeMap = toDBInfoItems(treeData)
    odfConversion(treeData)
  }

  protected def hasPathConversion: DBInfoItem => HasPath = {
    case (node, values) if node.isInfoItem  =>
      val odfValues      = values map (_.toOdf) toIterable
      val odfInfoItem    = node.toOdfInfoItem(odfValues)
      odfInfoItem
    case (node, values) if !node.isInfoItem =>
      node.toOdfObject
  }

  protected def odfConversion: DBInfoItem => OdfObjects =
    fromPath _ compose hasPathConversion

  protected def odfConversion(treeData: DBInfoItems): Option[OdfObjects] = {
    val odfObjectsTrees = treeData map odfConversion

    // safe version of reduce
    odfObjectsTrees.headOption map { head =>
        odfObjectsTrees.fold(head)(_ combine _)
    }
  }

  /**
   * @param root Root of the tree
   * @param depth Maximum traverse depth relative to root
   */
  protected def getSubTreeQ(
    root: DBNode,
    depth: Option[Int] = None
  ): Query[(DBNodesTable, Rep[Option[DBValuesTable]]), (DBNode, Option[DBValue]), Seq] = {
    val depthConstraint: DBNodesTable => Rep[Boolean] = node =>
      depth match {
        case Some(depthLimit) =>
          node.depth <= root.depth + depthLimit
        case None =>
          true
      }
    val nodesQ = hierarchyNodes filter { node =>
      node.leftBoundary >= root.leftBoundary &&
      node.rightBoundary <= root.rightBoundary &&
      depthConstraint(node)
    }

    val nodesWithValuesQ =
      nodesQ joinLeft latestValues on (_.id === _.hierarchyId)

    nodesWithValuesQ sortBy (_._1.leftBoundary.asc)
  }


  protected def getSubTreeI(
    path: Path,
    depth: Option[Int] = None
  ): DBIOAction[Seq[(DBNode, Option[DBValue])], NoStream, Effect.Read] = {

    val subTreeRoot = getHierarchyNodeI(path)

    subTreeRoot flatMap {
      case Some(root) =>

        getSubTreeQ(root, depth).result

      case None => DBIO.successful(Seq()) // TODO: What if not found?
    }
  }



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
  def getSub(id: Int): Option[DBSub] = runSync(getSubI(id))

  protected def getSubI(id: Int): DBIOAction[Option[DBSub],NoStream,Effect.Read] =
    subs.filter(_.id === id).result.map{
      _.headOption map {
        case sub: DBSub => sub
        case _ => throw new RuntimeException("got wrong or unknown sub class???")
      }
    }
}
