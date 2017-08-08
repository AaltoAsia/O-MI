package database

import java.sql.Timestamp

import scala.util.{Try, Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
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
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes._
import types.Path


case class DBPath(
  val id: Option[Long],
  val path: Path,
  val isInfoItem: Boolean
)

case class TimedValue(
  val id: Option[Long],
  val timestamp: Timestamp,
  val value: String,
  val valueType: String
)

trait Tables extends DBBase{
  import dc.driver.api._
  
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
    def * : ProvenShape[DBPath] = (id.?, path, isInfoItem) <> (
      DBPath.tupled,
      DBPath.unapply
    )
  }
  class StoredPath extends TableQuery[PathsTable](new PathsTable(_)){
    import dc.driver.api._
    def insertQ( dbPaths: Seq[DBPath] ) = (this returning this.map{ dbp => dbp.id }) ++= dbPaths
    def get( ids: Seq[Long] ) = this.filter{ row => row.id inSet( ids ) }
    def add( dbPaths: Seq[DBPath] ) = insertQ( dbPaths )
    def remove( ids: Seq[Long] ) = get( ids ).delete
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
    def removeValuesBefore( end: Timestamp) = befor(end).delete
    def trimToNNewestValues( n: Long ) = this.sortBy( _.timestamp.desc).drop( n ).delete
    def add( values: Seq[TimedValue] ) = DBIO.sequence(values.map{ v => this += v })
    def getNBetween( 
      beginO: Option[Timestamp],
      endO: Option[Timestamp],
      newestO: Option[Int],
      oldestO: Option[Int]
    )={
      (newestO, oldestO, beginO, endO) match{
        case ( None, None, None, None) => newestC(1)
        case ( Some(_), Some(_), _, _ ) => throw new Exception("Can not query oldest and newest values at same time.")
        case ( Some(n), None, None, None) => newestC(n)
        case ( None, Some(n), None, None) => oldestC(n)
        case ( None, None, Some(begin), None) => afterC(begin)
        case ( None, None, None, Some(end)) => beforC(end)
        case ( None, None, Some(begin), Some(end)) => betweenC(begin,end)
        case ( Some(n), None, Some(begin), None) => newestAfterC(n,begin)
        case ( Some(n), None, None, Some(end)) => newestBeforC(n,end)
        case ( Some(n), None, Some(begin), Some(end)) => newestBetweenC(n,begin,end)
        case ( None, Some(n), Some(begin), None) => oldestAfterC(n,begin)
        case ( None, Some(n), None, Some(end)) => oldestBeforC(n,end)
        case ( None, Some(n), Some(begin), Some(end)) => oldestBetweenC(n,begin,end)
      }
    }
    private def newest( n: ConstColumn[Long] ) = this.sortBy(_.timestamp.desc).take(n)
    private def oldest( n: ConstColumn[Long] ) = this.sortBy(_.timestamp.asc).take(n)
    lazy val newestC = Compiled( newest _ )
    lazy val oldestC = Compiled( oldest _ )
    private def after( begin: Rep[Timestamp] ) = this.filter( _.timestamp >= begin)
    private def befor( end: Rep[Timestamp] ) = this.filter( _.timestamp <= end)
    lazy val afterC = Compiled( after _ )
    lazy val beforC = Compiled( befor _ )
    private def newestAfter( n: ConstColumn[Long], begin: Rep[Timestamp] ) = after(begin).sortBy(_.timestamp.desc).take(n) 
    private def newestBefor( n: ConstColumn[Long], end: Rep[Timestamp] ) = befor(end).sortBy(_.timestamp.desc).take(n) 
    lazy val newestAfterC = Compiled( newestAfter _ )
    lazy val newestBeforC = Compiled( newestBefor _ )
    
    private def oldestAfter( n: ConstColumn[Long], begin: Rep[Timestamp] ) = after(begin).sortBy(_.timestamp.asc).take(n) 
    private def oldestBefor( n: ConstColumn[Long], end: Rep[Timestamp] ) = befor(end).sortBy(_.timestamp.asc).take(n) 
    lazy val oldestAfterC = Compiled( oldestAfter _ )
    lazy val oldestBeforC = Compiled( oldestBefor _ )

    private def between( begin: Rep[Timestamp], end: Rep[Timestamp] ) = this.filter{ tv => tv.timestamp >= begin && tv.timestamp <= end}
    lazy val betweenC = Compiled( between _ )

    private def newestBetween( n: ConstColumn[Long], begin: Rep[Timestamp], end: Rep[Timestamp] ) = between(begin,end).sortBy(_.timestamp.desc).take(n) 
    private def oldestBetween( n: ConstColumn[Long], begin: Rep[Timestamp], end: Rep[Timestamp] ) = between(begin,end).sortBy(_.timestamp.asc).take(n) 
    lazy val newestBetweenC = Compiled( newestBetween _ )
    lazy val oldestBetweenC = Compiled( oldestBetween _ )

  }
  val valueTables: MutableMap[Path, PathValues] = new MutableHashMap()
  val pathsTable = new StoredPath()
  def namesOfCurrentTables = MTable.getTables.map{ mt => mt.map{ t => t.name.name}}
  def tableByNameExists( name: String ) = namesOfCurrentTables.map{ names => names.contains( name )} 
}

