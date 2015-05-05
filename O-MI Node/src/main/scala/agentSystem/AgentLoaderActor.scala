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
object AgentLoader{
  def props() : Props = Props(new AgentLoader())
}

  case class ConfigUpdated()

/** AgentLoader loads agents from jars in deploy directory.
  * Supervise agents and startups bootables.
  *
  */
class AgentLoader  extends Actor with ActorLogging {
  
  import ExecutionContext.Implicits.global
  import context.system

  //Container for bootables
  protected var agents : scala.collection.mutable.Map[String,(ActorRef, String)] = Map.empty
  //getter method to allow testing
  private[agentSystem] def getAgents = agents
  //Classloader for loading classes in jars.
  private val classLoader = createClassLoader()
  Thread.currentThread.setContextClassLoader(classLoader)

  //Settings for getting list of Bootables and configs from application.conf
  private val settings = Settings(context.system)
  //loadAndStart

  /** Method for handling received messages.
    * Should handle:
    *   -- ConfigUpdate with updating running AgentActors.
    *   -- Terminated with trying to restart AgentActor. 
    */
  def receive = {
    case ConfigUpdated => loadAndStart
    // If an AgentActor is terminated, try restarting by Bootable. 
    case Terminated(agent:ActorRef) =>
    /*
     //FIX: one terminated causes n-1 terminated
    val b = bootables.find{ case (classname: String, (b: Bootable, config: String) ) => b.getAgentActor.exist(agent)}
    if(b.nonEmpty)
    {
      b.getAgentActor.foreach{a => a ! Stop}
      b.get._2._1.startup(context.system, b.get._2._2)
    }*/
  }

  /** Load Bootables from jars in deploy directory and start AgentActors up.
    *
    */
  def loadAndStart = {
    val classnames = getClassnamesWithConfigPath
    val toBeCreated =  classnames map { case (classname_jar: String, configPath: String) => 
      try {
        if(!agents.exists{case (classname: String, (ref: ActorRef, _ ) ) => classname == classname_jar}){
          log.info("Instantitating agent: " + classname_jar )
          val clazz = classLoader.loadClass(classname_jar)
          val const = clazz.getConstructors()(0)
          val agent = system.actorOf(Props.create(clazz, configPath), classname_jar)
          Tuple2( classname_jar, Tuple2( agent, classname_jar)) 
        } else { 
          log.warning("Agent allready running: "+ classname_jar)
        }
      } catch {
        case e: ClassNotFoundException  => log.warning("Classloading failed. Could not load: " + classname_jar +"\n" + e + " caught")
        case e: Exception => log.warning(s"Classloading failed. $e")
      }
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
  private def addShutdownHook(bootables: immutable.Seq[ActorRef]): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run = {
        log.warning("Shutting down Akka...")

        for (bootable <- bootables) {
          log.info("Shutting down " + bootable.getClass.getName)
          bootable ! Stop
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
