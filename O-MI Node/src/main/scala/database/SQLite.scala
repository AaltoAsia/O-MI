package database
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape
import java.io.File
import scala.collection.mutable.Map
object SQLite{
  
  val dbPath = "./test.sqlite3"
  val init = !new File(dbPath).exists()
  val latestValues = TableQuery[DBData]
  val db = Database.forURL("jdbc:sqlite:"+dbPath, driver = "org.sqlite.JDBC")
  db withSession{ implicit session =>
    if(init){
      initialize()
    }
  }
  
def setLatest(data:DBSensor) =
{
   db withSession{ implicit session =>
     val pathQuery = for{
       d <- latestValues if d.path === data.path
     }
     yield(d.value,d.timestamp)
     
    if(pathQuery.list.length > 0)
    {
     pathQuery.update(data.value,data.time)
    }
    else
    {
      latestValues += (data.path,data.value,data.time)
    }
   }
}

def removeData(path:String)
{
  db withSession{ implicit session =>
  val pathQuery = latestValues.filter(_.path === path)
    if(pathQuery.list.length > 0)
    {
     pathQuery.delete
    }
  }
  }
def getLatest(path:String):Option[DBSensor]=
{
  var result:Option[DBSensor] = None 
  db withSession{ implicit session =>
  val pathQuery = latestValues.filter(_.path === path)
    if(pathQuery.list.length > 0)
    {
     for(sensordata <- pathQuery)
     {
       sensordata match{
         case(path:String,value:String,time:java.sql.Timestamp) =>
           result = Some(new DBSensor(path,value,time))
       }
     }
    }
  }
  result
}

def initialize()(implicit session: Session)=
{
 latestValues.ddl.create
}
}

class DBSensor(val path:String,var value:String,var time:java.sql.Timestamp)

class DBData(tag: Tag)
  extends Table[(String, String,java.sql.Timestamp)](tag, "Latestvalues") {
  // This is the primary key column:
  def path = column[String]("PATH", O.PrimaryKey)
  def value = column[String]("VALUE")
  def timestamp = column[java.sql.Timestamp]("TIME")
  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(String, String,java.sql.Timestamp)] = (path,value,timestamp)
}