trait NewSimplifiedDatabase extends Tables with DB with TrimableDB{
  import dc.driver.api._

  protected val settings : OmiConfigExtension
  protected val log = LoggerFactory.getLogger("SimplifiedDB")//FIXME: Better name
  val pathToDBPath: MutableMap[Path, DBPath] = new MutableHashMap()

  def initialize(): Unit = {
    val findTables = db.run(namesOfCurrentTables)
    val createMissingTables = findTables.flatMap{ 
      case tableNames: Seq[String]  =>
        val queries = if( tableNames.contains( "PATHSTABLE" ) ){
          log.debug(s"Foound following tables:\n${tableNames.mkString("\n")}")
          //Found needed table, check for value tables
          val infoItemDBPaths = pathsTable.filter{
            dbPath => dbPath.isInfoItem
          }.result

          val valueTablesCreation = infoItemDBPaths.flatMap{
            dbPaths =>
              val actions = dbPaths.collect{
                case DBPath( Some(id), path, true) => 
                  val pathValues = new PathValues(path, id)
                  valueTables += path -> pathValues
                  //Check if table exists, is not create it
                  if( !tableNames.contains(pathValues.name) ){
                    Some( pathValues.schema.create.map{
                      case u: Unit =>
                        log.debug( s"Creatied values table ${pathValues.name} for $path")
                        pathValues.name
                    } )
                  } else None
              }.flatten
              //Action for creating missig Paths
              val countOfTables = actions.length
              log.info( s"Found total of ${dbPaths.length} InfoItems. Creating missing tables for $countOfTables." )
              DBIO.sequence( actions )
          }
          valueTablesCreation
        } else if( tableNames.nonEmpty ){
          val msg = s"Database contains unknown tables while PATHSTABLE could not be found."
          log.error( msg)
          throw new Exception( msg ) 
        } else{
          log.info( s"No tables found. Creating PATHSTABLE.")
          val queries = pathsTable.schema.create.map{ 
            case u: Unit => 
              log.debug("Created PATHSTABLE.")
            Seq("PATHSTABLE")
          }
          queries
        }
        db.run(queries.transactionally)
    }
    val populateMap = createMissingTables.flatMap{
      case tablesCreated: Seq[String] =>
        log.debug( s"Created following tables:\n${tablesCreated.mkString("\n")}")
        val actions = if( tablesCreated.contains("PATHSTABLE")){
          log.debug(s"Adding Objects to PATHSTABLE")
          val pRoot = Path("Objects")
          val dbP = DBPath(None, pRoot,false)
          pathsTable.add( Seq(dbP) ).map{
            case ids: Seq[Long] =>
              log.debug(s"Adding Objects to pathTODBPath Map.")
              pathToDBPath ++= ids.map{
                case id: Long => 
                  log.debug(s"Objects id is $id")
                  pRoot -> dbP.copy(id= Some(id))
              }
          }
        } else {
          log.debug(s"Getting current paths from PATHSTABLE for pathToDBPath.")
          pathsTable.result.map{ 
            dbPaths => 
              log.debug(s"Found following from PATHSTABLE:\n${dbPaths.mkString("\n")}")
              pathToDBPath ++= dbPaths.map{ dbPath => dbPath.path -> dbPath }
          }
        }
        db.run(actions.transactionally)
    }
    val initialization = populateMap
      initialization.onComplete{
      case Success( path2DBPath ) => 
        log.info( s"Initialized DB successfully. ${path2DBPath.length} paths in DB." )
        logAllTables.flatMap{
          case u: Unit =>
            logPathsTable
        }.map{
          case u: Unit =>
            logValueTables
        }
      case Failure( t ) => 
        log.error( "DB initialization failed.", t )
        logAllTables.flatMap{
          case u: Unit =>
            logPathsTable
        }.map{
          case u: Unit =>
            logValueTables
        }
    }
    Await.result( initialization, 1 minutes)
  }

