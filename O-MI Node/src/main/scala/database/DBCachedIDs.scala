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
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.collection.mutable.{ Map => MutableMap, HashMap => MutableHashMap}
import scala.language.postfixOps

import org.slf4j.LoggerFactory
//import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.{OmiReturn, Returns}
import types._

/**
 * Read-write interface methods for db tables.
 */
trait DBCachedReadWrite extends DBReadWrite{

  import dc.driver.api._

  val pathToHierarchyID: MutableMap[Path,Set[Int]] = MutableHashMap()
  val hierarchyIDToPath: MutableMap[Int,Path] = MutableHashMap()

  override protected val log = LoggerFactory.getLogger("DBCachedReadWrite")

  def initialize(): Unit = this.synchronized {

    val getHierachyIds = hierarchyNodes.filter(_.isInfoItem).map{ 
      hNode => (hNode.path, hNode.id) // NOTE: Heavy operation
    }.result.map{ 
      case pathToIds => 
        val p2IDset: Map[Path,Set[Int]] = pathToIds.flatMap{
          case (p,hid) => 
            p.getParentsAndSelf.map{ path => 
              (path,hid)
            }
        }.groupBy{ 
          case (p, hid) => p
          }.mapValues{ 
            case p2ids => 
              p2ids.map{
                case (p, hid) => hid
              }.toSet
          }
      pathToHierarchyID ++= p2IDset
      hierarchyIDToPath ++= pathToIds.map{
        case (p,id) => (id,p)
      }
    }
    val setup = DBIO.seq(
      allSchemas.create,
      addRoot,
      getHierachyIds
    )

    val existingTables = MTable.getTables.map{ tables => tables.map(_.name.name)}
    val existed : Seq[String] = Await.result(db.run(existingTables), 5 minutes).filter( !_.startsWith("pq"))
    if ( existed.contains("HIERARCHYNODES") && existed.contains("SENSORVALUES")) {
      //noop
      log.info(
        "Found tables: " +
          existed.mkString(", ") +
          "\n Not creating new tables.")
      Await.result(db.run(getHierachyIds), 5 minutes)
    } else {
      //run transactionally so there are all or no tables

      log.info("Creating new tables: " + allTables.map(_.baseTableRow.tableName).mkString(", "))
      Await.result(db.run(setup.transactionally), 5 minutes)
    }

  }

