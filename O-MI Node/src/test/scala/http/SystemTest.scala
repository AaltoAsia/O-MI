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


class SystemTest extends Specification{
sequential
  implicit val system = ActorSystem()
  import system.dispatcher
  
  val pipeline: HttpRequest => Future[NodeSeq] = sendReceive ~> unmarshal[NodeSeq]
  val printer = new scala.xml.PrettyPrinter(80, 2)
  
  val parser = new HTML5Parser
  val sourceFile = Source.fromFile("html/ImplementationDetails.html")
  val sourceXML: Node = parser.loadXML(sourceFile)
  val testArticles = sourceXML \\ ("article")
  val tests = testArticles.groupBy( x => x.\@("class") )
  
  val readTests = tests("read test").map { node => 
    val textAreas = node \\ ("textarea")
    val request = Try(textAreas.find(x=> x.\@("class") == "request").map(_.text).map(XML.loadString(_))).toOption.flatten
    val correctResponse = Try(textAreas.find(x => x.\@("class") == "response").map(_.text).map(XML.loadString(_))).toOption.flatten
    val description = node \ ("div") \ ("p") text
    
    (request, correctResponse, description)
    }//.filter(a => a.\@("class") == "read test").map(_ \\ ("textarea")).map(p => p)
  
  "Automatic System Tests" should {
    "WriteTest" in {
      //Only 1 write test
      val testCase = tests("write test").head \\ ("textarea")
      //val testCase = testArticles.filter(a => a.\@("class") == "write test" ).head \\ ("textarea") //sourceXML \\ ("write test")
      val request: Option[Elem] = Try(testCase.find(x=> x.\@("class") == "request").map(_.text).map(XML.loadString(_))).toOption.flatten
      val correctResponse: Option[Elem] = Try(testCase.find(x => x.\@("class") == "response").map(_.text).map(XML.loadString(_))).toOption.flatten
      
      request aka "Write request message" must beSome
      correctResponse aka "Correct write response message" must beSome
      
      val response = pipeline(Post("http://localhost:8080/", request.get))
      
      response must beEqualToIgnoringSpace(correctResponse.get).await(2, scala.concurrent.duration.Duration.apply(2, "second"))
    }
    
    readTests foreach { i=>
      val(request, correctResponse, description) = i
      ("test Case:\n" + description.trim ) >> {
        
        request aka "Read request message" must beSome
        correctResponse aka "Correct read response message" must beSome
        
        val response = pipeline(Post("http://localhost:8080/", request.get))
        
        response must beEqualToIgnoringSpace(correctResponse.get).await(2, scala.concurrent.duration.Duration.apply(2, "second"))
        }
        //(i % 2 === 0).updateMessage(f=> "ASDASD")}.updateMessage(f => "asd")
      }
    //Example to change test failure message
    /*
    (500 to 1000 by 2) foreach { i=>
      ("example " + i ) >> {
        (i aka "lol failed" must_== 1000).setMessage("ASdasdasd")}.updateMessage(c => "asd")
      }*/
    
  }
}