package agentSystem

import http._
import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.event.{Logging, LoggingAdapter}
import scala.collection.mutable.Map
import scala.collection.immutable
import scala.collection.JavaConverters._
import java.net.URLClassLoader
import java.util.jar.JarFile
import java.io.File
import scala.concurrent._


/** Helper Object for creating AgentLoader.
  *
  */
object InternalAgentLoader{
  def props(): Props = Props(new InternalAgentLoader())
}

  case class ConfigUpdated()

/** AgentLoader loads agents from jars in deploy directory.
  * Supervise agents and startups bootables.
  *
  */
class InternalAgentLoader  extends Actor with ActorLogging {
  
  import ExecutionContext.Implicits.global
  import context.system

  case class AgentInfo(name: String, configPath: String, thread: Thread)
  //Container for bootables
  protected var agents : scala.collection.mutable.Map[String,(InternalAgent, String)] = Map.empty
  //getter method to allow testing
  private[agentSystem] def getAgents = agents
  //Classloader for loading classes in jars.
  private val classLoader = createClassLoader()
  Thread.currentThread.setContextClassLoader(classLoader)

  //Settings for getting list of Bootables and configs from application.conf
  private val settings = Settings(context.system)
  start

  /** Method for handling received messages.
    * Should handle:
    *   -- ConfigUpdate with updating running AgentActors.
    *   -- Terminated with trying to restart AgentActor. 
    */
  def receive = {
    case "Start" => start
  }

  /** Load Bootables from jars in deploy directory and start AgentActors up.
    *
    */
  def start = {
    val classnames = getClassnamesWithConfigPath
    classnames.foreach{
      case (classname: String, configPath: String) => 
      try {
        if(!agents.exists{
            case (agentname: String, _ ) => classname == agentname
          })
        {
          loadAndStart(classname, configPath)
        } else { 
          log.warning("Agent allready running: "+ classname)
        }
      } catch {
        case e: ClassNotFoundException  => log.warning("Classloading failed. Could not load: " + classname +"\n" + e + " caught")
        case e: Exception => log.warning(s"Classloading failed. $e")
      }
    }
    addShutdownHook(agents)
  }

  def loadAndStart(classname: String, configPath: String) ={
    log.info("Instantitating agent: " + classname )
    val clazz = classLoader.loadClass(classname)

    val const = clazz.getConstructors()(0)
    val agent : InternalAgent = const.newInstance(configPath).asInstanceOf[InternalAgent] 
    agents += Tuple2( classname, Tuple2( agent, configPath) )
    agent.start()
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
  private def addShutdownHook(agents: Map[String, Tuple2[InternalAgent, String]]): Unit = {
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
    settings.agents.unwrapped().asScala.map{ case (s: String, o: Object) => (s, o.toString)}.toArray 
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
