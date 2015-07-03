package testHelpers
import org.specs2.mutable._
import org.specs2.specification.{ Step, Fragments }
import org.xml.sax.InputSource
import akka.testkit.TestKit
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.specs2.specification.Scope
import akka.actor._
import responses.RemoveSubscription
import scala.xml._
import parsing._

class SystemTestCallbackServer(destination: ActorRef) extends Actor {
  import akka.io.IO
  import spray.can.Http
  import spray.http._
  import HttpMethods._
  import spray.httpx.unmarshalling._
  import spray.util._

  implicit val system = ActorSystem()
  //vvv in test
  //  IO(Http) ! Http.Bind(this, interface = "localhost", port = "12345"
  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)

    case post @ HttpRequest(POST, _, _, entity: HttpEntity.NonEmpty, _) => {
      val xmldata: Option[NodeSeq] = entity.as[NodeSeq].toOption//.asInstanceOf[NodeSeq]
      println(xmldata)
      destination ! xmldata
    }
    
    
  }
}

trait BeforeAll extends Specification {
  override def map(fs: => Fragments) = {
    Step(beforeAll) ^ fs
  }

  protected def beforeAll()
}

trait AfterAll extends Specification {
  override def map(fs: => Fragments) = {
    fs ^ Step(afterAll)
  }

  protected def afterAll()
}

trait BeforeAfterAll extends Specification {
  override def map(fs: => Fragments) = {
    Step(beforeAll) ^ fs ^ Step(afterAll)
  }
  protected def beforeAll()
  protected def afterAll()
}

abstract class Actors extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with After with Scope {
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
    import nu.validator.htmlparser.{ sax, common }
    import sax.HtmlParser
    import common.XmlViolationPolicy

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

// Disable implicit from specs2 Specification so concurrent.Duration int.seconds etc can be used
trait DeactivatedTimeConversions extends org.specs2.time.TimeConversions {
  override def intToRichLong(v: Int) = super.intToRichLong(v)
} 
