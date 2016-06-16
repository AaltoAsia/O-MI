package testHelpers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.xml._
import scala.xml.parsing._

import akka.actor.{ActorSystem, _}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.TestFrameworkInterface
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.specs2.execute.{Failure, FailureException}
import org.specs2.mutable._
import org.specs2.specification.Scope
import org.xml.sax.InputSource
import responses.RemoveSubscription

class Actorstest(_system: ActorSystem) extends TestKit(_system) with Scope with After with ImplicitSender {

  def after = TestKit.shutdownActorSystem(system)
}

class SystemTestCallbackServer(destination: ActorRef, interface: String, port: Int){

  //implicit val system = ActorSystem()
  //vvv in test
  //IO(Http) ! Http.Bind(this, interface = "localhost", port = "12345"
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val route = post {
    decodeRequest{
      entity(as[NodeSeq]) { data =>
        destination ! Option(data)
        complete{
          "OK"
        }
      }
    }
  }


  val bindFuture = Http().bindAndHandle(route, interface, port)
  Await.ready(bindFuture, 2 seconds)
  //val reqHandler: HttpRequest => HttpResponse = {
  //  case HttpRequest(POST, _, _, ,_)
  //}

 /* def receive = {
    case _: Http.Connected => sender ! Http.Register(self)

    case post @ HttpRequest(POST, _, _, entity: HttpEntity.NonEmpty, _) => {
      val xmldata: Option[NodeSeq] = entity.as[NodeSeq].toOption//.asInstanceOf[NodeSeq]
      //log.debug("server received\n:" + xmldata )
      destination ! xmldata
//      println("\n\ndata sent to listener")
    }
//    case Timedout(_) => log.info("Callback test server connection timed out")
    

  }*/
}

/*trait BeforeAll extends Specification {
  override def map(fs: => Fragments) = {
    Step(beforeAll) ^ fs
  }

  protected[this] def beforeAll()
}

trait AfterAll extends Specification {
  override def map(fs: => Fragments) = {
    fs ^ Step(afterAll)
  }

  protected[this] def afterAll()
}

trait BeforeAfterAll extends Specification {
  override def map(fs: => Fragments) = {
    Step(beforeAll) ^ fs ^ Step(afterAll)
  }
  protected[this] def beforeAll()
  protected[this] def afterAll()
}
*/
abstract class Actors(val as: ActorSystem = ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """)))extends TestKit(as) with After with Scope {
  def after = system.shutdown()
}

class SubscriptionHandlerTestActor extends Actor {
  def receive = {
    case RemoveSubscription(x) => {
      if (x <= 10) {
        sender() ! true
      } else {
        sender() ! false
      }
    }
    case _ => sender() ! false
  }
}

class HTML5Parser extends NoBindingFactoryAdapter {
  override def loadXML(source: InputSource, parser: SAXParser) = {
    loadXML(source)
  }

  def loadXML(source: InputSource) = {
    import nu.validator.htmlparser.{common, sax}
    import common.XmlViolationPolicy
    import sax.HtmlParser

    val reader = new HtmlParser
    reader.setXmlPolicy(XmlViolationPolicy.ALLOW)
    reader.setContentHandler(this)
    reader.parse(source)
    rootElem
  }
}

  

import org.specs2.matcher._
class BeEqualFormatted(node: Seq[Node]) extends EqualIgnoringSpaceMatcher(node) {
  val printer = new scala.xml.PrettyPrinter(80, 2)
  override def apply[S <: Seq[Node]](n: Expectable[S]) = {
    super.apply(n).updateMessage { x => n.description + "\nis not equal to correct:\n" + printer.format(node.head) }

  }
}


// until akka-http gets support
trait Specs2Interface extends TestFrameworkInterface {
  // def cleanUp(): Unit

  // from spray-testkit
  def failTest(msg: String): Nothing = {
    val trace = new Exception().getStackTrace.toList
    val fixedTrace = trace.drop(trace.indexWhere(_.getClassName.startsWith("org.specs2")) - 1)
    throw new FailureException(Failure(msg, stackTrace = fixedTrace))
  }
}

/* depracated
// Disable implicit from specs2 Specification so concurrent.Duration int.seconds etc can be used
trait DeactivatedTimeConversions extends org.specs2.time.TimeConversions {
  override def intToRichLong(v: Int) = super.intToRichLong(v)
} */
