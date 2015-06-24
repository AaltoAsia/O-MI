package agentSystem

import http._
import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.event.{Logging, LoggingAdapter}
import akka.io.{ IO, Tcp  }
import scala.collection.mutable.Map
import scala.collection.immutable
import scala.collection.JavaConverters._
import java.net.URLClassLoader
import java.util.jar.JarFile
import java.io.File
import scala.concurrent._

import java.util.Date
import java.sql.Timestamp

import InternalAgentCLICmds._
/** Helper Object for creating AgentLoader.
  *
  */
object InternalAgentLoader{
  def props(): Props = Props(new InternalAgentLoader())
}

  case class ThreadException( agent: InternalAgent, exception: Exception)
  case class ThreadInitialisationException( agent: InternalAgent, exception: Exception)
/** AgentLoader loads agents from jars in deploy directory.
  * Supervise agents and startups bootables.
  *
  */
class InternalAgentLoader  extends Actor with ActorLogging {
  
  import Tcp._
  import ExecutionContext.Implicits.global
  import context.system

  case class AgentInfo(name: String, configPath: String, thread: Thread)
  //Container for bootables
  protected var agents : scala.collection.mutable.Map[String,(Option[InternalAgent], String, Timestamp)] = Map.empty
  //getter method to allow testing
  private[agentSystem] def getAgents = agents
  //Classloader for loading classes in jars.
  private val classLoader = createClassLoader()
  Thread.currentThread.setContextClassLoader(classLoader)

  //Settings for getting list of Bootables and configs from application.conf
  private val settings = Settings(context.system)
  InternalAgent.setLoader(self)
  InternalAgent.setLog(log)
  start()

  /** Method for handling received messages.
    * Should handle:
    *   -- ConfigUpdate with updating running AgentActors.
    *   -- Terminated with trying to restart AgentActor. 
    */
  def receive = {
    case StartCmd(agent: String) =>{
    
      val agentInfo = agents.find{ info: Tuple2[String,Tuple3[Option[InternalAgent],String, Timestamp]] => info._1 == agent}
      if(agentInfo.isEmpty){
         log.warning("Command for not stored agent!: " + agent)
       } else if( agentInfo.get._2._1.isEmpty ){
        log.warning(s"Starting: " + agentInfo.get._1)
        agents -= agentInfo.get._1
        Thread.sleep(3000)
        loadAndStart(agentInfo.get._1, agentInfo.get._2._2)
      }
    } 
    case ReStartCmd(agent: String) =>{
      val agentInfo = agents.find{ info: Tuple2[String,Tuple3[Option[InternalAgent],String, Timestamp]] => info._1 == agent}
      if(agentInfo.isEmpty){
         log.warning("Command for not stored agent!: " + agent)
       } else  if( agentInfo.get._2._1.nonEmpty && agentInfo.get._2._1.get.isAlive){
        log.warning(s"Re-Starting: " + agentInfo.get._1)
        agents -= agentInfo.get._1
        agentInfo.get._2._1.get.shutdown();
        Thread.sleep(3000)
        loadAndStart(agentInfo.get._1, agentInfo.get._2._2)
      }
    }
    case StopCmd(agent: String) => {
      val agentInfo = agents.find{ info: Tuple2[String,Tuple3[Option[InternalAgent],String, Timestamp]] => info._1 == agent}
      if(agentInfo.isEmpty){
         log.warning("Command for not stored agent!: " + agent)
       } else  if( agentInfo.get._2._1.nonEmpty && agentInfo.get._2._1.get.isAlive){
        log.warning(s"Stopping: " + agentInfo.get._1)
        agents -= agentInfo.get._1
        agentInfo.get._2._1.get.shutdown();
        agents += Tuple2( agentInfo.get._1, Tuple3( None, agentInfo.get._2._2, agentInfo.get._2._3) )
      }
    }
    case ThreadException( agent: InternalAgent, exception: Exception ) =>
      log.warning(s"InternalAgent caugth exception: $exception")
      val agentInfo = agents.find{ info: Tuple2[String,Tuple3[Option[InternalAgent],String, Timestamp]] => info._2._1.get == agent}
      var date = new Date()
      if(agentInfo.isEmpty){
         log.warning("Exception from not stored agent!: "+ agent)
       } else if( agentInfo.get._2._1.nonEmpty && date.getTime - agentInfo.get._2._3.getTime > 300000 ) {
        log.warning(s"Trying to relaunch:" + agentInfo.get._1)
        agents -= agentInfo.get._1
        loadAndStart(agentInfo.get._1, agentInfo.get._2._2)
      }
    case ThreadInitialisationException( agent: InternalAgent, exception: Exception) =>
      log.warning(s"InternalAgent $agent initialisation failed. $exception")

    case Bound(localAddress) =>
      // TODO: do something?
      // It seems that this branch was not executed?
   
    case CommandFailed(b: Bind) =>
      log.warning(s"CLI connection failed: $b")
      context stop self
   
    case Connected(remote, local) =>
      val connection = sender()
      log.info(s"CLI connected from $remote to $local")

      val cli = context.actorOf(
        Props(classOf[InternalAgentCLI], remote),
        "cli-"+remote.toString.tail
      )
      connection ! Register(cli)
  }

