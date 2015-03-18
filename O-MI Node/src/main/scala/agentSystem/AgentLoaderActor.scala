package agentSystem

import http._
import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.io.{ IO, Tcp }
import akka.event.{Logging, LoggingAdapter}
import akka.util.ByteString
import akka.util.Timeout
import akka.pattern.ask
import xml._
import io._
import scala.concurrent.duration._
import scala.collection.mutable.Map
import scala.collection.immutable
import scala.collection.JavaConverters._
import scala.collection.JavaConverters._
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.lang.Boolean.getBoolean
import java.util.jar.JarFile
import java.io.File

object AgentLoader{
  def props() : Props = Props(new AgentLoader())
}
case class ConfigUpdated()
class AgentLoader  extends Actor with ActorLogging {
  protected var bootables : scala.collection.mutable.Map[String,(Bootable, String)] = Map.empty 
  private val classLoader = createClassLoader()
  Thread.currentThread.setContextClassLoader(classLoader)

  val settings = Settings(context.system)
  self ! Start
  def receive = {
    case Start => loadAndStart
    case ConfigUpdated => loadAndStart
    case Terminated(agent:ActorRef) =>
    val b = bootables.find{ case (classname: String, (b: Bootable, config: String) ) => b.getAgentActor == agent}
    if(b.nonEmpty)
      b.get._2._1.startup(context.system, b.get._2._2)
  }

  def loadAndStart = {
    val classnames = getClassnamesWithConfigPath
      val toBeBooted =  classnames map { case (c: String, p: String) => 
        try {
        if(!bootables.exists{case (k:String, (b:Bootable, _ ) ) => k == c}){
            Tuple3( c, p, classLoader.loadClass(c).newInstance.asInstanceOf[Bootable]) 
        } else if(bootables.exists{case (k:String, (b:Bootable, config: String ) ) => k == c && p != config }){ 
          log.warning("Agent config changed: "+ c +"\n Agent will be removed and restarted.")
          val agent = bootables(c)._1.getAgentActor
          bootables -= c
          agent ! Stop
          Tuple3( c, p, classLoader.loadClass(c).newInstance.asInstanceOf[Bootable]) 
        } else { 
          log.warning("Agent allready running: "+ c)
        }
        } catch {
          case e: ClassNotFoundException  => log.warning("Classloading failed. Could not load: " + c +"\n" + e + " caucht")
        }
      }

    for ((c: String, p:String, b: Bootable)  <- toBeBooted) {
      log.info("Starting up " + b.getClass.getName)
      if(bootables.get(c).isEmpty){
        try{
          if(b.startup(context.system, p)){
            log.info("Successfully started: "+ b.getClass.getName)
            bootables += Tuple2(c, (b, p))
            context.watch(b.getAgentActor)
          } else {
            log.warning("Failed to start: "+ b.getClass.getName)
          } 
        } catch {
          case e: InvalidActorNameException  => log.warning("Tryed to create same named actors")
        }
      } else {
        
          log.warning("Multiple instances of: "+ b.getClass.getName)
      }
    }

   //TODO: Fix amtching error, exception. 
  addShutdownHook( bootables.map{ 
      case ( c: String, ( b: Bootable, p:String) ) => b
    }.to[immutable.Seq] )

  }

  private def createClassLoader(): ClassLoader = {
    val deploy = new File("deploy")
    if (deploy.exists) {
      loadDeployJars(deploy)
    } else {
      log.warning("No deploy dir found at " + deploy)
      Thread.currentThread.getContextClassLoader
    }
  }

  private def loadDeployJars(deploy: File): ClassLoader = {
    val jars = deploy.listFiles.filter(_.getName.endsWith(".jar"))

    val nestedJars = jars flatMap { jar =>
      val jarFile = new JarFile(jar)
      val jarEntries = jarFile.entries.asScala.toArray.filter(_.getName.endsWith(".jar"))
      jarEntries map { entry ⇒ new File("jar:file:%s!/%s" format (jarFile.getName, entry.getName)) }
    }

    val urls = (jars ++ nestedJars) map { _.toURI.toURL }

  //  urls foreach { url <= log("Deploying " + url) }

    new URLClassLoader(urls, Thread.currentThread.getContextClassLoader)
  }

  private def addShutdownHook(bootables: immutable.Seq[Bootable]): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run = {
        log.warning("Shutting down Akka...")

        for (bootable ← bootables) {
          log.info("Shutting down " + bootable.getClass.getName)
          bootable.shutdown()
        }

        log.info("Successfully shut down Akka")
      }
    }))
  }

  //TODO: handle config
  private def getClassnamesWithConfigPath : Array[(String,String)]= {
    settings.agents.unwrapped().asScala.map{ case (s: String, o: Object) => (s, o.toString)}.toArray 
  }
  final val defaultStrategy: SupervisorStrategy = {
    def defaultDecider: Decider = {
      case _: ActorInitializationException => Stop
      case _: ActorKilledException         => Stop
      case _: Exception                    => Restart 
    }
    OneForOneStrategy()(defaultDecider)
  }


}
