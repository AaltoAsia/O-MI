package testHelpers

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.xml.{Node, PrettyPrinter, SAXParser, XML}
import scala.xml.parsing._

import akka.testkit.TestEventListener
import akka.Done
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.stream._
import akka.stream.scaladsl._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.specs2.matcher._
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.specification.Scope
import org.xml.sax.InputSource

import types.odf._
import responses._
import http._
import database.journal.Models.GetTree
import database.SingleStores
import database.TestDB
import database.DBHandler
import agentSystem._

//class TestOmiServer(config: Config) extends OmiNode {
class TestOmiServer() extends OmiNode with OmiServiceTestImpl {

  // we need an ActorSystem to host our application in
  implicit val system: ActorSystem = ActorSystem("on-core")
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system) // execution context for futures


  implicit val cliListener = system.actorOf(
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

  implicit val httpExt: HttpExt = Http()
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


  implicit val timeoutForBind: Timeout = Timeout(5.seconds)
}

trait AnyActorSystem {
  implicit val system: ActorSystem
}
trait SilentActorSystem {
  implicit val system = Actorstest.createSilentAs()
}

trait TestOmiService extends OmiServiceTestImpl {
  implicit def default = RouteTestTimeout(5.second)

}
trait OmiServiceTestImpl extends OmiService with AnyActorSystem {
  lazy implicit val settings: OmiConfigExtension = OmiConfig(system)
  lazy implicit val callbackHandler: CallbackHandler = new CallbackHandler(settings)(system, materializer)
  lazy implicit val singleStores: SingleStores = SingleStores(settings)


  lazy implicit val dbConnection = new TestDB("test")(
    system,
    singleStores,
    settings
  )
  //dbConnection.clearDB()

  lazy val subscriptionManager = TestActorRef(SubscriptionManager.props(
    settings,
    singleStores,
    callbackHandler
  ))

  lazy val dbHandler = system.actorOf(
    DBHandler.props(
      dbConnection,
      singleStores,
      callbackHandler,
      new CLIHelper(singleStores, dbConnection)
    ),
    "database-handler"
  )
  lazy val requestHandler = system.actorOf(
    RequestHandler.props(
      subscriptionManager,
      dbHandler,
      settings
    ),
    "RequestHandler"
  )
  lazy val agentSystem = system.actorOf(
    AgentSystem.props(
      dbHandler,
      requestHandler,
      settings
    ),
    "agent-system"
  )
}

class SystemTestCallbackServer(destination: ActorRef, interface: String, port: Int) {

  implicit val system = ActorSystem("callback-test-server",
    Actorstest.silentLoggerConf.withFallback(ConfigFactory.defaultReference()))

  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val prettyPrint = new PrettyPrinter(80, 2)
  val route = formFields("msg".as[String]) { msg =>

    //val pretty = prettyPrint.format( XML.loadString(msg))
    //println("\n\n")
    //println(msg)
    //println("\n\n")
    destination ! Option(XML.loadString(msg))
    //entity(as[NodeSeq]) { data =>
    //destination ! Option(data)
    complete {
      "OK"
    }
  }


  val bindFuture = Http().bindAndHandle(route, interface, port)
  Await.ready(bindFuture, 5 seconds)

  def unbind() = {
    bindFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .map(_ => system.terminate())
  }
}

class WsTestCallbackClient(destination: ActorRef, interface: String, port: Int)(implicit system: ActorSystem) {
  //send messages received from ws connection to the given actor
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  //val prettyPrint = new PrettyPrinter(80,2)
  val incoming: Sink[Message, Future[Done]] = {
    Sink.foreach[Message] {
      case message: TextMessage.Strict => {
        //val pretty = prettyPrint.format( XML.loadString(message.text) )
        //println(s"$interface:$port received: $pretty")
        destination ! message.text
      }
      case _ =>
    }
  }
  val outgoingSourceQueue = akka.stream.scaladsl.Source.queue[Message](5,
    akka.stream.OverflowStrategy.fail) //(TextMessage("ping"))

  private def peekMatValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
    val p = Promise[M]
    val s = src.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }
    (s, p.future)
  }

  val (outgoing, sourceQueue) = peekMatValue(outgoingSourceQueue)

  //val queue = outgoing.mapMaterializedValue()

  //create websocket connection
  val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(interface + ":" + port))

  val (upgradeResponse, closed) =
    outgoing
      .viaMat(webSocketFlow)(Keep.right)
      .toMat(incoming)(Keep.both)
      .run()

  val connected = upgradeResponse.flatMap { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Future.successful(Done)
    }
    else {
      Future.failed(new RuntimeException(s"Websocket connection failed ${upgrade.response.status}"))
    }
  }

  closed.foreach(c => system.log.debug("Closed WS connection"))

  def offer(message: String) = {
    sourceQueue.flatMap(que => que.offer(TextMessage(message)))
  }

  def close() = sourceQueue.map(_.complete())


}

class WsTestCallbackServer(destination: ActorRef, interface: String, port: Int)(implicit system: ActorSystem) {

  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  type InSink = Sink[Message, _]
  type OutSource = Source[Message, SourceQueueWithComplete[Message]]
  implicit val httpExt = Http()
  val route = path("") {
    extractUpgradeToWebSocket { wsRequest =>

      val (inSink, outSource) = createInSinkAndOutSource()
      complete(
        wsRequest.handleMessagesWithSinkSource(inSink, outSource)
      )
    }
  }

