package responses

import org.specs2.mutable._
import org.specs2.matcher.XmlMatchers._
import scala.io.Source
import responses._
//import responses.Common._
import parsing._
import parsing.Types._
import parsing.Types.Path._
import parsing.Types.OmiTypes._
import database._
import parsing.OdfParser._
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone
import java.text.SimpleDateFormat;
import scala.xml.Utility.trim
import scala.xml.XML
import akka.actor._
import akka.testkit.{TestKit,TestActorRef}
import testHelpers.{BeforeAfterAll, SubscriptionHandlerTestActor}
import scala.concurrent.{ Await, Future }
import scala.collection.mutable.Iterable
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.util.Try

class SubscriptionTest extends Specification with BeforeAfterAll {
  sequential
  
  implicit val system = ActorSystem("on-core")
  implicit val dbConnection1 = new TestDB("subscription-response-test")
  
  val subscriptionHandlerRef = TestActorRef(Props(new SubscriptionHandler{override implicit val dbConnection = dbConnection1}))//[SubscriptionHandler]
//  val subscriptionHandler = subscriptionHandlerRef.underlyingActor
  
  val requestHandler = new RequestHandler(subscriptionHandlerRef)(dbConnection1)
//  val subsResponseGen = new OMISubscription.SubscriptionResponseGen
//  val pollResponseGen = new OMISubscription.PollResponseGen()

  def beforeAll = {
    val calendar = Calendar.getInstance()
    val timeZone = TimeZone.getTimeZone("Etc/GMT+2")
    calendar.setTimeZone(timeZone)
    val date = calendar.getTime
    val testtime = new java.sql.Timestamp(date.getTime)
    dbConnection1.clearDB()
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
      dbConnection1.remove(path)
      dbConnection1.set(path, testtime, value)
    }

    var count = 1000000

    dbConnection1.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    for (value <- intervaltestdata) {
      dbConnection1.set(Path("Objects/ReadTest/SmartOven/Temperature"), new java.sql.Timestamp(date.getTime + count), value)
      count = count + 1000
    }

    //  lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
    //  val parserlist = OmiParser.parse(simpletestfile)

    //  val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.head.asInstanceOf[SubscriptionRequest])

    //  lazy val simpletestfilecallback = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequestWithCallback.xml").getLines.mkString("\n")
    //  val parserlistcallback = OmiParser.parse(simpletestfilecallback)

    //  val (requestIDcallback, xmlreturncallback) = OMISubscription.setSubscription(parserlistcallback.head.asInstanceOf[SubscriptionRequest])
  }
  def afterAll ={
    dbConnection1.destroy()
  }

  "Subscription response" should {
    "Return with just a requestId when subscribed" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
//      parserlist.isRight === true
      val requestOption = parserlist.right.toOption.flatMap(x=> x.headOption.collect({case y: SubscriptionRequest => y}))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
//      val (xmlreturn, requestID) = requestHandler.handleRequest(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])

      val correctxml = requestReturn map(x =>{
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns="odf.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ttl="1.0" version="1.0">
          <omi:response>
            <omi:result>
							<omi:return description="Successfully started subcription" returnCode="200" />
              <omi:requestId>{ x._1.\\("requestId").text}</omi:requestId>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>
      })

      //      trim(xmlreturn.head).toString() === trim(correctxml).toString()
      requestReturn must beSome.which(requestReturn=>correctxml must beSome.which(correct =>requestReturn._1 must beEqualToIgnoringSpace(correct)))
//      xmlreturn must beEqualToIgnoringSpace(correctxml)
    }

    "Return with no values when interval is larger than time elapsed and no callback given" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequestWithLargeInterval.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val requestOption = parserlist.right.toOption.flatMap(x=> x.headOption.collect({case y: SubscriptionRequest => y}))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
      val requestId = Try(requestReturn.map(x => x._1.\\("requestId").text.toInt)).toOption.flatten
