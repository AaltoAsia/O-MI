package database

import org.specs2.mutable._
import database._

object SQLiteTest extends Specification {
  
  "SQLite" should {
    
    "return correct response" in {
       database.SQLite.getContent shouldEqual "1 a 2 b 3 c 4 d 5 e "
    }
  }
}
