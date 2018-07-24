package http

import java.io.File
import java.net.InetSocketAddress
import java.sql.Timestamp
import java.util.Date

import agentSystem._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.http.scaladsl.model.Uri
import akka.io.Tcp.{Received, Write}
import akka.pattern.ask
import akka.testkit._
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory
import database._
import http.CLICmds._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher._
import org.specs2.mutable._
import responses.{AllSubscriptions, CLIHelperT, RemoveSubscription}
import testHelpers.Actorstest
import types.OmiTypes._
import types.Path
import types.odf._

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Future
import scala.concurrent.duration._


class NodeCLITest(implicit ee: ExecutionEnv) extends Specification {
  sequential
  "NodeCLI should " >> {
    "return list of available commands when help command is received" >> helpTest
    "return table of agents when list agents command is received" >> listAgentsTest
    "return correct message when start agent command is received" >> startAgentTest
    "return correct message when stop agent command is received" >> stopAgentTest
    "return help information when unknown command is received" >> unknownCmdTest
    "return correct message when path remove command is received" >> removePathTest
    "return correct message when path remove command for unexisting path is received" >> removeUnexistingPathTest
    "return table of subscriptions when list subs command is received" >> listSubsTest
    "return correct message when sub remove command is received" >> removeSubTest
    "return correct message when sub remove command for unexisting id is received" >> removeUnexistingSubTest
    "return correct message when show sub command is received for" >> {
      "Interval subscription" >> showSubTestInterval
      "Event subscription" >> showSubTestEvent
      "Polled Interval subscription" >> showSubTestPollInterval
      "Polled Event subscription" >> showSubTestPollEvent
      "nonexistent subscription" >> showSubTestNonexistent
    }
    "Successfully create backup file for odf and subscription" >> backupTest
  }
  implicit val timeout: Timeout = Timeout(1.minutes)

  def timeoutDuration: FiniteDuration = 10.seconds

  def emptyConfig = ConfigFactory.empty()

  def strToMsg(str: String) = Received(ByteString(str))

  def decodeWriteStr(future: Future[Any])(implicit system: ActorSystem) = {
    import system.dispatcher
    future.map {
      case Write(byteStr: ByteString, _) => byteStr.decodeString("UTF-8")
    }
  }

  class RemoveTester(path: Path) extends CLIHelperT {
    val getAllData: Future[Option[ODF]] = Future.successful(Some(ImmutableODF(Vector(Objects()))))//new Exception("not implemented"))

    def writeOdf(odf: ImmutableODF) = Future.successful(())
    def takeSnapshot() = Future.successful(())
    override def handlePathRemove(parentPaths: Seq[Path]): Future[Seq[Int]] = {
      if (path == parentPaths.head || path.isAncestorOf(parentPaths.head)) {
        Future.successful(Seq(1)) //???
      } else {
        Future.successful(Seq(0))
      }
    }
  }

