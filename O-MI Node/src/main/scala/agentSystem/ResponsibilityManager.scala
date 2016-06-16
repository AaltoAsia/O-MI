
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

import java.lang.{Iterable => JavaIterable}

import scala.collection.immutable.Map
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import akka.actor.ActorRef
import database.SingleStores.valueShouldBeUpdated
import database._
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes.MetaData
import responses.CallbackHandlers._
import responses.OmiGenerator.xmlFromResults
import responses.{Results}
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import types.Path

sealed trait ResponsibilityMessage
case class RegisterOwnership( agent: AgentName, paths: Seq[Path])
case class PromiseWrite(result: PromiseResult, write:WriteRequest )
sealed trait ResponsibilityResponse extends ResponsibilityMessage
object PromiseResult{
  def apply(): PromiseResult= new PromiseResult(Promise[Iterable[Promise[ResponsibleAgentResponse]]]())
}
case class PromiseResult( promise: Promise[Iterable[Promise[ResponsibleAgentResponse]]] ){
  def futures(implicit ec: ExecutionContext) : Future[Iterable[Future[ResponsibleAgentResponse]]]= {
    promise.future.map{
      promises => 
      promises.map{ pro => pro.future }
    }
  }
  def resultSequence(implicit ec: ExecutionContext) : Future[Iterable[ResponsibleAgentResponse]]= {
    futures.flatMap{ results => Future.sequence(results) }
  }
  def isSuccessful(implicit ec: ExecutionContext) : Future[ResponsibleAgentResponse]= {
    resultSequence.map{ 
      res : Iterable[ResponsibleAgentResponse] =>
      res.foldLeft(SuccessfulWrite(Vector.empty)){
        (l, r) =>
        r match{
          case SuccessfulWrite( paths ) =>
          SuccessfulWrite( paths ++ l.paths ) 
          case _ =>  
          throw new Exception(s"Unknown response encountered.")
        }   
      }   
    }
  }
}

trait ResponsibleAgentManager extends BaseAgentSystem with InputPusher{
  import context.{system, dispatcher}
  /*
   * TODO: Use database and Authetication for responsible agents
   */
  protected[this] def pathOwners: scala.collection.mutable.Map[Path,AgentName] =
    getConfigsOwnerships()
  protected def getOwners( paths: Path*) : Map[AgentName,Seq[Path]] = {
    paths.collect{
      case path  => 
      val many = path.getParentsAndSelf
      val key = pathOwners.keys.find{
        key =>
        many.contains(key)
      }
      key.map{ p => pathOwners.get(p).map{ 
        name => (name,path)
      }}
    }.flatten.flatten.groupBy{
      case (name, path) => name
    }.mapValues{
      seq => seq.map{case (name, path) => path}
    }
  } 
  protected def handleRegisterOwnership(registerOwnership: RegisterOwnership ) = {}

  protected def getConfigsOwnerships() = {
    val pathsToOwner =agents.values.collect{ 
      case agentInfo : AgentInfo if agentInfo.ownedPaths.nonEmpty => 
      agentInfo.ownedPaths.map{ 
        path => (path -> agentInfo.name) 
      }
    }.flatten.toMap
    collection.mutable.Map(pathsToOwner: _*)
  }

