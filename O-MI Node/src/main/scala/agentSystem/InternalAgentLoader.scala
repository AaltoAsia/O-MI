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
import akka.actor.{
  Actor, 
  ActorRef, 
  ActorInitializationException, 
  ActorKilledException, 
  ActorLogging, 
  OneForOneStrategy, 
  Props, 
  SupervisorStrategy}
import akka.pattern.ask
import akka.util.Timeout
import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.collection.JavaConversions._
import scala.collection.mutable.Map
import java.io.File
import java.net.URLClassLoader
import java.util.Date
import java.util.jar.JarFile
import com.typesafe.config.Config

trait InternalAgentLoader extends BaseAgentSystem {
  import context.dispatcher

  /** Classloader for loading classes in jars. */
  Thread.currentThread.setContextClassLoader( createClassLoader())
  /** Settings for getting list of internal agents and their configs from application.conf */


  def start() : Unit= {
    val classnames = settings.agentConfigurations
    classnames.foreach {
      configEntry : AgentConfigEntry =>
      agents.get( configEntry.name ) match{
        case None =>
          loadAndStart(configEntry)
        case Some( agentInfo ) =>
          log.warning("Agent already running: " + configEntry.name)
      }
    }
  }

  protected def loadAndStart(name : AgentName, classname : String, config : Config, ownedPaths: Seq[Path]) = {
    Try {
      log.info("Instantiating agent: " + name + " of class " + classname)
      val classLoader     = Thread.currentThread.getContextClassLoader
      val actorClazz      = classLoader.loadClass(classname)
      val objectInterface  = classOf[PropsCreator]
      val agentInterface  = classOf[InternalAgent]
      val responsibleInterface  = classOf[ResponsibleInternalAgent]
      actorClazz match {
        //case actorClass if responsibleInterface.isAssignableFrom(actorClass) =>
        case actorClass if agentInterface.isAssignableFrom(actorClass) =>
        val objectClazz = classLoader.loadClass(classname + "$")
        objectClazz match { 
          case objectClass if objectInterface.isAssignableFrom(objectClass) =>
          //Static field MODULE$ contains Object it self
          //Method get is used to get value of field for a Object.
          //Because field MODULE$ is static, it return  the companion object recardles of argument
          //To see the proof, decompile byte code to java and look for exampe in SubscribtionManager$.java
          val propsCreator : PropsCreator = objectClass.getField("MODULE$").get(null).asInstanceOf[PropsCreator] 
          //Get props and create agent
          val props = propsCreator.props(config).props
          props.actorClass match {
            case clazz if clazz == actorClazz =>
            val agent = context.actorOf( props, name.toString )
            startAgent(agent)
            case clazz: Class[_] =>
            log.warning(s"Object $classname does created Props for $clazz, should create for $actorClazz.")
            Future.failed( new Exception(" asdf"))
          }
          case clazz: Class[_] =>
          log.warning(s"Object  $classname does not implement PropsCreator trait.")
          Future.failed( new Exception(" asdf"))
        }
        case clazz: Class[_] =>
        log.warning(s"Class  $classname does not implement InternalAgent trait.")
          Future.failed( new Exception(" asdf"))
      }
    } match {
      case Success(startF: Future[ActorRef]) => ()
        startF.onSuccess{ 
          case agentRef: ActorRef =>
          log.info( s"Started agent $name successfully.")
          agents += name -> AgentInfo(name,classname, config, agentRef, true, ownedPaths)
        }
      case Failure(e) => e match {
        case _:NoClassDefFoundError | _:ClassNotFoundException =>
          log.warning("Classloading failed. Could not load: " + classname + "\n" + e + " caught")
        case e: Throwable =>
          log.warning(s"Class $classname could not be loaded, created, initialized or started. Because received $e.")
          log.warning(e.getStackTrace.mkString("\n"))
        case _ => throw e
      }
    }
  }
  protected def startAgent(agent: ActorRef) = { 
    val timeout = settings.internalAgentsStartTimout
    val startF = ask(agent,Start())(timeout)
    val resultF = startF.flatMap{ 
      case result : Try[InternalAgentSuccess] =>  Future.fromTry(result)
    }.map{
      case success : InternalAgentSuccess => agent
    }
    resultF.onFailure{ 
      case e: Throwable => 
      context.stop(agent)
    }
    resultF
  }
  protected def loadAndStart(configEntry: AgentConfigEntry) : Unit = loadAndStart( configEntry.name,configEntry.classname, configEntry.config, configEntry.ownedPaths)

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

  private[this] def loadJar( jar: File) : Option[Array[File ] ]= {
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

}
