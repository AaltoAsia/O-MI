package testHelpers
import scala.language.postfixOps

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.xml._
import scala.xml.parsing._

import akka.actor._
import akka.util.Timeout
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.TestFrameworkInterface
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.execute.{Failure, FailureException}
import org.specs2.mutable._
import org.specs2.specification.Scope
import org.xml.sax.InputSource
import responses.RemoveSubscription
import database._
import agentSystem._
import responses._
import http._

class TestOmiServer( config: Config )  extends OmiNode {

  // we need an ActorSystem to host our application in
  implicit val system : ActorSystem = ActorSystem("on-core") 
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  import system.dispatcher // execution context for futures

  /**
   * Settings loaded by akka (typesafe config) and our OmiConfigExtension
   */
  implicit val settings : OmiConfigExtension = new OmiConfigExtension(config)

  implicit val singleStores = new SingleStores(settings)
  implicit val dbConnection = new TestDB("system-test")(
    system,
    singleStores,
    settings
  )

  implicit val callbackHandler: CallbackHandler = new CallbackHandler(settings)( system, materializer)

   val subscriptionManager = system.actorOf(SubscriptionManager.props(), "subscription-handler")
   val agentSystem = system.actorOf(
    AgentSystem.props(),
    "agent-system"
  )

  implicit val requestHandler : RequestHandler = new RequestHandler(
    )(system,
    agentSystem,
    subscriptionManager,
    settings,
    dbConnection,
    singleStores
    )

  implicit val cliListener =system.actorOf(
    Props(
      new OmiNodeCLIListener(
        system,
        agentSystem,
        subscriptionManager,
        singleStores,
        dbConnection
      )),
    "omi-node-cli-listener"
  )

  implicit val httpExt = Http() 
  // create omi service actor
  val omiService = new OmiServiceImpl(
    system,
    materializer,
    subscriptionManager,
    settings,
    singleStores,
    requestHandler,
    callbackHandler
  )


  implicit val timeoutForBind = Timeout(5.seconds)

}

class Actorstest(system: ActorSystem) extends TestKit(system) with Scope with After with ImplicitSender {

  def after = {
    //TestKit.shutdownActorSystem(system)
    system.terminate
  }
}

class SystemTestCallbackServer(destination: ActorRef, interface: String, port: Int){

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
  Await.ready(bindFuture, 5 seconds)
}


abstract class Actors(val as: ActorSystem = ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """)))extends TestKit(as) with After with Scope {
  //def after = system.shutdown()
  def after = {
    //TestKit.shutdownActorSystem(system)
    system.terminate
  }
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


trait Specs2Interface extends TestFrameworkInterface {

  def failTest(msg: String): Nothing = {
    val trace = new Exception().getStackTrace.toList
    val fixedTrace = trace.drop(trace.indexWhere(_.getClassName.startsWith("org.specs2")) - 1)
    throw new FailureException(Failure(msg, stackTrace = fixedTrace))
  }
}