  def writeMany(data: Seq[OdfInfoItem]): Future[OmiReturn] = {
    val odf : OdfObjects= data.foldLeft(OdfObjects()){
      case (objs: OdfObjects, ii: OdfInfoItem) =>
        objs.union(ii.createAncestors)
    }
    writeWithDBIOs(odf)
  }

  def writeWithDBIOs( odf: OdfObjects ): Future[OmiReturn] = {
  
    val leafs =  getLeafs(odf)

    //Add new paths to PATHSTABLE and create new values tables for InfoItems
    val pathsToAdd = leafs.map{
      case node: OdfNode => 
      (node, pathToDBPath.get(node.path))
    }.collect{
      case (obj: OdfObjects, None) => 
        log.warn("No \"Objects\" path found! Adding.")
        Seq(DBPath( None, obj.path, false))

      case (obj: OdfObject, None) =>
        obj.path.ancestorsAndSelf.filter{
          p: Path => !pathToDBPath.keys.contains( p )
        }.map{
          case ancestor: Path => DBPath( None, ancestor, false)
        }

      case (ii: OdfInfoItem, None) =>
        ii.path.ancestors.filter{
          p: Path => !pathToDBPath.keys.contains( p )
        }.map{
          case ancestor: Path => DBPath( None, ancestor, false)
        } ++ Seq( DBPath( None, ii.path, isInfoItem = true ))

        }.flatten.distinct.filterNot{ dbPath => pathToDBPath.contains(dbPath) }
    log.info( s"Adding total of  ${pathsToAdd.length} paths to DB.") 
    val pathAddingAction = pathsTable.add(pathsToAdd)
    val getAddedDBPaths =  pathAddingAction.flatMap{
      ids: Seq[Long] => 
        pathsTable.get(ids).result
    }
    val valueTableCreations = getAddedDBPaths.flatMap{
      addedDBPaths: Seq[DBPath] => 
        pathToDBPath ++= addedDBPaths.map{ 
          dbPath => dbPath.path ->dbPath
        }
        namesOfCurrentTables.flatMap{
          case tableNames: Seq[String] =>
          val creations = addedDBPaths.collect{
            case DBPath(Some(id), path, true) => 
              val pathValues = new PathValues(path, id)
              valueTables += path -> pathValues
              if( tableNames.contains(pathValues.name) ) None
              else Some(pathValues.schema.create.map{ u: Unit => pathValues.name} ) 
          }.flatten
          DBIO.sequence(creations)
        }
    }

    //Write all values to values tables and create non-existing values tables
    val valueWritingIOs = leafs.collect{
      case ii: OdfInfoItem =>
        pathsTable.filter{ dbPath => dbPath.path === ii.path }.result.flatMap{
          case dbPaths: Seq[DBPath] => 
            val ios = dbPaths.collect{
              case DBPath(Some(id), path, isInfoItem ) => 
                //Find correct TableQuery
                val valuesTable = valueTables.get(path) match{
                  case Some( tq ) => tq
                  case None =>
                    log.warn(s"Could not find PathValues in valueTables Map for $path.")
                    val pathValues = new PathValues(path, id)
                    valueTables += path -> pathValues
                    pathValues
                }
                //Create DBValues
                val timedValues: Seq[TimedValue] = ii.values.map{
                  value => TimedValue( None, value.timestamp, value.value.toString, value.typeValue )
                }
                //Find InfoItems values table from all tables
                val valueInserts = tableByNameExists( valuesTable.name).flatMap{//Create missing table and write values
                  case true =>
                    valuesTable.add(timedValues)
                  case false =>
                    log.warn( s"Creating missing values table for $path") 
                    valuesTable.schema.create.flatMap{
                      case u: Unit => valuesTable.add(timedValues)
                    }
                }
                valueInserts.map{
                  case idsOfCreatedValues: Seq[Long] =>
                    idsOfCreatedValues.length
                }
            }
            DBIO.sequence(ios)
        }
    }
    val actions = valueTableCreations.flatMap{
      case createdTables: Seq[String] =>
        if( createdTables.nonEmpty) log.info(s"Created following tables:\n${createdTables.mkString(", ")}")
        DBIO.sequence( valueWritingIOs ).map{
          case countsOfCreatedValuesPerPath: Seq[Seq[Int]] =>  
            val sum = countsOfCreatedValuesPerPath.map( _.sum).sum
            log.info( s"Writed total of $sum values to ${countsOfCreatedValuesPerPath.length} paths." ) 
            Returns.Success()
        }
    }
    val r = db.run(actions.transactionally)
    /*r.onSuccess{
      case a  =>
        logAllTables.flatMap{
          case u: Unit =>
            logPathsTable
        }.map{
          case u: Unit =>
            logValueTables
        }
    }*/
    r.recover{
      
      case e: Exception =>
      /*  logAllTables.flatMap{
          case u: Unit =>
            logPathsTable
        }.map{
          case u: Unit =>
            logValueTables
        }
        log.error(s"Caught $e when writing", e )*/
        Returns.InternalError(e)
    }
  }

