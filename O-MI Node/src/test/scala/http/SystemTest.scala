package http

import org.specs2.mutable._//Specification
import org.specs2.matcher.XmlMatchers._
import org.xml.sax.InputSource
import spray.http._
import spray.client.pipelining._
import akka.actor.ActorSystem
import scala.concurrent._
import scala.xml._
import scala.util.Try
import parsing._
import testHelpers.HTML5Parser
import database._
import testHelpers.AfterAll


class SystemTest extends Specification with Starter with AfterAll{
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
  val tests = testArticles.groupBy( x => x.\@("class") )
  
  val readTests = tests("readtest").map { node => 
    val textAreas = node \\ ("textarea")
    val request = Try(textAreas.find(x=> x.\@("class") == "request").map(_.text).map(XML.loadString(_))).toOption.flatten
    val correctResponse = Try(textAreas.find(x => x.\@("class") == "response").map(_.text).map(XML.loadString(_))).toOption.flatten
    val testDescription = node \ ("div") \ ("p") text
    
    (request, correctResponse, testDescription)
    }
  def afterAll = {
    system.shutdown()
    dbConnection.destroy()
  }
  "Automatic System Tests" should {
    "WriteTest" >> {
      //Only 1 write test
      val testCase = tests("writetest").head \\ ("textarea")
      //val testCase = testArticles.filter(a => a.\@("class") == "write test" ).head \\ ("textarea") //sourceXML \\ ("write test")
      val request: Option[Elem] = Try(testCase.find(x=> x.\@("class") == "request").map(_.text).map(XML.loadString(_))).toOption.flatten
      val correctResponse: Option[Elem] = Try(testCase.find(x => x.\@("class") == "response").map(_.text).map(XML.loadString(_))).toOption.flatten
      
      request aka "Write request message" must beSome
      correctResponse aka "Correct write response message" must beSome
      
      val response = pipeline(Post("http://localhost:8080/", request.get))
      
      response must beEqualToIgnoringSpace(correctResponse.get).await(2, scala.concurrent.duration.Duration.apply(2, "second"))
    }
    
    step({Thread.sleep(2000)})
    
    readTests foreach { i=>
      val(request, correctResponse, testDescription) = i
      ("test Case:\n" + testDescription.trim + "\n" + Try(printer.format(request.head)).getOrElse("head of an empty list")  ) >> { 
        
        request aka "Read request message" must beSome
        correctResponse aka "Correct read response message" must beSome
        
        val responseFuture = pipeline(Post("http://localhost:8080/", request.get))
        val response = Try(Await.result(responseFuture, scala.concurrent.duration.Duration.apply(2, "second")))
        
        response must beSuccessfulTry
        
//        s"${"\n"+printer.format(response.get.head) + "\n"}" <==> {
        response.get aka ("\n"+printer.format(response.get.head) + "\n") must beEqualToIgnoringSpace(correctResponse.get)
//        }
        }

      }
    
  }
}