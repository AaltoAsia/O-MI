package database
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape
import java.io.File

object SQLite{
  
  val DatabasePath = "./test.sqlite3"
  val init = !new File(DatabasePath).exists()
  val items = TableQuery[Data]
  
  val db = Database.forURL("jdbc:sqlite:"+DatabasePath, driver = "org.sqlite.JDBC")
  db withSession{ implicit session =>
    if(init){
      initialize()
    }
  }
def getContent:String={
  var res = ""
  db withSession { implicit session =>
    items foreach{
      case (id,name)=>
        res += id + " " + name + " "
    }
  }
  return res
}
def initialize()(implicit session: Session)=
{
 items.ddl.create
 items ++= Seq(
 (1,"a"),
 (2,"b"),
 (3,"c"),
 (4,"d"),
 (5,"e")
 )
}
}
class Data(tag: Tag)
  extends Table[(Int, String)](tag, "myDB") {

  // This is the primary key column:
  def id = column[Int]("ID", O.PrimaryKey)
  def name = column[String]("NAME")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Int, String)] = (id, name)
}