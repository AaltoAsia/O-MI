package database
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape
import java.io.File
import scala.collection.mutable.Map

object SQLite {

  val dbPath = "./sensorDB.sqlite3"
  val init = !new File(dbPath).exists()
  val latestValues = TableQuery[DBData]
  val objects = TableQuery[DBNode]
  val db = Database.forURL("jdbc:sqlite:" + dbPath, driver = "org.sqlite.JDBC")
  db withSession { implicit session =>
    if (init) {
      initialize()
    }
  }

  def set(data: DBSensor) =
    {
      db withSession { implicit session =>
        val pathQuery = for {
          d <- latestValues if d.path === data.path
        } yield (d.value, d.timestamp)

        if (pathQuery.list.length > 0) {
          pathQuery.update(data.value, data.time)
        } else {
          latestValues += (data.path, data.value, data.time)
        }
        addObjects(data.path)
      }
    }

  def remove(path: String) {
    db withSession { implicit session =>
      val pathQuery = latestValues.filter(_.path === path)
      pathQuery.delete
    }
  }

  def get(path: String): Option[DBItem] =
    {
      var result: Option[DBItem] = None
      db withSession { implicit session =>
        val pathQuery = latestValues.filter(_.path === path)
        if (pathQuery.list.length > 0) {
          for (sensordata <- pathQuery) {
            sensordata match {
              case (path: String, value: String, time: java.sql.Timestamp) =>
                result = Some(DBSensor(path, value, time))
            }
          }
        }
        else
        {
         var childs = getChilds(path)
         if(!childs.isEmpty)
         {
           var obj = DBObject(path)
           obj.childs = childs
           result = Some(obj)
         }
        }
      }
      result
    }
  def addObjects(path: String) {
    db withSession
      { implicit session =>
        var pathparts = path.split("/")
        var curpath = ""
        var fullpath = ""
        for (i <- 0 until pathparts.size) {
          if(fullpath != "")
          {
            fullpath += "/"
          }
          fullpath+=pathparts(i)
          if (!hasObject(fullpath)) {
            objects += (fullpath,curpath,pathparts(i))
          }
          curpath = fullpath
        }
      }
  }
 def getChilds(path:String):Array[DBItem]=
 {
   var childs = Array[DBItem]()
   db withSession{
      implicit Session =>
      val objectQuery = for{
        c <- objects if c.parentPath === path
      }yield(c.path)
      childs = Array.ofDim[DBItem](objectQuery.list.length)
      var index = 0
      objectQuery foreach{
        case(path:String) =>
          childs(index) = DBObject(path)
          index+=1
      } 
    }
   childs
 }
 def hasObject(path:String): Boolean =
    {
    db withSession{
      implicit Session =>
      var objectQuery = objects.filter(_.path === path)
      objectQuery.list.length > 0
    }
    }
  def initialize()(implicit session: Session) =
    {
      latestValues.ddl.create
      objects.ddl.create
    }
}

abstract class DBItem(val path:String)

case class DBSensor(pathto: String, var value: String, var time: java.sql.Timestamp) extends DBItem(pathto)

case class DBObject(pathto: String) extends DBItem(pathto)
{
  var childs = Array[DBItem]()
}


class DBData(tag: Tag)
  extends Table[(String, String, java.sql.Timestamp)](tag, "Latestvalues") {
  // This is the primary key column:
  def path = column[String]("PATH", O.PrimaryKey)
  def value = column[String]("VALUE")
  def timestamp = column[java.sql.Timestamp]("TIME")
  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(String, String, java.sql.Timestamp)] = (path, value, timestamp)
}
class DBNode(tag: Tag)
  extends Table[(String, String,String)](tag, "Objects") {
  // This is the primary key column:
  def path = column[String]("PATH",O.PrimaryKey)
  def parentPath = column[String]("PARENTPATH")
  def key = column[String]("KEY")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(String, String,String)] = (path,parentPath,key)
}