package http

import org.specs2.mutable.Specification
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


  
  
  implicit val system = ActorSystem()
  import system.dispatcher
  val pipeline: HttpRequest => Future[String] = sendReceive
  val printer = new scala.xml.PrettyPrinter(80, 2)
  
  val parser = new HTML5Parser
  val sourceFile = Source.fromFile("html/ImplementationDetails.html")
  val sourceXML: Node = parser.loadXML(sourceFile)
  
  println("WWWWWWWWWWWWWWWWWWW")
  val test1 = sourceXML.\\("textarea").tail
  val test2 = test1.head.text//.filter(_.\@("class") == "test").head.\("textarea")
  val test3 = test1.tail.head.text
  println(test2)
  println(test3)
  
  
  "test block" should {
    "WriteTest" in {
      println("ASDAKLSDKALSKD")
      val response = pipeline(Post("http://localhost:8080/", test1))
      val waited = Await.result(response, scala.concurrent.duration.Duration.apply(5, "second"))
      println()
      println(waited)
      waited.entity.asString must be(test3)
    }
    (1 to 10) foreach { i=>
      ("example " + i ) in {(i % 2 === 0).updateMessage(f=> "ASDASD")}.updateMessage(f => "asd")
      }
    //Example to change test failure message
    /*
    (500 to 1000 by 2) foreach { i=>
      ("example " + i ) >> {
        (i aka "lol failed" must_== 1000).setMessage("ASdasdasd")}.updateMessage(c => "asd")
      }*/
    
  }
}