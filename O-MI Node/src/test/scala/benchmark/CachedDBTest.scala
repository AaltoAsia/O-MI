package bench
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import java.sql.Timestamp
import java.util.Date
import scala.util.{Random, Try, Success, Failure}
import org.scalameter.api._
import akka.actor.ActorSystem
import http._
import database._
import types._
import types.OdfTypes._
import org.scalameter.picklers.Implicits._

trait CachedDBTest extends DBTest {
  val name: String = "CachedDBTest"
  implicit val system = ActorSystem(name + "System")
  implicit val settings = OmiConfig(system)
  implicit val singleStores = new SingleStores(settings)
  val db: DBReadWrite = new TestDB(name,false)(system, singleStores, settings)
  test(db, "CachedDB")
}
