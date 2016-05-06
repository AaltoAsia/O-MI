/**
  Copyright (c) 2016 Aalto University.

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
import http.CLICmds._
import http._
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
import scala.collection.mutable.Map
import java.io.File
import java.net.URLClassLoader
import java.sql.Timestamp
import java.util.Date
import java.util.jar.JarFile
import http.CLICmds._

object InternalAgentManager {
  def props(): Props = Props(new InternalAgentManager())
}

class InternalAgentManager extends Actor with ActorLogging {
  import context.dispatcher
  sealed trait AAgent{
    def name:       String
    def classname:  String
    def config:     String
  }
  case class AgentConfigEntry(
    val name:       String,
    val classname:  String,
    val config:     String
  ) extends AAgent
  case class AgentInfo(
    name:       String,
    classname:  String,
    config:     String,
    agent:      ActorRef,
    running:    Boolean
  ) extends AAgent

  /** Container for internal agents */
  protected[this] val agents: scala.collection.mutable.Map[String, AgentInfo] = Map.empty

  /** Getter for internal agents */
  private[agentSystem] def getAgents = agents

  /** Classloader for loading classes in jars. */
  Thread.currentThread.setContextClassLoader( createClassLoader())
  /** Settings for getting list of internal agents and their configs from application.conf */
  private[this] val settings = Settings(context.system)

  start()

  /** Helper method for checking is agent even stored. If was handle will be processed.
    *
    */
  private def handleAgentCmd(agentName: String)(handle: AgentInfo => String): String = {
    agents.get(agentName) match {
      case None =>
        log.warning("Command for not stored agent!: " + agentName)
        "Could not find agent: " + agentName
      case Some(agentInfo) =>
        handle(agentInfo)
    }
  }

  implicit val timeout = Timeout(5 seconds) 
  /**
   * Method for handling received messages.
   * Should handle:
   *   -- ConfigUpdate with updating running AgentActors.
   *   -- Terminated with trying to restart AgentActor.
   */
  def receive = {
    case StartAgentCmd(agentName: String) => {
      sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
        agentInfo.running match{
          case false=>
            log.info(s"Starting: " + agentInfo.name)
            val result = agentInfo.agent ? Start()
            val msg = s"Agent $agentName started succesfully."
            log.info(msg)
            agents += agentInfo.name -> AgentInfo(agentInfo.name,agentInfo.classname, agentInfo.config, agentInfo.agent, true)
            msg
          case true =>
            val msg = s"Agent $agentName was already Running. 're-start' should be used to restart running Agents"
            log.info(msg)
            msg
        }
      }
    }
    case StopAgentCmd(agentName: String) => {
      sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
        agentInfo.running match{
          case true =>
            log.warning(s"Stopping: " + agentInfo.name)
            val result = agentInfo.agent ? Stop()
            agents += agentInfo.name -> AgentInfo(agentInfo.name,agentInfo.classname, agentInfo.config, agentInfo.agent, false)
            val msg = s"Agent $agentName stopped succesfully."
            log.info(msg)
            msg
          case true =>
            val msg = s"Agent $agentName was already stopped."
            log.info(msg)
            msg
        }
      }
    }
    case ReStartAgentCmd(agentName: String) => {
      sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
        agentInfo.running match{
          case true=>
            log.info(s"Restarting: " + agentInfo.name)
            val result = agentInfo.agent ? Restart()
            val msg = s"Agent $agentName restarted succesfully."
            log.info(msg)
            msg
          case false =>
            val msg = s"Agent $agentName was not running. 'start' should be used to start stopped Agents."
            log.info(msg)
            msg
        }
      }
    }

    case ListAgentsCmd() => {
      sender() ! agents.values.toSeq
    }
    case _ => //noop?
  }

  /**
   *
   */
  def start() = {
    val classnames = getClassnamesWithConfigPath
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

  def loadAndStart(name : String, classname : String, config : String) = {
    Try {
      log.info("Instantiating agent: " + name + " of class " + classname)
      val classLoader = Thread.currentThread.getContextClassLoader
      val clazz = classLoader.loadClass(classname)
      val interface =  classOf[AbstractInternalAgent]
      if( interface.isAssignableFrom(clazz) ){
        val prop  = Props(clazz)
        val agent = context.actorOf( prop, name )
        val date = new Date()
        val timeout = Timeout(5 seconds) 
        log.warning(s"Configuration of agent $name. $agent")
        val configureF = ask(agent,Configure(config))(timeout)
        log.warning(s"Configuration of agent $name. $agent")
        configureF.onSuccess{
          case error : InternalAgentFailure =>  
          log.warning(s"Agent $name failed at configuration. Terminating agent.")
          context.stop(agent)
          case success : InternalAgentSuccess =>  
          val startF = ask(agent,Start())(timeout)
          startF.onSuccess{
            case error : InternalAgentFailure =>  
            log.warning(s"Agent $name failed to start. Terminating agent.")
            context.stop(agent)
            case success : InternalAgentSuccess =>  
            log.info(s"Agent $name started successfully.")
            agents += name -> AgentInfo(name,classname, config, agent, true)
          }
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
        case e: Exception =>
          log.warning(s"Class $classname could not be loaded, created, initialized or started. Because received $e.")
          log.warning(e.getStackTraceString)
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

  /**
   * Simple function for getting Agent's name and config string pairs.
   */
  private[agentSystem] def getClassnamesWithConfigPath: Array[AgentConfigEntry] = {
    settings.internalAgents.unwrapped().asScala.map {
      //TODO: find way to exract to strings from object, first classname, second config
      case (name: String, config: Object) => new AgentConfigEntry(name, name, config.toString) 
    }.toArray
  }

}
