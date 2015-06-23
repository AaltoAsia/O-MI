package responses

import org.specs2.mutable._
import org.specs2.matcher.XmlMatchers._
import scala.io.Source
import responses._
//import responses.Common._
import parsing._
import types._
import types.Path._
import types.OmiTypes._
import database._
import parsing.OdfParser._
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone
import java.text.SimpleDateFormat;
import scala.xml.Utility.trim
import scala.xml.XML
import akka.actor._
import akka.testkit.{ TestKit, TestActorRef }
import testHelpers.{ BeforeAfterAll, SubscriptionHandlerTestActor }
import scala.concurrent.{ Await, Future }
import scala.collection.mutable.Iterable
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.util.Try
import java.sql.Timestamp

class SubscriptionTest extends Specification with BeforeAfterAll {
  sequential

  implicit val system = ActorSystem("on-core")
  implicit val dbConnection = new TestDB("subscription-response-test") 
//  {
//    def setVal(path: Path) = runSync(this.addObjectsI(path, true))
//  }

  def newTimestamp(time: Long = -1L): Timestamp = {
    if (time == -1) {
      new Timestamp(new java.util.Date().getTime)
    } else {
      new java.sql.Timestamp(time)
    }
  }

  val subscriptionHandlerRef = TestActorRef(Props(new SubscriptionHandler()(dbConnection))) //[SubscriptionHandler]
  //  val subscriptionHandler = subscriptionHandlerRef.underlyingActor

  val requestHandler = new RequestHandler(subscriptionHandlerRef)(dbConnection)
  //  val subsResponseGen = new OMISubscription.SubscriptionResponseGen
  //  val pollResponseGen = new OMISubscription.PollResponseGen()

  def beforeAll = {
    val calendar = Calendar.getInstance()
    val timeZone = TimeZone.getTimeZone("Etc/GMT+2")
    calendar.setTimeZone(timeZone)
    val date = calendar.getTime
    val testtime = new java.sql.Timestamp(date.getTime)
    //    dbConnection.clearDB()
    val testData = Map(
      Path("Objects/ReadTest/Refrigerator123/PowerConsumption") -> "0.123",
      Path("Objects/ReadTest/Refrigerator123/RefrigeratorDoorOpenWarning") -> "door closed",
      Path("Objects/ReadTest/Refrigerator123/RefrigeratorProbeFault") -> "Nothing wrong with probe",
      Path("Objects/ReadTest/RoomSensors1/Temperature/Inside") -> "21.2",
      Path("Objects/ReadTest/RoomSensors1/CarbonDioxide") -> "too much",
      Path("Objects/ReadTest/RoomSensors1/Temperature/Outside") -> "12.2",
      Path("Objects/ReadTest/SmartCar/Fuel") -> "30")

    val intervaltestdata = List(
      "100",
      "102",
      "105",
      "109",
      "115",
      "117")

    for ((path, value) <- testData) {
      //      dbConnection.remove(path)
      dbConnection.set(path, testtime, value)
    }

    var count = 1000000

    //    dbConnection.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    for (value <- intervaltestdata) {
      dbConnection.set(Path("Objects/ReadTest/SmartOven/Temperature"), new java.sql.Timestamp(date.getTime + count), value)
      count = count + 1000
    }

    //  lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
    //  val parserlist = OmiParser.parse(simpletestfile)

    //  val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.head.asInstanceOf[SubscriptionRequest])

    //  lazy val simpletestfilecallback = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequestWithCallback.xml").getLines.mkString("\n")
    //  val parserlistcallback = OmiParser.parse(simpletestfilecallback)

