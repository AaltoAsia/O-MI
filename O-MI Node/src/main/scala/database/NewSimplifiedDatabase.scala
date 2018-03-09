package database

import java.sql.Timestamp

import scala.util.{Try, Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.stm._
import scala.collection.mutable.{ Map => MutableMap, HashMap => MutableHashMap}
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


case class DBPath(
                   id: Option[Long],
  path: Path,
  isInfoItem: Boolean
)

case class TimedValue(
                       id: Option[Long],
  timestamp: Timestamp,
  value: String,
  valueType: String
)

trait Tables extends DBBase{
  import dc.driver.api._
  
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]
  type DBIOwo[Result] = DBIOAction[Result, NoStream, Effect.Write]
  type ReadWrite = Effect with Effect.Write with Effect.Read with Effect.Transactional
  type DBIOrw[Result] = DBIOAction[Result, NoStream, ReadWrite]
  implicit lazy val pathColumnType = MappedColumnType.base[Path, String](
    { _.toString }, // Path to String
    { Path(_) }     // String to Path
    )

  class PathsTable( tag: Tag ) extends Table[DBPath](tag, "PATHSTABLE"){
    import dc.driver.api._
    def id: Rep[Long] = column[Long]("PATHID", O.PrimaryKey, O.AutoInc)
    def path: Rep[Path] = column[Path]("PATH")  
    def isInfoItem: Rep[Boolean] = column[Boolean]( "ISINFOITEM" )
    def pathIndex: Index = index( "PATHINDEX",path, unique = true)
    def infoItemIndex: Index = index( "INFOITEMINDEX",isInfoItem, unique = false)
    def * : ProvenShape[DBPath] = (id.?, path, isInfoItem) <> (
      DBPath.tupled,
      DBPath.unapply
    )
  }
  class StoredPath extends TableQuery[PathsTable](new PathsTable(_)){
    import dc.driver.api._
    def getByPath( path: Path) = getByPathCQ(path).result
    def getByID( id: Long ) = getByIDCQ(id).result
    def getByIDs( ids: Seq[Long] ) = getByIDsQ(ids).result
    def getByPaths( paths: Seq[Path] ) = getByPathsQ( paths ).result

    def add( dbPaths: Seq[DBPath] ) = insertQ( dbPaths.distinct )
    def removeByIDs( ids: Seq[Long] ) = getByIDsQ( ids ).delete
    def removeByPaths( paths: Seq[Path] ) = getByPathsQ( paths ).delete
    def getInfoItems = infoItemsCQ.result

    protected  lazy val infoItemsCQ = Compiled( infoItemsQ )
    protected  def infoItemsQ = this.filter{ dbp => dbp.isInfoItem }

    protected def getByIDsQ( ids: Seq[Long] ) = this.filter{ row => row.id inSet( ids ) }
    protected def getByPathsQ( paths: Seq[Path] ) = this.filter{ row => row.path inSet( paths ) }
    protected lazy val getByIDCQ = Compiled( getByIDQ _ )
    protected def getByIDQ( id: Rep[Long] ) = this.filter{ row => row.id === id  }

    protected lazy val getByPathCQ = Compiled( getByPathQ _ )
    protected def getByPathQ( path: Rep[Path] ) = this.filter{ row => row.path === path  }
    protected def insertQ( dbPaths: Seq[DBPath] ) = (this returning this.map{ dbp => dbp.id }) ++= dbPaths.distinct
  }

  class TimedValuesTable(val path: Path, val pathID:Long, tag: Tag) extends Table[TimedValue](
    tag, s"PATH_${pathID.toString}"){

      import dc.driver.api._
      def id: Rep[Long] = column[Long]("VALUEID", O.PrimaryKey, O.AutoInc)
      def timestamp: Rep[Timestamp] = column[Timestamp]( "TIME", O.SqlType("TIMESTAMP(3)"))
      def value: Rep[String] = column[String]("VALUE")
      def valueType: Rep[String] = column[String]("VALUETYPE")
      def timeIndex: Index = index(s"PATH_${pathID.toString}_TIMEINDEX",timestamp, unique = false)
      def * : ProvenShape[TimedValue] = (id.?, timestamp, value, valueType) <> (
        TimedValue.tupled,
        TimedValue.unapply
      )
    }
  class PathValues( val path: Path, val pathID: Long ) extends TableQuery[TimedValuesTable]({tag: Tag => new TimedValuesTable(path, pathID,tag)}){
    def name = s"PATH_${pathID.toString}"
    import dc.driver.api._
    def removeValuesBefore( end: Timestamp) = before(end).delete
    def trimToNNewestValues( n: Long ) = selectAllExpectNNewestValuesCQ( n ).result.flatMap{
      values: Seq[TimedValue] =>
        val ids = values.map(_.id).flatten
        this.filter(_.id inSet(ids)).delete
    }
    def add( values: Seq[TimedValue] ) = this ++= values.distinct
    def getNBetween(
      beginO: Option[Timestamp],
      endO: Option[Timestamp],
      newestO: Option[Int],
      oldestO: Option[Int]
    )  ={
      val compiledQuery = (newestO, oldestO, beginO, endO) match{
        case ( None, None, None, None) => newestC(1)
        case ( Some(_), Some(_), _, _ ) => throw new Exception("Can not query oldest and newest values at same time.")
        case ( Some(n), None, None, None) => newestC(n)
        case ( None, Some(n), None, None) => oldestC(n)
        case ( None, None, Some(begin), None) => afterC(begin)
        case ( None, None, None, Some(end)) => beforeC(end)
        case ( None, None, Some(begin), Some(end)) => betweenC(begin,end)
        case ( Some(n), None, Some(begin), None) => newestAfterC(n,begin)
        case ( Some(n), None, None, Some(end)) => newestBeforeC(n,end)
        case ( Some(n), None, Some(begin), Some(end)) => newestBetweenC(n,begin,end)
        case ( None, Some(n), Some(begin), None) => oldestAfterC(n,begin)
        case ( None, Some(n), None, Some(end)) => oldestBeforeC(n,end)
        case ( None, Some(n), Some(begin), Some(end)) => oldestBetweenC(n,begin,end)
      }
      compiledQuery.result
    }
    protected def newest( n: ConstColumn[Long] ) = this.sortBy(_.timestamp.desc).take(n)
    protected def oldest( n: ConstColumn[Long] ) = this.sortBy(_.timestamp.asc).take(n)
    protected lazy val newestC = Compiled( newest _ )
    protected lazy val oldestC = Compiled( oldest _ )
    protected def after( begin: Rep[Timestamp] ) = this.filter( _.timestamp >= begin)
    protected def before(end: Rep[Timestamp] ) = this.filter( _.timestamp <= end)
    protected lazy val afterC = Compiled( after _ )
    protected lazy val beforeC = Compiled( before _ )
    protected def newestAfter( n: ConstColumn[Long], begin: Rep[Timestamp] ) = after(begin).sortBy(_.timestamp.desc).take(n)
    protected def newestBefore(n: ConstColumn[Long], end: Rep[Timestamp] ) = before(end).sortBy(_.timestamp.desc).take(n)
    protected lazy val newestAfterC = Compiled( newestAfter _ )
    protected lazy val newestBeforeC = Compiled( newestBefore _ )

    protected def oldestAfter( n: ConstColumn[Long], begin: Rep[Timestamp] ) = after(begin).sortBy(_.timestamp.asc).take(n)
    protected def oldestBefore(n: ConstColumn[Long], end: Rep[Timestamp] ) = before(end).sortBy(_.timestamp.asc).take(n)
    protected lazy val oldestAfterC = Compiled( oldestAfter _ )
    protected lazy val oldestBeforeC = Compiled( oldestBefore _ )

    protected def between( begin: Rep[Timestamp], end: Rep[Timestamp] ) = this.filter{ tv => tv.timestamp >= begin && tv.timestamp <= end}
    protected lazy val betweenC = Compiled( between _ )

    protected def newestBetween( n: ConstColumn[Long], begin: Rep[Timestamp], end: Rep[Timestamp] ) = between(begin,end).sortBy(_.timestamp.desc).take(n) 
    protected def oldestBetween( n: ConstColumn[Long], begin: Rep[Timestamp], end: Rep[Timestamp] ) = between(begin,end).sortBy(_.timestamp.asc).take(n) 
    protected lazy val newestBetweenC = Compiled( newestBetween _ )
    protected lazy val oldestBetweenC = Compiled( oldestBetween _ )

    protected def selectAllExpectNNewestValuesQ( n: ConstColumn[Long] ) = this.sortBy( _.timestamp.desc).drop( n )
    protected lazy val selectAllExpectNNewestValuesCQ = Compiled( selectAllExpectNNewestValuesQ _ )
  }
  val valueTables: MutableMap[Path, PathValues] = new MutableHashMap()
  val pathsTable = new StoredPath()
  def namesOfCurrentTables = MTable.getTables.map{ 
    mts => 
    mts.map{ 
      mt => mt.name.name
    }
  }
  def tableByNameExists( name: String ) = namesOfCurrentTables.map {
    names: Seq[String] =>
      names.contains(name)
  } 
}

