package database

import org.specs2.mutable._
import database._

object SQLiteTest extends Specification {
  
  "SQLite" should {
    sequential
    
    database.SQLite.set(database.DBSensor("path/to/sensor1/temp1","25.5C",new java.sql.Timestamp(new java.util.Date().getTime)))
    database.SQLite.set(database.DBSensor("path/to/sensor1/temp2","25.5C",new java.sql.Timestamp(new java.util.Date().getTime)))
    database.SQLite.set(database.DBSensor("path/to/sensor2/temp1","25.5C",new java.sql.Timestamp(new java.util.Date().getTime)))
    database.SQLite.set(database.DBSensor("path/to/sensor2/temp2","25.5C",new java.sql.Timestamp(new java.util.Date().getTime)))
    
    "return correct value for given valid path" in {
        var res = ""
        
       database.SQLite.get("path/to/sensor1/temp1") match
       {
        case Some(obj) =>
          obj match{
            case DBSensor(p:String,v:String,t:java.sql.Timestamp) =>
              res = v
          }
        case None =>
          res = "not found"
       }
       res shouldEqual "25.5C"
    }
    "return correct objects for given valid path" in {
        var res = Array[String]()
        
       database.SQLite.get("path/to/sensor1") match
       {
        case Some(obj) =>
          obj match{
            case ob:DBObject =>
              var i = 0
              res = Array.ofDim[String](ob.childs.length)
              for(o <- ob.childs)
              {
                res(i) = o.path
                i += 1
              }
          }
        case None =>
          
       }
       res shouldEqual Array("path/to/sensor1/temp1","path/to/sensor1/temp2")
    }
    "return correct value for invalid path" in {
       database.SQLite.get("invalid/path/to/sensor/temp1") shouldEqual None
    }
    
    "return correct value for deleted path" in {
      database.SQLite.remove("path/to/sensor1/temp1")
       database.SQLite.get("path/to/sensor1/temp1") shouldEqual None
    }
    "return correct value for deleted path" in {
      database.SQLite.remove("path/to/sensor1/temp2")
       database.SQLite.get("path/to/sensor1/temp2") shouldEqual None
    }
    "return correct value for deleted path" in {
       database.SQLite.get("path/to/sensor1") shouldEqual None
    }
    "return correct value for deleted path" in {
       database.SQLite.get("path/to") shouldNotEqual None
    }
    
  }
}
