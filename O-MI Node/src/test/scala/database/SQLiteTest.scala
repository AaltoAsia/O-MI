package database

import org.specs2.mutable._
import database._
import java.sql.Timestamp
import types.OdfTypes._
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable
//import scala.collection.JavaConversions.i
import testHelpers.AfterAll

import types._

object SQLiteTest extends Specification with AfterAll {

//  implicit def javaIterableToSeq[T](x: java.lang.Iterable[T]): Seq[T] = {
//    iterableAsScalaIterable(x).toSeq
//  }
  implicit def seqAsjavaIterable[T](x: Seq[T]): java.lang.Iterable[T] = {
    asJavaIterable(x.toIterable)
  }
  def newTs = new Timestamp(new java.util.Date().getTime)
  implicit val db = new TestDB("dbtest")

  def afterAll = db.destroy()

  def pathToInfoItemIterable(x: Path) = {
    Iterable(OdfInfoItem(x, Iterable()))

  }
  def OdfObjectsToValues(x: OdfObjects): Seq[String] = {
    val temp1 = getLeafs(x)
    val temp = temp1.toSeq.collect{case c: OdfInfoItem => c.values}.flatten.map(_.value)
    temp
//    val temp2: Option[Seq[OdfInfoItem]] = temp1.headOption.map(_.infoItems)
//    val temp3: Option[Seq[OdfValue]] = temp2.flatMap(_.headOption.map(_.values))
//    temp3.map(x => x.map(_.value))
  }
  def OdfObjectsToPaths(x: OdfObjects): Seq[Path] = {
    val temp1: Seq[OdfObject] = x.objects.toSeq
    temp1.map(n => n.path)
  }
  val testSub1 = db.saveSub(NewDBSub(-1, newTs, -1, None), Array(Path("/Objects/path/to/sensor3/temp")))
  val testSub2 = db.saveSub(NewDBSub(-1, newTs, -1, None), Array(Path("/Objects/path/to/sensor3/temp")))