//      dbConnection1.getSub(requestId.get) must beSome
      val subxml = requestId.map(id => requestHandler.handleRequest((PollRequest(10, None, asJavaIterable(Seq(id))))))

      val correctxml = requestId map (x=> {
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"></omi:return>
              <omi:requestId>{x}</omi:requestId>
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
      subxml must beSome.which(requestReturn => correctxml must beSome.which(correct=> requestReturn._1 must beEqualToIgnoringSpace(correct)))
//      subxml.head must beEqualToIgnoringSpace(correctxml)

    }

    "Return with right values and requestId in subscription generation" in {
      lazy val simpletestfilecallback = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlistcallback = OmiParser.parse(simpletestfilecallback)
      parserlistcallback.isRight === true
      val requestOption = parserlistcallback.right.toOption.flatMap(x=> x.headOption.collect({case y: SubscriptionRequest => y}))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
      val requestId = Try(requestReturn.map(x => x._1.\\("requestId").text.toInt)).toOption.flatten
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
              <omi:requestId>1</omi:requestId>
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

      subxml must beSome.which(_._1.\\("value").headOption must beSome.which(_.text === "0.123"))//subxml.\\("value").head.text === "0.123"
    }

    "Return error code when asked for nonexisting infoitem" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/BuggyRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val requestOption = parserlist.right.toOption.flatMap(x=> x.headOption.collect({case y: SubscriptionRequest => y}))
      val requestReturn = requestOption.map(x => requestHandler.handleRequest(x))
      val requestId = Try(requestReturn.map(x => x._1.\\("requestId").text.toInt)).toOption.flatten
//      val (requestID, xmlreturn) = requestHandler.handleRequest(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="400" description="No InfoItems found in the paths"></omi:return>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      requestId must beSome(===(-1))// === -1
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
              </omi:return><omi:requestId>1234</omi:requestId>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      //      trim(xmlreturn.head) === trim(correctxml)
      xmlreturn._1.headOption must beSome.which(_ must beEqualToIgnoringSpace(correctxml))
    }
    "Return polled data only once" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection1.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")), 60.0, 1, None, Some(new java.sql.Timestamp(testTime))))
      //      dbConnection1.startBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))

      dbConnection1.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))
      dbConnection1.get(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")) === None

      (0 to 10).foreach(n =>
        dbConnection1.set(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"), new java.sql.Timestamp(testTime + n * 1000), n.toString()))
      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
        //omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      val dataLength = test.\\("value").length
      dataLength must be_>=(10)
      val test2 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      val newDataLength = test2.\\("value").length
      newDataLength must be_<=(dataLength) and be_<=(3)

      dbConnection1.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))

      //      dbConnection1.stopBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))
      dbConnection1.removeSub(testSub)
    }
    
    //this test will be removed when db upgrade is ready
    "TTL should decrease by some multiple of interval" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection1.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")), 60.0, 3, None, Some(new java.sql.Timestamp(testTime))))
      val ttlFirst = dbConnection1.getSub(testSub).map(_.ttl)
      ttlFirst must beSome(60.0)
      (0 to 10).foreach(n =>
        dbConnection1.set(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"), new java.sql.Timestamp(testTime + n * 1000), n.toString()))
      requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))
      requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))
      val ttlEnd = dbConnection1.getSub(testSub).map(_.ttl)
      ttlFirst must beSome.which(first=> ttlEnd must beSome.which(last=> (first-last) % 3 === 0))//(ttlFirst - ttlEnd) % 3 === 0

      dbConnection1.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))
      dbConnection1.removeSub(testSub)
    }
    "Event based subscription without callback should return all the new values when polled" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection1.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).foreach(n =>
        dbConnection1.set(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), new java.sql.Timestamp(testTime - 5000 + n * 1000), n.toString()))
      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      test.\\("value").length === 6
      dbConnection1.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      dbConnection1.removeSub(testSub)

    }
    "Event based subscription without callback should not return already polled data" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection1.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).foreach(n =>
        dbConnection1.set(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), new java.sql.Timestamp(testTime - 5000 + n * 1000), n.toString()))
      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      test.\\("value").length === 6
      val test2 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      test2.\\("value").length === 0
      dbConnection1.set(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), new java.sql.Timestamp(new Date().getTime), "testvalue")
      val test3 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      test3.\\("value").length === 1

      dbConnection1.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      dbConnection1.removeSub(testSub)
    }
    "Event based subscription should return new values only when the value changes" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection1.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).zip(Array(1, 1, 1, 2, 3, 3, 4, 5, 5, 6, 7)).foreach(n =>
        dbConnection1.set(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), new java.sql.Timestamp(testTime + n._1 * 900), n._2.toString()))
      val test = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      test.\\("value").length === 7
      val test2 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      test2.\\("value").length === 0
      dbConnection1.set(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), new java.sql.Timestamp(new Date().getTime), "testvalue")
      val test3 = requestHandler.handleRequest(PollRequest(10, None, Seq(testSub)))._1
      test3.\\("value").length === 1

      dbConnection1.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      dbConnection1.removeSub(testSub)
    }
    "Subscriptions should be removed from database when their ttl expires" in {
      val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n").replaceAll("""ttl="10.0"""", """ttl="1.0"""")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val testSub = requestHandler.handleRequest(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])._2
//      val temp = dbConnection1.getSub(testSub).get
      dbConnection1.getSub(testSub) must beSome
//      Thread.sleep(3000) //NOTE this might need to be uncommented 
      dbConnection1.getSub(testSub) must beNone.eventually(3, new org.specs2.time.Duration(1000))
    }

  }

}