  def bind() = {
    val bindingFuture =
      httpExt.bindAndHandle(route, interface, port)

    bindingFuture.failed.foreach {
      case ex: Exception =>
        system.log.error(ex, "Failed to bind to {}:{}!", interface, port)

    }
    def unbind: () => Future[akka.Done] = () =>
      bindingFuture
        .flatMap(_.unbind()) // trigger unbinding from the port

    (bindingFuture, unbind)
  }


  // Queue howto: http://loicdescotte.github.io/posts/play-akka-streams-queue/
  // T is the source type
  // M is the materialization type, here a SourceQueue[String]
  private def peekMatValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
    val p = Promise[M]
    val s = src.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }
    (s, p.future)
  }

  // akka.stream
  protected def createInSinkAndOutSource(): (InSink, OutSource) = {
    val queueSize = 10
    val (outSource, futureQueue) =
      peekMatValue(Source.queue[Message](queueSize, OverflowStrategy.fail))

    // keepalive? http://doc.akka.io/docs/akka/2.4.8/scala/stream/stream-cookbook.html#Injecting_keep-alive_messages_into_a_stream_of_ByteStrings

    val stricted = Flow.fromFunction[Message, Future[String]] {
      case textMessage: TextMessage =>
        textMessage.textStream.runFold("")(_ + _)
      case msg: Message => Future successful ""
    }
    val msgSink = Sink.foreach[Future[String]] { future: Future[String] =>
      future.map {
        requestString: String =>
          destination ! requestString
      }
    }

    val inSink = stricted.to(msgSink)
    (inSink, outSource)
  }
}


class SilentTestEventListener extends TestEventListener {
  override def print(event: Any): Unit = ()
}


class Actorstest
  (val as: ActorSystem = Actorstest.createSilentAs()) extends TestKit(as) with
  After with
  Scope with
  ImplicitSender {
  //def after = system.shutdown()
  def after = {
    //TestKit.shutdownActorSystem(system)
    system.terminate
  }
}

class NoisyActorstest
  (as: ActorSystem = Actorstest.createAs()) extends Actorstest(as)

object Actorstest {
  val silentLoggerConf = ConfigFactory.parseString(
    """
    akka {
      stdout-loglevel = "OFF"
      loglevel = "OFF"
      loggers = []
      persistence {
        journal { # somehow these come from resources/application.conf???
          plugin = ""
          auto-start-journals = []
        }
        snapshot-store {
          plugin = ""
          auto-start-snapshot-stores = []
        }
      }
    }
    """
  )
  val silentLoggerConfFull = silentLoggerConf.withFallback(ConfigFactory.load())
  val loggerConf = ConfigFactory.parseString(
    """
      akka.loggers = ["testHelpers.SilentTestEventListener"]
    """)
  def createAs() = ActorSystem("testsystem", loggerConf.withFallback(ConfigFactory.load()))
  def createSilentAs() = ActorSystem("testsystem", silentLoggerConfFull)
  def apply() = new Actorstest()
  def silent() = new Actorstest(createSilentAs())
}


class SubscriptionHandlerTestActor extends Actor {
  def receive = {
    case RemoveSubscription(x, ttl) => {
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


class BeEqualFormatted(node: Seq[Node]) extends EqualIgnoringSpaceMatcher(node) {
  val printer = new scala.xml.PrettyPrinter(80, 2)

  override def apply[S <: Seq[Node]](n: Expectable[S]) = {
    super.apply(n).updateMessage { x => n.description + "\nis not equal to correct:\n" + printer.format(node.head) }

  }
}


/* XXX: Check Throwable type and message without using withThrowable, that
 * causes compiler to assertion error from typer. Issue is caused by combination
 * of depencies and could not be reproduced with only Specs2.
 *
class TesterTest extends Specification 
{
    "test" >> {
      val t = Try{ throw new java.lang.IllegalArgumentException("test")}
      t must beFailedTry.withThrowable[java.lang.IllegalArgumentException]("test")
    }
}*/

class OmiServiceDummy extends OmiService with Mockito {
  override protected def requestHandler: ActorRef = ???

  override val callbackHandler: CallbackHandler = mock[CallbackHandler]
  override protected val system: ActorSystem = Actorstest.createAs()
  override val singleStores: SingleStores = mock[SingleStores]

  override implicit def materializer: ActorMaterializer = ???

  override protected def subscriptionManager: ActorRef = ???

  implicit val settings: OmiConfigExtension = OmiConfig(system)
}

object DummyHierarchyStore {
  def apply(odf: ODF)(implicit system: ActorSystem) =
    system actorOf Props(new Actor {
      def receive = {
        case GetTree => sender() ! odf.immutable
      }
    }) 
}

class DummySingleStores(
  override protected val settings: OmiConfigExtension,
  override val latestStore: ActorRef = ActorRef.noSender,
  override val subStore: ActorRef =  ActorRef.noSender,
  override val pollDataStore: ActorRef =  ActorRef.noSender,
  override val hierarchyStore: ActorRef =  ActorRef.noSender,
  )(implicit val system: ActorSystem)  extends SingleStores{
}
