
package bench
import java.io.File
import akka.actor.ActorSystem
import org.scalameter.api._
import org.scalameter.persistence.json._
import org.scalameter.picklers.Implicits._
import database._
import http._

class BenchmarkSuite extends Bench.Group{
//  override def persistor = new JSONSerializationPersistor( "benchResults")
//  include(new OldAndNewDBTest(){})
}
