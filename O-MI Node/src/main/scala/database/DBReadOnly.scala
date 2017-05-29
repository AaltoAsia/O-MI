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
import scala.concurrent.Future
import scala.language.postfixOps

import org.slf4j.LoggerFactory
//import slick.driver.H2Driver.api._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types._
import http.OmiNodeContext

/**
 * Read only restricted interface methods for db tables
 */
trait DBReadOnly extends DBBase with OdfConversions with DBUtility with OmiNodeTables {
  import dc.driver.api._
  protected def singleStores : SingleStores
  protected[this] def findParentI(childPath: Path): DBIOro[Option[DBNode]] = findParentQ(childPath).result.headOption

  protected[this] def findParentQ(childPath: Path): Query[DBNodesTable, DBNode, Seq] =
    if (childPath.length == 0)
      hierarchyNodes filter (_.path === childPath)
    else
      hierarchyNodes filter (_.path === Path(childPath.init))
  
  private val log = LoggerFactory.getLogger("DBReadOnly")


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
  def get(path: Path): Future[Option[OdfNode]] = db.run(getQ(path))

  //def getQ(single: OdfElement): OdfElement = ???
  def getQ(path: Path): DBIOro[Option[OdfNode]] = for {

    subTreeData <- getSubTreeI(path, depth = Some(1))

    dbInfoItems = toDBInfoItems(subTreeData)

    result = singleObjectConversion(dbInfoItems)

  } yield result


