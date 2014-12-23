package database
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape

class SQLite{
  val data: TableQuery[Data] = TableQuery[Data]
  val db = Database.forURL("jdbc:h2:mem:hello", driver = "org.SQLite.Driver")
  db.withSession { implicit session =>
    data += (1, "a")
    data += (2, "b")
    data += (3, "c")
  }
  def getResult(): String =
    {
      var result = ""
      val db = Database.forURL("jdbc:h2:mem:hello", driver = "org.SQLite.Driver")
      db.withSession { implicit session =>
        data foreach {
          case (id, name) =>
            result += id + " " + name + " "
        }
      }
      return result
    }
}
class Data(tag: Tag)
  extends Table[(Int, String)](tag, "example") {

  // This is the primary key column:
  def id = column[Int]("SUP_ID", O.PrimaryKey)
  def name = column[String]("SUP_NAME")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Int, String)] =
    (id, name)
}