  def getNBetween(
    nodes: Iterable[OdfNode],
    beginO: Option[Timestamp],
    endO: Option[Timestamp],
    newestO: Option[Int],
    oldestO: Option[Int]
  ): Future[Option[OdfObjects]] = {
    val iiIOAs =pathToDBPath.values.filter{
      dbPath => 
        nodes.exists{ 
          node => 
            dbPath.path == node.path ||
            dbPath.path.isDescendantOf(node.path) 
        }
    }.collect{
      case DBPath(Some(id), path, true) =>
        log.info( s"Getting values for $path ..." )
        val valueTable = valueTables.get(path) match{ //Is table stored?
              case Some(pathValues) => //Found table/ TableQuery
                pathValues
              case None =>//No TableQuery found for table. Create one for it
                val pathValues = new PathValues(path, id)
                valueTables += path -> pathValues
                pathValues
            }
        
        val getNBetweenResults = tableByNameExists(valueTable.name).flatMap{
          case true =>
            valueTable.getNBetween(beginO,endO,newestO,oldestO).result
          case false =>
            valueTable.schema.create.flatMap{
              case u: Unit =>
                valueTable.getNBetween(beginO,endO,newestO,oldestO).result
            }
        }
        getNBetweenResults.filter{
          case tvs: Seq[TimedValue] =>
          tvs.nonEmpty 
        }.map{
          case tvs: Seq[TimedValue] =>
            val ii = OdfInfoItem(
              path,
              values = tvs.map{
                tv => OdfValue( tv.value, tv.valueType, tv.timestamp)
              }
              )
          ii.createAncestors 
        }
      } 
    //Create OdfObjects from InfoItems and union them to one with parameter ODF
    val finalAction = DBIO.sequence(iiIOAs).map{
      case objs: Seq[OdfObjects] if objs.nonEmpty =>
        val r = objs.fold(OdfObjects()){
          case (odf: OdfObjects, obj: OdfObjects) =>
            odf.union(obj)
        }
        log.info( r.asXML.toString )
        Some(r)
      case objs: Seq[OdfObjects] if objs.isEmpty => None
    }
    val r = db.run(finalAction.transactionally)
    r.onComplete{
      case Success(_)  =>
        /*
        logAllTables.flatMap{
          case u: Unit =>
            logPathsTable
        }.map{
          case u: Unit =>
            logValueTables
        }*/
      case Failure(e) =>
        /*
        logAllTables.flatMap{
          case u: Unit =>
            logPathsTable
        }.map{
          case u: Unit =>
            logValueTables
        }*/
        log.error(s"Caught $e when reading from DB", e)
    }
    r
  }

