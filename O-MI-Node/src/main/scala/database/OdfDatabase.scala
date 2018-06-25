package database

import java.sql.Timestamp

import akka.util.Timeout

import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.stm._
import scala.collection.mutable.{HashMap => MutableHashMap, Map => MutableMap}
import scala.language.postfixOps
import org.slf4j.LoggerFactory
//import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable

import slick.backend.DatabaseConfig
//import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
import slick.lifted.{Index, ForeignKeyQuery, ProvenShape}
//import scala.collection.JavaConversions.iterableAsScalaIterable
import http.OmiConfigExtension
import types.odf._
import types.OmiTypes._
import types.Path
import akka.pattern.ask
import journal.Models.GetTree
import journal.Models.MultipleReadCommand
trait OdfDatabase extends Tables with DB with TrimmableDB{
  import dc.driver.api._

  protected val settings : OmiConfigExtension
  protected val singleStores : SingleStores
  protected val log = LoggerFactory.getLogger("O-DF-database")
  val pathToDBPath: TMap[Path, DBPath] = TMap()
  implicit val timeout: Timeout = 2 minutes

  def initialize(): Unit = {
    val findTables = db.run(namesOfCurrentTables)
    val createMissingTables = findTables.flatMap {
      tableNames: Seq[String] =>
        val queries = if (tableNames.contains("PATHSTABLE")) {
          //Found needed table, check for value tables
          /*
          val currentPathsF: Future[Seq[Path]] = db.run(pathsTable.currentPaths)
            currentPathsF.foreach{
              paths: Seq[Path] => 
                log.info(s"Found following paths in DB:${paths.mkString("\n")}") 
          }*/
          val infoItemDBPaths = pathsTable.selectAllInfoItems

          val valueTablesCreation = infoItemDBPaths.flatMap {
            dbPaths =>
              val actions = dbPaths.collect {
                case DBPath(Some(id), path, true) =>
                  val pathValues = new PathValues(path, id)
                  valueTables += path -> pathValues
                  //Check if table exists, is not create it
                  if (!tableNames.contains(pathValues.name)) {
                    Some(pathValues.schema.create.map {
                      u: Unit =>
                        log.debug(s"Created values table ${pathValues.name} for $path")
                        pathValues.name
                    })
                  } else None
              }.flatten
              //Action for creating missing Paths
              val countOfTables = actions.length
              log.info(s"Found total of ${dbPaths.length} InfoItems. Creating missing tables for $countOfTables tables.")
              DBIO.sequence(actions)
          }
          valueTablesCreation
        } else {
          if (tableNames.nonEmpty) {
            val msg = s"Database contains unknown tables while PATHSTABLE could not be found.\n Found following tables:\n${tableNames.mkString(", ")}"
            log.warn(msg)
          } else {
            log.info(s"No tables found. Creating PATHSTABLE.")
          }
          val queries = pathsTable.schema.create.map {
            u: Unit =>
              log.debug("Created PATHSTABLE.")
              Seq("PATHSTABLE")
          }
          queries
        }
        db.run(queries.transactionally)
    }
    val populateMap = createMissingTables.flatMap {
      tablesCreated: Seq[String] =>
        val actions =
          if (tablesCreated.contains("PATHSTABLE")) {
            val pRoot = Path("Objects")
            val dbP = DBPath(None, pRoot, isInfoItem = false)
            pathsTable.add(Seq(dbP)).map {
              ids: Seq[Long] =>
                atomic { implicit txn =>
                  pathToDBPath ++= ids.map {
                    id: Long =>
                      pRoot -> dbP.copy(id = Some(id))
                  }
                }
            }
          } else {
            pathsTable.result.map {
              dbPaths =>
                atomic { implicit txn =>
                  pathToDBPath ++= dbPaths.map { dbPath => dbPath.path -> dbPath }
                }
            }
          }
        db.run(actions.transactionally)
    }
    val initialization = populateMap
    initialization.onComplete{
      case Success( path2DBPath ) => 
        //log.info( s"Initialized DB successfully. ${path2DBPath.single.length} paths in DB." )
      case Failure( t ) => 
        log.error( "DB initialization failed.", t )
        /*
        logAllTables.flatMap{
          case u: Unit =>
            logPathsTable
        }.map{
          case u: Unit =>
            logValueTables
        }*/
    }
    Await.result( initialization, 1 minutes)
  }