  /**
   * Makes a Query which filters, limits and sorts as limited by the parameters.
   * See [[getNBetween]].
   */
  protected[this] def nBetweenLogicQ(
    values: Query[DBValuesTable, DBValue, Seq],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]): Query[DBValuesTable, DBValue, Seq] = {

    //are these values already sorted?
    val timeFrame = values filter betweenLogicR(begin, end)

    // NOTE: duplicate code: takeLogic
    val query = (begin, end, oldest, newest) match {
      case (_, _, Some(_oldest), _) => timeFrame sortBy (_.timestamp.asc) take (_oldest)
      case (_, _, _, Some(_newest)) => timeFrame sortBy (_.timestamp.desc) take (_newest) sortBy (_.timestamp.asc)
      case (None, None, _, _)       => timeFrame sortBy (_.timestamp.desc) take 1
      case _                        => timeFrame
    }
    //old
    //    val query =
    //      if (oldest.nonEmpty) {
    //        timeFrame sortBy (_.timestamp.asc) take (oldest.get) //sortBy (_.timestamp.asc)
    //      } else if (newest.nonEmpty) {
    //        timeFrame sortBy (_.timestamp.desc) take (newest.get) sortBy (_.timestamp.asc)
    //      } else if (begin.isEmpty && end.isEmpty) {
    //        timeFrame sortBy (_.timestamp.desc) take 1
    //      } else {
    //        timeFrame
    //      }
    query
  }

  protected[this] def betweenLogicR(
    begin: Option[Timestamp],
    end: Option[Timestamp]): DBValuesTable => Rep[Boolean] =
    (end, begin) match {
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
  protected[this] def betweenLogic(
    begin: Option[Timestamp],
    end: Option[Timestamp]): DBValue => Boolean =
    (end, begin) match {
      case (None, Some(startTime)) =>
        { _.timestamp.getTime >= startTime.getTime }

      case (Some(endTime), None) =>
        { _.timestamp.getTime <= endTime.getTime }

      case (Some(endTime), Some(startTime)) =>
        { value =>
          value.timestamp.getTime >= startTime.getTime && value.timestamp.getTime <= endTime.getTime
        }
      case (None, None) =>
        { value => true }
    }

  // NOTE: duplicate code: nBetweenLogicQ
  protected[this] def takeLogic(
    newest: Option[Int],
    oldest: Option[Int],
    timeFrameEmpty: Boolean): Seq[DBValue] => Seq[DBValue] = {
    (newest, oldest) match {
      case (_, Some(_oldest))    => _.sortBy(_.timestamp.getTime) take (_oldest)
      case (Some(_newest), _)    => _.sortBy(_.timestamp.getTime)(Ordering.Long.reverse) take (_newest) reverse
      case _ if (timeFrameEmpty) => _.sortBy(_.timestamp.getTime)(Ordering.Long.reverse) take 1
      case _                     => _.sortBy(_.timestamp.getTime)
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
   * @param begin optional start Timestamp
   * @param end optional end Timestamp
   * @param newest number of values to be returned from start
   * @param oldest number of values to be returned from end
   * @return Combined results in a O-DF tree
   */
  def getNBetween(
    requests: Iterable[OdfNode],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]): Future[Option[OdfObjects]] = {
      val requestsSeq = requests.toSeq

      if( newest.isDefined && oldest.isDefined ){ 
        val msg = "Both newest and oldest at the same time not supported!"
        log.error(msg)
        Future.failed( new Exception(msg))
      } else if( requestsSeq.isEmpty){
        val msg = "getNBetween should be called with at least one request thing"
        log.error(msg)
        Future.failed( new Exception(msg))
      } else {

        def processObjectI(path: Path, attachObjectDescription: Boolean): DBIO[Option[OdfObjects]] = {
          getHierarchyNodeI(path) flatMap {
            case Some(rootNode) => for { // DBIO

              subTreeData <- getSubTreeQ(rootNode).result // TODO: don't get all values

              // NOTE: We can only apply "between" logic here because of the subtree query
              // basicly we fetch too much data if "newest" or "oldest" is set

              timeframedTreeData = subTreeData filter {
                case (node, Some(value)) => betweenLogic(begin, end)(value)
                case (node, None)        => false
              }

              dbInfoItems: DBInfoItems =
                toDBInfoItems(timeframedTreeData) mapValues takeLogic(newest, oldest, begin.isEmpty && end.isEmpty)


                results = odfConversion(dbInfoItems)
                /*for {
                  odf <- odfConversion(dbInfoItems)
                  //desc = getDescObject(path, odf, attachObjectDescription)
                } yield odf
                 */
            } yield results

                case None => // Requested object was not found, TODO: think about error handling
                  DBIO.successful(None)
          }

        }

        def getFromDB(): Seq[Future[Option[OdfObjects]]] = requestsSeq map { // par

          case obj @ OdfObjects(objects, _, _) =>
            require(objects.isEmpty,
              s"getNBetween requires leaf OdfElements from the request, given nonEmpty $obj")


            db.run(processObjectI(obj.path, attachObjectDescription = false))

          case obj @ OdfObject(id, path, items, objects, description, typeVal,attr) =>
            require(items.isEmpty && objects.isEmpty,
              s"getNBetween requires leaf OdfElements from the request, given nonEmpty $obj")

            db.run(processObjectI(path, description.nonEmpty))

          case qry @ OdfInfoItem(path, rvalues, _, _,typeValue,attr) =>

            val odfInfoItemI = getHierarchyNodeI(path) flatMap { nodeO =>

              nodeO match {
                case Some(node @ DBNode(Some(nodeId), _, _, _, _, _, _, true)) => for {

                  odfInfoItem <- processObjectI(path, attachObjectDescription = false)


                  result = odfInfoItem.map {
                    infoItem => createAncestors(infoItem)// union createAncestors(metaInfoItem)
                  }

                } yield result

                case n : Option[DBNode ]=>
                  log.warn(s"Requested '$path' as InfoItem, found '$n'")
                  DBIO.successful(None) // Requested object was not found or not infoitem, TODO: think about error handling
              }
            }

            db.run(odfInfoItemI)

        }

        def getFromCache(): Seq[Option[OdfObjects]] = {
          lazy val hTree = singleStores.hierarchyStore execute GetTree()
          val objectData: Seq[Option[OdfObjects]] = requestsSeq collect {

            case obj@OdfObjects(objects, _, _) => {
              require(objects.isEmpty,
                s"getNBetween requires leaf OdfElements from the request, given nonEmpty $obj")

              Some(singleStores.buildOdfFromValues(
                singleStores.latestStore execute LookupAllDatas()))
            }

            case obj @ OdfObject(_, path, items, objects, _, _, _) => {
              require(items.isEmpty && objects.isEmpty,
                s"getNBetween requires leaf OdfElements from the request, given nonEmpty $obj")
              val resultsO = for {
                odfObject <- hTree.get(path).collect{
                  case o: OdfObject => o
                }
                paths = getLeafs(odfObject).map(_.path)
                pathValues = singleStores.latestStore execute LookupSensorDatas(paths)
              } yield singleStores.buildOdfFromValues(pathValues)

              //val paths = getLeafs(obj).map(_.path)
              //val objs = singleStores.latestStore execute LookupSensorDatas(paths)
              //val results = singleStores.buildOdfFromValues(objs)

              resultsO
            }
          }

          // And then get all InfoItems with the same call
          val reqInfoItems = requestsSeq collect {case ii: OdfInfoItem => ii}
          val paths = reqInfoItems map (_.path)

          val infoItemData = singleStores.latestStore execute LookupSensorDatas(paths)
          val foundPaths = (infoItemData map { case (path,_) => path }).toSet

          val resultOdf = singleStores.buildOdfFromValues(infoItemData)

          objectData :+ Some(
            reqInfoItems.foldLeft(resultOdf){(result, info) =>
              info match {
                case qry @ OdfInfoItem(path, _, _, _,typeValue,attr) if foundPaths contains path =>
                  result union createAncestors(qry)
                case _ => result // else discard
              }
            }
            )
        }

        // Optimizing basic read requests,
        // TODO: optimize newest.exists(_ == 1) && (begin.nonEmpty || end.nonEmpty)
        val allResults: Future[Seq[Option[OdfObjects]]] =
          if ((newest.isEmpty || newest.contains(1)) && (oldest.isEmpty && begin.isEmpty && end.isEmpty))
            Future(getFromCache())
          else
            Future.sequence(getFromDB())

    // Combine some Options
    val results = allResults.map(_.fold(None){
      case (Some(_results), Some(otherResults)) => Some(_results union otherResults)
      case (None, Some(_results))               => Some(_results)
      case (Some(_results), None)               => Some(_results)
      case (None, None)                        => None
    })

    results.map{
      case Some(OdfObjects(x, _, _)) if x.isEmpty => None
      case default : Option[OdfObjects ]  => default//default.map(res => metadataTree.intersect(res)) //copy information from hierarchy tree to result
    }
    
  }
}

//  def getNBetweenWithHierarchyIds(
//    infoItemIdTuples: Seq[(Int, OdfInfoItem)],
//    begin: Option[Timestamp],
//    end: Option[Timestamp],
//    newest: Option[Int],
//    oldest: Option[Int]): OdfObjects =
//    {
//      val ids = infoItemIdTuples.map { case (id, info) => id }
//      val betweenValues = db.run(
//        nBetweenLogicQ(
//          latestValues.filter { _.hierarchyId.inSet(ids) },
//          begin,
//          end,
//          newest,
//          oldest).result)
//      infoItemIdTuples.map {
//        case (id, info) =>
//          createAncestors(info.copy(values = betweenValues.collect { case dbval if dbval.hierarchyId == id => dbval.toOdf }))
//      }.foldLeft(OdfObjects())(_.union(_))
//    }


/**
 * @param root Root of the tree
 * @param depth Maximum traverse depth relative to root
 */
protected[this] def getSubTreeQ(
  root: DBNode,
  depth: Option[Int] = None): Query[(DBNodesTable, Rep[Option[DBValuesTable]]), DBValueTuple, Seq] = {

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

      nodesWithValuesQ sortBy {case (nodes, _) => nodes.leftBoundary.asc}
  }

  protected[this] def getSubTreeI(
    path: Path,
    depth: Option[Int] = None): DBIOro[Seq[(DBNode, Option[DBValue])]] = {

      val subTreeRoot = getHierarchyNodeI(path)

      subTreeRoot flatMap {
        case Some(root) =>

          getSubTreeQ(root, depth).result

        case None => DBIO.successful(Seq()) // TODO: What if not found?
      }
    }

  protected[this] def getInfoItemsI(hNodes: Seq[DBNode]): DBIO[DBInfoItems] =
    dbioDBInfoItemsSum(
      hNodes map { hNode =>
        for {
          subTreeData <- getSubTreeI(hNode.path)

          infoItems: DBInfoItems = toDBInfoItems(subTreeData)

          result: DBInfoItems = infoItems collect {
            case (node, seqVals) if seqVals.nonEmpty =>
              (node, seqVals sortBy (_.timestamp.getTime) take 1)
          }
        } yield result
      })


  /*def getHierarchyIds = {
    db.run(q.result)
  }*/
 /* /**
  * Query to the database for given subscription id.
  * Data removing is done separately
  * @param id id of the subscription to poll
  * @return
  */
 def pollSubData(id: Long): Seq[SubValue] = {
   db.run(pollSubDataI(id))
 }
 private def pollSubDataI(id: Long) = {
   val subData = pollSubs filter (_.subId === id)
   subData.result
 }*/
}
