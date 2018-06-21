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

/**
 * Base trait for databases. Has basic protected interface.
 */
trait DBBase{
  val dc : DatabaseConfig[JdbcProfile] //= DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.driver.api._
  val db: Database
  //protected[this] val db: Database
}

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
  type DBSIOro[Result] = DBIOAction[Seq[Result],Streaming[Result],Effect.Read] 
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]
  type DBIOwo[Result] = DBIOAction[Result, NoStream, Effect.Write]
  type DBIOsw[Result] = DBIOAction[Result, NoStream, Effect.Schema with Effect.Write]
  type ReadWrite = Effect with Effect.Write with Effect.Read with Effect.Transactional
  type DBIOrw[Result] = DBIOAction[Result, NoStream, ReadWrite]
  implicit lazy val pathColumnType = MappedColumnType.base[Path, String](
  { p: Path => p.toString},
    { str: String => Path(str) }     // String to Path
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
    def selectByPath( path: Path): DBSIOro[DBPath] = selectByPathCQ(path).result
    def selectByID( id: Long ): DBSIOro[DBPath]  = selectByIDCQ(id).result
    def selectByIDs( ids: Seq[Long] ): DBSIOro[DBPath] = selectByIDsQ(ids).result
    def selectByPaths( paths: Seq[Path] ): DBSIOro[DBPath]= selectByPathsQ( paths ).result
    def currentPaths: DBSIOro[Path] = currentPathsQ.result

    def add( dbPaths: Seq[DBPath] ): DBIOwo[Seq[Long]] = {
      insertQ( dbPaths.distinct )
    }
    def removeByIDs( ids: Seq[Long] ): DBIOwo[Int]  = selectByIDsQ( ids ).delete
    def removeByPaths( paths: Seq[Path] ): DBIOwo[Int]= selectByPathsQ( paths ).delete
    def selectAllInfoItems:  DBSIOro[DBPath]  = infoItemsCQ.result

    protected  lazy val infoItemsCQ = Compiled( infoItemsQ )
    protected  def infoItemsQ = this.filter{ dbp => dbp.isInfoItem }

    protected def currentPathsQ = this.map{ row => row.path}
    protected def selectByIDsQ( ids: Seq[Long] ) = this.filter{ row => row.id inSet( ids ) }
    protected def selectByPathsQ( paths: Seq[Path] ) = this.filter{ row => row.path inSet( paths ) }
    protected lazy val selectByIDCQ = Compiled( selectByIDQ _ )
    protected def selectByIDQ( id: Rep[Long] ) = this.filter{ row => row.id === id  }

    protected lazy val selectByPathCQ = Compiled( selectByPathQ _ )
    protected def selectByPathQ( path: Rep[Path] ) = this.filter{ row => row.path === path  }
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
    val name: String = s"PATH_${pathID.toString}"
    import dc.driver.api._
    def removeValuesBefore( end: Timestamp): DBIOwo[Int] = before(end).delete
    def trimToNNewestValues( n: Long ): DBIOrw[Int] = selectAllExpectNNewestValuesCQ( n ).result.flatMap{
      values: Seq[TimedValue] =>
        val ids = values.map(_.id).flatten
        this.filter(_.id inSet(ids)).delete
    }
    def add( values: Seq[TimedValue] ): DBIOwo[Option[Int]]  = this ++= values.distinct
    def selectNBetween(
      beginO: Option[Timestamp],
      endO: Option[Timestamp],
      newestO: Option[Int],
      oldestO: Option[Int]
    ): DBSIOro[TimedValue]  ={
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
  val pathsTable: StoredPath = new StoredPath()
  def namesOfCurrentTables: DBIOro[Vector[String]] = MTable.getTables.map{ 
    mts => 
    mts.map{ 
      mt => mt.name.name
    }
  }
  def tableByNameExists( name: String ) : DBIOro[Boolean]= namesOfCurrentTables.map {
    names: Seq[String] =>
      names.contains(name)
  } 
}