  def writeMany(data: Seq[InfoItem]): Future[OmiReturn] = {
    log.debug("Writing many...")
    writeWithDBIOs(ImmutableODF(data))
  }

  private def returnOrReserve(path: Path, isInfoItem: Boolean): DBPath = {
    val ret = atomic { implicit txn =>
      pathToDBPath.get(path) match {
        case Some(dbpath @ DBPath(Some(_),_,_)) => 
          log.debug(s"Reserve $path $isInfoItem")
          dbpath // Exists
        case Some(         DBPath(None   ,_,_)) => 
          log.debug(s"$path is reserved, retry")
          retry  // Reserved
        case None => // Not found -> reserve
          log.debug(s"Reserve $path for creation. $isInfoItem")
          val newDbPath = DBPath(None, path, isInfoItem)
          pathToDBPath += path -> newDbPath
          newDbPath
      }
    }
  if (ret.isInfoItem != isInfoItem)
    throw new IllegalArgumentException(
      s"$path has Object/InfoItem conflict; Request has ${if (isInfoItem) "InfoItem" else "Object"} "+
    s"while DB has ${if (ret.isInfoItem) "InfoItem" else "Object"}")
    else ret
  }
  private def reserveNewPaths(nodes: Set[Node]): Map[Path,DBPath] = {

    def handleNode(o: Node, isInfo: Boolean, reserved: Map[Path,DBPath]): Map[Path, DBPath] = {
      val path = o.path
      val dbpath = returnOrReserve(path, isInfo)
      reserved ++ (dbpath match {
        case DBPath(None, _, _) =>
          val ancestors = path.ancestors
          val ancestorsNotReserved = ancestors.filterNot(reserved contains _)
          log.debug(s" Following ancestors of Path $path are not yet reserved: ${ancestorsNotReserved}")
          Map(path -> dbpath) ++ (
          ancestorsNotReserved
              .map(returnOrReserve(_, isInfoItem = false))
              .collect{
                case dbpath @ DBPath(None, _path, false) =>
                  dbpath.path -> dbpath
              }
              .toMap
          )
        case _ => Map() // noop
      })
    }

    val newNodes = nodes.toSeq.sortBy(_.path)(Path.PathOrdering)
    log.debug(s"Reserving New paths for ${newNodes.size} nodes...")
    log.debug(s"Handle following nodes: ${newNodes.map(_.path).mkString("\n")}")
    val re= newNodes.foldLeft(Map[Path,DBPath]()){
      case (reserved: Map[Path,DBPath], node: Node) =>
        node match {
          case obj: Objects => handleNode(obj, isInfo = false, reserved)
          case obj: Object => handleNode(obj, isInfo = false, reserved)
          case ii: InfoItem => handleNode(ii, isInfo = true, reserved)
          case default => throw new Exception("Unknown Node type.") 
        }
    }
    log.debug(s"Reserved New paths for ${newNodes.size} nodes")
    re
  }


