package http
import org.specs2.mutable._
import spray.http._
import spray.client.pipelining._
import akka.actor.{ ActorRef, ActorSystem, Props }
import scala.concurrent._
import scala.xml._
import scala.util.Try
import parsing._
import testHelpers.{BeEqualFormatted ,HTML5Parser}
import database._
import testHelpers.AfterAll
import responses.{ SubscriptionHandler, RequestHandler }
import agentSystem.ExternalAgentListener
import scala.concurrent.duration._
import akka.util.Timeout
import akka.io.{ IO, Tcp }
import akka.pattern.ask
import java.net.InetSocketAddress
import org.specs2.specification.Fragments



class SystemTest extends Specification with Starter with AfterAll {

  override def start(dbConnection: DB = new SQLiteConnection): ActorRef = {
    val subHandler = system.actorOf(Props(new SubscriptionHandler()(dbConnection)), "subscription-handler")
    val sensorDataListener = system.actorOf(Props(classOf[ExternalAgentListener]), "agent-listener")
    val omiService = system.actorOf(Props(new OmiServiceActor(new RequestHandler(subHandler)(dbConnection))), "omi-service")

    implicit val timeoutForBind = Timeout(Duration.apply(5, "second"))

    IO(Tcp) ? Tcp.Bind(sensorDataListener, new InetSocketAddress(settings.externalAgentInterface, settings.externalAgentPort))

    return omiService
  }

  sequential
  import system.dispatcher

  //start the program
  implicit val dbConnection = new TestDB("SystemTest")

  init(dbConnection)
  val serviceActor = start(dbConnection)
  bindHttp(serviceActor)

  val pipeline: HttpRequest => Future[NodeSeq] = sendReceive ~> unmarshal[NodeSeq]
  val printer = new scala.xml.PrettyPrinter(80, 2)

  val parser = new HTML5Parser
  val sourceFile = Source.fromFile("html/ImplementationDetails.html")
  val sourceXML: Node = parser.loadXML(sourceFile)

  val testArticles = sourceXML \\ ("article")
  val tests = testArticles.groupBy(x => x.\@("class"))

  lazy val readTests = tests("request-response test").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length == 2, s"Each request must have exactly 1 response in request-response tests, could not find for: $node")
    val request: Try[Elem] = getSingleRequest(textAreas)
    val correctResponse: Try[Elem] = getSingleResponse(textAreas)
    val testDescription = node \ ("div") \ ("p") text

    (request, correctResponse, testDescription)
  }

  lazy val subsNoCallback = tests("subscription-nocallback").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length % 2 == 0, "There must be even amount of response and request messages(1 response for each request)")

    val testDescription: String = node \ ("div") \ ("p") text

    val groupedRequests = textAreas.grouped(2).toSeq.map { reqresp =>
      val request: Try[Elem] = getSingleRequest(reqresp)
      val correctresponse: Try[Elem] = getSingleResponse(reqresp)
      val responseWait: Option[Int] = Try(reqresp.last.\@("wait").toInt).toOption
      (request, correctresponse, responseWait)
    }
    (groupedRequests, testDescription)

  }

  //  dbConnection.remove(types.Path("Objects/OMI-service"))
  def afterAll = {
    system.shutdown()
    dbConnection.destroy()
  }

  def getSingleRequest(reqresp: NodeSeq): Try[Elem] = {
    require(reqresp.length >= 1)
    Try(XML.loadString(reqresp.head.text))
  }

  def getSingleResponse(reqresp: NodeSeq): Try[Elem] = {
    Try(XML.loadString(reqresp.last.text))
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

        val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
        val response = Try(Await.result(responseFuture, scala.concurrent.duration.Duration.apply(2, "second")))

        response must beSuccessfulTry

        response.get showAs (n =>
          "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)
      }
    }

    step({
      //let the database write the data
      Thread.sleep(2000);
    })
    "Read Test\n" >> {
      readTests.foldLeft(Fragments())((res, i) => {
        val (request, correctResponse, testDescription) = i
        res.append(
          (testDescription.trim + "\n") >> {

            request aka "Read request message" must beSuccessfulTry
            correctResponse aka "Correct read response message" must beSuccessfulTry

            val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
            val response = Try(Await.result(responseFuture, scala.concurrent.duration.Duration.apply(2, "second")))

            response must beSuccessfulTry

            response.get showAs (n =>
              "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)
          })
      })
    }

    "Subscription Test\n" >> {
      subsNoCallback.foldLeft(Fragments())((res, i) => {
        val (reqrespwait, testDescription) = i
        (testDescription.trim() + "\n") >> {

          reqrespwait.foldLeft(Fragments())((res, j) => {
            "step: " >> {

              val (request, correctResponse, responseWait) = j
              request aka "Subscription request message" must beSuccessfulTry
              correctResponse aka "Correct response message" must beSuccessfulTry

              val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
              val response = Try(Await.result(responseFuture, scala.concurrent.duration.Duration.apply(2, "second")))

              response must beSuccessfulTry

              response.get showAs (n =>
                "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must new BeEqualFormatted(correctResponse.get)
            }
          })
        }

      })
    }

  }
}