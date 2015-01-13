package database

import org.specs2.mutable._
import database._

object SQLiteTest extends Specification {
  
  "SQLite" should {
    sequential
    var data1 = new database.DBSensor("path/to/sensor/temp1","25.5C",new java.sql.Timestamp(new java.util.Date().getTime))

    database.SQLite.set(data1)
    
    "return correct value for given valid path" in {
       database.SQLite.get("path/to/sensor/temp1").get.value shouldEqual "25.5C"
    }
    
    database.SQLite.remove("path/to/sensor/temp1")
    
    "return correct value for given invalid path" in {
       database.SQLite.get("path/to/sensor/temp1") shouldEqual None
    }
  }
}