  def writeWithDBIOs( odf: ODF ): Future[OmiReturn] = {
  
    val leafs = odf.getLeafs

    //Add new paths to PATHSTABLE and create new values tables for InfoItems

    val pathsToAdd: Map[Path,DBPath]= reserveNewPaths(leafs.toSet)

    log.debug( s"Adding total of  ${pathsToAdd.size} paths to DB")//: $pathsToAdd")

    log.debug("Create Insert for new paths")
    val pathAddingAction = pathsTable.add(pathsToAdd.values.toVector)
    val getAddedDBPaths =  pathAddingAction.flatMap {
      ids: Seq[Long] =>
        log.debug(s"Getting ${ids.length} paths by ids")
        pathsTable.selectByIDs(ids)
    }

    val valueTableCreations = getAddedDBPaths.flatMap {
      addedDBPaths: Seq[DBPath] =>
        log.debug(s"Got ${addedDBPaths.size} paths")
        atomic { implicit txn =>
          addedDBPaths.map {
            dbPath: DBPath => pathToDBPath += dbPath.path -> dbPath
          }
        }
        namesOfCurrentTables.flatMap {
          tableNames: Seq[String] =>
            val creations = addedDBPaths.collect {
              case DBPath(Some(id), path, true) =>
                val pathValues = new PathValues(path, id)
                valueTables += path -> pathValues
                if (tableNames.contains(pathValues.name)) None // TODO: CHECKME
                else Some(pathValues.schema.create.map { u: Unit => pathValues.name })
            }.flatten
            log.debug(s"Creating ${creations.length} new tables...")
            DBIO.sequence(creations)
        }
    }

    //Write all values to values tables and create non-existing values tables
    log.debug(s"Get leaf infoitems of odf")
    val valueWritingIOs = leafs.collect{
      case ii: InfoItem =>
        pathsTable.selectByPath(ii.path).flatMap {
          dbPaths: Seq[DBPath] =>
            log.debug(s"Got ${dbPaths.length} DBPaths for ${ii.path}")
            val ios = dbPaths.collect {
              case DBPath(Some(id), path, isInfoItem) =>
                //Find correct TableQuery
                val valuesTable = valueTables.get(path) match {
                  case Some(tq) => tq
                  case None =>
                    log.warn(s"Could not find PathValues in valueTables Map for $path.")
                    val pathValues = new PathValues(path, id)
                    valueTables += path -> pathValues
                    pathValues
                }
                //Create DBValues
                log.debug(s"Creating ${ii.values.length} TimedValues for ${ii.path}")
                val timedValues: Seq[TimedValue] = ii.values.map {
                  value => TimedValue(None, value.timestamp, value.value.toString, value.typeAttribute)
                }
                //Find InfoItems values table from all tables
                val valueInserts = tableByNameExists(valuesTable.name).flatMap {
                  //Create missing table and write values
                  case true =>
                    log.debug(s"Found values table of ${ii.path}, writing ${timedValues.length} values.")
                    valuesTable.add(timedValues)
                  case false =>
                    log.warn(s"Creating missing values table for $path")
                    valuesTable.schema.create.flatMap {
                      u: Unit => valuesTable.add(timedValues)
                    }
                }
                valueInserts.map {
                  case None => 0
                  case Some(count) => count
                  /*
                  case idsOfCreatedValues: Seq[Long] =>
                    idsOfCreatedValues.length
                    */
                }
            }
            DBIO.sequence(ios)
        }
    }
    log.debug("Aggregate writing actions...")
    val actions = valueTableCreations.flatMap {
      createdTables: Seq[String] =>
        if (createdTables.nonEmpty) log.debug(s"Created following tables:\n${createdTables.mkString(", ")}")
        DBIO.sequence(valueWritingIOs).map {
          countsOfCreatedValuesPerPath: Seq[Seq[Int]] =>
            val sum = countsOfCreatedValuesPerPath.map(_.sum).sum
            log.debug(s"Wrote total of $sum values to ${countsOfCreatedValuesPerPath.length} paths.")
            Returns.Success()
        }
    }
    log.debug("Running writing actions...")
    val future:Future[OmiReturn] = db.run(actions.transactionally)
    future.onSuccess{
      case default => 
        log.debug("Writing finished.")
    }
    future
  }

