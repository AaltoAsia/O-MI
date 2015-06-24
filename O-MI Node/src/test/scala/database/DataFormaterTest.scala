//package database
//import org.specs2.specification._
//import org.specs2.execute.AsResult
//import org.specs2.mutable._
//import database._
//import java.sql.Timestamp
//
//import parsing._
//import parsing.Types._
//import parsing.Types.Path._
//import testHelpers.AfterAll
//
//import java.sql.Timestamp
//
//object DataFormaterTest extends Specification {
//  var rnd = new java.util.Random()
//  // trying independent test databases
//  val withDB = new Fixture[DB] {
//    def apply[R: AsResult](f: DB => R) = {
//      val dbConnection = new TestDB("testdb" + rnd.nextInt + ".h2")
//      AsResult(try {
//        f(dbConnection)
//      } finally {
//        dbConnection.destroy()
//      })
//    }
//  }
//
//  "SDataFromater" should {
//
//    "return rigth values when enough values are present" in withDB { implicit dbConnection =>
//      val timeNow = new java.util.Date().getTime
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 9000),"-272C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 8000),"-271C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 7000),"-270C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 6000),"-269C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 5000),"-268C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 4000),"-267C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 3000),"-266C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 2000),"-265C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 1000),"-264C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow),"-263C")
//      
//      var fdata = DataFormater.FormatSubData(Path("path/to/test/sensor1"), new Timestamp(timeNow - 10000), 2, Some(new Timestamp(timeNow)))(dbConnection)
//      
//      fdata.length shouldEqual 5
//      fdata(0).value shouldEqual "-271C"
//      fdata(4).value shouldEqual "-263C"
//    }
//
//    "fill in if some values are missing in the end" in withDB { implicit dbConnection =>
//      dbConnection.remove(Path("path/to/test/sensor1"))
//      val timeNow = new java.util.Date().getTime
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 9000),"-272C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 8000),"-271C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 7000),"-270C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 6000),"-269C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 5000),"-268C")
//      
//      var fdata = DataFormater.FormatSubData(Path("path/to/test/sensor1"), new Timestamp(timeNow - 10000), 2, Some(new Timestamp(timeNow)))(dbConnection)
//      
//      fdata.length shouldEqual 5
//      fdata(0).value shouldEqual "-271C"
//      fdata(4).value shouldEqual "-268C"
//    }
//    "fill in if some values are missing in the beginning and older data is available" in withDB { implicit dbConnection =>
//      dbConnection.remove(Path("path/to/test/sensor1"))
//      val timeNow = new java.util.Date().getTime
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 13000),"-273C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 12000),"-272C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 4000),"-271C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 3000),"-270C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 2000),"-269C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 1000),"-268C")
//      
//      var fdata = DataFormater.FormatSubData(Path("path/to/test/sensor1"), new Timestamp(timeNow - 10000), 2, Some(new Timestamp(timeNow)))(dbConnection)
//      
//      fdata.length shouldEqual 5
//      fdata(0).value shouldEqual "-272C"
//      fdata(4).value shouldEqual "-268C"
//    }
//    // No longer returns null values at the beginning
//    //  "return null for values that are missing in the beginning" in withDB { implicit dbConnection =>
//    //     dbConnection.remove(Path("path/to/test/sensor1"))
//    //     val timeNow = new java.util.Date().getTime
//    //     dbConnection.set(new DBSensor(Path("path/to/test/sensor1"),"-272C",new Timestamp(timeNow-4000)))
//    //     dbConnection.set(new DBSensor(Path("path/to/test/sensor1"),"-271C",new Timestamp(timeNow-3000)))
//    //     dbConnection.set(new DBSensor(Path("path/to/test/sensor1"),"-270C",new Timestamp(timeNow-2000)))
//    //     dbConnection.set(new DBSensor(Path("path/to/test/sensor1"),"-269C",new Timestamp(timeNow-1000)))
//    //     dbConnection.set(new DBSensor(Path("path/to/test/sensor1"),"-268C",new Timestamp(timeNow)))
//    //     var fdata = DataFormater.FormatSubData(Path("path/to/test/sensor1"),new Timestamp(timeNow-10000), 2,Some(new Timestamp(timeNow)))(dbConnection)
//    //     fdata.length shouldEqual 5
//    //     fdata(0) shouldEqual null
//    //     fdata(4).value shouldEqual "-268C"
//    //  }
//    "fill all data with old latest data if no new data available" in withDB { implicit dbConnection =>
//      dbConnection.remove(Path("path/to/test/sensor1"))
//      val timeNow = new java.util.Date().getTime
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 13000),"-272C")
//      dbConnection.set(Path("path/to/test/sensor1"), new Timestamp(timeNow - 12000),"-271C")
//
//      var fdata = DataFormater.FormatSubData(Path("path/to/test/sensor1"), new Timestamp(timeNow - 10000), 2, Some(new Timestamp(timeNow)))(dbConnection)
//      fdata.length shouldEqual 5
//      fdata(0).value shouldEqual "-271C"
//      fdata(4).value shouldEqual "-271C"
//    }
//    "return empty array if absolutely no data is found" in withDB { implicit dbConnection =>
//      dbConnection.remove(Path("path/to/test/sensor1"))
//      val timeNow = new java.util.Date().getTime
//      var fdata = DataFormater.FormatSubData(Path("path/to/test/sensor1"), new Timestamp(timeNow - 10000), 2, Some(new Timestamp(timeNow)))(dbConnection)
//      fdata.length shouldEqual 0
//    }
//  }
//}
