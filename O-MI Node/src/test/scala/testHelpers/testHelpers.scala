package testHelpers

import java.io.File

import akka.actor.{ActorSystem, _}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import database.SingleStores
import org.specs2.mutable._
import org.specs2.specification.{Fragments, Scope, Step}
import org.xml.sax.InputSource
import responses.RemoveSubscription

import scala.xml._
import scala.xml.parsing._

class Actorstest(_system: ActorSystem) extends TestKit(_system) with Scope with After with ImplicitSender {

  def after = TestKit.shutdownActorSystem(system)
}

class SystemTestCallbackServer(destination: ActorRef) extends Actor with ActorLogging {
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
      //log.debug("server received\n:" + xmldata )
      destination ! xmldata
//      println("\n\ndata sent to listener")
    }
//    case Timedout(_) => log.info("Callback test server connection timed out")
    
    
  }
}

trait BeforeAll extends Specification {
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
package object utils {
  def removeAllPrevaylers = {
    def removeAll(path: String) = {
      def getRecursively(f:File): Seq[File] ={
        f.listFiles.filter(_.isDirectory).flatMap(getRecursively(_)) ++ f.listFiles()
      }
      getRecursively(new File(path)).foreach{
        f => f.delete()
      }
    }
   SingleStores.latestStore.close()
   SingleStores.hierarchyStore.close()
   SingleStores.eventPrevayler.close()
   SingleStores.intervalPrevayler.close()
   SingleStores.pollPrevayler.close()
   SingleStores.idPrevayler.close()
   removeAll(http.Boot.settings.journalsDirectory)

  }
}

// Disable implicit from specs2 Specification so concurrent.Duration int.seconds etc can be used
trait DeactivatedTimeConversions extends org.specs2.time.TimeConversions {
  override def intToRichLong(v: Int) = super.intToRichLong(v)
} 