  /**
   * Used to set many values efficiently to the database.
   */
  def writeMany(infos: Seq[OdfInfoItem]): Future[OmiReturn] = {
    val pathToWrite: Seq[DBValue] = infos.flatMap {
      case info =>
        pathToHierarchyID.get(info.path).flatMap {
          case hIDset =>
            hIDset.headOption.map {
              hID =>
                info.values.map {
                  case odfVal =>
                    DBValue(
                      hID,
                      //create new timestamp if option is None
                      odfVal.timestamp,
                      odfVal.value.toString,
                      odfVal.typeValue
                    )
                }
            }
        }
    }.flatten

    val writeExisting = (latestValues ++= pathToWrite)

    val idNotFound = infos.filter{ case info => pathToHierarchyID.get(info.path).isEmpty}
    val writeAction = if( idNotFound.nonEmpty ){
      val pathsData: Map[Path, Seq[OdfValue[Any]]] = idNotFound.map(ii => (ii.path -> ii.values.sortBy(_.timestamp.getTime))).toMap

      val writeNewAction = for {
        addObjectsAction <- DBIO.sequence(
          pathsData.keys map (addObjectsI(_, lastIsInfoItem = true))) // NOTE: Heavy operation

        idQry <- getHierarchyNodesQ(pathsData.keys.toSeq) map { hNode => // NOTE: Heavy operation
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
              odfVal.value.toString,
              odfVal.typeValue)
          }
        }
        updateAction <- latestValues ++= dbValues
          
      } yield idMap.toSeq
      writeExisting.flatMap{
        case writen => 
          writeNewAction.map{
            case p2IDs : Seq[(types.Path, Int)] if p2IDs.nonEmpty => 
              val p2set: Map[Path,Set[Int]] = p2IDs.flatMap{
                case (p,hid) => 
                  p.getParentsAndSelf.map{ path => 
                    (path,hid)
                  }
              }.groupBy{ 
                case (p, hid) => p
              }.mapValues{ 
                case p2ids => p2ids.map{ case (_,id) => id}.toSet
              }.map{
                case (p,hidSet) => 
                  val preSet = pathToHierarchyID.get(p)
                  val newSet = preSet.map{ set=> set ++ hidSet}.getOrElse(hidSet)
                  pathToHierarchyID.update(p,newSet)
                  (p,newSet)
              }
              hierarchyIDToPath ++= p2IDs.map{ case (p,id) => (id,p) }
              Returns.Success()
            case seq : Seq[(types.Path, Int)] if seq.isEmpty =>
              Returns.InternalError(Some("Using old database. Should use Warp 10."))
          }
      }
    } else writeExisting.map{
            case Some(count: Int) => 
              Returns.Success()
            case None =>
              Returns.InternalError(Some("Using old database. Should use Warp 10."))

          }
    val pathIdRelations : Future[OmiReturn] = db.run(writeAction.transactionally)

    //Call hooks
    /*
    pathIdRelations.onSuccess{
      case res =>
      log.debug("Path to id map after write:\n" + pathToHierarchyID.mkString("\n"))
    } */
    pathIdRelations
  }

  def getNBetween(
    requests: Iterable[OdfNode],
    beginO: Option[Timestamp],
    endO: Option[Timestamp],
    newestO: Option[Int],
    oldestO: Option[Int]
  ): Future[Option[OdfObjects]] = {
    //log.debug("Current path to id map:\n" + pathToHierarchyID.mkString("\n"))
      //log.debug("Request:\n"+requests.mkString("\n"))
      val infoitemIDs = requests.flatMap {
      node =>
        pathToHierarchyID.get(node.path).map {
          hIDset =>
            hIDset.map {
              hID =>
                hierarchyIDToPath.get(hID).map { path => (path, hID) }
            }.flatten
        }
    }.flatten.toMap
      //log.debug("Paths to be read:\n" + infoitemIDs.mkString("\n"))

      val ids = infoitemIDs.values.toVector
      val timeframed = 
        (beginO, endO) match {
        case (Some(begin), Some(end)) =>
          ids.map{ id =>
            latestValues.filter{
              dbval =>
                dbval.timestamp >= begin  && dbval.timestamp <= end && dbval.hierarchyId === id
            }
          }
        case (Some(begin), None) =>
          ids.map{ id =>
            latestValues.filter{
              dbval =>
                dbval.timestamp >= begin  && dbval.hierarchyId === id
            }
          }
        case (None, Some(end)) =>
          ids.map{ id =>
            latestValues.filter{
              dbval =>
                dbval.timestamp <= end && dbval.hierarchyId === id
            }
          }
        case (None, None) => 
          ids.map{ id =>
            latestValues.filter{
              dbval => dbval.hierarchyId === id
            }
          }
      }

      val limited = (newestO, oldestO) match {
        case (None, None) => 
          if( beginO.isEmpty && endO.isEmpty )
            timeframed.map{
              case query =>
                query.sortBy(_.timestamp.desc).take(1)
            }
          else timeframed.map{
              case query =>
                query.sortBy(_.timestamp.desc)
            }

        case ( Some(newest), None ) =>
          timeframed.map{
            case query =>
              query.sortBy(_.timestamp.desc).take( newest ) 
          }

        case ( None, Some(oldest)) =>
          timeframed.map{
            case query =>
                query.sortBy(_.timestamp.asc).take( oldest )
            }
          

        case ( Some(newest), Some(oldest)) =>  
          timeframed.map{
            case query =>
                  (
                    query.sortBy(_.timestamp.desc).take( newest ) ++ 
                    query.sortBy(_.timestamp.asc).take( oldest )
                  ).sortBy(_.timestamp.desc)
          }
      }

      val dbioAction = DBIO.sequence(limited.map(_.result)).map( _.flatten )
      val toObjects = dbioAction.map{
        case dbvals if dbvals.nonEmpty =>
          //log.debug("DBValue results:\n" +dbvals.mkString("\n"))
          Some(
          dbvals.groupBy(_.hierarchyId).flatMap{
            case (id, dbvalues) => 
              hierarchyIDToPath.get(id).map{
                path =>
                  OdfInfoItem( path, dbvalues.map(_.toOdf) )
              }
            }.foldLeft(OdfObjects()){ case (l, r) => l.union(r.createAncestors) }
            )
          case dbvals => None
      }
      db.run(toObjects.transactionally)

  }

  /** Removes path from DB and cache. Also removes childs and parents that would
   *  be empty
   *
   * @param path path to to-be-deleted sub tree.
   */
  override def remove( path: Path ) : Future[Seq[Int]] = {
    super.remove(path).map{
      case ids: Seq[Int] => 
        val paths = ids.flatMap{
          id: Int => hierarchyIDToPath.get(id)
        }
        pathToHierarchyID --= paths
        hierarchyIDToPath --= ids
        ids
    }
  }


}