trait NewSimplifiedDatabase extends Tables with DB with TrimmableDB{
  import dc.driver.api._

  protected val settings : OmiConfigExtension
  protected val singleStores : SingleStores
  protected val log = LoggerFactory.getLogger("SimplifiedDB")//FIXME: Better name
  val pathToDBPath: TMap[Path, DBPath] = TMap()

  def initialize(): Unit = {
    val findTables = db.run(namesOfCurrentTables)
    val createMissingTables = findTables.flatMap {
      tableNames: Seq[String] =>
        val queries = if (tableNames.contains("PATHSTABLE")) {
          //Found needed table, check for value tables
          val infoItemDBPaths = pathsTable.getInfoItems

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
    writeWithDBIOs(ImmutableODF(data))
  }

  private def returnOrReserve(path: Path, isInfoItem: Boolean): DBPath = {
    val ret = atomic { implicit txn =>
      pathToDBPath.get(path) match {
        case Some(dbpath @ DBPath(Some(_),_,_)) => dbpath // Exists
        case Some(         DBPath(None   ,_,_)) => retry  // Reserved
        case None => // Not found -> reserve
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
          Map(path -> dbpath) ++ (
            path.ancestors
              .filterNot(reserved contains _)
              .map(returnOrReserve(_, isInfoItem = false))
              .collect{
                case dbpath @ DBPath(None, _path, false) =>
                  _path -> dbpath
              }
              .toMap
          )
        case _ => Map() // noop
      })
    }

    nodes.foldLeft(Map[Path,DBPath]()){ case (reserved: Map[Path,DBPath], node: Node) =>
      node match {
        case obj: Objects => handleNode(obj, isInfo = false, reserved)
        case obj: Object => handleNode(obj, isInfo = false, reserved)
        case ii: InfoItem => handleNode(ii, isInfo = true, reserved)
      }
    }
  }


  def writeWithDBIOs( odf: ODF ): Future[OmiReturn] = {
  
    val leafs = odf.getLeafs

    //Add new paths to PATHSTABLE and create new values tables for InfoItems

    val pathsToAdd = reserveNewPaths(leafs.toSet)

    //log.debug( s"Adding total of  ${pathsToAdd.length} paths to DB")//: $pathsToAdd")

    val pathAddingAction = pathsTable.add(pathsToAdd.values.toVector)
    val getAddedDBPaths =  pathAddingAction.flatMap {
      ids: Seq[Long] =>
        pathsTable.getByIDs(ids)
    }

    val valueTableCreations = getAddedDBPaths.flatMap {
      addedDBPaths: Seq[DBPath] =>
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
            DBIO.sequence(creations)
        }
    }

    //Write all values to values tables and create non-existing values tables
    val valueWritingIOs = leafs.collect{
      case ii: InfoItem =>
        pathsTable.getByPath(ii.path).flatMap {
          dbPaths: Seq[DBPath] =>
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
                val timedValues: Seq[TimedValue] = ii.values.map {
                  value => TimedValue(None, value.timestamp, value.value.toString, value.typeAttribute)
                }
                //Find InfoItems values table from all tables
                val valueInserts = tableByNameExists(valuesTable.name).flatMap {
                  //Create missing table and write values
                  case true =>
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
    db.run(actions.transactionally)
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
        nodes.flatMap{ 
          node => node.path.getAncestorsAndSelf
        }.toSet.toSeq
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
              valueTable.getNBetween(beginO,endO,newestO,oldestO)
            case false =>
              valueTable.schema.create.flatMap {
                u: Unit =>
                  valueTable.getNBetween(beginO, endO, newestO, oldestO)
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
        case objs: Seq[InfoItem] if objs.isEmpty => None
      }
      val r = db.run(finalAction.transactionally)
      r
    }
  }

  def remove(path: Path): Future[Seq[Int]] ={
    val actions = pathToDBPath.single.get(path) match{
      case Some( DBPath(Some(id), p, true) ) =>
          valueTables.get(path) match{
            case Some( pathValues ) =>
              pathValues.schema.drop.flatMap {
                u: Unit =>
                  atomic { implicit txn =>
                    pathToDBPath -= path
                  }
                  pathsTable.removeByIDs(Vector(id))
              }
          }
      case Some( DBPath(Some(id), originPath, false) ) =>
          val ( objs, iis ) = pathToDBPath.single.values.filter { // FIXME: filter on a Map
            dbPath: DBPath => dbPath.path == originPath || dbPath.path.isDescendantOf(originPath)
          }.partition { dbPath: DBPath => dbPath.isInfoItem }
          val tableDrops = iis.map{
            case DBPath(Some(_id), descendantPath, true)  =>
            valueTables.get(descendantPath) match{
              case Some( pathValues ) =>
                pathValues.schema.drop
            }
          }
          val tableDropsAction = DBIO.seq( tableDrops.toSeq:_* )
          val iiPaths =  iis.map( _.path)
          val removedPaths = objs.map( _.path ) ++ iiPaths 
          val removedPathIDs = (objs ++ iis).map( _.id ).flatten 
          val pathRemoves = pathsTable.removeByIDs(removedPathIDs.toSeq)
          atomic { implicit txn =>
            pathToDBPath --= removedPaths
          }
          valueTables --= iiPaths
          tableDropsAction.flatMap{
            _ =>
              pathRemoves
          }
    }
    db.run(actions.transactionally).map{ i: Int => Seq( i ) }
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

  def logPathsTable ={
    val pathsLog =pathsTable.result.map{
      dbPaths => log.debug(s"PATHSTABLE CONTAINS CURRENTLY:\n${dbPaths.mkString("\n")}") 
    }
    db.run(pathsLog)
  }
  def logValueTables() ={
    val tmp = valueTables.mapValues(vt => vt.name)
    log.debug(s"CURRENTLY VALUE TABLES IN MAP:\n${tmp.mkString("\n")}") 
  }
  def logAllTables ={
    val tables = MTable.getTables.map{
      tables =>
        val names = tables.map( _.name.name)
        log.debug(s"ALL TABLES CURRENTLY FOUND FROM DB:\n ${names.mkString(", ")}")
    }
    db.run(tables)
  }
  def dropDB(): Future[Unit] = {
    val valueTableDrops = pathsTable.getInfoItems.flatMap {
      dbPaths: Seq[DBPath] =>
        DBIO.sequence(dbPaths.map {
          case DBPath(Some(id), path, true) =>
            val pv = new PathValues(path, id)
            tableByNameExists(pv.name).flatMap {
              case true => pv.schema.drop
              case false => DBIO.successful()
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

  def readLatestFromCache( requestedOdf: ODF ): Future[Option[ImmutableODF]] = { 
    readLatestFromCache(requestedOdf.getPaths)
  }
  def readLatestFromCache( requestedPaths: Seq[Path] ): Future[Option[ImmutableODF]] = Future{
    // NOTE: Might go off sync with tree or values if the request is large,
    // but it shouldn't be a big problem
    val iiPaths = (singleStores.hierarchyStore execute GetTree()).getInfoItems.collect{
      case ii: InfoItem if requestedPaths.exists{ path: Path => path.isAncestorOf( ii.path) || path == ii.path} =>
        ii.path
    }

    val pathToValue = singleStores.latestStore execute LookupSensorDatas( iiPaths.toVector)
    val objectsWithValues = ImmutableODF(pathToValue.map{
      case ( path: Path, value: Value[Any]) => InfoItem( path, values = Vector( value))
    })

    Some(objectsWithValues)
}

}
