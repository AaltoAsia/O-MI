package database

import org.specs2.mutable._
import database._
import java.sql.Timestamp
import parsing.Types.OdfTypes._

import parsing.Types._

object SQLiteTest extends Specification {

  "dbConnection" should {
    sequential
    println("asdasdasdasda1")
    implicit val db = new SQLiteConnection//new TestDB("dbtest")
    println("asdasdasdasda2")
    var data1 = (Path("path/to/sensor1/temp"), new java.sql.Timestamp(1000), "21.5C")
    var data2 = (Path("path/to/sensor1/hum"), new java.sql.Timestamp(2000), "40%")
    var data3 = (Path("path/to/sensor2/temp"), new java.sql.Timestamp(3000), "24.5")
    var data4 = (Path("path/to/sensor2/hum"), new java.sql.Timestamp(4000), "60%")
    var data5 = (Path("path/to/sensor1/temp"), new java.sql.Timestamp(5000), "21.6C")
    var data6 = (Path("path/to/sensor1/temp"), new java.sql.Timestamp(6000), "21.7C")
    println("asdasdasdasda")
    "set test" in {
      println("asdasdasdasda3")
    db.set(data1._1,data1._2,data1._3) shouldEqual true
    println("asdasdasdasda4")
    db.set(data2._1,data2._2,data2._3) shouldEqual true
    println("asdasdasdasda5")
    db.set(data3._1,data3._2,data3._3) shouldEqual true
    db.set(data4._1,data4._2,data4._3) shouldEqual true
    db.set(data5._1,data5._2,data5._3) shouldEqual false
    db.set(data6._1,data6._2,data6._3) shouldEqual false
//    db.get(Path("path/to/sensor1/hum")) must beSome.like({ case OdfInfoItem(_, value, _,_) => value === "40%" })
  }
  }}
