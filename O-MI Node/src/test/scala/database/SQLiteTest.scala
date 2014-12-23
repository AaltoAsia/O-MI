package database

import org.specs2.mutable._
import database._

object SQLiteTest extends Specification {
  val testdb = new SQLite()
  "SQLite" should {
    
    "return correct response" in {
       testdb.getResult() mustEqual "1 a 2 b 3 c "
    }
  }
}