    //  val (requestIDcallback, xmlreturncallback) = OMISubscription.setSubscription(parserlistcallback.head.asInstanceOf[SubscriptionRequest])
  }
  def afterAll = {
    dbConnection.destroy()
  }

  "Subscription response" should {
    "Return with just a requestID when subscribed" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      //      parserlist.isRight === true
      val requestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: SubscriptionRequest => y }))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
      //      val (xmlreturn, requestID) = requestHandler.handleRequest(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])

      val correctxml = requestReturn map (x => {
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns="odf.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ttl="1.0" version="1.0">
          <omi:response>
            <omi:result>
              <omi:return description="Successfully started subcription" returnCode="200"/>
              <omi:requestID>{ x._1.\\("requestID").text }</omi:requestID>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>
      })

      //      trim(xmlreturn.head).toString() === trim(correctxml).toString()
      requestReturn must beSome.which(requestReturn => correctxml must beSome.which(correct => requestReturn._1 must beEqualToIgnoringSpace(correct)))
      //      xmlreturn must beEqualToIgnoringSpace(correctxml)
    }

    "Return with no values when interval is larger than time elapsed and no callback given" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequestWithLargeInterval.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val requestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: SubscriptionRequest => y }))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
      val requestId = Try(requestReturn.map(x => x._1.\\("requestID").text.toInt)).toOption.flatten
      //      dbConnection.getSub(requestId.get) must beSome
      val subxml = requestId.map(id => requestHandler.handleRequest((PollRequest(10, None, asJavaIterable(Seq(id))))))

      val correctxml = requestId map (x => {
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"></omi:return>
              <omi:requestID>{ x }</omi:requestID>
              <omi:msg xsi:schemaLocation="odf.xsd odf.xsd" xmlns="odf.xsd">
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>Refrigerator123</id>
                      <InfoItem name="PowerConsumption">
                      </InfoItem>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>
      })

      //      trim(subxml.head) === trim(correctxml)
      subxml must beSome.which(requestReturn => correctxml must beSome.which(correct => requestReturn._1 must beEqualToIgnoringSpace(correct)))
      //      subxml.head must beEqualToIgnoringSpace(correctxml)

    }

    "Return with right values and requestID in subscription generation" in {
      lazy val simpletestfilecallback = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlistcallback = OmiParser.parse(simpletestfilecallback)
      parserlistcallback.isRight === true
      val requestOption = parserlistcallback.right.toOption.flatMap(x => x.headOption.collect({ case y: SubscriptionRequest => y }))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
      val requestId = Try(requestReturn.map(x => x._1.\\("requestID").text.toInt)).toOption.flatten
      //XXX: Stupid hack, for interval      
      Thread.sleep(1000)
      val subxml = requestId.map(id => requestHandler.handleRequest((PollRequest(10, None, asJavaIterable(Seq(id))))))

      //      val (requestIDcallback, xmlreturncallback) = requestHandler.handleRequest(parserlistcallback.right.get.head.asInstanceOf[SubscriptionRequest])

      //      val subxml = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(requestIDcallback))))

      //      val correctxml =
      /*
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"></omi:return>
              <omi:requestID>1</omi:requestID>
              <omi:msg xsi:schemaLocation="odf.xsd odf.xsd" xmlns="odf.xsd">
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>Refrigerator123</id>
                      <InfoItem name="PowerConsumption">
                        <value dateTime="1970-01-17T12:56:15.723">0.123</value>
                      </InfoItem>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope> */

      //      trim(subxml.head) === trim(correctxml)

      subxml must beSome.which(_._1.\\("value").headOption must beSome.which(_.text === "0.123")) //subxml.\\("value").head.text === "0.123"
    }

    "Return error code when asked for nonexisting infoitem" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/BuggyRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val requestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: SubscriptionRequest => y }))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
      val requestId = Try(requestReturn.map(x => x._1.\\("requestID").text.toInt)).toOption.flatten
      //      val (requestID, xmlreturn) = requestHandler.handleRequest(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="400" description="No InfoItems found in the paths"></omi:return>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      requestId must beSome(===(-1)) // === -1
      requestReturn must beSome.which(_._1.headOption must beSome.which(_ must beEqualToIgnoringSpace(correctxml)))
      //xmlreturn.head must beEqualToIgnoringSpace(correctxml)
      //      (requestID, trim(xmlreturn.head)) === (-1, trim(correctxml))
    }

    "Return with error when subscription doesn't exist" in {
      val xmlreturn = requestHandler.handleRequest((PollRequest(10, None, Seq(1234))))

      val correctxml =
        <omi:omiEnvelope xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd">
          <omi:response>
            <omi:result>
              <omi:return returnCode="404" description="A subscription with this id has expired or doesn't exist">
              </omi:return><omi:requestID>1234</omi:requestID>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      //      trim(xmlreturn.head) === trim(correctxml)
      xmlreturn._1.headOption must beSome.which(_ must beEqualToIgnoringSpace(correctxml))
    }
    "Return polled data only once" in {
      val testPath = Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest1")
//      dbConnection.setVal(testPath)

      val testTime = new Date().getTime - 10000
      //      db.saveSub(NewDBSub(1, newTs, 0, None), Array(Path("/Objects/path/to/sensor1"), Path("/Objects/path/to/sensor2")))
//      val testSub = dbConnection.saveSub(NewDBSub(1, newTimestamp(testTime), -1, None), Array(testPath))
      //      dbConnection.startBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))

      //      dbConnection.remove(testPath)
      dbConnection.get(testPath) === None

      (0 to 10).foreach(n =>
        dbConnection.set(testPath, new java.sql.Timestamp(testTime + n * 1000), n.toString()))
      val testSub = dbConnection.saveSub(NewDBSub(1, newTimestamp(testTime), 60.0, None), Array(testPath))
      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      
      //omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))

      val dataLength = test.\\("value").length

      dataLength must be_>=(10)
      val test2 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      val newDataLength = test2.\\("value").length
      newDataLength must be_<=(dataLength) and be_<=(3)

      //      dbConnection.remove(testPath)

      //      dbConnection.stopBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))
      dbConnection.removeSub(testSub)
    }

    //this test will be removed when db upgrade is ready
    "TTL should decrease by some multiple of interval" in {
      val testPath = Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest2")
//      dbConnection.setVal(testPath)

      val testTime = new Date().getTime - 10000
      
//      val ttlFirst = dbConnection.getSub(testSub.id).map(_.ttl)
//      ttlFirst must beSome(60.0)
      (0 to 10).foreach(n =>
        dbConnection.set(testPath, new java.sql.Timestamp(testTime + n * 1000), n.toString()))
        
      val testSub = dbConnection.saveSub(NewDBSub(3, newTimestamp(testTime), 60.0, None), Array(testPath))
      val ttlFirst = dbConnection.getSub(testSub.id).map(_.ttl)
      ttlFirst must beSome(60.0)
      
      requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))
      requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))
      val ttlEnd = dbConnection.getSub(testSub.id).map(_.ttl)
      ttlFirst must beSome.which(first => ttlEnd must beSome.which(last => (first - last) % 3 === 0)) //(ttlFirst - ttlEnd) % 3 === 0

      //      dbConnection.remove(testPath)
      dbConnection.removeSub(testSub)
    }
    "Event based subscription without callback should return all the new values when polled" in {
      val testPath = Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest1")
//      dbConnection.setVal(testPath)

      val testTime = new Date().getTime - 10000
      (0 to 10).foreach(n =>
        dbConnection.set(testPath, new java.sql.Timestamp(testTime - 4999 + n * 1000), n.toString()))
        
      val testSub = dbConnection.saveSub(NewDBSub(-1, newTimestamp(testTime), 60.0, None), Array(testPath))

      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test.\\("value").length === 6
      //      dbConnection.remove(testPath)
      dbConnection.removeSub(testSub)

    }
    "Event based subscription without callback should not return already polled data" in {
      val testPath = Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest2")
//      dbConnection.setVal(testPath)

      val testTime = new Date().getTime - 10000

      (0 to 10).foreach(n =>
        dbConnection.set(testPath, new java.sql.Timestamp(testTime - 4999 + n * 1000), n.toString()))

      val testSub = dbConnection.saveSub(NewDBSub(-1, newTimestamp(testTime), 60.0, None), Array(testPath))
      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test.\\("value").length === 6
      val test2 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test2.\\("value").length === 0
      dbConnection.set(testPath, new java.sql.Timestamp(new Date().getTime), "testvalue")
      val test3 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test3.\\("value").length === 1

      //      dbConnection.remove(testPath)
      dbConnection.removeSub(testSub)
    }
    "Event based subscription should return new values only when the value changes" in {
      val testPath = Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest3")
//      dbConnection.setVal(testPath)

      val testTime = new Date().getTime - 10000
//      val testSub = dbConnection.saveSub(NewDBSub(-1, newTimestamp(testTime), 60.0, None), Array(testPath))
      (0 to 10).zip(Array(1, 1, 1, 2, 3, 4, 3, 5, 5, 6, 7)).foreach(n =>
        dbConnection.set(testPath, new java.sql.Timestamp(testTime + n._1 * 900), n._2.toString()))
        
      val testSub = dbConnection.saveSub(NewDBSub(-1, newTimestamp(testTime), 60.0, None), Array(testPath))
      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test.\\("value").length === 8
      val test2 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test2.\\("value").length === 0
      dbConnection.set(testPath, new java.sql.Timestamp(new Date().getTime), "testvalue")
      val test3 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test3.\\("value").length === 1
      //does not return same value twice
      dbConnection.set(testPath, new java.sql.Timestamp(new Date().getTime), "testvalue")
      val test4 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub.id)))._1
      test4.\\("value").length === 0

      //      dbConnection.remove(testPath)
      dbConnection.removeSub(testSub)
    }
    "Subscriptions should be removed from database when their ttl expires" in {
      val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n").replaceAll("""ttl="10.0"""", """ttl="1.0"""")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val requestReturn = requestHandler.handleRequest(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])._1
      val testSub = requestReturn.\\("requestID").text.toInt
      //      val temp = dbConnection.getSub(testSub).get
      dbConnection.getSub(testSub) must beSome
      //      Thread.sleep(3000) //NOTE this might need to be uncommented 
      dbConnection.getSub(testSub) must beNone.eventually(3, new org.specs2.time.Duration(1000))
    }

  }

}