  def helpTest = new Actorstest() {

    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestActorRef(new DummyRemote(remote.toString()))
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String] = decodeWriteStr(listenerRef ? strToMsg("help"))
    val correct: String = listener.commands
    resF should beEqualTo(correct).await(0, timeoutDuration)
  }

  def ignoreConnectedToAgentManager(connection: TestProbe): Unit = {
    connection
      .ignoreMsg { case Write(data, ack) => data.decodeString("UTF-8") == s"CLI connected to AgentManager.\r\n>" }
  }

  def listAgentsTest = new Actorstest() {

    val agents = Vector(
      AgentInfo("test1", "testClass", emptyConfig, None, running = true, Nil, Java()),
      AgentInfo("test2", "testClass", emptyConfig, None, running = true, Nil, Scala()),
      AgentInfo("test3", "testClass2", emptyConfig, None, running = true, Nil, Java()),
      AgentInfo("test4", "testClass2", emptyConfig, None, running = false, Nil, Scala())
    ).sortBy { info => info.name }
    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap(agents.map { info => info.name -> info }: _*)
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystemRef = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val agentSystem = agentSystemRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestActorRef(new DummyRemote(remote.toString()))
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection,
      remote,
      removeHandler,
      agentSystemRef,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String] = decodeWriteStr(listenerRef ? strToMsg("list agents"))
    val correct: String = listener.agentsStrChart(agents)
    resF should beEqualTo(correct).await(0, timeoutDuration)
  }

  def startAgentTest = new Actorstest() {

    val name = "StartSuccess"
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val ref = system.actorOf(SSAgent.props(emptyConfig, requestHandler, dbHandler), name)
    val clazz = "agentSystem.SSAgent"
    val agentInfo = AgentInfo(name, clazz, emptyConfig, Some(ref), running = false, Nil, Scala())
    val testAgents = MutableMap(name -> agentInfo)
    val managerRef = TestActorRef(new TestManager(testAgents, dbHandler, requestHandler))
    val managerActor = managerRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestProbe() //ActorRef( new DummyRemote(remote.toString()))
    ignoreConnectedToAgentManager(connection)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection.ref,
      remote,
      removeHandler,
      managerRef,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    // val resF :Future[String ]=decodeWriteStr(listenerRef ? strToMsg(s"start $name"))
    // val correct : String =  s"Agent $name started succesfully.\r\n"
    /// resF should beEqualTo( correct ).await( 0, timeoutDuration)
    connection.send(listenerRef, strToMsg(s"start $name"))
    (connection.expectMsgType[Write].data.decodeString("UTF-8") must beEqualTo(s">")) and (
      connection.expectMsgType[Write].data.decodeString("UTF-8") must beEqualTo(s"Agent $name started.\r\n>"))
  }

  def stopAgentTest = new Actorstest() {
    val name = "StartSuccess"
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val ref = system.actorOf(SSAgent.props(emptyConfig, requestHandler, dbHandler), name)
    val clazz = "agentSystem.SSAgent"
    val agentInfo = AgentInfo(name, clazz, emptyConfig, Some(ref), running = true, Nil, Scala())
    val testAgents = MutableMap(name -> agentInfo)
    val managerRef = TestActorRef(new TestManager(testAgents, dbHandler, requestHandler))
    val managerActor = managerRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestProbe()
    ignoreConnectedToAgentManager(connection)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection.testActor,
      remote,
      removeHandler,
      managerRef,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    //val resF :Future[String ]= decodeWriteStr(listenerRef ? strToMsg(s"stop $name"))

    connection.send(listenerRef, strToMsg(s"stop $name"))
    (connection.expectMsgType[Write].data.decodeString("UTF-8") must beEqualTo(s">")) and (
      connection.expectMsgType[Write].data.decodeString("UTF-8") must beEqualTo(s"Agent $name stopped.\r\n>"))
  }

  def unknownCmdTest = new Actorstest() {

    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestProbe() //ActorRef( new DummyRemote(remote.toString()))
    ignoreConnectedToAgentManager(connection)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection.ref,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val correct: String = "Unknown command. Use help to get information of current commands.\r\n>"
    connection.send(listenerRef, strToMsg(s"aueo"))
    connection.expectMsgType[Write].data.decodeString("UTF-8") must beEqualTo(correct)

  }

  def removePathTest() = new Actorstest() {

    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = ActorRef.noSender
    val path = "Objects/object/sensor"
    val removeHandler = new RemoveTester(Path(path))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestProbe() //ActorRef( new DummyRemote(remote.toString()))
    ignoreConnectedToAgentManager(connection)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection.ref,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    // val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"remove $path"))
    val correct: String = s"Successfully removed path $path\r\n>"
    connection.send(listenerRef, strToMsg(s"remove $path"))
    connection.expectMsgType[Write].data.decodeString("UTF-8") must beEqualTo(correct)
  }

  def removeUnexistingPathTest() = new Actorstest() {

    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = ActorRef.noSender
    val path = "Objects/object/sensor"
    val removeHandler = new RemoveTester(Path(path))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestProbe() //ActorRef( new DummyRemote(remote.toString()))
    ignoreConnectedToAgentManager(connection)
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection.ref,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val correct: String = s"Could not remove ${path + "ueaueo"}\r\n>"
    connection.send(listenerRef, strToMsg(s"remove $path" + "ueaueo"))
    connection.expectMsgType[Write].data.decodeString("UTF-8") must beEqualTo(correct)
  }

  def listSubsTest = new Actorstest() {

    val startTime = new Timestamp(new Date().getTime())
    val endTime = new Timestamp(new Date().getTime() + 1.hours.toMillis)
    val interval = 5.minutes
    val nextRunTime = new Timestamp(new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))
    val paths = Vector(
      Path("Objects/object/sensor1"),
      Path("Objects/object/sensor2")
    )
    val intervalSubs: Set[IntervalSub] = Set(
      IntervalSub(35, paths, endTime, callback, interval, startTime),
      IntervalSub(55, paths, endTime, callback, interval, startTime)
    )
    val eventSubs: Set[EventSub] = Set(
      NormalEventSub(40, paths, endTime, callback),
      NormalEventSub(430, paths, endTime, callback),
      NormalEventSub(32, paths, endTime, callback)
    )
    val pollSubs: Set[PolledSub] = Set(
      PollNormalEventSub(59, endTime, nextRunTime, startTime, paths),
      PollNormalEventSub(173, endTime, nextRunTime, startTime, paths),
      PollIntervalSub(37, endTime, interval, nextRunTime, startTime, paths),
      PollIntervalSub(3047, endTime, interval, nextRunTime, startTime, paths)
    )
    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = TestActorRef(new Actor {
      def receive = {
        case ListSubsCmd(ttl) => sender() ! AllSubscriptions(intervalSubs, eventSubs, pollSubs)
      }

    })
    val removeHandler = new RemoveTester(Path("objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestActorRef(new DummyRemote(remote.toString()))
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String] = decodeWriteStr(listenerRef ? strToMsg("list subs"))
    val correct: String = listener.subsStrChart(intervalSubs, eventSubs, pollSubs)
    resF should beEqualTo(correct).await(0, timeoutDuration)
  }

  def removeSubTest() = new Actorstest() {

    val id = 13
    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = TestActorRef(new Actor {
      def receive = {
        case RemoveSubscription(di, ttl) => sender() ! true
      }

    })
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestActorRef(new DummyRemote(remote.toString()))
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String] = decodeWriteStr(listenerRef ? strToMsg(s"remove $id"))
    val correct: String = s"Removed subscription with $id successfully.\r\n>"
    resF should beEqualTo(correct).await(0, timeoutDuration)
  }

  def removeUnexistingSubTest() = new Actorstest() {

    val id = 13
    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = TestActorRef(new Actor {
      def receive = {
        case RemoveSubscription(di, ttl) => sender() ! false
      }

    })
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestActorRef(new DummyRemote(remote.toString()))
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String] = decodeWriteStr(listenerRef ? strToMsg(s"remove $id"))
    val correct: String = s"Failed to remove subscription with $id. Subscription does not exist or it is already expired.\r\n>"
    resF should beEqualTo(correct).await(0, timeoutDuration)
  }

  def showSubTestInterval = {
    val id: Long = 57171
    val paths = Vector(Path("Objects/obj1/"), Path("Objects/obj2/sensor1"), Path("Objects/obj3/sobj/sensor"))
    val startTime = new Timestamp(new Date().getTime())
    val endTime = new Timestamp(new Date().getTime() + 1.hours.toMillis)
    val interval = 5.minutes
    //val nextRunTime = new Timestamp(new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))

    //val remote = new InetSocketAddress("Tester", 22)
    //val removeHandler = new RemoveTester(Path("objects/aue"))
    val sub = Some(IntervalSub(
      id,
      paths,
      endTime,
      callback,
      interval,
      startTime
    ))
    val correct: String = s"Started: $startTime\r\n" +
      s"Ends: $endTime\r\n" +
      s"Interval: $interval\r\n" +
      s"Callback: $callback\r\n" +
      s"Paths:\r\n${paths.mkString("\r\n")}\r\n>"
    showSubTestBase(sub, correct)
  }
  def backupTest = new Actorstest() with FileMatchers {
    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val subscriptionManager = TestActorRef(new Actor {
      def receive = {
        case RemoveSubscription(di, ttl) => sender() ! true
        case GetSubsWithPollData(ttl) => sender() ! Seq.empty[(SavedSub, Option[SubData])]
      }

    })
    val removeHandler = new RemoveTester(Path("Objects/aue"))
    val remote = new InetSocketAddress("Tester", 22)
    val connection = TestActorRef(new DummyRemote(remote.toString()))

    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val filename1 = "nodeclisubfiletest"
    val filename2 = "nodecliodffiletest"
    val resF: Future[String] = decodeWriteStr(listenerRef ? strToMsg(s"backup $filename1 $filename2"))
    val correct = "Success\r\n>"
    resF should beEqualTo(correct).await(0, timeoutDuration)

    (filename1 must beAFilePath) and(
      filename2 must beAFilePath)
    new File(filename1).delete()
    new File(filename2).delete()

  }

  def showSubTestEvent = {
    val id: Long = 57171
    val paths = Vector(Path("Objects/obj1/"), Path("Objects/obj2/sensor1"), Path("Objects/obj3/sobj/sensor"))
    //val startTime = new Timestamp(new Date().getTime())
    val endTime = new Timestamp(new Date().getTime() + 1.hours.toMillis)
    //val interval = 5.minutes
    //val nextRunTime = new Timestamp(new Date().getTime() + interval.toMillis)
    val callback = HTTPCallback(Uri("http://test.org:31"))

    //val remote = new InetSocketAddress("Tester", 22)
    //val removeHandler = new RemoveTester(Path("objects/aue"))
    val sub = Some(NormalEventSub(
      id,
      paths,
      endTime,
      callback
    ))
    val correct: String = s"Ends: $endTime\r\n" +
      s"Callback: $callback\r\n" +
      s"Paths:\r\n${paths.mkString("\r\n")}\r\n>"
    showSubTestBase(sub, correct)
  }

  def showSubTestPollInterval = {
    val id: Long = 57171
    val paths = Vector(Path("Objects/obj1/"), Path("Objects/obj2/sensor1"), Path("Objects/obj3/sobj/sensor"))
    val startTime = new Timestamp(new Date().getTime())
    val endTime = new Timestamp(new Date().getTime() + 1.hours.toMillis)
    val interval = 5.minutes
    val nextRunTime = new Timestamp(new Date().getTime() + interval.toMillis)
    //val callback = HTTPCallback(Uri("http://test.org:31"))

    //val remote = new InetSocketAddress("Tester", 22)
    //val removeHandler = new RemoveTester(Path("objects/aue"))
    val sub = Some(PollIntervalSub(
      id,
      endTime,
      interval,
      nextRunTime,
      startTime,
      paths
    ))
    val correct: String = s"Started: $startTime\r\n" +
      s"Ends: $endTime\r\n" +
      s"Interval: $interval\r\n" +
      s"Last polled: $nextRunTime\r\n" +
      s"Paths:\r\n${paths.mkString("\r\n")}\r\n>"
    showSubTestBase(sub, correct)
  }

  def showSubTestPollEvent = {
    val id: Long = 57171
    val paths = Vector(Path("Objects/obj1/"), Path("Objects/obj2/sensor1"), Path("Objects/obj3/sobj/sensor"))
    val startTime = new Timestamp(new Date().getTime())
    val endTime = new Timestamp(new Date().getTime() + 1.hours.toMillis)
    val interval = 5.minutes
    val nextRunTime = new Timestamp(new Date().getTime() + interval.toMillis)
    //val callback = HTTPCallback(Uri("http://test.org:31"))

    //val remote = new InetSocketAddress("Tester", 22)
    //val removeHandler = new RemoveTester(Path("objects/aue"))
    val sub = Some(PollNormalEventSub(
      id,
      endTime,
      nextRunTime,
      startTime,
      paths
    ))
    val correct: String = s"Started: $startTime\r\n" +
      s"Ends: $endTime\r\n" +
      s"Interval: -1\r\n" +
      s"Last polled: $nextRunTime\r\n" +
      s"Paths:\r\n${paths.mkString("\r\n")}\r\n>"
    showSubTestBase(sub, correct)
  }

  def showSubTestNonexistent = {
    val id: Long = 57171
    //val paths = Vector(Path("Objects/obj1/"), Path("Objects/obj2/sensor1"), Path("Objects/obj3/sobj/sensor"))
    //val startTime = new Timestamp(new Date().getTime())
    //val endTime = new Timestamp(new Date().getTime() + 1.hours.toMillis)
    //val interval = 5.minutes
    //val nextRunTime = new Timestamp(new Date().getTime() + interval.toMillis)
    //val callback = HTTPCallback(Uri("http://test.org:31"))

    //val remote = new InetSocketAddress("Tester", 22)
    //val removeHandler = new RemoveTester(Path("objects/aue"))
    val correct: String = s"Subscription with id $id not found.\r\n>"
    showSubTestBase(None, correct)
  }

  def showSubTestBase(sub: Option[SavedSub], correctOut: String) = new Actorstest() {
    implicit val is = system
    val correct = correctOut
    val remote = new InetSocketAddress("Tester", 22)
    val agentsMap: MutableMap[AgentName, AgentInfo] = MutableMap.empty
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val agentSystem = TestActorRef(new TestManager(agentsMap, dbHandler, requestHandler))
    val removeHandler = new RemoveTester(Path("objects/aue"))
    val subscriptionManager = TestActorRef(new Actor {
      def receive = {
        case SubInfoCmd(id, ttl) => sender() ! sub
      }
    })
    val connection = TestActorRef(new DummyRemote(remote.toString()))
    val listenerRef = TestActorRef(new OmiNodeCLI(
      connection,
      remote,
      removeHandler,
      agentSystem,
      subscriptionManager
    ))
    val resF: Future[String] = decodeWriteStr(listenerRef ?
      strToMsg(s"showSub ${sub.map { s => s.id }.getOrElse(57171)}"))
    resF should beEqualTo(correct).await(0, timeoutDuration)
  }


  def strToWrite(str: String) = Write(ByteString(str))

  class DummyRemote(val ip: String) extends Actor with ActorLogging {
    def receive = {
      case Write(byteStr: ByteString, _) =>
        val str = byteStr.decodeString("UTF-8")
        log.info(s"DummyRemote $ip received $str")
      case str: String => log.info(s" $ip received $str")
      case _ =>
    }
  }

}