  def getNBetween(
    nodes: Iterable[Node],
    beginO: Option[Timestamp],
    endO: Option[Timestamp],
    newestO: Option[Int],
    oldestO: Option[Int]
  ): Future[Option[ODF]] = {
    if( beginO.isEmpty && endO.isEmpty &&  newestO.isEmpty && oldestO.isEmpty ){
      readLatestFromCache( 
        nodes.map{ 
          node => node.path
        }.toSeq
      )

    } else{
      val iiIOAs = pathToDBPath.single.values.filter { // FIXME: filter is not how you use a Map?
        dbPath: DBPath =>
          nodes.exists {
            node: Node =>
              dbPath.path == node.path ||
                dbPath.path.isDescendantOf(node.path)
          }
      }.collect{
        case DBPath(Some(id), path, true) =>
          val valueTable = valueTables.get(path) match{ //Is table stored?
                case Some(pathValues) => //Found table/ TableQuery
                  pathValues
                case None => //No TableQuery found for table. Create one for it
                  val pathValues = new PathValues(path, id)
                  valueTables += path -> pathValues
                  pathValues
              }

          val getNBetweenResults = tableByNameExists(valueTable.name).flatMap{
            case true =>
              valueTable.selectNBetween(beginO,endO,newestO,oldestO)
            case false =>
              valueTable.schema.create.flatMap {
                u: Unit =>
                  valueTable.selectNBetween(beginO, endO, newestO, oldestO)
              }
          }
          getNBetweenResults.map {
            tvs: Seq[TimedValue] =>
              val ii = InfoItem(
                path,
                values = tvs.map {
                  tv => Value(tv.value, tv.valueType, tv.timestamp)
                }.toVector
                )
              ii
          }
        }
      //Create OdfObjects from InfoItems and union them to one with parameter ODF
      val finalAction = DBIO.sequence(iiIOAs).map{
        case iis: Seq[InfoItem] if iis.nonEmpty =>
          Some(ImmutableODF(iis.toVector))
        case iis: Seq[InfoItem] if iis.isEmpty => None
        case _ => None
      }
      val r = db.run(finalAction.transactionally)
      r
    }
  }

  def remove(path: Path): Future[Seq[Int]] ={
    val actionsO: Option[DBIOsw[Int]] = pathToDBPath.single.get(path).collect{
      case  DBPath(Some(id), p, true) =>
          valueTables.get(path).map{
            pathValues: PathValues  =>
              pathValues.schema.drop.flatMap {
                u: Unit =>
                  atomic { implicit txn =>
                    pathToDBPath -= path
                  }
                  pathsTable.removeByIDs(Vector(id))
              }
          }
      case  DBPath(Some(id), originPath, false)  =>
          val ( objs, iis ) = pathToDBPath.single.values.filter { // FIXME: filter on a Map
            dbPath: DBPath => dbPath.path == originPath || dbPath.path.isDescendantOf(originPath)
          }.partition { dbPath: DBPath => dbPath.isInfoItem }
          val tableDrops = iis.collect{
            case DBPath(Some(_id), descendantPath, true)  =>
            valueTables.get(descendantPath).map{
              pathValues: PathValues =>
                pathValues.schema.drop
            }
          }.flatten
          val tableDropsAction = DBIO.seq( tableDrops.toSeq:_* )
          val iiPaths =  iis.map( _.path)
          val removedPaths = objs.map( _.path ) ++ iiPaths 
          val removedPathIDs = (objs ++ iis).map( _.id ).flatten 
          val pathRemoves = pathsTable.removeByIDs(removedPathIDs.toSeq)
          atomic { implicit txn =>
            pathToDBPath --= removedPaths
          }
          valueTables --= iiPaths
         Some( tableDropsAction.flatMap{
            _ =>
              pathRemoves
          })
    }.flatten 
    actionsO match{
      case Some( actions: DBIOsw[Int] ) =>
        db.run(actions.transactionally).map{ i: Int => Seq( i ) }
      case None =>
        Future.failed(
          new Exception(
            s"Could not find DBPpath for $path"
          )
        )
    }
  }

  def trimDB(): Future[Seq[Int]] = {
    val trimActions = valueTables.values.map{
      pathValues =>
        pathValues.trimToNNewestValues(settings.numLatestValues)
    }
    val deletionCounts = DBIO.sequence( trimActions )
    db.run(deletionCounts.transactionally).map(_.toVector)
  }

  /**
   * Empties all the data from the database
   * 
   */
  def clearDB(): Future[Int] = {
    val valueDropsActions = DBIO.seq(
      valueTables.values.map{
      pathValues => pathValues.schema.drop.map{
        u: Unit => 1 //XXX: Cast DriverAction to DBIOAction
      }
    }.toSeq:_*)
    db.run( valueDropsActions.andThen( pathsTable.delete ).andThen(
      pathsTable.add( Seq( DBPath(None, Path("Objects"),isInfoItem = false))).map{ seq => seq.length }
    ).transactionally )
  }