  "dbConnection" should {
    sequential
    var data1 = (Path("/Objects/path/to/sensor1/temp"), new java.sql.Timestamp(1000), "21.5C")
    var data2 = (Path("/Objects/path/to/sensor1/hum"), new java.sql.Timestamp(2000), "40%")
    var data3 = (Path("/Objects/path/to/sensor2/temp"), new java.sql.Timestamp(3000), "24.5")
    var data4 = (Path("/Objects/path/to/sensor2/hum"), new java.sql.Timestamp(4000), "60%")
    var data5 = (Path("/Objects/path/to/sensor1/temp"), new java.sql.Timestamp(5000), "21.6C")
    var data6 = (Path("/Objects/path/to/sensor1/temp"), new java.sql.Timestamp(6000), "21.7C")

    //uncomment other tests too from parsing/responses when fixing this
    var id1 = db.saveSub(NewDBSub(1, newTs, 0, None), Array(Path("/Objects/path/to/sensor1"), Path("/Objects/path/to/sensor2")))
    var id2 = db.saveSub(NewDBSub(2, newTs, 0, Some("callbackaddress")), Array(Path("/Objects/path/to/sensor1"), Path("/Objects/path/to/sensor2")))
    var id3 = db.saveSub(NewDBSub(2, newTs, 100, None), Array(Path("/Objects/path/to/sensor1"), Path("/Objects/path/to/sensor2"), Path("/Objects/path/to/sensor3"), Path("/Objects/path/to/another/sensor2")))

    "return true when adding new data" in {
      db.set(data1._1, data1._2, data1._3) 
      db.set(data2._1, data2._2, data2._3) === 0
      db.set(data3._1, data3._2, data3._3) === 0 
      db.set(data4._1, data4._2, data4._3) === 0 
//      db.set(data5._1, data5._2, data5._3) ===0 // shouldEqual false
//      db.set(data6._1, data6._2, data6._3) ===0
    }

    "return false when trying to add data with older timestamp" in {
      //adding many values for one path for testing
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(6000), "21.0C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(7000), "21.1C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(8000), "21.1C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(9000), "21.2C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(10000), "21.2C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(11000), "21.3C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(12000), "21.3C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(13000), "21.4C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(14000), "21.4C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(15000), "21.5C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(16000), "21.5C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(17000), "21.6C")
      db.set(data6._1, data6._2, data6._3) === 0 
      db.set(data5._1, data5._2, data5._3) === 0 
    }

    "return correct value for given valid path" in {
      db.get(Path("/Objects/path/to/sensor1/hum")) must beSome.like { case OdfInfoItem(_, value, _, _) => iterableAsScalaIterable(value).headOption.map(_.value) must beSome(===("40%")) }
    }

    "return correct value for given valid updated path" in {
      db.get(Path("/Objects/path/to/sensor3/temp")) must beSome.like { case OdfInfoItem(_, value, _, _) => iterableAsScalaIterable(value).headOption.map(_.value) must beSome(===("21.6C")) }
    }

    "return correct childs for given valid path" in {
      db.get(Path("/Objects/path/to/sensor1")) must beSome.like {
        case ob: OdfObject => {
          val infoitems: Seq[OdfInfoItem] = ob.infoItems.toSeq
          infoitems must have size (2)
          val paths = infoitems.map(n => n.path)
          paths must contain(Path("/Objects/path/to/sensor1/temp"), Path("/Objects/path/to/sensor1/hum"))
        }
      }
    }

    "return None for given invalid path" in {
      db.get(Path("/Objects/path/to/nosuchsensor")) shouldEqual None
    }

    "return correct values for given valid path and timestamps" in {
      val sensors1 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/sensor1/temp")), Some(new Timestamp(900)), Some(new Timestamp(5500)), None, None) //.getNBetween(Iterable(OdfInfoItem(Path("/Objects/path/to/sensor1/temp")), ), Some(new Timestamp(900)), Some(new Timestamp(5500)), None, None)
      
      val values1: Option[Seq[String]] = sensors1.map { x => OdfObjectsToValues(x) }
      val sensors2 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/sensor1/temp")), Some(new Timestamp(1500)), Some(new Timestamp(6001)), None, None)
      val values2: Option[Seq[String]] = sensors2.map { x => OdfObjectsToValues(x) }

      values1 must beSome.which(_ must have size (2)) // must have size (2)
      values1 must beSome.which(_ must contain("21.5C", "21.6C"))

      values2 must beSome.which(_ must have size (2))
      values2 must beSome.which(_ must contain("21.7C", "21.6C"))
    }

    "return correct values for N latest values" in {
      val sensors1 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")), None, None, Some(12), None)
      val values1: Option[Seq[String]] = sensors1.map { x => OdfObjectsToValues(x) }
      val sensors2 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")), None, None, Some(3), None)
      val values2: Option[Seq[String]] = sensors2.map { x => OdfObjectsToValues(x) }

      values1 must beSome.which(_ must have size (10))
      values1 must beSome.which(_ must contain("21.1C", "21.6C"))

      values2 must beSome.which(_ must have size (3))
      values2 must beSome.which(_ must contain("21.5C", "21.6C"))
    }

    "return correct values for N oldest values" in {
      val sensors1 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")), None, None, None, Some(12))
      val values1: Option[Seq[String]] = sensors1.map { x => OdfObjectsToValues(x) }
      val sensors2 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")), None, None, None, Some(2))
      val values2: Option[Seq[String]] = sensors2.map { x => OdfObjectsToValues(x) }

      values1 must beSome.which(_ must have size (10))
      values1 must beSome.which(_ must contain("21.1C", "21.6C"))

      values2 must beSome.which(_ must have size (2))
      values2 must beSome.which(_ must contain("21.1C", "21.2C"))
    }

    "return true when removing valid path" in {
      db.remove(Path("/Objects/path/to/sensor3/temp"))
      db.remove(Path("/Objects/path/to/sensor1/hum")) shouldEqual true
    }
    

    "be able to buffer data on demand" in {

      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(6000), "21.0C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(7000), "21.1C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(8000), "21.1C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(9000), "21.2C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(10000), "21.2C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(11000), "21.3C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(12000), "21.3C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(13000), "21.4C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(14000), "21.4C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(15000), "21.5C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(16000), "21.5C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(17000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(18000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(19000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(20000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(21000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(22000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(23000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(24000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(25000), "21.6C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new java.sql.Timestamp(26000), "21.6C")

      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        None,
        None,
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (21))

    }

    "return values between two timestamps" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        Some(new Timestamp(6000)),
        Some(new Timestamp(10000)),
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (5))

      val temp3 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        Some(new Timestamp(6000)),
        Some(new Timestamp(10000)),
        Some(10),
        None)
      val temp4 = temp3.map(OdfObjectsToValues(_))
      temp4 must beSome.which(_ must have size (5))
    }

    "return values from start" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        Some(new Timestamp(20000)),
        None,
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (7))
    }

    "return values before end" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        None,
        Some(new Timestamp(10000)),
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (5))
    }

    "return 10 values before end" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        None,
        Some(new Timestamp(26000)),
        None,
        Some(10))
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (10))
    }

    "return 10 values after start" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        Some(new Timestamp(6000)),
        None,
        Some(10),
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (10))
    }

    "return all values if no options given" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        None,
        None,
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (21))
    }

    "return all values if both fromStart and fromEnd is given" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        None,
        None,
        Some(10),
        Some(5))
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (21))
    }

    "return empty array if Path doesnt exist" in {
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor/doesntexist")),
        None,
        None,
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beNone
    }

    "should not rever to historyLength if other are still buffering" in {

      db.removeSub(testSub1)
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        None,
        None,
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (21))
    }

    "be able to stop buffering and revert to using historyLenght" in {
      db.removeSub(testSub2)
      val temp1 = db.getNBetween(
        pathToInfoItemIterable(Path("/Objects/path/to/sensor3/temp")),
        None,
        None,
        None,
        None)
      val temp2 = temp1.map(OdfObjectsToValues(_))
      temp2 must beSome.which(_ must have size (10))
    }

    "return true when removing valid path" in {
      db.remove(Path("/Objects/path/to/sensor3/temp"))
      db.remove(Path("/Objects/path/to/sensor1/temp")) shouldEqual true
    }

    "return false when trying to remove object from the middle" in {
      db.remove(Path("/Objects/path/to/sensor2")) shouldEqual false
    }

    "return true when removing valid path" in {
      db.remove(Path("/Objects/path/to/sensor2/temp")) shouldEqual true
      db.remove(Path("/Objects/path/to/sensor2/hum")) shouldEqual true
    }

    "return None when searching non existent object" in {
      db.get(Path("/Objects/path/to/sensor2")) shouldEqual None
      db.get(Path("/Objects/path/to/sensor1")) shouldEqual None
    }

    "return correct callback adress for subscriptions" in {
      db.getSub(id1.id).get.callback shouldEqual None
      db.getSub(id2.id).get.callback.get shouldEqual "callbackaddress"
      db.getSub(id3.id).get.callback shouldEqual None
    }

    //    "return correct boolean whether subscription is expired" in {
    //      db.isExpired(id1) shouldEqual true
    //      db.isExpired(id2) shouldEqual true
    //      db.isExpired(id3) shouldEqual false
    //    }

    "return correct paths as array" in {
      db.getSubData(id1.id) must beSome.which(OdfObjectsToPaths(_) must have size (2))
      db.getSubData(id2.id) must beSome.which(OdfObjectsToPaths(_) must have size (2))
      db.getSubData(id3.id) must beSome.which(OdfObjectsToPaths(_) must have size (4))
    }

    "return None for removed subscriptions" in {
      db.removeSub(id1)
      db.removeSub(id2)
      db.removeSub(id3)
      db.getSub(id1.id) must beNone
      db.getSub(id2.id) must beNone
      db.getSub(id3.id) must beNone
    }
    /*
    case class NewDBSub(
  val interval: Double,
  val startTime: Timestamp,
  val ttl: Double,
  val callback: Option[String]
) extends SubLike with DBSubInternal*/
    "return right values in getsubdata" in {
      val timeNow = new java.util.Date().getTime
      val id = db.saveSub(NewDBSub(1, new Timestamp(timeNow - 3500), 0, None), Array(Path("/Objects/path/to/sensor1/temp"), Path("/Objects/path/to/sensor2/temp"), Path("/Objects/path/to/sensor3/temp")))

      db.set(Path("/Objects/path/to/sensor1/temp"), new Timestamp(timeNow - 3000), "21.0C")
      db.set(Path("/Objects/path/to/sensor1/temp"), new Timestamp(timeNow - 2000), "21.0C")
      db.set(Path("/Objects/path/to/sensor1/temp"), new Timestamp(timeNow - 1000), "21.0C")

      db.set(Path("/Objects/path/to/sensor2/temp"), new Timestamp(timeNow - 3000), "21.0C")
      db.set(Path("/Objects/path/to/sensor2/temp"), new Timestamp(timeNow - 2000), "21.0C")
      db.set(Path("/Objects/path/to/sensor2/temp"), new Timestamp(timeNow - 1000), "21.0C")

      db.set(Path("/Objects/path/to/sensor3/temp"), new Timestamp(timeNow - 3000), "21.0C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new Timestamp(timeNow - 2000), "21.0C")
      db.set(Path("/Objects/path/to/sensor3/temp"), new Timestamp(timeNow - 1000), "21.0C")

      val res = db.getSubData(id.id) //), Some(new Timestamp(timeNow)))
      db.removeSub(id)
      db.remove(Path("/Objects/path/to/sensor1/temp"))
      db.remove(Path("/Objects/path/to/sensor2/temp"))
      db.remove(Path("/Objects/path/to/sensor3/temp"))
      res must beSome.which(OdfObjectsToPaths(_) must have size (9))
    }

    "return correct subscriptions with getAllSubs" in
      {
        val time = Some(new Timestamp(1000))
        val id1 = db.saveSub(new NewDBSub(1, newTs, 0, None), Array[Path]())
        val id2 = db.saveSub(new NewDBSub(1, newTs, 0, None), Array[Path]())
        val id3 = db.saveSub(new NewDBSub(1, newTs, 0, None), Array[Path]())
        val id4 = db.saveSub(new NewDBSub(1, newTs, 0, None), Array[Path]())
        val id5 = db.saveSub(new NewDBSub(1, newTs, 0, Some("addr1")), Array[Path]())
        val id6 = db.saveSub(new NewDBSub(1, newTs, 0, Some("addr2")), Array[Path]())
        val id7 = db.saveSub(new NewDBSub(1, newTs, 0, Some("addr3")), Array[Path]())
        val id8 = db.saveSub(new NewDBSub(1, newTs, 0, Some("addr4")), Array[Path]())
        val id9 = db.saveSub(new NewDBSub(1, newTs, 0, Some("addr5")), Array[Path]())

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
    "be able to add many values in one go" in {
      db.clearDB()
      val testSub3 = db.saveSub(NewDBSub(-1, newTs, -1, None), Array(Path("/Objects/path/to/setmany/test1")))

      //      db.startBuffering(Path("/Objects/path/to/setmany/test1"))
      val testdata: List[(Path, OdfValue)] = {
        List(
          (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1001)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1002)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1003)))),
          (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1004)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1005)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1006)))),
          (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1007)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1008)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1009)))),
          (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1010)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1011)))), (Path("/Objects/path/to/setmany/test1"), OdfValue("val1", "", Some(new Timestamp(1012)))),
          (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1013)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1014)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1015)))),
          (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1016)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1017)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1018)))),
          (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1019)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1020)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1021)))),
          (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1022)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1023)))), (Path("/Objects/path/to/setmany/test2"), OdfValue("val1", "", Some(new Timestamp(1024)))))
      }
      val pathValuePairs = testdata.map(n => (Path(n._1), n._2))
      db.setMany(pathValuePairs)
      val temp1 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/setmany/test1")), None, None, None, None)
      val values1 = temp1.map(OdfObjectsToValues(_))
      values1 must beSome.which(_ must have size (12))
      val temp2 = db.getNBetween(pathToInfoItemIterable(Path("/Objects/path/to/setmany/test2")), None, None, None, None)
      val values2 = temp1.map(OdfObjectsToValues(_))
      values2 must beSome.which(_ must have size (10))
      db.removeSub(testSub3.id)
      db.remove(Path("/Objects/path/to/setmany/test1"))
      db.remove(Path("/Objects/path/to/setmany/test2"))
    }
    "be able to save and load metadata for a path" in {
      val metadata = "<meta><infoItem1>value</infoItem1></meta>"
      db.setMetaData(Path("/Objects/path/to/metaDataTest/test"), metadata)
      db.getMetaData(Path("/Objects/path/to/metaDataTest/test/fail")) shouldEqual None
      db.getMetaData(Path("/Objects/path/to/metaDataTest/test")) shouldEqual Some(metadata)
    }
    //    "close" in {
    //      db.destroy()
    //      true shouldEqual true
    //    }
  }
}
