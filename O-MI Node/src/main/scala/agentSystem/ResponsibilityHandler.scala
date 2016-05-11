
package agentSystem

import agentSystem._
import types.Path
import types.OmiTypes._
import types.OdfTypes._
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
import scala.concurrent.{ Future,ExecutionContext, TimeoutException }
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable.Map
import com.typesafe.config.ConfigException

sealed trait ResponsibilityMessage
case class RegisterOwnership( agent: AgentName, paths: Seq[Path])
sealed trait ResponsibilityResponse extends ResponsibilityMessage
case class Error(msg:String) extends ResponsibilityResponse
case class NotOwned( paths: Seq[Path]) extends ResponsibilityResponse
case class Owned( agentResult: Seq[Future[Any]] ) extends ResponsibilityResponse
case class MixedOwnership( notOwned: Seq[Path], agentResults: Seq[Future[Any]] ) extends ResponsibilityResponse
trait ResponsibleAgentManager extends BaseAgentSystem{
  /*
   * TODO: Use database and Authetication for responsible agents
   */
  protected[this] val pathOwners: scala.collection.mutable.Map[Path,AgentName] = Map.empty
  getConfigsOwnerships
  receiver {
    //Write can be received either from RequestHandler or from InternalAgent
    case write: WriteRequest => sender() ! handleWrite(write)  
    case registerOwnership: RegisterOwnership => sender() ! handleRegisterOwnership(registerOwnership)  
  }
  def getOwners( paths: Path*) : scala.collection.immutable.Map[AgentName,Seq[Path]] = {
    paths.map{
      path => pathOwners.get(path).map{ name => (name,path)}
    }.flatten.groupBy{case (name, path) => name}.mapValues{seq => seq.map{case (name, path) => path}}
  } 
  def handleRegisterOwnership(registerOwnership: RegisterOwnership ) = {}
  def handleWrite(request:WriteRequest) = {
    val infos = getInfoItems(request.odf)
    val paths = infos.map(_.path)
    val infoMap = infos.groupBy(_.path)
    val ownerToPaths = getOwners(paths:_*)
    val ownedPaths = ownerToPaths.values.flatten
    val notOwnedPaths = paths.filter{ path => !ownedPaths.contains(path) }

    val ownerToInfos = ownerToPaths.mapValues{
      ownersPaths =>
      ownersPaths.flatMap{
        path =>
        infoMap.get(path)
      }.flatten
    }
    val agentToInfos = ownerToInfos.flatMap{
      case (name, ownersInfos)  =>
      agents.get(name).map{
        agentInfo =>
        (agentInfo.agent, ownersInfos)
      }
    }
    val agentResults = agentToInfos.map{
      case ( agent, ownersInfos ) =>
      agent ? Write( ownersInfos:_* )
    }.toSeq
    if( ownedPaths.isEmpty && notOwnedPaths.nonEmpty ){
      NotOwned( notOwnedPaths)
    } else if( ownedPaths.nonEmpty && notOwnedPaths.nonEmpty ){
      MixedOwnership( notOwnedPaths, agentResults )
    } else if( ownedPaths.nonEmpty && notOwnedPaths.isEmpty ){
      Owned(agentResults)
    } else {
      Error( "No owned or free pathes." )
    }
  }
  private[agentSystem] def getConfigsOwnerships {
    log.info(s"Setting path ownerships for Agents from config.")
    val agents = settings.internalAgents
    val names : Set[String] = asScalaSet(agents.keySet()).toSet // mutable -> immutable
    val pathsToOwner =names.map{ 
      name =>
      val agentConfig = agents.toConfig().getObject(name).toConfig()
      try{
        val ownedPaths = agentConfig.getStringList("owns")
        ownedPaths.map{ path => (Path(path), name)}
      } catch {
        case e: ConfigException.Missing   =>
          //Not a ResponsibleAgent
          List.empty
        case e: ConfigException.WrongType =>
          log.warning(s"List of owned paths for $name couldn't converted to java.util.List<String>")
          List.empty
      }
    }.flatten.toArray
    pathOwners ++= pathsToOwner
    log.info(s"Path ownerships for Agents in config set.")
  }
}
