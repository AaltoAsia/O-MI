
package agentSystem

import agentSystem._
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

sealed trait ResposibilityMessages
case class NotOwned()
case class Owned()
case class MixedOwnership()
trait ResbonsibleAgentManager extends BaseAgentSystem{
  /*
   * TODO: Use database and Authetication for responsible agents
   */
  protected[this] val pathOwners: scala.collection.mutable.Map[Path,String] = Map.empty
  def getOwners( paths: Path*) : scala.collection.immutable.Map[String,Seq[Path]] = {
    paths.map{
      path => pathOwners.get(path).map{ name => (name,path)}
    }.flatten.groupBy{case (name, path) => name}.mapValues{seq => seq.map{case (name, path) => path}}
  }
  def handleResponsible() = {
    
  }
}
