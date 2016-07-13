package http
import java.text.SimpleDateFormat
import java.util.TimeZone

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try
import scala.xml._

import agentSystem.AgentSystem
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import database._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll
import responses.{RequestHandler, SubscriptionManager}
import testHelpers.{BeEqualFormatted, HTML5Parser, SystemTestCallbackServer}

class SystemTest(implicit ee: ExecutionEnv) extends Specification with Starter with BeforeAfterAll {

  // TODO: better cleaning after tests
  def beforeAll() = {
    SingleStores.hierarchyStore execute TreeRemovePath(types.Path("/Objects"))
  }

  val conf = ConfigFactory.load("testconfig")
  val testSettings = new OmiConfigExtension(
    conf
  )

  override val settings = testSettings

  //start the program
  implicit val dbConnection = new TestDB("SystemTest")


  override val subManager = system.actorOf(SubscriptionManager.props(), "subscription-handler-test")

  override def start(dbConnection: DB): OmiServiceImpl = {
    val agentManager = system.actorOf(
      Props({val as = new AgentSystem(dbConnection,subManager) {
        override val settings = testSettings
      }
      as.start()
      as}),
      "agent-system"
    )
    
    val requestHandler = new RequestHandler(subManager, agentManager)(dbConnection)

    // create omi service actor
    val omiService = new OmiServiceImpl(requestHandler)

    implicit val timeoutForBind = Timeout(Duration.apply(5, "second"))


    omiService
  }

  sequential



  bindHttp(start(dbConnection))
  implicit val materializer = ActorMaterializer()
  val probe = TestProbe()
  val testServer = new SystemTestCallbackServer(probe.ref, "localhost", 20002)
  val http = Http(system)



  val printer = new scala.xml.PrettyPrinter(80, 2)
  val parser = new HTML5Parser
  val sourceFile = if(java.nio.file.Files.exists(java.nio.file.Paths.get("O-MI Node/html/ImplementationDetails.html"))){
    Source.fromFile("O-MI Node/html/ImplementationDetails.html")
  }else Source.fromFile("html/ImplementationDetails.html")
  val sourceXML: Node = parser.loadXML(sourceFile)
  val testArticles = sourceXML \\ ("article")
  val tests = testArticles.groupBy(x => x.\@("class"))