  /** Load Bootables from jars in deploy directory and start AgentActors up.
    *
    */
  def start() = {
    val classnames = getClassnamesWithConfigPath
    classnames.foreach{
      case (classname: String, configPath: String) => 
        if(!agents.exists{
            case (agentname: String, _ ) => classname == agentname
          })
        {
          loadAndStart(classname, configPath)
        } else { 
          log.warning("Agent allready running: "+ classname)
        }
    }
  }

  def loadAndStart(classname: String, configPath: String) ={
      try {
        log.info("Instantitating agent: " + classname )
        val clazz = classLoader.loadClass(classname)
        val const = clazz.getConstructors()(0)
        val agent : InternalAgent = const.newInstance(configPath).asInstanceOf[InternalAgent] 
        val date = new Date()
        agents += Tuple2( classname, Tuple3( Some(agent), configPath, new Timestamp(date.getTime) ) )
        agent.start()
      } catch {
        case e: NoClassDefFoundError => 
          log.warning("Classloading failed. Could not load: " + classname +"\n" + e + " caught")
        case e: ClassNotFoundException  =>
          log.warning("Classloading failed. Could not load: " + classname +"\n" + e + " caught")
        case e: Exception =>
          log.warning(s"Classloading failed. $e")
      }    
  }

  /** Creates classloader for loading classes from jars in deploy directory.
    *
    */
  private def createClassLoader(): ClassLoader = {
    val deploy = new File("deploy")
    if (deploy.exists) {
      loadDeployJars(deploy)
    } else {
      log.warning("No deploy dir found at " + deploy)
      Thread.currentThread.getContextClassLoader
    }
  }

  /** Method for loading jars in deploy directory.
    * Jars should contain class files of agents and their bootables.
    *
    */
  private def loadDeployJars(deploy: File): ClassLoader = {
    val jars = deploy.listFiles.filter(_.getName.endsWith(".jar"))

    val nestedJars = jars flatMap { jar =>
      val jarFile = new JarFile(jar)
      val jarEntries = jarFile.entries.asScala.toArray.filter(_.getName.endsWith(".jar"))
      jarEntries map { entry => new File("jar:file:%s!/%s" format (jarFile.getName, entry.getName)) }
    }

    val urls = (jars ++ nestedJars) map { _.toURI.toURL }

    urls.foreach{ url => log.info("Deploying " + url) }

    new URLClassLoader(urls, Thread.currentThread.getContextClassLoader)
  }
  /** Simple shutdown hook adder for AgentActors.
    *
    */
  private def addShutdownHook(agents: Map[String, Tuple3[InternalAgent, String, Timestamp]]): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run = {
        log.warning("Shutting down Akka...")

        for (agent <- agents) {
          log.info("Shutting down " + agent._1)
          agent._2._1.shutdown
        }

        log.info("Successfully shut down Akka")
      }
    }))
  }

  /** Simple function for getting Bootable's name and Agent config file path pairs.
    *
    */
  private[agentSystem] def getClassnamesWithConfigPath : Array[(String,String)]= {
    settings.internalAgents.unwrapped().asScala.map{ case (s: String, o: Object) => (s, o.toString)}.toArray 
  }
  /** Supervison strategy for supervising AgentActors.
    *
    */
  final val defaultStrategy: SupervisorStrategy = {
    def defaultDecider: Decider = {
      case _: ActorInitializationException => Stop
      case _: ActorKilledException         => Stop
      case _: Exception                    => Restart 
    }
    OneForOneStrategy()(defaultDecider)
  }


}
