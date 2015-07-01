package http
import org.specs2.mutable._ //Specification
import org.specs2.matcher.XmlMatchers._
//import org.specs2.specification.{Example, ExampleFactory}
import org.xml.sax.InputSource
import spray.http._
import spray.client.pipelining._
import akka.actor.{ ActorRef, ActorSystem, Props }
import scala.concurrent._
import scala.xml._
import scala.util.Try
import parsing._
import testHelpers.HTML5Parser
import database._
import testHelpers.AfterAll
import responses.{ SubscriptionHandler, RequestHandler }
import agentSystem.ExternalAgentListener
import scala.concurrent.duration._
import akka.util.Timeout
import akka.io.{ IO, Tcp }
import akka.pattern.ask
import java.net.InetSocketAddress
import org.specs2.matcher._

class BeEqualFormatted(node: Seq[Node]) extends EqualIgnoringSpaceMatcher(node) {
  val printer = new scala.xml.PrettyPrinter(80, 2)
  override def apply[S <: Seq[Node]](n: Expectable[S]) = {
    super.apply(n).updateMessage { x => n.description + "\nis not equal to correct:\n" + printer.format(node.head) }

  }
}

class SystemTest extends Specification with Starter with AfterAll {

  override def start(dbConnection: DB = new SQLiteConnection): ActorRef = {
    val subHandler = system.actorOf(Props(new SubscriptionHandler()(dbConnection)), "subscription-handler")

    // create and start sensor data listener
    // TODO: Maybe refactor to an internal agent!
    val sensorDataListener = system.actorOf(Props(classOf[ExternalAgentListener]), "agent-listener")

    //    val agentLoader = system.actorOf(InternalAgentLoader.props() , "agent-loader")

    // create omi service actor
    val omiService = system.actorOf(Props(new OmiServiceActor(new RequestHandler(subHandler)(dbConnection))), "omi-service")

    implicit val timeoutForBind = Timeout(Duration.apply(5, "second"))

    IO(Tcp) ? Tcp.Bind(sensorDataListener, new InetSocketAddress(settings.externalAgentInterface, settings.externalAgentPort))

    return omiService
  }
  sequential
  //  implicit val system = ActorSystem()
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

  val readTests = tests("request-response test").map { node =>
    val textAreas = node \\ ("textarea")
    val request = Try(textAreas.find(x => x.\@("class") == "request").map(_.text).map(XML.loadString(_))).toOption.flatten
    val correctResponse = Try(textAreas.find(x => x.\@("class") == "response").map(_.text).map(XML.loadString(_))).toOption.flatten
    val testDescription = node \ ("div") \ ("p") text

    (request, correctResponse, testDescription)
  }
//  dbConnection.remove(types.Path("Objects/OMI-service"))
  def afterAll = {
    system.shutdown()
    dbConnection.destroy()
  }
  "Automatic System Tests" should {
    "WriteTest" >> {
      dbConnection.clearDB()
      //Only 1 write test
      val testCase = tests("write test").head \\ ("textarea")
      //val testCase = testArticles.filter(a => a.\@("class") == "write test" ).head \\ ("textarea") //sourceXML \\ ("write test")
      val request: Option[Elem] = Try(testCase.find(x => x.\@("class") == "request").map(_.text).map(XML.loadString(_))).toOption.flatten
      val correctResponse: Option[Elem] = Try(testCase.find(x => x.\@("class") == "response").map(_.text).map(XML.loadString(_))).toOption.flatten

      request aka "Write request message" must beSome
      correctResponse aka "Correct write response message" must beSome

      val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
      val response = Try(Await.result(responseFuture, scala.concurrent.duration.Duration.apply(2, "second")))

      response must beSuccessfulTry

      response.get showAs (n => "\n" + printer.format(n.head)) must beEqualToIgnoringSpace(correctResponse.get) //.await(2, scala.concurrent.duration.Duration.apply(2, "second"))
    }

    step({

      Thread.sleep(2000);
      //      val temp = dbConnection.get(types.Path("Objects/Object1")).map(types.OdfTypes.fromPath(_))
    })

    readTests foreach { i =>
      val (request, correctResponse, testDescription) = i
      ("test Case:\n" + testDescription.trim + "\n" /*+ Try(printer.format(request.head)).getOrElse("head of an empty list")  */ ) >> {

        request aka "Read request message" must beSome
        correctResponse aka "Correct read response message" must beSome

        val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
        val response = Try(Await.result(responseFuture, scala.concurrent.duration.Duration.apply(2, "second")))

        response must beSuccessfulTry
        //                                                                          prettyprinter only returns string :/
        response.get showAs (n => "Request Message:\n" + printer.format(request.head) + "\n\n" + "Actual response:\n" + printer.format(n.head)) must /*beEqualToIgnoringSpace*/ new BeEqualFormatted(XML.loadString(printer.format(correctResponse.get.head)))

      }

    }

  }
}