  private def callAgentsForResponsibility( ttl: Duration, ownerToObjects: Map[AgentName,OdfObjects]): Iterable[Promise[ResponsibleAgentResponse]]={
      val allExists = ownerToObjects.map{
        case (name: AgentName, objects: OdfObjects) =>
        (name,
        agents.get(name).map{
          agent : AgentInfo => 
          val write = WriteRequest( ttl, objects) 
          (agent, write) 
        })
      }
      val nonExistingOwner = allExists.find{ case (name, exists) => exists.isEmpty }
      val agentsToWrite = allExists.values.flatten
      val stoppedOwner = agentsToWrite.find{ case (agent, write) => !agent.running }
      if( nonExistingOwner.nonEmpty ){
        nonExistingOwner.map{ 
          case (agent, write) =>
          val name = agent
          val msg = s"Received write for nonexistent agent." 
          log.warning(msg + s"Agent $name.")
          val promise = Promise[ResponsibleAgentResponse]()
          promise.failure( new Exception(msg) )
        }.toIterable
      } else if( stoppedOwner.nonEmpty ){
        stoppedOwner.map{
          case (agent, write) =>
          val name = agent.name
          val paths = getLeafs(write.odf).map(_.path)
          val msg = s"Received write for paths:\n" + paths.mkString("\n") + s"owned by stopped agent." 
          log.warning(msg + s"Agent $name.")
          val promise = Promise[ResponsibleAgentResponse]()
          promise.failure( new Exception(msg) )
        }.toIterable
      } else {
        agentsToWrite.map{ 
          case ( agentInfo, write ) =>
          val name = agentInfo.name
          log.debug( s"Asking $name to handle $write" )
          val promise = Promise[ResponsibleAgentResponse]()
          val future = agentInfo.agent ! ResponsibleWrite( promise, write)
          promise
        }
      }
    }

  protected def handleWrite( result: PromiseResult, write: WriteRequest ) : Unit={
    val senderName = sender().path.name
    log.debug( s"Received WriteRequest from $senderName.")
    val odfObjects = write.odf
    val allInfoItems : Seq[OdfInfoItem] = odfObjects.infoItems // getInfoItems(odfObjects)

    // Collect metadata 
    val objectsWithMetadata = odfObjects.objectsWithMetadata
    //val allNodes = allInfoItems ++ objectsWithMetadata

    val allPaths = allInfoItems.map( _.path )
    val ownerToPath = getOwners(allPaths:_*)
    
    //Get part that is owned by sender()
    val writesBySender:Promise[ResponsibleAgentResponse] ={
      val pathsO = ownerToPath.get(senderName)
      val promise = Promise[ResponsibleAgentResponse]()
      val future = pathsO.map{
        paths: Seq[Path] =>
        val infoItems= allInfoItems.filter{
          infoItem  : OdfInfoItem => paths.contains(infoItem.path) 
        }
        log.debug( s"$senderName writing to paths owned by it: $pathsO")
        writeValues(infoItems)
      }.getOrElse{
        Future.successful{SuccessfulWrite( Vector.empty )}  
      }
      promise.completeWith(future)
    } 
    val allOwnedPaths : Seq[Path] = ownerToPath.values.flatten.toSeq
    
    //Get part that isn't owned by anyone
    val writesToOwnerless:Promise[ResponsibleAgentResponse] = {
      val paths : Seq[Path] = allPaths.filter{ path => !allOwnedPaths.contains(path) }
      val infoItems = allInfoItems.filter{
        infoItem : OdfInfoItem => paths.contains(infoItem.path) 
      }
      log.debug( s"$senderName writing to paths not owned by anyone: $paths")
      val promise = Promise[ResponsibleAgentResponse]()
      promise.completeWith(writeValues(infoItems, objectsWithMetadata))
    }

    //Get part that is owned by other agents than sender()
    val writesToOthers: Iterable[Promise[ResponsibleAgentResponse]]={ 
      val ownerToPaths= ownerToPath - senderName
      val ownerToObjects = ownerToPaths.mapValues{ 
        paths => 
        allInfoItems.collect{
          case infoItem if paths.contains(infoItem.path) => createAncestors(infoItem)
        }.foldLeft(OdfObjects())(_.union(_))
      }
      log.debug( s"$senderName writing to paths owned other agents.")
      callAgentsForResponsibility( write.ttl, ownerToObjects)
    }
    //Collect all futures and return
    val res : Iterable[Promise[ResponsibleAgentResponse]] = Iterable(writesToOwnerless, writesBySender) ++ writesToOthers
    result.promise.success( res ) 
  }


}
