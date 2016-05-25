/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package agentSystem

import agentSystem._
import agentSystem.AgentTypes._
import http.CLICmds._
import http._
import types.Path
import akka.actor.SupervisorStrategy._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.{
  Actor, 
  ActorRef, 
  ActorInitializationException, 
  ActorKilledException, 
  ActorLogging, 
  OneForOneStrategy, 
  Props, 
  SupervisorStrategy}
import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable.Map
import java.io.File
import java.net.URLClassLoader
import java.sql.Timestamp
import java.util.Date
import java.util.jar.JarFile
import http.CLICmds._
import scala.language.postfixOps

trait InternalAgentLoader extends BaseAgentSystem {
  import context.dispatcher

  /** Classloader for loading classes in jars. */
  Thread.currentThread.setContextClassLoader( createClassLoader())
  /** Settings for getting list of internal agents and their configs from application.conf */


  def start() = {
    val classnames = getAgentConfigurations
    classnames.foreach {
      case configEntry : AgentConfigEntry =>
      agents.get( configEntry.name ) match{
        case None =>
          loadAndStart(configEntry)
        case Some( agentInfo ) =>
          log.warning("Agent already running: " + configEntry.name)
      }
    }
  }

  def loadAndStart(name : AgentName, classname : String, config : String) = {
    Try {
      log.info("Instantiating agent: " + name + " of class " + classname)
      val classLoader = Thread.currentThread.getContextClassLoader
      val clazz = classLoader.loadClass(classname)
      val interface =  classOf[InternalAgent]
      if( interface.isAssignableFrom(clazz) ){
        val prop  = Props( clazz )
        val agent = context.actorOf( prop, name.toString )
        val date = new Date()
        val timeout = settings.internalAgentsStartTimout
        val configureF = ask(agent,Configure(config))(timeout)
        configureF.onSuccess{
          case error : InternalAgentFailure =>  
          log.warning(s"Agent $name failed at configuration. Terminating agent.")
          context.stop(agent)
          case success : InternalAgentSuccess =>  
          log.info(s"Agent $name has been configured. Starting...");
          val startF = ask(agent,Start())(timeout)
          startF.onSuccess{
            case error : InternalAgentFailure =>  
            log.warning(s"Agent $name failed to start. Terminating agent.")
            context.stop(agent)
            case success : InternalAgentSuccess =>  
            log.info(s"Agent $name started successfully.")
            agents += name -> AgentInfo(name,classname, config, agent, true)
          }
          startF.onFailure{ 
            case e => 
            log.warning( s"Starting $name failed with: " + e )
          }
        }
        configureF.onFailure{ 
          case e => 
          log.warning( s"Configuration $name failed with: " + e )
        }
      } else {
        log.warning(s"Class $classname did not implement AbstractInternalAgent.")
      }
    } match {
      case Success(_) => ()
      case Failure(e) => e match {
        case e: NoClassDefFoundError =>
          log.warning("Classloading failed. Could not load: " + classname + "\n" + e + " caught")
        case e: ClassNotFoundException =>
          log.warning("Classloading failed. Could not load: " + classname + "\n" + e + " caught")
        case e: Throwable =>
          log.warning(s"Class $classname could not be loaded, created, initialized or started. Because received $e.")
          log.warning(e.getStackTrace.mkString("\n"))
        case t => throw t
      }
    }
  }
  def loadAndStart(configEntry: AgentConfigEntry) : Unit = loadAndStart( configEntry.name,configEntry.classname, configEntry.config)

  /**
   * Creates classloader for loading classes from jars in deploy directory.
   *
   */
  private[this] def createClassLoader(): URLClassLoader = {
    val deploy = new File("O-MI Node/deploy")
    lazy val ideDeploy = new File("deploy")

    if (deploy.exists) {
      val urls = loadDirectoryJars(deploy)
      urls foreach { url => log.info("Deploying " + url) }
      new URLClassLoader(urls, Thread.currentThread.getContextClassLoader)
    } else if (ideDeploy.exists()) {
      val urls =  loadDirectoryJars(ideDeploy)
      urls foreach { url => log.info("Deploying " + url) }
      new URLClassLoader(urls, Thread.currentThread.getContextClassLoader)
    }else {
      log.warning("No deploy dir found at " + deploy)
      new URLClassLoader(Array.empty, Thread.currentThread.getContextClassLoader)
    }
  }

  /**
   * Method for loading jars in deploy directory.
   * Jars should contain class files of agents.
   */
  private[this] def loadDirectoryJars(directory: File) = {
    val jars = directory.listFiles.filter(_.getName.endsWith(".jar"))
    val nestedJars = jars map { jar: File =>
      loadJar(jar)
    } collect {
      case Some(arr) => arr
    } flatten

    (jars ++ nestedJars) map { _.toURI.toURL }

  }

  private[this] def loadJar( jar: File) : Option[ Array[ File ] ]= {
    if( jar.getName.endsWith(".jar") && jar.exists() ){
        val jarFile = new JarFile(jar)
        val jarEntries = jarFile.entries.asScala.toArray.filter(_.getName.endsWith(".jar"))
        val urls = jarEntries map { entry => new File("jar:file:%s!/%s" format (jarFile.getName, entry.getName)) }
        Some(urls)
    } else None
  }
  private[this] def loadJar( jarName: String) : Option[ Array[ File ] ] = {
    val file = new File(jarName)
    loadJar( file )
  }
  private[this] def addJarToClassloader( jarName: String) = {
    val urlsO = loadJar(jarName)
    urlsO match {
      case None => 
      case Some(arr) =>
        arr foreach {
          url =>
          log.info("Deploying " + url) 
        }
        val urls = arr map { _.toURI.toURL } 
        val classLoader = new URLClassLoader(urls, Thread.currentThread.getContextClassLoader)
        Thread.currentThread.setContextClassLoader(classLoader)
    }
  }

  private[agentSystem] def getAgentConfigurations: Array[AgentConfigEntry] = {
    val agentsO = Option(settings.internalAgents)
    agentsO match {
      case Some(agents) => 
        val names : Set[String] = asScalaSet(agents.keySet()).toSet // mutable -> immutable
        names.map{ 
          name =>
          val tuple = agents.toConfig().getObject(name).unwrapped().asScala
          for{
            classname <- tuple.get("class")
            config <- tuple.get("config")
          } yield AgentConfigEntry(name, classname.toString, config.toString) 
        }.flatten.toArray
      case None =>
        Array.empty
    }
  }
}
