package agentLoader

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import xml._
import io._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging
import java.net.URLClassLoader
import java.io.File
import agents._
import scala.collection.mutable.Map

import java.lang.Boolean.getBoolean
import java.util.jar.JarFile
import scala.collection.immutable
import scala.collection.JavaConverters._

trait Bootable {
  def hasConfig : Boolean 
  def setConfigPath( path : String): Unit  
  /**
   * Callback run on microkernel startup.
   * Create initial actors and messages here.
   */
  def startup(): Unit

  /**
   * Callback run on microkernel shutdown.
   * Shutdown actor systems here.
   */
  def shutdown(): Unit

  def getAgentActor : ActorRef
}

case class GivehMeODF(msg: String)
case class Start()
case class ConfigUpdated()
class AgentLoaderActor extends Actor with ActorLogging {

  protected var bootables : Map[String,Bootable] = Map.empty 
  private val classLoader = createClassLoader()
  Thread.currentThread.setContextClassLoader(classLoader)

  def receive = {
    case Start =>
    case ConfigUpdated =>
  }

  def loadAndStart = {
    val classnames = getClassnamesWithConfigPath
    val toBeBooted =  classnames map { case (c: String, p: String) => 
      if(!bootables.exists{case (k:String, b:Bootable) => k == c})
        Tuple3( c, p, classLoader.loadClass(c).newInstance.asInstanceOf[Bootable]) 
    }

    for ((c: String, p:String, b: Bootable)  <- toBeBooted) {
   //   log("Starting up " + bootable.getClass.getName)
      b.setConfigPath(p)
      b.startup()
      bootables += Tuple2(c, b)
    }

    addShutdownHook( toBeBooted.map{ case ( c: String, p:String, b: Bootable ) => b}.to[immutable.Seq] )

  }

  private def createClassLoader(): ClassLoader = {
    if (ActorSystem.GlobalHome.isDefined) {
      val home = ActorSystem.GlobalHome.get
      val deploy = new File(home, "deploy")
      if (deploy.exists) {
        loadDeployJars(deploy)
      } else {
   //     log("[warning] No deploy dir found at " + deploy)
        Thread.currentThread.getContextClassLoader
      }
    } else {
   //   log("[warning] Akka home is not defined")
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
        //log("")
        //log("Shutting down Akka...")

        for (bootable ← bootables) {
          //log("Shutting down " + bootable.getClass.getName)
          bootable.shutdown()
        }

        //log("Successfully shut down Akka")
      }
    }))
  }

  private def getClassnamesWithConfigPath : Array[(String,String)]= ???

}
