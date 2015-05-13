package database

import org.specs2.mutable._
import database._
import java.sql.Timestamp
import parsing.Types.OdfTypes._

import parsing.Types._

object SQLiteTest extends Specification {
  
  "dbConnection" should {
    sequential
    implicit val db = new TestDB("dbtest")
    var data1 = DBSensor(Path("path/to/sensor1/temp"),"21.5C",new java.sql.Timestamp(1000))
    var data2 = DBSensor(Path("path/to/sensor1/hum"),"40%",new java.sql.Timestamp(2000))
    var data3 = DBSensor(Path("path/to/sensor2/temp"),"24.5",new java.sql.Timestamp(3000))
    var data4 = DBSensor(Path("path/to/sensor2/hum"),"60%",new java.sql.Timestamp(4000))
    var data5 = DBSensor(Path("path/to/sensor1/temp"),"21.6C",new java.sql.Timestamp(5000))
    var data6 = DBSensor(Path("path/to/sensor1/temp"),"21.7C",new java.sql.Timestamp(6000))
    var id1 = db.saveSub(new DBSub(Array(Path("path/to/sensor1"),Path("path/to/sensor2")),0,1,None,None))
    var id2 = db.saveSub(new DBSub(Array(Path("path/to/sensor1"),Path("path/to/sensor2")),0,2,Some("callbackaddress"),None))
    var id3 = db.saveSub(new DBSub(Array(Path("path/to/sensor1"),Path("path/to/sensor2"),Path("path/to/sensor3"),Path("path/to/another/sensor2")),100,2,None,None))
    
    "return true when adding new data" in {
      db.set(data1) shouldEqual true
    }
    "return true when adding new data" in {
      db.set(data2) shouldEqual true
    }
    "return true when adding new data" in {
      db.set(data3) shouldEqual true
    }
    "return true when adding new data" in {
      db.set(data4) shouldEqual true
    }
    "return true when adding new data" in {
      //adding many values for one path for testing
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new java.sql.Timestamp(6000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(7000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(8000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(9000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(10000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(11000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(12000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(13000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(14000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(15000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(16000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(17000)))
      db.set(data6)
      db.set(data5) shouldEqual false
    }
     "return correct value for given valid path" in {
        var res = ""
        
       db.get(Path("path/to/sensor1/hum")) match
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
        
       db.get(Path("path/to/sensor3/temp")) match
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
       db.get(Path("path/to/sensor1")) match
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
      db.get(Path("path/to/nosuchsensor")) shouldEqual None
    }
    
    "return correct values for given valid path and timestamps" in {
        var sensrs = db.getNBetween(Path("path/to/sensor1/temp"),Some(new Timestamp(900)),Some(new Timestamp(5500)),None,None)
        var values = sensrs.map { x => x.value }
       values.length == 2 && values.contains("21.5C") && values.contains("21.6C") shouldEqual true
    }
    
    "return correct values for given valid path and timestamps" in {
        var sensrs = db.getNBetween(Path("path/to/sensor1/temp"),Some(new Timestamp(1500)),Some(new Timestamp(6001)),None,None)
        var values = sensrs.map { x => x.value }
       values.length == 2 && values.contains("21.7C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N latest values" in {
        var sensrs = db.getNBetween(Path("path/to/sensor3/temp"),None,None,None,Some(12))
        var values = sensrs.map { x => x.value }
  
       values.length == 10 && values.contains("21.1C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N latest values" in {
        var sensrs = db.getNBetween(Path("path/to/sensor3/temp"),None,None,None,Some(3))
        var values = sensrs.map { x => x.value }
       
       values.length == 3 && values.contains("21.5C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N oldest values" in {
        var sensrs = db.getNBetween(Path("path/to/sensor3/temp"),None,None,Some(12),None)
        var values = sensrs.map { x => x.value }

       values.length == 10 && values.contains("21.1C") && values.contains("21.6C") shouldEqual true
    }
    "return correct values for N oldest values" in {
        var sensrs = db.getNBetween(Path("path/to/sensor3/temp"),None,None,Some(2),None)
        var values = sensrs.map { x => x.value }

       values.length == 2 && values.contains("21.1C") && values.contains("21.2C") shouldEqual true
    }
    "return true when removing valid path" in{
      db.remove(Path("path/to/sensor3/temp"))
      db.remove(Path("path/to/sensor1/hum")) shouldEqual true
    }
    "be able to buffer data on demand"in{
      db.startBuffering(Path("path/to/sensor3/temp"))
      db.startBuffering(Path("path/to/sensor3/temp"))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new java.sql.Timestamp(6000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(7000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.1C",new java.sql.Timestamp(8000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(9000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.2C",new java.sql.Timestamp(10000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(11000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.3C",new java.sql.Timestamp(12000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(13000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.4C",new java.sql.Timestamp(14000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(15000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.5C",new java.sql.Timestamp(16000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(17000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(18000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(19000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(20000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(21000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(22000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(23000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(24000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(25000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.6C",new java.sql.Timestamp(26000)))
      db.getNBetween(Path("path/to/sensor3/temp"),None,None,None,None).length shouldEqual 21
    }
    "return values between two timestamps" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(6000)), Some(new Timestamp(10000)), None, None).length shouldEqual 5
    }
    "return values from start" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(20000)), None, None, None).length shouldEqual 7
    }
    "return values before end" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), None, Some(new Timestamp(10000)), None, None).length shouldEqual 5
    }
     "return 10 values before end" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), None, Some(new Timestamp(26000)), None, Some(10)).length shouldEqual 10
    }
     "return 10 values after start" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(6000)),None, Some(10), None).length shouldEqual 10
    }
     "return values between two timestamps" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), Some(new Timestamp(6000)), Some(new Timestamp(10000)), Some(10), None).length shouldEqual 5
    }
     "return all values if no options given" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), None, None, None, None).length shouldEqual 21
    }
     "return all values if both fromStart and fromEnd is given" in
    {
      db.getNBetween(Path("path/to/sensor3/temp"), None, None, Some(10), Some(5)).length shouldEqual 21
    }
      "return empty array if Path doesnt exist" in
    {
      db.getNBetween(Path("path/to/sensor/doesntexist"), None, None,None, None).length shouldEqual 0
    }
     "should not rever to historyLength if other are still buffering" in{
       
       db.stopBuffering(Path("path/to/sensor3/temp"))
       db.getNBetween(Path("path/to/sensor3/temp"), None, None, None, None).length shouldEqual 21
     }
    "be able to stop buffering and revert to using historyLenght" in
    {
      db.stopBuffering(Path("path/to/sensor3/temp"))
      db.getNBetween(Path("path/to/sensor3/temp"),None,None,None,None).length shouldEqual 10
    }
    "return true when removing valid path" in{
      db.remove(Path("path/to/sensor3/temp"))
      db.remove(Path("path/to/sensor1/temp")) shouldEqual true
    }
    "return false when trying to remove object from the middle" in{
      db.remove(Path("path/to/sensor2")) shouldEqual false
    }
    "return true when removing valid path" in{
      db.remove(Path("path/to/sensor2/temp")) shouldEqual true
       db.remove(Path("path/to/sensor2/hum")) shouldEqual true
    }
     "return None when searching non existent object" in{
      db.get(Path("path/to/sensor2")) shouldEqual None
      db.get(Path("path/to/sensor1")) shouldEqual None
    }
    "return correct callback adress for subscriptions" in{
      db.getSub(id1).get.callback shouldEqual None
      db.getSub(id2).get.callback.get shouldEqual "callbackaddress"
      db.getSub(id3).get.callback shouldEqual None
    }
    "return correct boolean whether subscription is expired" in{
      db.isExpired(id1) shouldEqual true
      db.isExpired(id2) shouldEqual true
      db.isExpired(id3) shouldEqual false
    }
    "return correct paths as array" in{
      db.getSub(id1).get.paths.length shouldEqual 2
      db.getSub(id2).get.paths.length shouldEqual 2
      db.getSub(id3).get.paths.length shouldEqual 4
    }
   "return None for removed subscriptions" in{
      db.removeSub(id1)
      db.removeSub(id2)
      db.removeSub(id3)
      db.getSub(id1) shouldEqual None
      db.getSub(id2) shouldEqual None
      db.getSub(id3) shouldEqual None
    }
   "return rigtht values in getsubdata" in
   {
      var timeNow= new java.util.Date().getTime
      var id = db.saveSub(new DBSub(Array(Path("path/to/sensor1/temp")
          ,Path("path/to/sensor2/temp"),Path("path/to/sensor3/temp")),0,1,None,
          Some(new Timestamp(timeNow-3500))))
       
      db.set(DBSensor(Path("path/to/sensor1/temp"),"21.0C",new Timestamp(timeNow-3000)))
      db.set(DBSensor(Path("path/to/sensor1/temp"),"21.0C",new Timestamp(timeNow-2000)))
      db.set(DBSensor(Path("path/to/sensor1/temp"),"21.0C",new Timestamp(timeNow-1000)))
      
      db.set(DBSensor(Path("path/to/sensor2/temp"),"21.0C",new Timestamp(timeNow-3000)))
      db.set(DBSensor(Path("path/to/sensor2/temp"),"21.0C",new Timestamp(timeNow-2000)))
      db.set(DBSensor(Path("path/to/sensor2/temp"),"21.0C",new Timestamp(timeNow-1000)))
      
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new Timestamp(timeNow-3000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new Timestamp(timeNow-2000)))
      db.set(DBSensor(Path("path/to/sensor3/temp"),"21.0C",new Timestamp(timeNow-1000)))
      
      var res = db.getSubData(id,Some(new Timestamp(timeNow))).length
      db.removeSub(id)
      db.remove(Path("path/to/sensor1/temp"))
      db.remove(Path("path/to/sensor2/temp"))
      db.remove(Path("path/to/sensor3/temp"))
      res shouldEqual 9
   }
   "return correct subscriptions with getAllSubs" in
   {
     val time = Some(new Timestamp(1000))
     val id1 = db.saveSub(new DBSub(Array(),0,1,None,time))
     val id2 = db.saveSub(new DBSub(Array(),0,1,None,time))
     val id3 = db.saveSub(new DBSub(Array(),0,1,None,time))
     val id4 = db.saveSub(new DBSub(Array(),0,1,None,time))
     val id5 = db.saveSub(new DBSub(Array(),0,1,Some("addr1"),time))
     val id6 = db.saveSub(new DBSub(Array(),0,1,Some("addr2"),time))
     val id7 = db.saveSub(new DBSub(Array(),0,1,Some("addr3"),time))
     val id8 = db.saveSub(new DBSub(Array(),0,1,Some("addr4"),time))
     val id9 = db.saveSub(new DBSub(Array(),0,1,Some("addr5"),time))
     
     db.getAllSubs(None).length should be >= 9
     db.getAllSubs(Some(true)).length should be >= 5
     db.getAllSubs(Some(false)).length should be >= 4
     db.removeSub(id1)
     db.removeSub(id2)
     db.removeSub(id3)
     db.removeSub(id4)
     db.removeSub(id5)
     db.removeSub(id6)
     db.removeSub(id7)
     db.removeSub(id8)
     db.removeSub(id9)
   }
   "be able to add many values in one go" in{
     db.startBuffering(Path("path/to/setmany/test1"))
     val testdata:List[(Path, OdfValue)] = {
       List(
         (Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1001)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1002)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1003)))),
         (Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1004)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1005)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1006)))),
         (Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1007)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1008)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1009)))),
         (Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1010)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1011)))),(Path("path/to/setmany/test1"),OdfValue("val1", "", Some(new Timestamp(1012)))),
         (Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1013)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1014)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1015)))),
         (Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1016)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1017)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1018)))),
         (Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1019)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1020)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1021)))),
         (Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1022)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1023)))),(Path("path/to/setmany/test2"),OdfValue("val1", "", Some(new Timestamp(1024))))
         )}
     db.setMany(testdata.map(n=> (Path(n._1), n._2)))
     db.getNBetween(Path("path/to/setmany/test1"), None, None,None, None).length shouldEqual 12
     db.getNBetween(Path("path/to/setmany/test2"), None, None,None, None).length shouldEqual 10
     db.stopBuffering(Path("path/to/setmany/test1"))
     db.remove(Path("path/to/setmany/test1"))
     db.remove(Path("path/to/setmany/test2"))
   }
   "be able to save and load metadata for a path" in{
     val metadata = "<meta><infoItem1>value</infoItem1></meta>"
     db.setMetaData(Path("path/to/metaDataTest/test"), metadata)
     db.getMetaData(Path("path/to/metaDataTest/test/fail")) shouldEqual None
     db.getMetaData(Path("path/to/metaDataTest/test")) shouldEqual Some(metadata)
   }
   "close" in{
     db.destroy()
     true shouldEqual true
   }
  }
}
