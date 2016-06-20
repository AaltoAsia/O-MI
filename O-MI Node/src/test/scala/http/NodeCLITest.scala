package http

import java.net.InetSocketAddress//(String hostname, int port)
import scala.collection.mutable.{ Map => MutableMap }
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.{ByteString, Timeout}
import akka.actor.{Actor, ActorSystem, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.testkit._
import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.create.InterpolatedFragment
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.matcher.FutureMatchers._
import com.typesafe.config.{ConfigFactory, Config}
import testHelpers.Actorstest
import types.Path
import responses.RemoveHandler
import agentSystem.{AgentName, AgentInfo, TestManager, SSAgent}
import akka.util.{ByteString, Timeout}
import akka.io.Tcp.{Write, Received}

class NodeCLITest(implicit ee: ExecutionEnv) extends Specification{
  "NodeCLI should " >> {
    "return list of available commands when help command is received" >> helpTest
    "return table of agents when list agents command is received" >> listAgentsTest
    "return correct message when start agent command is received" >> startAgentTest
    "return correct message when stop agent command is received" >> stopAgentTest
    "return help information when unknown command is received" >> unknownCmdTest
    "return correct message when path remove command is received" >> removePathTest
    "return correct message when path remove command for unexisting path is received" >> removeUnexistingPathTest
    "return table of subscriptions when list subs command is received" >> listSubsTest.pendingUntilFixed
    "return correct message when sub remove command is received" >> removeSubTest.pendingUntilFixed
    "return correct message when sub remove command for unexisting id is received" >> removeUnexistingSubTest.pendingUntilFixed
  }
  implicit val timeout = Timeout( 1.minutes )
 def timeoutDuration= 10.seconds
 def emptyConfig = ConfigFactory.empty()
  def AS =ActorSystem(
    "startstop",
    ConfigFactory.load(
      ConfigFactory.parseString(
        """
        akka.loggers = ["akka.testkit.TestEventListener"]
        """).withFallback(ConfigFactory.load()))
  )
  def strToMsg(str: String) = Received(ByteString(str))
  def decodeWriteStr( future : Future[Any] )(implicit system: ActorSystem) ={
    import system.dispatcher
    future.map{  
      case Write( byteStr: ByteString, _ ) => byteStr.decodeString("UTF-8")
    }
  }

  class RemoveTester( path: Path)extends RemoveHandler{
    implicit def dbConnection: database.DB = ???
    protected def log: akka.event.LoggingAdapter = ???

    override def handlePathRemove(parentPath: Path): Boolean = { 
      path == parentPath || path.isAncestor(parentPath)
    }
  }
  def helpTest = new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
        remote,
        agentSystem,
        subscriptionManager,
        removeHandler
      ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg("help"))    
    val correct: String  = listener.commands 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def listAgentsTest= new Actorstest(AS){
    import system.dispatcher
    val agents = Vector(
       AgentInfo( "test1", "testClass", emptyConfig, ActorRef.noSender, true, Nil ), 
       AgentInfo( "test2", "testClass", emptyConfig, ActorRef.noSender, true, Nil ),
       AgentInfo( "test3", "testClass2", emptyConfig, ActorRef.noSender, true, Nil ), 
       AgentInfo( "test4", "testClass2", emptyConfig, ActorRef.noSender, false, Nil ) 
     ).sortBy{info => info.name }
    val agentsMap : MutableMap[AgentName,AgentInfo] = MutableMap(agents.map{ info => info.name -> info }:_*)
    val agentSystemRef = TestActorRef(new TestManager( agentsMap ))
    val agentSystem = agentSystemRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
        remote,
        agentSystemRef,
        subscriptionManager,
        removeHandler
      ))
    val listener = listenerRef.underlyingActor
    val resF :Future[String ]=decodeWriteStr(listenerRef ? strToMsg("list agents"))    
    val correct : String =  listener.agentsStrTable( agents) 
    resF should beEqualTo( correct ).await( 0, timeoutDuration)
  }
  
  def startAgentTest=  new Actorstest(AS){
  
   val name = "StartSuccess"
   val ref = system.actorOf( Props( new SSAgent), name)
   val clazz = "agentSystem.SSAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, false, Nil)
   val testAgents = MutableMap( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
   val managerActor = managerRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
        remote,
        managerRef,
        subscriptionManager,
        removeHandler
      ))
    val listener = listenerRef.underlyingActor
    val resF :Future[String ]=decodeWriteStr(listenerRef ? strToMsg(s"start $name"))    
    val correct : String =  s"Agent $name started succesfully.\n" 
    resF should beEqualTo( correct ).await( 0, timeoutDuration)

  }
  def stopAgentTest= new Actorstest(AS){
   val name = "StartSuccess"
   val ref = system.actorOf( Props( new SSAgent), name)
   val clazz = "agentSystem.SSAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, true, Nil)
   val testAgents = MutableMap( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
   val managerActor = managerRef.underlyingActor
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
        remote,
        managerRef,
        subscriptionManager,
        removeHandler
      ))
    val listener = listenerRef.underlyingActor
    val resF :Future[String ]=decodeWriteStr(listenerRef ? strToMsg(s"stop $name"))    
    val correct : String =  s"Agent $name stopped succesfully.\n" 
    resF should beEqualTo( correct ).await( 0, timeoutDuration)

  }
  def unknownCmdTest = new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val removeHandler = new RemoveTester( Path("Objects/aue" ) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
        remote,
        agentSystem,
        subscriptionManager,
        removeHandler
      ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg("aueo"))    
    val correct: String  = "Unknown command. Use help to get information of current commands.\n" 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def removePathTest= new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val path = "Objects/object/sensor"
    val removeHandler = new RemoveTester( Path(path) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
        remote,
        agentSystem,
        subscriptionManager,
        removeHandler
      ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"remove $path"))    
    val correct: String  = s"Successfully removed path $path\n" 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def removeUnexistingPathTest= new Actorstest(AS){
    import system.dispatcher
    val agentSystem =ActorRef.noSender
    val subscriptionManager = ActorRef.noSender
    val path = "Objects/object/sensor"
    val removeHandler = new RemoveTester( Path(path) )
    val remote = new InetSocketAddress("Tester",22)
    val listenerRef = TestActorRef(new OmiNodeCLI(
        remote,
        agentSystem,
        subscriptionManager,
        removeHandler
      ))
    val listener = listenerRef.underlyingActor
    val resF: Future[String ] =decodeWriteStr(listenerRef ? strToMsg(s"remove " + path +"ueaueo" ))    
    val correct: String  = s"Given path does not exist\n" 
    resF should beEqualTo(correct ).await( 0, timeoutDuration)
  }
  def listSubsTest= new Actorstest(AS){
   1 === 2 
  }
  def removeUnexistingSubTest= new Actorstest(AS){
    //TODO
    1 === 2
  }
  def removeSubTest= new Actorstest(AS){
    //TODO
    1 === 2
  }
}
