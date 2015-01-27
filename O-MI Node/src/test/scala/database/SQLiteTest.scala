package database

import org.specs2.mutable._
import database._

object SQLiteTest extends Specification {
  
  "SQLite" should {
    sequential
    var data1 = DBSensor("path/to/sensor1/temp","21.5C",new java.sql.Timestamp(1000))
    var data2 = DBSensor("path/to/sensor1/hum","40%",new java.sql.Timestamp(2000))
    var data3 = DBSensor("path/to/sensor2/temp","24.5",new java.sql.Timestamp(3000))
    var data4= DBSensor("path/to/sensor2/hum","60%",new java.sql.Timestamp(4000))
    var data5 = DBSensor("path/to/sensor1/temp","21.6C",new java.sql.Timestamp(5000))
     var data6 = DBSensor("path/to/sensor1/temp","21.7C",new java.sql.Timestamp(6000))
    "return true when adding new data" in {
      database.SQLite.set(data1) shouldEqual true
    }
    "return true when adding new data" in {
      database.SQLite.set(data2) shouldEqual true
    }
    "return true when adding new data" in {
      database.SQLite.set(data3) shouldEqual true
    }
    "return true when adding new data" in {
      database.SQLite.set(data4) shouldEqual true
    }
    "return true when adding new data" in {
      database.SQLite.set(data6)
      database.SQLite.set(data5) shouldEqual true
    }
     "return correct value for given valid path" in {
        var res = ""
        
       database.SQLite.get("path/to/sensor1/hum") match
       {
        case Some(obj) =>
          obj match{
            case DBSensor(p:String,v:String,t:java.sql.Timestamp) =>
              res = v
          }
        case None =>
          res = "not found"
       }
       res shouldEqual "40%"
    }
    "return correct value for given valid updated path" in {
        var res = ""
        
       database.SQLite.get("path/to/sensor1/temp") match
       {
        case Some(obj) =>
          obj match{
            case DBSensor(p:String,v:String,t:java.sql.Timestamp) =>
              res = v
          }
        case None =>
          res = "not found"
       }
       res shouldEqual "21.7C"
    }
    
    "return correct childs for given valid path" in {
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
       res.length == 2 && res.contains("path/to/sensor1/temp") && res.contains("path/to/sensor1/hum") shouldEqual true
    }
    "return None for given invalid path" in {
      database.SQLite.get("path/to/nosuchsensor") shouldEqual None
    }
    
    "return correct values for given valid path and timestamps" in {
        var sensrs = database.SQLite.getInterval("path/to/sensor1/temp",new java.sql.Timestamp(900),new java.sql.Timestamp(5500))
        var values = sensrs.map { x => x.value }
       values.length == 2 && values.contains("21.5C") && values.contains("21.6C") shouldEqual true
    }
    
    "return true when removing valid path" in{
      database.SQLite.remove("path/to/sensor1/hum") shouldEqual true
    }
    "return true when removing valid path" in{
      database.SQLite.remove("path/to/sensor1/temp") shouldEqual true
    }
    "return false when trying to remove object from the middle" in{
      database.SQLite.remove("path/to/sensor2") shouldEqual false
    }
    "return true when removing valid path" in{
      database.SQLite.remove("path/to/sensor2/temp") shouldEqual true
    }
    "return true when removing valid path" in{
      database.SQLite.remove("path/to/sensor2/hum") shouldEqual true
    }
     "return None when searching non existent object" in{
      database.SQLite.get("path/to/sensor2") shouldEqual None
    }
     "return None when searching non existent object" in{
      database.SQLite.get("path/to/sensor1") shouldEqual None
    }
   
    
  }
}
