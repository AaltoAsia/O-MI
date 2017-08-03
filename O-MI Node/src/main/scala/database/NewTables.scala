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
    def insertQ( dbPaths: Seq[DBPath] ) = (this returning this.map{ dbp => dbp }) ++= dbPaths
    def add( dbPaths: Seq[DBPath] ) = insertQ( dbPaths )
    def remove( ids: Seq[Long] ) = pathsTable.filter{ row => row.id inSet( ids ) }.delete
  }

  class TimedValuesTable(val path: Path, val pathID:Long, tag: Tag) extends Table[TimedValue](
    tag, pathID.toString){

      import dc.driver.api._
      def id: Rep[Long] = column[Long]("VALUEID", O.PrimaryKey, O.AutoInc)
      def timestamp: Rep[Timestamp] = column[Timestamp]( "TIME", O.SqlType("TIMESTAMP(3)"))
      def value: Rep[String] = column[String]("VALUE")
      def valueType: Rep[String] = column[String]("VALUETYPE")
      def timeIndex: Index = index( "TIMEINDEX",timestamp, unique = false)
      def * : ProvenShape[TimedValue] = (id.?, timestamp, value, valueType) <> (
        TimedValue.tupled,
        TimedValue.unapply
      )
    }
  class PathValues( val path: Path, val pathID: Long ) extends TableQuery[TimedValuesTable]({tag: Tag => new TimedValuesTable(path, pathID,tag)}){
    import dc.driver.api._
    def removeValuesBefore( end: Timestamp) = befor(end).delete
    def trimToNNewestValues( n: Long ) = this.sortBy( _.timestamp.desc).drop( n ).delete
    def add( values: Seq[TimedValue] ) = this ++= values
    def getNBetween( 
      beginO: Option[Timestamp],
      endO: Option[Timestamp],
      newestO: Option[Int],
      oldestO: Option[Int]
    )={
      (newestO, oldestO, beginO, endO) match{
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
  val valueTables: MutableMap[Path, PathValues]
  val pathsTable = new StoredPath()
  def namesOfCurrentTables = MTable.getTables.map{ mt => mt.map{ t => t.name.name}}
  def tableByNameExists( name: String ) = namesOfCurrentTables.map{ names => names.contains( name )} 
}

trait NewSimplifiedDatabase extends Tables with DB{
  import dc.driver.api._

  protected val log = LoggerFactory.getLogger("SimplifiedDB")//FIXME: Better name
  val pathToDBPath: MutableMap[Path, DBPath]

  def initialize(): Unit = this.synchronized {
    val findTables = namesOfCurrentTables.flatMap{ 
      tableNames =>
        if( tableNames.contains( "PATHSTABLE" ) ){
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
                  if( !tableNames.contains(id.toString) ) Some( pathValues.schema.create )
                  else None
              }.flatten
              //Action for creating missig Paths
              val countOfTables = actions.length
              log.info( s"Found total of ${dbPaths.length} InfoItems. Creating missing tables for $countOfTables." )
              DBIO.seq( actions:_* ).map{
                case u: Unit =>  countOfTables
              }
          }
          valueTablesCreation
        } else if( tableNames.nonEmpty ){
          val msg = s"Database contains unknown tables while PATHSTABLE could not be found."
          log.error( msg)
          DBIO.failed(throw new Exception( msg ) )
        } else{
          log.info( s"Could not find PATHSTABLE. Creating new and adding Objects.")
          val queries = pathsTable.schema.create.flatMap{
            case u: Unit => 
              pathsTable.add( Seq( DBPath(None, Path("Objects"),false))).map{ seq => seq.length }
          }
          queries
        }
    }
    val populateMap = findTables.flatMap{
      a: Int =>
        pathsTable.result.map{ 
          dbPaths => 
            pathToDBPath ++= dbPaths.map{ dbPath => dbPath.path -> dbPath }
        }
    }
    db.run(populateMap).onComplete{
      case Success( path2DBPath: Map[Path,DBPath]) => 
        log.info( s"Initialized DB successfully. ${path2DBPath.length} paths in DB." )
      case Failure( t ) => 
        log.error( "DB initialization failed.", t )
    }
  }

  def write( odf: OdfObjects ): Future[Option[OmiReturn]] = {
  
    val leafs =  getLeafs(odf)

    //Add new paths to PATHSTABLE and create new values tables for InfoItems
    val pathsToAdd = leafs.map{
      case node: OdfNode => 
      (node, pathToDBPath.get(node.path))
    }.collect{
      case (obj: OdfObjects, None) => 
        //log.warning("No "Objects" path found! Adding.")
        Seq(DBPath( None, obj.path, false))

      case (obj: OdfObject, None) =>
        obj.path.ancestorsAndSelf.filter{
          p => !pathToDBPath.keys.contains( p )
        }.map{
          case ancestor: Path => DBPath( None, ancestor, false)
        }

      case (ii: OdfInfoItem, None) =>
        ii.path.ancestors.filter{
          p => !pathToDBPath.keys.contains( p )
        }.map{
          case ancestor: Path => DBPath( None, ancestor, false)
        } ++ Seq( DBPath( None, ii.path, isInfoItem = true ))

    }.flatten
    val pathAddingAction = pathsTable.add(pathsToAdd)
    val valueTableCreations = pathAddingAction.flatMap{
      addedDBPaths => 
        pathToDBPath ++= addedDBPaths.map{ 
          dbPath => dbPath.path ->dbPath
        }
        DBIO.seq(
          addedDBPaths.collect{
            case DBPath(Some(id), path, true) => 
              val pathValues = new PathValues(path, id)
              valueTables += path -> pathValues
              pathValues.schema.create
          }:_*
          )
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
                    val pathValues = new PathValues(path, id)
                    valueTables += path -> pathValues
                    pathValues
                }
                //Create DBValues
                val timedValues: Seq[TimedValue] = ii.values.map{
                  value => TimedValue( None, value.timestamp, value.value.toString, value.typeValue )
                }
                //Find InfoItems values table from all tables
                tableByNameExists( id.toString).asTry.flatMap{//Create missing table and write values
                  case Success( true ) =>
                    DBIO.seq(valuesTable ++= timedValues)
                  case Success( false ) =>
                    DBIO.seq(valuesTable.schema.create, valuesTable ++= timedValues)
                  case Failure( t ) => 
                    throw new Exception( "Could not find current tables", t)
                }
            }
            DBIO.seq(ios: _*)
        }
    }
    val actions = valueTableCreations.asTry.flatMap{
      case Success( u: Unit ) =>
        DBIO.seq( valueWritingIOs:_* ).asTry.map{
          case Success( u: Unit ) =>
            Some(Returns.Success())
          case Failure( t ) =>
            Some(Returns.InternalError(t))
        }
      case Failure( t ) =>
        DBIO.successful(Some(Returns.InternalError(t)))
    }
    db.run(actions.transactionally)
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
        dbPath.isInfoItem && 
        nodes.exists{ node => dbPath.path.isDescendantOf(node.path) }
    }.collect{
      case DBPath(Some(id), path, true) =>
        tableByNameExists(id.toString).asTry.flatMap{
          case Success( true ) =>//Table with name exist
            val getNBetweenQueries = valueTables.get(path) match{ //Is table stored?
              case Some(pathValues) => //Found table/ TableQuery
                pathValues.getNBetween(beginO,endO,newestO,oldestO)
              case None =>//No TableQuery found for table. Create one for it
                val pathValues = new PathValues(path, id)
                valueTables += path -> pathValues
                pathValues.getNBetween(beginO,endO,newestO,oldestO)
            }
            getNBetweenQueries.result
          case Success( false ) =>//No table created for InfoItem. Create table for it.
            val pathValues = new PathValues(path, id)
            valueTables += path -> pathValues
            val creation = pathValues.schema.create
            creation.flatMap{
              case u: Unit => 
                pathValues.getNBetween(beginO,endO,newestO,oldestO).result
            }
          case Failure( t ) => 
            throw new Exception( s"Could not find $id table for $path from current tables", t)
      }.map{
        case tvs: Seq[TimedValue] =>
          OdfInfoItem(
            path,
            values = tvs.map{
              tv => OdfValue( tv.value, tv.valueType, tv.timestamp)
            }
            )
      }
    }
    //Create OdfObjects from parameters
    val odf = nodes.foldLeft(OdfObjects()){ 
      case ( odf, node ) => odf.union(node.createAncestors)
    }
    //Create OdfObjects from InfoItems and union them to one with parameter ODF
    val finalAction = DBIO.fold(iiIOAs.toSeq.map{ ioa =>
      ioa.map{ ii => ii.createAncestors }
    },odf ){
      case ( odf, objs ) => odf.union( objs)
    }.map{
      case odf : OdfObjects => Some(odf)
    }
    db.run(finalAction)
  
  }

  def remove(path: Path): Future[Seq[Int]] ={
    val actions = pathToDBPath.get(path) match{
      case Some( DBPath(Some(id), p, true) ) =>
          valueTables.get(path) match{
            case Some( pathValues ) =>
              pathValues.schema.drop.flatMap{
                case u: Unit =>
                  pathsTable.remove( Vector(id) )
              }
          }
      case Some( DBPath(Some(id), originPath, false) ) =>
          val ( objs, iis ) = pathToDBPath.values.filter{
            dbPath => dbPath.path.isDescendantOf(originPath)
          }.partition{ dbPath => dbPath.isInfoItem }
          val tableDrops = iis.map{
            case DBPath(Some(id), descendantPath, true)  =>
            valueTables.get(descendantPath) match{
              case Some( pathValues ) =>
                pathValues.schema.drop
            }
          }
          val tableDropsAction = DBIO.seq( tableDrops.toSeq:_* )
          val pathRemoves = pathsTable.remove((objs ++ iis).map( _.id ).flatten.toSeq)
          tableDropsAction.flatMap{
            u: Unit =>
              pathRemoves
          }
    }
    db.run(actions).map{ i: Int => Seq( i ) }
  }
}
