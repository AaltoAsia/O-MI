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
/** Helper Object for creating AgentLoader.
  *
  */
object AgentLoader{
  def props() : Props = Props(new AgentLoader())
}


/** AgentLoader loads agents from jars in deploy directory.
  * Supervise agents and startups bootables.
  *
  */
class AgentLoader  extends Actor with ActorLogging {
  
  case class ConfigUpdated()
  //Container for bootables
  protected var bootables : scala.collection.mutable.Map[String,(Bootable, String)] = Map.empty
  //getter method to allow testing
  private[agentSystem] def getBootables = bootables
  //Classloader for loading classes in jars.
  private val classLoader = createClassLoader()
  Thread.currentThread.setContextClassLoader(classLoader)

  //Settings for getting list of Bootables and configs from application.conf
  private val settings = Settings(context.system)
  loadAndStart

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
      val toBeBooted =  classnames map { case (c: String, p: String) => 
        try {
          if(!bootables.exists{case (k:String, (b:Bootable, _ ) ) => k == c}){
              Tuple3( c, p, classLoader.loadClass(c).newInstance.asInstanceOf[Bootable]) 
          } else if(bootables.exists{case (k:String, (b:Bootable, config: String ) ) => k == c && p != config }){ 
            log.warning("Agent config changed: "+ c +"\n Agent will be removed and restarted.")
            val agents = bootables(c)._1.getAgentActor
            bootables -= c
            agents foreach{ a => a ! Stop}
            Tuple3( c, p, classLoader.loadClass(c).newInstance.asInstanceOf[Bootable]) 
          } else { 
            log.warning("Agent allready running: "+ c)
          }
        } catch {
          case e: ClassNotFoundException  => log.warning("Classloading failed. Could not load: " + c +"\n" + e + " caught")
          case e: Exception => log.warning(s"Classloading failed. $e")
        }
      }

    for ((c: String, p:String, b: Bootable)  <- toBeBooted) {
      log.info("Starting up " + b.getClass.getName)
      if(bootables.get(c).isEmpty){
        try{
          if(b.startup(context.system, p)){
            log.info("Successfully started: "+ b.getClass.getName)
            bootables += Tuple2(c, (b, p))
            val agents = b.getAgentActor
            agents.foreach{agent => context.watch(agent)}
          } else {
            log.warning("Failed to start: "+ b.getClass.getName)
          } 
        } catch {
          case e: InvalidActorNameException  => log.warning("Tried to create same named actors")
          case e: Exception => log.warning(s"Classloading failed. $e")
        }
      } else {
        
          log.warning("Multiple instances of: "+ b.getClass.getName)
      }
    }

  addShutdownHook( bootables.map{ 
      case ( c: String, ( b: Bootable, p:String) ) => b
    }.to[immutable.Seq] )

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
  private def addShutdownHook(bootables: immutable.Seq[Bootable]): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run = {
        log.warning("Shutting down Akka...")

        for (bootable <- bootables) {
          log.info("Shutting down " + bootable.getClass.getName)
          bootable.shutdown()
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