  def remove(path: Path): Future[Seq[Int]] ={
    val actions = pathToDBPath.get(path) match{
      case Some( DBPath(Some(id), p, true) ) =>
          valueTables.get(path) match{
            case Some( pathValues ) =>
              pathValues.schema.drop.flatMap{
                case u: Unit =>
                  pathToDBPath -= path
                  pathsTable.remove( Vector(id) )
              }
          }
      case Some( DBPath(Some(id), originPath, false) ) =>
          val ( objs, iis ) = pathToDBPath.values.filter{
            dbPath => dbPath == originPath || dbPath.path.isDescendantOf(originPath)
          }.partition{ dbPath => dbPath.isInfoItem }
          val tableDrops = iis.map{
            case DBPath(Some(id), descendantPath, true)  =>
            valueTables.get(descendantPath) match{
              case Some( pathValues ) =>
                pathValues.schema.drop
            }
          }
          val tableDropsAction = DBIO.seq( tableDrops.toSeq:_* )
          val iiPaths =  iis.map( _.path)
          val removedPaths = objs.map( _.path ) ++ iiPaths 
          val removedPathIDs = (objs ++ iis).map( _.id ).flatten 
          val pathRemoves = pathsTable.remove(removedPathIDs.toSeq)
          pathToDBPath --= removedPaths
          valueTables --= iiPaths
          tableDropsAction.flatMap{
            u: Unit =>
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
    val deletionCounts = DBIO.fold( trimActions, Vector[Int]() ){ 
      case ( seq: Vector[Int], deleted: Int ) =>
        seq ++ Vector( deleted )
    }
    db.run(deletionCounts.transactionally).mapTo[Vector[Int]]
  }

  /**
   * Empties all the data from the database
   * 
   */
  def clearDB(): Future[Int] = {
    val valueDropsActions = DBIO.seq(valueTables.values.map{
      pathValues => pathValues.schema.drop
    }:_*)
    db.run( valueDropsActions.andThen( pathsTable.delete ).andThen(
      pathsTable.add( Seq( DBPath(None, Path("Objects"),false))).map{ seq => seq.length }
    ).transactionally )
  }

  def logPathsTable ={
    val pathsLog =pathsTable.result.map{
      dbPaths => log.debug(s"PATHSTABLE CONTAINS CURRENTLY:\n${dbPaths.mkString("\n")}") 
    }
    db.run(pathsLog)
  }
  def logValueTables ={
    val tmp = valueTables.mapValues{ case vt => vt.name }
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
    val valueTableDrops = pathsTable.filter(_.isInfoItem).result.flatMap{
      case dbPaths: Seq[DBPath] =>
        DBIO.sequence(dbPaths.map{
          case DBPath( Some(id), path, true ) =>
            val pv = new PathValues(path, id)
            tableByNameExists( pv.name).flatMap{
              case true => pv.schema.drop
              case false => DBIO.successful()
            }
        })
    }
    db.run( 
      valueTableDrops.flatMap{
        case u: Seq[Unit] => 
          pathsTable.schema.drop
      }.transactionally 
    ).flatMap{
      case u: Unit =>
      db.run(namesOfCurrentTables).map{
        case tableNames: Seq[String] =>
          if( tableNames.nonEmpty ){
            val msg = s"Could not drop all tables.  Following tables found afterwards: ${tableNames.mkString(", ")}."
            log.error( msg )
            throw new Exception( msg )
          } else {
            log.warn( s"All tables dropped successfullly, no tables found afterwards.")
          }
      }
    }
  }
}
