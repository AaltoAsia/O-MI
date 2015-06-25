package http
import org.specs2.mutable.Specification
import spray.http._
import spray.client.pipelining._
import akka.actor.ActorSystem
import scala.concurrent._

class SystemTest extends Specification{
  
  implicit val system = ActorSystem()
  import system.dispatcher
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  val printer = new scala.xml.PrettyPrinter(80, 2)

  
  "test block" should {
    (1 to 500) foreach { i=>
      ("example " + i ) in {(i === i)}
      }
    
    (500 to 1000 by 2) foreach { i=>
      ("example " + i ) >> {
        (i aka "lol failed" must_== 1000)}
      }
    
  }
}