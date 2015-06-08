package database

import slick.driver.H2Driver.api._
import java.sql.Timestamp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.iterableAsScalaIterable

import java.io.File

import parsing.Types.Path



package object database {

  private var setEventHooks: List[Seq[Path] => Unit] = List()

  /**
   * Set hooks are run when new data is saved to database.
   * @param f Function that takes the updated paths as parameter.
   */
  def attachSetHook(f: Seq[Path] => Unit) =
    setEventHooks = f :: setEventHooks
  def getSetHooks = setEventHooks

  private var histLength = 10
  /**
   * Sets the historylength to desired length
   * default is 10
   * @param newLength new length to be used
   */
  def setHistoryLength(newLength: Int) {
    histLength = newLength
  }
  def historyLength = histLength

  val dbConfigName = "h2-conf"

}
import database._



/**
 * Old way of using single connection
 */
object singleConnection extends DB {
  val db = Database.forConfig(dbConfigName)
  initialize()

  def destroy() = {
    println("[WARN] Destroying db connection, to drop the database: remove db file!")
    db.close()
  }
}



/**
 * Database class for sqlite. Actually uses config parameters through forConfig in singleConnection.
 * To be used during actual runtime.
 */
class SQLiteConnection extends DB {
  //override val db = singleConnection.db
  val db = Database.forConfig(dbConfigName)

  def destroy() = {
     dropDB()
     db.close()

     // Try to remove the db file
     val confUrl = slick.util.GlobalConfig.driverConfig(dbConfigName).getString("url")
     // XXX: trusting string operations
     val dbPath = confUrl.split(":").last

     val fileExt = dbPath.split(".").lastOption.getOrElse("")
     if (fileExt == "sqlite3" || fileExt == "db")
       new File(dbPath).delete()
  }
}



/**
 * Database class to be used during tests instead of production db to prevent
 * problems caused by overlapping test data.
 * Uses h2 named in-memory db
 * @param name name of the test database, optional. Data will be stored in memory
 */
class TestDB(val name:String = "") extends DB
{
  println("Creating TestDB: " + name)
  val db = Database.forURL(s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver",
    keepAliveConnection=true)
  initialize()

  /**
  * Should be called after tests.
  */
  def destroy() = {
    println("Removing TestDB: " + name)
    db.close()
  }
}




/**
 * Database trait used by db classes.
 * Contains a public high level read-write interface for the database tables.
 */
trait DB extends DBReadWrite with DBBase {
  def asReadOnly: DBReadOnly = this
  def asReadWrite: DBReadWrite = this
}






