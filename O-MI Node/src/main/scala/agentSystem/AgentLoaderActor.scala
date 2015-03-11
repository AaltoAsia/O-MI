package agentSystem

import http._
import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
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
  protected var bootables : Map[String,Bootable] = Map.empty 
  private val classLoader = createClassLoader()
  Thread.currentThread.setContextClassLoader(classLoader)

  val settings = Settings(context.system)
  self ! Start
  def receive = {
    case Start => loadAndStart
    case ConfigUpdated => loadAndStart
  }

  def loadAndStart = {
    val classnames = getClassnamesWithConfigPath
    val toBeBooted =  classnames map { case (c: String, p: String) => 
      if(!bootables.exists{case (k:String, b:Bootable) => k == c})
        Tuple3( c, p, classLoader.loadClass(c).newInstance.asInstanceOf[Bootable]) 
    }

    for ((c: String, p:String, b: Bootable)  <- toBeBooted) {
      log.info("Starting up " + b.getClass.getName)
      if(b.startup(context.system, p)){
        log.info("Successfully started: "+ b.getClass.getName)
        bootables += Tuple2(c, b)
      } else {
        log.warning("Failed to start: "+ b.getClass.getName)
      } 
    }

    addShutdownHook( toBeBooted.map{ case ( c: String, p:String, b: Bootable ) => b}.to[immutable.Seq] )

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

}