  def logPathsTables: Unit ={
    val pathsLog =pathsTable.result.map{
      dbPaths => log.debug(s"PATHSTABLE CONTAINS CURRENTLY:\n${dbPaths.mkString("\n")}") 
    }
    db.run(pathsLog)
  }
  def logValueTables(): Unit ={
    val tmp = valueTables.mapValues(vt => vt.name)
    log.debug(s"CURRENTLY VALUE TABLES IN MAP:\n${tmp.mkString("\n")}") 
  }
  def logAllTables: Unit ={
    val tables = MTable.getTables.map{
      tables =>
        val names = tables.map( _.name.name)
        log.debug(s"ALL TABLES CURRENTLY FOUND FROM DB:\n ${names.mkString(", ")}")
    }
    db.run(tables)
  }
  def dropDB(): Future[Unit] = {
    val valueTableDrops = pathsTable.selectAllInfoItems.flatMap {
      dbPaths: Seq[DBPath] =>
        DBIO.sequence(dbPaths.collect{
          case DBPath(Some(id), path, true) =>
            val pv = new PathValues(path, id)
            tableByNameExists(pv.name).flatMap {
              default: Boolean => 
                if( default ) pv.schema.drop
                else DBIO.successful(())
            }
        })
    }
    db.run( 
      valueTableDrops.flatMap{ _ => 
          atomic {implicit txn =>
            pathToDBPath.clear()
          }
          pathsTable.schema.drop
      }.transactionally 
    ).flatMap{ _ =>
      db.run(namesOfCurrentTables).map {
        tableNames: Seq[String] =>
          if (tableNames.nonEmpty) {
            val msg = s"Could not drop all tables.  Following tables found afterwards: ${tableNames.mkString(", ")}."
            log.error(msg)
            throw new Exception(msg)
          } else {
            log.warn(s"All tables dropped successfully, no tables found afterwards.")
          }
      }
    }
  }

  def readLatestFromCache( requestedOdf: ODF ): Future[Option[ImmutableODF]] ={
    readLatestFromCache(requestedOdf.getLeafPaths.toSeq)
  }
  def readLatestFromCache( leafPaths: Seq[Path]): Future[Option[ImmutableODF]] = {
    // NOTE: Might go off sync with tree or values if the request is large,
    // but it shouldn't be a big problem
    val  p2iisF: Future[Map[Path, InfoItem]] = (singleStores.hierarchyStore ? GetTree).mapTo[ImmutableODF].map(_.getInfoItems.collect{
      case ii: InfoItem if leafPaths.exists{ path: Path => path.isAncestorOf( ii.path) || path == ii.path} =>
        ii.path -> ii
    }.toMap)

    for {
      p2iis <- p2iisF
      pathToValue <- (singleStores.latestStore ? MultipleReadCommand(p2iis.keys.toSeq)).mapTo[Seq[(Path,Option[Value[Any]])]]
      objectsWithValues = Some(ImmutableODF(pathToValue.flatMap{ //why option??
        case ( path: Path, value: Value[Any]) =>
          p2iis.get(path).map{
            ii: InfoItem =>
              ii.copy(
                names = Vector.empty,
                descriptions = Set.empty,
                metaData = None,
                values = Vector( value)
              )}
      }.toVector))
    } yield objectsWithValues

    //val pathToValue = singleStores.latestStore execute LookupSensorDatas( p2iis.keys.toVector)
    //val objectsWithValues = ImmutableODF(pathToValue.flatMap{
    //  case ( path: Path, value: Value[Any]) =>
    //    p2iis.get(path).map{
    //      ii: InfoItem =>
    //        ii.copy(
    //          names = Vector.empty,
    //          descriptions = Set.empty,
    //          metaData = None,
    //          values = Vector( value)
    //        )}
    //}.toVector)

   // Some(objectsWithValues)
}

}
