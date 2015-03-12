package database

import org.specs2.mutable._
import database._
import java.sql.Timestamp

import parsing._
import parsing.Types._
import parsing.Types.Path._

object SQLiteTest extends Specification {
  
  "SQLite" should {
    sequential
    var data1 = DBSensor(Path("path/to/sensor1/temp"),"21.5C",new java.sql.Timestamp(1000))
    var data2 = DBSensor(Path("path/to/sensor1/hum"),"40%",new java.sql.Timestamp(2000))
    var data3 = DBSensor(Path("path/to/sensor2/temp"),"24.5",new java.sql.Timestamp(3000))
    var data4 = DBSensor(Path("path/to/sensor2/hum"),"60%",new java.sql.Timestamp(4000))
    var data5 = DBSensor(Path("path/to/sensor1/temp"),"21.6C",new java.sql.Timestamp(5000))
    var data6 = DBSensor(Path("path/to/sensor1/temp"),"21.7C",new java.sql.Timestamp(6000))
    var id1 = SQLite.saveSub(new DBSub(Array(Path("path/to/sensor1"),Path("path/to/sensor2")),0,1,None,None))
    var id2 = SQLite.saveSub(new DBSub(Array(Path("path/to/sensor1"),Path("path/to/sensor2")),0,2,Some("callbackaddress"),None))
    var id3 = SQLite.saveSub(new DBSub(Array(Path("path/to/sensor1"),Path("path/to/sensor2"),Path("path/to/sensor3"),Path("path/to/another/sensor2")),100,2,None,None))
    
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
      //adding many values for one path for testing
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new java.sql.Timestamp(6000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(7000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(8000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(9000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(10000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(11000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(12000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(13000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(14000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(15000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(16000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(17000)))
      database.SQLite.set(data6)
      database.SQLite.set(data5) shouldEqual false
    }
     "return correct value for given valid path" in {
        var res = ""
        
       database.SQLite.get(Path("path/to/sensor1/hum")) match
       {
        case Some(obj) =>
          obj match{
            case DBSensor(p:Path,v:String,t:java.sql.Timestamp) =>
              res = v
            case _ => throw new Exception("unhandled case") //TODO any better ideas?
          }
        case None =>
          res = "not found"
        case _ => throw new Exception("unhandled case")
       }
       res shouldEqual "40%"
    }
    "return correct value for given valid updated path" in {
        var res = ""
        
       database.SQLite.get(Path("path/to/sensor3/temp")) match
       {
        case Some(obj) =>
          obj match{
            case DBSensor(p:Path,v:String,t:java.sql.Timestamp) =>
              res = v
            case _ => throw new Exception("unhandled case") //TODO any better ideas?
          }
        case None =>
          res = "not found"
        case _ => throw new Exception("unhandled case") //TODO any better ideas?!?
       }
       res shouldEqual "21.6C"
    }
    
    "return correct childs for given valid path" in {
        var res = Array[String]()
       database.SQLite.get(Path("path/to/sensor1")) match
       {
        case Some(obj) =>
          obj match{
            case ob:DBObject =>
              var i = 0
              res = Array.ofDim[String](ob.childs.length)
              for(o <- ob.childs)
              {
                res(i) = o.path.toString()
                i += 1
              }
            case _ => throw new Exception("unhandled case") //TODO any better ideas ?
          }
        case None =>
        case _ => throw new Exception("unhandled case")//TODO any better ideas?
       }
       res.length == 2 && res.contains("path/to/sensor1/temp") && res.contains("path/to/sensor1/hum") shouldEqual true
    }
    "return None for given invalid path" in {
      database.SQLite.get(Path("path/to/nosuchsensor")) shouldEqual None
    }
    
    "return correct values for given valid path and timestamps" in {
        var sensrs = database.SQLite.getNBetween(Path("path/to/sensor1/temp"),Some(new Timestamp(900)),Some(new Timestamp(5500)),None,None)
        var values = sensrs.map { x => x.value }
       values.length == 2 && values.contains("21.5C") && values.contains("21.6C") shouldEqual true
    }
    
    "return correct values for given valid path and timestamps" in {
        var sensrs = database.SQLite.getNBetween(Path("path/to/sensor1/temp"),Some(new Timestamp(1500)),Some(new Timestamp(6001)),None,None)
        var values = sensrs.map { x => x.value }
       values.length == 2 && values.contains("21.7C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N latest values" in {
        var sensrs = database.SQLite.getNBetween(Path("path/to/sensor3/temp"),None,None,None,Some(12))
        var values = sensrs.map { x => x.value }
      println(values.mkString(" "))
       values.length == 10 && values.contains("21.1C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N latest values" in {
        var sensrs = database.SQLite.getNBetween(Path("path/to/sensor3/temp"),None,None,None,Some(3))
        var values = sensrs.map { x => x.value }
       
       values.length == 3 && values.contains("21.5C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N oldest values" in {
        var sensrs = database.SQLite.getNBetween(Path("path/to/sensor3/temp"),None,None,Some(12),None)
        var values = sensrs.map { x => x.value }
      println(values.mkString(" "))
       values.length == 10 && values.contains("21.1C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N oldest values" in {
        var sensrs = database.SQLite.getNBetween(Path("path/to/sensor3/temp"),None,None,Some(2),None)
        var values = sensrs.map { x => x.value }
        println(values.mkString(" "))
       values.length == 2 && values.contains("21.1C") && values.contains("21.2C") shouldEqual true
    }
    "return true when removing valid path" in{
      database.SQLite.remove(Path("path/to/sensor3/temp"))
      database.SQLite.remove(Path("path/to/sensor1/hum")) shouldEqual true
    }
    "be able to buffer data on demand"in{
      database.SQLite.startBuffering(Path("path/to/sensor3/temp"))
      database.SQLite.startBuffering(Path("path/to/sensor3/temp"))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new java.sql.Timestamp(6000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(7000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(8000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(9000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(10000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(11000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(12000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(13000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(14000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(15000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(16000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(17000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(18000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(19000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(20000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(21000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(22000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(23000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(24000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(25000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(26000)))
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"),None,None,None,None).length shouldEqual 21
    }
    "return values between two timestamps" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(6000)), Some(new Timestamp(10000)), None, None).length shouldEqual 5
    }
    "return values from start" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(20000)), None, None, None).length shouldEqual 7
    }
    "return values before end" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), None, Some(new Timestamp(10000)), None, None).length shouldEqual 5
    }
     "return 10 values before end" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), None, Some(new Timestamp(26000)), None, Some(10)).length shouldEqual 10
    }
     "return 10 values after start" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(6000)),None, Some(10), None).length shouldEqual 10
    }
     "return values between two timestamps" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(6000)), Some(new Timestamp(10000)), Some(10), None).length shouldEqual 5
    }
     "return all values if no options given" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), None, None, None, None).length shouldEqual 21
    }
     "return all values if both fromStart and fromEnd is given" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"), None, None, Some(10), Some(5)).length shouldEqual 21
    }
      "return empty array if Path doesnt exist" in
    {
      database.SQLite.getNBetween(Path("path/to/sensor/doesntexist"), None, None,None, None).length shouldEqual 0
    }
     "should not rever to historyLength if other are still buffering" in{
       
       database.SQLite.stopBuffering(Path("path/to/sensor3/temp"))
       database.SQLite.getNBetween(Path("path/to/sensor3/temp"), None, None, None, None).length shouldEqual 21
     }
    "be able to stop buffering and revert to using historyLenght" in
    {
      database.SQLite.stopBuffering(Path("path/to/sensor3/temp"))
      database.SQLite.getNBetween(Path("path/to/sensor3/temp"),None,None,None,None).length shouldEqual 10
    }
    "return true when removing valid path" in{
      database.SQLite.remove(Path("path/to/sensor3/temp"))
      database.SQLite.remove(Path("path/to/sensor1/temp")) shouldEqual true
    }
    "return false when trying to remove object from the middle" in{
      database.SQLite.remove(Path("path/to/sensor2")) shouldEqual false
    }
    "return true when removing valid path" in{
      database.SQLite.remove(Path("path/to/sensor2/temp")) shouldEqual true
       database.SQLite.remove(Path("path/to/sensor2/hum")) shouldEqual true
    }
     "return None when searching non existent object" in{
      database.SQLite.get(Path("path/to/sensor2")) shouldEqual None
      database.SQLite.get(Path("path/to/sensor1")) shouldEqual None
    }
    "return correct callback adress for subscriptions" in{
      database.SQLite.getSub(id1).get.callback shouldEqual None
      database.SQLite.getSub(id2).get.callback.get shouldEqual "callbackaddress"
      database.SQLite.getSub(id3).get.callback shouldEqual None
    }
    "return correct boolean whether subscription is expired" in{
      database.SQLite.isExpired(id1) shouldEqual true
      database.SQLite.isExpired(id2) shouldEqual true
      database.SQLite.isExpired(id3) shouldEqual false
    }
    "return correct paths as array" in{
      database.SQLite.getSub(id1).get.paths.length shouldEqual 2
      database.SQLite.getSub(id2).get.paths.length shouldEqual 2
      database.SQLite.getSub(id3).get.paths.length shouldEqual 4
    }
   "return None for removed subscriptions" in{
      database.SQLite.removeSub(id1)
      database.SQLite.removeSub(id2)
      database.SQLite.removeSub(id3)
      database.SQLite.getSub(id1) shouldEqual None
      database.SQLite.getSub(id2) shouldEqual None
      database.SQLite.getSub(id3) shouldEqual None
    }
   "return rigtht values in getsubdata" in
   {
      var timeNow= new java.util.Date().getTime
      var id = SQLite.saveSub(new DBSub(Array(Path("path/to/sensor1/temp")
          ,Path("path/to/sensor2/temp"),Path("path/to/sensor3/temp")),0,1,None,
          Some(new Timestamp(timeNow-3500))))
       
      database.SQLite.set(DBSensor(Path("path/to/sensor1/temp"),"21.0C",new Timestamp(timeNow-3000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor1/temp"),"21.0C",new Timestamp(timeNow-2000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor1/temp"),"21.0C",new Timestamp(timeNow-1000)))
      
      database.SQLite.set(DBSensor(Path("path/to/sensor2/temp"),"21.0C",new Timestamp(timeNow-3000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor2/temp"),"21.0C",new Timestamp(timeNow-2000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor2/temp"),"21.0C",new Timestamp(timeNow-1000)))
      
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new Timestamp(timeNow-3000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new Timestamp(timeNow-2000)))
      database.SQLite.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new Timestamp(timeNow-1000)))
      
      var res = database.SQLite.getSubData(id,Some(new Timestamp(timeNow))).length
      database.SQLite.removeSub(id)
      database.SQLite.remove(Path("path/to/sensor1/temp"))
      database.SQLite.remove(Path("path/to/sensor2/temp"))
      database.SQLite.remove(Path("path/to/sensor3/temp"))
      res shouldEqual 9
   }
   "return correct subscriptions with getAllSubs" in
   {
     val time = Some(new Timestamp(1000))
     val id1 = SQLite.saveSub(new DBSub(Array(),0,1,None,time))
     val id2 = SQLite.saveSub(new DBSub(Array(),0,1,None,time))
     val id3 = SQLite.saveSub(new DBSub(Array(),0,1,None,time))
     val id4 = SQLite.saveSub(new DBSub(Array(),0,1,None,time))
     val id5 = SQLite.saveSub(new DBSub(Array(),0,1,Some("addr1"),time))
     val id6 = SQLite.saveSub(new DBSub(Array(),0,1,Some("addr2"),time))
     val id7 = SQLite.saveSub(new DBSub(Array(),0,1,Some("addr3"),time))
     val id8 = SQLite.saveSub(new DBSub(Array(),0,1,Some("addr4"),time))
     val id9 = SQLite.saveSub(new DBSub(Array(),0,1,Some("addr5"),time))
     
     SQLite.getAllSubs(None).length shouldEqual 9
     SQLite.getAllSubs(Some(true)).length shouldEqual 5
     SQLite.getAllSubs(Some(false)).length shouldEqual 4
     SQLite.removeSub(id1)
     SQLite.removeSub(id2)
     SQLite.removeSub(id3)
     SQLite.removeSub(id4)
     SQLite.removeSub(id5)
     SQLite.removeSub(id6)
     SQLite.removeSub(id7)
     SQLite.removeSub(id8)
     SQLite.removeSub(id9)
   }

  }
}
