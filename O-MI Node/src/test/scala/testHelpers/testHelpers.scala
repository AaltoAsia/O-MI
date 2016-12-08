package testHelpers
import scala.language.postfixOps

import scala.concurrent.{Promise, Future, Await}
import scala.concurrent.duration._
import scala.xml.{SAXParser, Node, PrettyPrinter, XML}
import scala.xml.parsing._

import akka.Done
import akka.actor._
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout
import akka.http.scaladsl._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.TestFrameworkInterface
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import akka.stream._
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
    AgentSystem.props(None),
    "agent-system"
  )

  implicit val requestHandler : RequestHandler = new RequestHandler(
    )(system,
    agentSystem,
    subscriptionManager,
    settings,
    dbConnection,
    singleStores,
    None //analytics
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

  val route = formFields("msg".as[String]) {msg =>
    destination ! Option(XML.loadString(msg))
    //entity(as[NodeSeq]) { data =>
    //destination ! Option(data)
    complete{
      "OK"
    }
    }



    val bindFuture = Http().bindAndHandle(route, interface, port)
    Await.ready(bindFuture, 5 seconds)
  }

  class WsTestCallbackClient(destination: ActorRef, interface: String, port: Int)(implicit system: ActorSystem){
    //send messages received from ws connection to the given actor
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val prettyPrint = new PrettyPrinter(80,2)
  val incoming: Sink[Message, Future[Done]] = {
   Sink.foreach[Message]{
     case message: TextMessage.Strict => {
     //val pretty = prettyPrint.format( XML.loadString(message.text) )
     //println(s"$interface:$port received: $pretty")
     destination ! message.text
     }
   }
 }
  val outgoingSourceQueue = akka.stream.scaladsl.Source.queue[Message](5,akka.stream.OverflowStrategy.fail)//(TextMessage("ping"))

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

  val connected = upgradeResponse.flatMap{ upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols){
      Future.successful(Done)
    }
    else{
      throw new RuntimeException(s"Websocket connection failed ${upgrade.response.status}")
    }
  }

  closed.foreach(c => system.log.debug("Closed WS connection"))

  def offer(message: String) = {
    sourceQueue.map(que => que.offer(TextMessage(message)))
  }

  def close = sourceQueue.map(_.complete())


}
class WsTestCallbackServer(destination: ActorRef, interface: String, port: Int)(implicit system: ActorSystem){

  protected implicit val materializer = ActorMaterializer()
  import system.dispatcher
  type InSink = Sink[Message, _]
  type OutSource = Source[Message, SourceQueueWithComplete[Message]]
  implicit val httpExt = Http()
  val route = path(""){
      extractUpgradeToWebSocket {wsRequest =>

        val (inSink, outSource) = createInSinkAndOutSource()
        complete(
          wsRequest.handleMessagesWithSinkSource(inSink, outSource)
        )
      }
  }
  def bind() ={
    val bindingFuture =
      httpExt.bindAndHandle(route, interface, port)
    
    bindingFuture.onFailure {
      case ex: Exception =>
        system.log.error(ex, "Failed to bind to {}:{}!", interface, port)

    }
    bindingFuture
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

    val stricted = Flow.fromFunction[Message,Future[String]]{
      case textMessage: TextMessage =>
        textMessage.textStream.runFold("")(_+_)
      case msg: Message => Future successful ""
    }
    val msgSink = Sink.foreach[Future[String]]{ future: Future[String]  => 
      future.map{ 
        case requestString: String =>
          destination ! requestString
      }
    }

    val inSink = stricted.to(msgSink)
    (inSink, outSource)
  }
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