  //tests with request response pairs, each containing description and forming single test(req, resp), (req, resp)...
  lazy val readTests = tests("request-response single test").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length == 2, s"Each request must have exactly 1 response in request-response tests, could not find for: $node")
    val request: Try[Elem] = getSingleRequest(textAreas)
    val correctResponse: Try[Elem] = getSingleResponse(textAreas)
    val testDescription = node \ ("div") \ ("p") text

    (request, correctResponse, testDescription)
  }

  //tests that have multiple request-response pairs that all have common description (req, resp, req, resp...)
  lazy val subsNoCallback = tests("request-response test").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length % 2 == 0, "There must be even amount of response and request messages(1 response for each request)\n" + textAreas)

    val testDescription: String = node \ ("div") \ ("p") text

    val groupedRequests = textAreas.grouped(2).map { reqresp =>
      val request: Try[Elem] = getSingleRequest(reqresp)
      val correctresponse: Try[Elem] = getSingleResponseNoTime(reqresp)
      val responseWait: Option[Int] = Try(reqresp.last.\@("wait").toInt).toOption
      (request, correctresponse, responseWait)
    }
    (groupedRequests, testDescription)

  }
  /*
   * tests that have callback server responses mixed:
   * for example if the test in html is (req, resp, callbackresp, callbackresp, req, resp)
   * this groups them like: (req, resp), (callbackresp), (callbackresp), (req,resp)
   * so that each req resp pair forms 1 test and callbackresp alone forms 1 test
   */
  lazy val sequentialTest = tests("sequential-test").map { node =>
    val textAreas = node \\ ("textarea")
    val testDescription: String = node \ ("div") \ ("p") text
    val reqrespCombined: Seq[NodeSeq] = textAreas.foldLeft[Seq[NodeSeq]](NodeSeq.Empty) { (res, i) =>
      if (res.isEmpty) i
      else {
        if (res.last.length == 1 && (res.last.head.\@("class") == "request")) {
          val indx: Int = res.lastIndexWhere { x => x.head.\@("class") == "request" }
          res.updated(indx, res.last.head :+ i)

        } else res.:+(i) 
      }
    }

    (reqrespCombined, testDescription)
  }

  def afterAll = {
    Await.ready(system.terminate(), 2 seconds)
    dbConnection.destroy()
    SingleStores.hierarchyStore execute TreeRemovePath(types.Path("/Objects"))

  }

  def getPostRequest(in: NodeSeq): HttpRequest = {
    RequestBuilding.Post("http://localhost:8080", in)
  }
  def getSingleRequest(reqresp: NodeSeq): Try[Elem] = {
    require(reqresp.length >= 1)
    Try(XML.loadString(setTimezoneToSystemLocale(reqresp.head.text)))
  }

  def removeTimes( text: String) : String =removeUnixTime(removeDateTime(text))
  def removeDateTime( text: String) : String =text.replaceAll(
    """dateTime\s*=\s*"\S*?"""",
    ""
  )
  def removeUnixTime( text: String) : String =text.replaceAll(
    """unixTime\s*=\s*"\d*"""",
    ""
  )

  def getSingleResponseNoTime(reqresp: NodeSeq): Try[Elem] = {
    Try(
      XML.loadString(
        setTimezoneToSystemLocale(
          removeTimes(reqresp.last.text)
        )
      )
    )
  }
  def getSingleResponse(reqresp: NodeSeq): Try[Elem] = {
    Try(XML.loadString(setTimezoneToSystemLocale(removeDateTime(reqresp.last.text))))
  }
  def getCallbackRequest(reqresp: NodeSeq): Try[Elem] = {
    require(reqresp.length >= 1)
    Try(XML.loadString(
      setTimezoneToSystemLocale(
        reqresp.head.text.replaceAll(
          """callback\s*=\s*"(http:\/\/callbackAddress\.com:5432)"""",
          """callback="http://localhost:20002/"""")
        )
      )
    )
  }

  def setTimezoneToSystemLocale(in: String): String = {
    val date = """(end|begin)\s*=\s*"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})"""".r

    val replaced = date replaceAllIn (in, _ match {

      case date(pref, timestamp) => {

        val form = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        form.setTimeZone(TimeZone.getTimeZone("UTC"))

        val parsedTimestamp = form.parse(timestamp)

        form.setTimeZone(TimeZone.getDefault)

        val newTimestamp = form.format(parsedTimestamp)

        (pref + "=\"" + newTimestamp + "\"")
      }
    })
    replaced
  }

  "Automatic System Tests" should {
    "Write Test" >> {
      dbConnection.clearDB()
      //Only 1 write test
      val writearticle = tests("write test").head
      val testCase = writearticle \\ ("textarea")
      val request: Try[Elem] = getSingleRequest(testCase)
      val correctResponse: Try[Elem] = getSingleResponse(testCase)
      val testDescription = writearticle \ ("div") \ ("p") text

      ("test case:\n " + testDescription.trim + "\n") >> {
        request aka "Write request message" must beSuccessfulTry
        correctResponse aka "Correct write response message" must beSuccessfulTry

        val responseFuture = http.singleRequest(getPostRequest(request.get))//pipeline(Post("http://localhost:8080/", request.get))

        val response = Try(Await.result(responseFuture.flatMap(n => Unmarshal(n).to[NodeSeq]), Duration(10, "second")))

        response must beSuccessfulTry

        response.get showAs (n =>
          "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)
        ) must new BeEqualFormatted(correctResponse.get)
      }
    }

    step({
      Thread.sleep(2000);
    })
    
    "Read Test" >> {
      readTests.foldLeft(org.specs2.specification.core.Fragments.empty)((res, i) => {
        val (request, correctResponse, testDescription) = i
        (testDescription.trim + "\n") in {

          request aka "Read request message" must beSuccessfulTry
          correctResponse aka "Correct read response message" must beSuccessfulTry

          val responseFuture = http.singleRequest(getPostRequest(request.get))
          val responseXML = Try(Await.result(responseFuture.flatMap(Unmarshal(_).to[NodeSeq]), Duration(2, "second")))

          responseXML must beSuccessfulTry
          val response = XML.loadString(removeDateTime(responseXML.get.toString))
          response showAs (n =>
            "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)
          ) must new BeEqualFormatted(correctResponse.get)
        }
      })
    }


    "Subscription Test" >> {
      subsNoCallback.foldLeft(org.specs2.specification.core.Fragments.empty)((res, i) => {
        val (reqrespwait, testDescription) = i
        (testDescription.trim() + "\n") >> {
          reqrespwait.foldLeft(org.specs2.specification.core.Fragments.empty)((res, j) => {
            "step: " >> {
              val (request, correctResponse, responseWait) = j
              request aka "Subscription request message" must beSuccessfulTry
              correctResponse aka "Correct response message" must beSuccessfulTry

              responseWait.foreach { x => Thread.sleep(x * 1000) }

              val responseFuture = http.singleRequest(getPostRequest(request.get))
              val responseXml = Try(Await.result(responseFuture.flatMap(Unmarshal(_).to[NodeSeq]), Duration(2, "second")))

              responseXml must beSuccessfulTry
              
              val response = XML.loadString(removeTimes(responseXml.get.toString))

              response showAs (n =>
                "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)
              ) must new BeEqualFormatted(correctResponse.get)
            }
          })
        }

      })
    }
    "Callback Test" >> {
      sequentialTest.foldLeft(org.specs2.specification.core.Fragments.empty)((res, i) => {

        val (singleTest, testDescription) = i

        (testDescription.trim()) >> {
          singleTest.foldLeft(org.specs2.specification.core.Fragments.empty)((res, j) => {
            "Step: " >> {

              require(j.length == 2 || j.length == 1)
              val correctResponse = getSingleResponseNoTime(j)
              val responseWait: Option[Int] = Try(j.last.\@("wait").toInt).toOption

              correctResponse aka "Correct response message" must beSuccessfulTry

              if (j.length == 2) {
                val request = getCallbackRequest(j)

                request aka "Subscription request message" must beSuccessfulTry

                responseWait.foreach { x => Thread.sleep(x * 1000) }

                val responseFuture = http.singleRequest(getPostRequest(request.get))
                val responseXml = Try(Await.result(responseFuture.flatMap(Unmarshal(_).to[NodeSeq]), Duration(2, "second")))
             

                responseXml must beSuccessfulTry
              
              val response = XML.loadString(removeTimes(responseXml.get.toString))
                //remove blocking waits if possible
                if(request.get.\\("write").nonEmpty){
                  Thread.sleep(2000)
                }
                response showAs (n =>
                  "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)
                ) must new BeEqualFormatted(correctResponse.get)

              } else {
                val messageOption = probe.expectMsgType[Option[NodeSeq]](Duration(responseWait.getOrElse(2), "second"))
                
                messageOption must beSome
                val response = XML.loadString(removeTimes(messageOption.get.toString()))
                
                response showAs (n =>
                  "Response at callback server:\n" + printer.format(n.head)
                ) must new BeEqualFormatted(correctResponse.get)

              }
            }
          })
        }
      })
    }
  }
}