/*
// * case class DBSub(
//  val id: Option[Int] = None,
//  val interval: Double,
//  val startTime: Timestamp,
//  val ttl: Double,
//  val callback: Option[String],
//  val lastValue: String // for event polling subs
//) extends SubLike*/
//    
//    
//    //uncomment other tests too from parsing/responses when fixing this
//    var id1 = db.saveSub(new DBSub(Array(Path("path/to/sensor1"), Path("path/to/sensor2")), 0, 1, None, None,""))
//    var id2 = db.saveSub(new DBSub(Array(Path("path/to/sensor1"), Path("path/to/sensor2")), 0, 2, Some("callbackaddress"), None))
//    var id3 = db.saveSub(new DBSub(Array(Path("path/to/sensor1"), Path("path/to/sensor2"), Path("path/to/sensor3"), Path("path/to/another/sensor2")), 100, 2, None, None))
//
//    "return true when adding new data" in {
//      db.set(data1._1,data1._2,data1._3) shouldEqual true
//      db.set(data2._1, data2._2, data2._3) shouldEqual true
//      db.set(data3._1, data3._2, data3._3) shouldEqual true
//      db.set(data4._1, data4._2, data4._3) shouldEqual true
//    }
//
//    "return false when trying to add data with older timestamp" in {
//      //adding many values for one path for testing
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(6000), "21.0C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(7000), "21.1C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(8000), "21.1C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(9000), "21.2C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(10000), "21.2C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(11000), "21.3C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(12000), "21.3C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(13000), "21.4C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(14000), "21.4C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(15000), "21.5C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(16000), "21.5C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(17000), "21.6C")
//      db.set(data6._1, data6._2, data6._3)
//      db.set(data5._1, data5._2, data5._3) shouldEqual false
//    }
//
//    "return correct value for given valid path" in {
//
//      db.get(Path("path/to/sensor1/hum")) must beSome.like({ case DBSensor(_, value, _) => value === "40%" })
//    }
//
//    "return correct value for given valid updated path" in {
//      db.get(Path("path/to/sensor3/temp")) must beSome.like({ case DBSensor(_, value, _) => value === "21.6C" })
//    }
//
//    "return correct childs for given valid path" in {
//      db.get(Path("path/to/sensor1")) must beSome.like({
//        case ob: DBObject => {
//          ob.childs must have size (2)
//
//          val paths: Seq[Path] = ob.childs.map(n => n.path)
//          paths must contain(Path("path/to/sensor1/temp"), Path("path/to/sensor1/hum"))
//        }
//      })
//    }
//
//    "return None for given invalid path" in {
//      db.get(Path("path/to/nosuchsensor")) shouldEqual None
//    }
//
//    "return correct values for given valid path and timestamps" in {
//      val sensors1 = db.getNBetween(Path("path/to/sensor1/temp"), Some(new Timestamp(900)), Some(new Timestamp(5500)), None, None)
//      val values1: Seq[String] = sensors1.map { x => x.value }
//      val sensors2 = db.getNBetween(Path("path/to/sensor1/temp"), Some(new Timestamp(1500)), Some(new Timestamp(6001)), None, None)
//      val values2: Seq[String] = sensors2.map { x => x.value }
//
//      values1 must have size (2)
//      values1 must contain("21.5C", "21.6C")
//
//      values2 must have size (2)
//      values2 must contain("21.7C", "21.6C")
//    }
//
//    "return correct values for N latest values" in {
//      val sensors1 = db.getNBetween(Path("path/to/sensor3/temp"), None, None, None, Some(12))
//      val values1: Seq[String] = sensors1.map { x => x.value }
//      val sensors2 = db.getNBetween(Path("path/to/sensor3/temp"), None, None, None, Some(3))
//      val values2: Seq[String] = sensors2.map { x => x.value }
//
//      values1 must have size (10)
//      values1 must contain("21.1C", "21.6C")
//
//      values2 must have size (3)
//      values2 must contain("21.5C", "21.6C")
//    }
//
//    "return correct values for N oldest values" in {
//      val sensors1 = db.getNBetween(Path("path/to/sensor3/temp"), None, None, Some(12), None)
//      val values1: Seq[String] = sensors1.map { x => x.value }
//      val sensors2 = db.getNBetween(Path("path/to/sensor3/temp"), None, None, Some(2), None)
//      val values2: Seq[String] = sensors2.map { x => x.value }
//
//      values1 must have size (10)
//      values1 must contain("21.1C", "21.6C")
//
//      values2 must have size (2)
//      values2 must contain("21.1C", "21.2C")
//    }
//
//    "return true when removing valid path" in {
//      db.remove(Path("path/to/sensor3/temp"))
//      db.remove(Path("path/to/sensor1/hum")) shouldEqual true
//    }
//
//    "be able to buffer data on demand" in {
//      db.startBuffering(Path("path/to/sensor3/temp"))
//      db.startBuffering(Path("path/to/sensor3/temp"))
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(6000), "21.0C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(7000), "21.1C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(8000), "21.1C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(9000), "21.2C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(10000), "21.2C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(11000), "21.3C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(12000), "21.3C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(13000), "21.4C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(14000), "21.4C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(15000), "21.5C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(16000), "21.5C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(17000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(18000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(19000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(20000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(21000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(22000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(23000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(24000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(25000), "21.6C")
//      db.set(Path("path/to/sensor3/temp"), new java.sql.Timestamp(26000), "21.6C")
//
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        None,
//        None,
//        None,
//        None) must have size (21)
//    }
//
//    "return values between two timestamps" in {
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        Some(new Timestamp(6000)),
//        Some(new Timestamp(10000)),
//        None,
//        None) must have size (5)
//
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        Some(new Timestamp(6000)),
//        Some(new Timestamp(10000)),
//        Some(10),
//        None) must have size (5)
//    }
//
//    "return values from start" in {
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        Some(new Timestamp(20000)),
//        None,
//        None,
//        None) must have size (7)
//    }
//
//    "return values before end" in {
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        None,
//        Some(new Timestamp(10000)),
//        None,
//        None) must have size (5)
//    }
//
//    "return 10 values before end" in {
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        None,
//        Some(new Timestamp(26000)),
//        None,
//        Some(10)) must have size (10)
//    }
//
//    "return 10 values after start" in {
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        Some(new Timestamp(6000)),
//        None,
//        Some(10),
//        None) must have size (10)
//    }
//
//    "return all values if no options given" in {
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        None,
//        None,
//        None,
//        None) must have size (21)
//    }
//
//    "return all values if both fromStart and fromEnd is given" in {
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        None,
//        None,
//        Some(10),
//        Some(5)) must have size (21)
//    }
//
//    "return empty array if Path doesnt exist" in {
//      db.getNBetween(
//        Path("path/to/sensor/doesntexist"),
//        None,
//        None,
//        None,
//        None) must be empty
//    }
//
//    "should not rever to historyLength if other are still buffering" in {
//
//      db.stopBuffering(Path("path/to/sensor3/temp"))
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        None,
//        None,
//        None,
//        None) must have size (21)
//    }
//
//    "be able to stop buffering and revert to using historyLenght" in {
//      db.stopBuffering(Path("path/to/sensor3/temp"))
//      db.getNBetween(
//        Path("path/to/sensor3/temp"),
//        None,
//        None,
//        None,
//        None) must have size (10)
//    }
//
//    "return true when removing valid path" in {
//      db.remove(Path("path/to/sensor3/temp"))
//      db.remove(Path("path/to/sensor1/temp")) shouldEqual true
//    }
//
//    "return false when trying to remove object from the middle" in {
//      db.remove(Path("path/to/sensor2")) shouldEqual false
//    }
//
//    "return true when removing valid path" in {
//      db.remove(Path("path/to/sensor2/temp")) shouldEqual true
//      db.remove(Path("path/to/sensor2/hum")) shouldEqual true
//    }
//
//    "return None when searching non existent object" in {
//      db.get(Path("path/to/sensor2")) shouldEqual None
//      db.get(Path("path/to/sensor1")) shouldEqual None
//    }
//
//    "return correct callback adress for subscriptions" in {
//      db.getSub(id1).get.callback shouldEqual None
//      db.getSub(id2).get.callback.get shouldEqual "callbackaddress"
//      db.getSub(id3).get.callback shouldEqual None
//    }
//
//    "return correct boolean whether subscription is expired" in {
//      db.isExpired(id1) shouldEqual true
//      db.isExpired(id2) shouldEqual true
//      db.isExpired(id3) shouldEqual false
//    }
//
//    "return correct paths as array" in {
//      db.getSub(id1) must beSome.which(_.paths must have size (2))
//      db.getSub(id2) must beSome.which(_.paths must have size (2))
//      db.getSub(id3) must beSome.which(_.paths must have size (4))
//    }
//
//    "return None for removed subscriptions" in {
//      db.removeSub(id1)
//      db.removeSub(id2)
//      db.removeSub(id3)
//      db.getSub(id1) must beNone
//      db.getSub(id2) must beNone
//      db.getSub(id3) must beNone
//    }
//
//    "return right values in getsubdata" in {
//      val timeNow = new java.util.Date().getTime
//      val id = db.saveSub(new DBSub(Array(Path("path/to/sensor1/temp"), Path("path/to/sensor2/temp"), Path("path/to/sensor3/temp")), 0, 1, None,
//        Some(new Timestamp(timeNow - 3500))))
//
//      db.set(Path("path/to/sensor1/temp"), new Timestamp(timeNow - 3000), "21.0C")
//      db.set(Path("path/to/sensor1/temp"), new Timestamp(timeNow - 2000), "21.0C")
//      db.set(Path("path/to/sensor1/temp"), new Timestamp(timeNow - 1000), "21.0C")
//
//      db.set(Path("path/to/sensor2/temp"), new Timestamp(timeNow - 3000), "21.0C")
//      db.set(Path("path/to/sensor2/temp"), new Timestamp(timeNow - 2000), "21.0C")
//      db.set(Path("path/to/sensor2/temp"), new Timestamp(timeNow - 1000), "21.0C")
//
//      db.set(Path("path/to/sensor3/temp"), new Timestamp(timeNow - 3000), "21.0C")
//      db.set(Path("path/to/sensor3/temp"), new Timestamp(timeNow - 2000), "21.0C")
//      db.set(Path("path/to/sensor3/temp"), new Timestamp(timeNow - 1000), "21.0C")
//
//      val res = db.getSubData(id, Some(new Timestamp(timeNow)))
//      db.removeSub(id)
//      db.remove(Path("path/to/sensor1/temp"))
//      db.remove(Path("path/to/sensor2/temp"))
//      db.remove(Path("path/to/sensor3/temp"))
//      res must have size (9)
//    }
//
//    "return correct subscriptions with getAllSubs" in
//      {
//        val time = Some(new Timestamp(1000))
//        val id1 = db.saveSub(new DBSub(Array(), 0, 1, None, time))
//        val id2 = db.saveSub(new DBSub(Array(), 0, 1, None, time))
//        val id3 = db.saveSub(new DBSub(Array(), 0, 1, None, time))
//        val id4 = db.saveSub(new DBSub(Array(), 0, 1, None, time))
//        val id5 = db.saveSub(new DBSub(Array(), 0, 1, Some("addr1"), time))
//        val id6 = db.saveSub(new DBSub(Array(), 0, 1, Some("addr2"), time))
//        val id7 = db.saveSub(new DBSub(Array(), 0, 1, Some("addr3"), time))
//        val id8 = db.saveSub(new DBSub(Array(), 0, 1, Some("addr4"), time))
//        val id9 = db.saveSub(new DBSub(Array(), 0, 1, Some("addr5"), time))
//
//        db.getAllSubs(None).length should be >= 9
//        db.getAllSubs(Some(true)).length should be >= 5
//        db.getAllSubs(Some(false)).length should be >= 4
//        db.removeSub(id1)
//        db.removeSub(id2)
//        db.removeSub(id3)
//        db.removeSub(id4)
//        db.removeSub(id5)
//        db.removeSub(id6)
//        db.removeSub(id7)
//        db.removeSub(id8)
//        db.removeSub(id9)
//      }
//    "be able to add many values in one go" in {
//      db.clearDB()
//      db.startBuffering(Path("path/to/setmany/test1"))
//      val testdata: List[(Path, OdfValue)] = {
//        List(
//          (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1001)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1002)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1003)))),
//          (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1004)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1005)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1006)))),
//          (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1007)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1008)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1009)))),
//          (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1010)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1011)))), (Path("path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1012)))),
//          (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1013)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1014)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1015)))),
//          (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1016)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1017)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1018)))),
//          (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1019)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1020)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1021)))),
//          (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1022)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1023)))), (Path("path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1024)))))
//      }
//      val pathValuePairs = testdata.map(n => (Path(n._1), n._2))
//      db.setMany(pathValuePairs)
//      db.getNBetween(Path("path/to/setmany/test1"), None, None, None, None) must have size (12)
//      db.getNBetween(Path("path/to/setmany/test2"), None, None, None, None) must have size (10)
//      db.stopBuffering(Path("path/to/setmany/test1"))
//      db.remove(Path("path/to/setmany/test1"))
//      db.remove(Path("path/to/setmany/test2"))
//    }
//    "be able to save and load metadata for a path" in {
//      val metadata = "<meta><infoItem1>value</infoItem1></meta>"
//      db.setMetaData(Path("path/to/metaDataTest/test"), metadata)
//      db.getMetaData(Path("path/to/metaDataTest/test/fail")) shouldEqual None
//      db.getMetaData(Path("path/to/metaDataTest/test")) shouldEqual Some(metadata)
//    }
//    //    "close" in {
//    //      db.destroy()
//    //      true shouldEqual true
//    //    }
//  }
//}
