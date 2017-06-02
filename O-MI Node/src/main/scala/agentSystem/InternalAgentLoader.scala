/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
package agentSystem

import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import akka.actor.{Props, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import com.typesafe.config.Config
import AgentResponsibilities._
import types.Path
import AgentEvents._

sealed trait InternalAgentLoadFailure{ def msg : String }
abstract class InternalAgentLoadException(val msg: String)  extends  Exception(msg) with InternalAgentLoadFailure
final case class PropsCreatorNotImplemented[T](clazz : Class[T] ) extends InternalAgentLoadException({ 
  val start = clazz.toString.replace( "class", "Object" ).replace( "$", "")
    start + " does not implement PropsCreator trait."
  })
final case class InternalAgentNotImplemented[T](clazz: Class[T]) extends InternalAgentLoadException({ 
  val start = clazz.toString.replace( "class", "Class" )
  start + " does not implement InternalAgent trait."
})
final case class WrongPropsCreated(props : Props, classname: String ) extends InternalAgentLoadException({
  val created = props.actorClass
  s"Object $classname creates InternalAgentProps for $created,"+
  s" but should create for class $classname."
})

trait InternalAgentLoader extends BaseAgentSystem {
  import context.dispatcher

  /** Classloader for loading classes in jars. */
  Thread.currentThread.setContextClassLoader( createClassLoader())
  /** Settings for getting list of internal agents and their configs from application.conf */

  protected def dbHandler: ActorRef
  protected def requestHandler: ActorRef
  def start() : Unit = {
    val classnames = settings.agentConfigurations
    classnames.foreach {
      case configEntry : AgentConfigEntry =>
      agents.get( configEntry.name ) match{
        case None =>
          loadAndStart(configEntry)
        case Some( agentInf ) =>
          log.warning("Agent already running: " + configEntry.name)
      }
    }
  }

  protected[agentSystem] def loadAndStart(
    agentConfigEntry: AgentConfigEntry
  ) : Unit = {
      val classLoader = Thread.currentThread.getContextClassLoader
      val initialization : Try[AgentInfo]= agentConfigEntry.language match{
        case Some( Scala()) => scalaAgentInit(agentConfigEntry)
        case Some( Java()) => javaAgentInit(agentConfigEntry)
        case Some( Unknown( lang ) ) => 
          Try{ throw new Exception( s"Agent's language is not supported: $lang ")}
        case None => //Lets try to figure it out ourselves
          Try{ throw new Exception( s"Agent's language not provided")} 
      }
    initialization match {
      case Success(agentInfo:AgentInfo) => 
          log.info( s"Created agent ${agentInfo.name} successfully.")
          agents += agentInfo.name -> agentInfo
          notifyAboutNewAgent( agentInfo )
      case Failure( e : InternalAgentLoadFailure ) =>
        log.warning( e.msg ) 
      case Failure(e:NoClassDefFoundError) => 
        log.warning(s"Classloading failed. Could not load: ${agentConfigEntry.classname}. Received $e")
      case Failure(e:ClassNotFoundException ) =>
        log.warning(s"Classloading failed. Could not load: ${agentConfigEntry.classname}. Received $e")
      case Failure( e:NoSuchMethodException ) => 
        log.warning(s"Class ${agentConfigEntry.classname} did not have method props. Received $e")
      case Failure(e: Throwable) =>
        log.warning(s"Class ${agentConfigEntry.classname} could not be loaded or created. Received $e")
        log.warning(e.getStackTrace.mkString("\n"))
    }
  }
  def notifyAboutNewAgent( agentInfo: AgentInfo ) ={
    agentInfo.agent.foreach{
      agentRef: ActorRef =>
        def msg = NewAgent(agentInfo.name,agentRef,agentInfo.responsibilities)
        requestHandler ! msg
        dbHandler ! msg
    }
        connectedCLIs.foreach{
          case (ip, ref) => ref ! s"Agent ${agentInfo.name} started."
        }
  }
    
  private def scalaAgentInit(
    agentConfigEntry: AgentConfigEntry
  ): Try[AgentInfo] = Try{
      log.info("Instantiating agent: " + agentConfigEntry.name + " of class " + agentConfigEntry.classname)
      val classLoader           = Thread.currentThread.getContextClassLoader
      val actorClazz            = classLoader.loadClass(agentConfigEntry.classname)
      val objectClazz           = classLoader.loadClass(agentConfigEntry.classname + "$")
      val objectInterface       = classOf[PropsCreator]
      val agentInterface        = classOf[ScalaInternalAgent]
      val responsibleInterface  = classOf[ResponsibleScalaInternalAgent]
      actorClazz match {
        //case actorClass if responsibleInterface.isAssignableFrom(actorClass) =>
        case actorClass if agentInterface.isAssignableFrom(actorClass) =>
          objectClazz match { 
            case objectClass if objectInterface.isAssignableFrom(objectClass) =>
              //Static field MODULE$ contains Object it self
              //Method get is used to get value of field for a Object.
              //Because field MODULE$ is static, it return  the companion object recardles of argument
              //To see the proof, decompile byte code to java and look for exampe in SubscribtionManager$.java
              val propsCreator : PropsCreator = objectClass.getField("MODULE$").get(null).asInstanceOf[PropsCreator] 
              //Get props and create agent
              val props = propsCreator.props(agentConfigEntry.config, requestHandler, dbHandler)
              props.actorClass match {
                case clazz if clazz == actorClazz =>
                  val agentRef = context.actorOf( props, agentConfigEntry.name.toString )
                  context.watch( agentRef )
                  agentConfigEntry.toInfo(agentRef)
                case clazz: Class[_] => throw new WrongPropsCreated(props, agentConfigEntry.classname)
              }
            case clazz: Class[_] => throw new PropsCreatorNotImplemented(clazz)
          }
          case clazz: Class[_] => throw new InternalAgentNotImplemented(clazz)
      }
  
  }
  private def javaAgentInit(
    agentConfigEntry: AgentConfigEntry
  ): Try[AgentInfo] = Try{
    log.info("Instantiating agent: " + agentConfigEntry.name + " of class " + agentConfigEntry.classname)
    val classLoader           = Thread.currentThread.getContextClassLoader
    val actorClazz            = classLoader.loadClass(agentConfigEntry.classname)
    val creatorInterface       = classOf[PropsCreator]
    val agentInterface        = classOf[JavaInternalAgent]
    actorClazz match {
        case actorClass if agentInterface.isAssignableFrom(actorClass) => //&& 
                         // creatorInterface.isAssignableFrom(actorClass)) =>
          //Get props and create agent
          val method = actorClass.getDeclaredMethod("props",classOf[Config],classOf[ActorRef],classOf[ActorRef])
          val props : Props = method.invoke(null,agentConfigEntry.config,requestHandler,dbHandler).asInstanceOf[Props]
          props.actorClass match {
            case clazz if clazz == actorClazz =>
              val agentRef = context.actorOf( props, agentConfigEntry.name.toString )
              context.watch( agentRef )
              agentConfigEntry.toInfo(agentRef)
            case clazz: Class[_] => throw new WrongPropsCreated(props, agentConfigEntry.classname)
          }
        case clazz: Class[_] if !creatorInterface.isAssignableFrom(clazz) =>
          throw new PropsCreatorNotImplemented(clazz)
        case clazz: Class[_] if !agentInterface.isAssignableFrom(clazz) => 
          throw new InternalAgentNotImplemented(clazz)
    }
  }
  
  protected def stopAgent(agentName:AgentName): Unit ={
    log.warning( s"Stopping Agent $agentName...")

    agents.get( agentName  ).foreach{
      case agentInfo: AgentInfo =>
      
        agentInfo.agent.foreach{
          case agentRef : ActorRef =>
            context.stop( agentRef )
        }
        requestHandler ! AgentStopped(agentInfo.name)
        dbHandler ! AgentStopped(agentInfo.name)
        agents.update(agentName, agentInfo.copy( running = false, agent = None ) )
    }
  }
  protected def agentStopped(agentRef: ActorRef): Unit ={
    val agentName = agentRef.path.name 
    log.warning( s"Agent $agentName has been stopped/terminated" )
    context.unwatch( agentRef )
    agents.get( agentName  ).foreach{
      case agentInfo: AgentInfo =>
        requestHandler ! AgentStopped(agentInfo.name)
        dbHandler ! AgentStopped(agentInfo.name)
        agents.update(agentName, agentInfo.copy( running = false, agent = None ) )
        connectedCLIs.foreach{
          case (ip, ref) => ref ! s"Agent $agentName stopped."
        }
    }
  }


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
