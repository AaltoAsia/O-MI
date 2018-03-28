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
    { _.toString.replace("\\/","\\\\/" )}, // Path to String
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
    def currentPaths = currentPathsQ.result

    def add( dbPaths: Seq[DBPath] ) = {
      println( s"Adding following paths: ${dbPaths.map(_.path.toString).mkString("\n")}")
      insertQ( dbPaths.distinct )
    }
    def removeByIDs( ids: Seq[Long] ) = getByIDsQ( ids ).delete
    def removeByPaths( paths: Seq[Path] ) = getByPathsQ( paths ).delete
    def getInfoItems = infoItemsCQ.result

    protected  lazy val infoItemsCQ = Compiled( infoItemsQ )
    protected  def infoItemsQ = this.filter{ dbp => dbp.isInfoItem }

    protected def currentPathsQ = this.map{ row => row.path}
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
