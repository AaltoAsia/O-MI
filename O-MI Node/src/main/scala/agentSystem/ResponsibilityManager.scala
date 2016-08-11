
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

//import java.lang.{Vector => JavaVector}

import scala.collection.immutable.Map
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import database.SingleStores.valueShouldBeUpdated
import database._
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes.MetaData
import responses.CallbackHandlers._
import responses.OmiGenerator.xmlFromResults
import responses.Results
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes._
import types.Path

case class ResponsibilityRequest( senderName: String, request: OmiRequest)
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

  protected def getConfigsOwnerships() = {
    val pathsToOwner =agents.values.collect{ 
      case agentInfo : AgentInfo if agentInfo.ownedPaths.nonEmpty => 
      agentInfo.ownedPaths.map{ 
        path => (path -> agentInfo.name) 
      }
    }.flatten.toMap
    collection.mutable.Map(pathsToOwner: _*)
  }

  private def callAgentsForResponsibility( ttl: Duration, ownerToObjects: Map[AgentName,OdfObjects]): Vector[Future[ResponsibleAgentResponse]]={
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
          Future.failed( new Exception(msg) )
      }.toVector
    } else if( stoppedOwner.nonEmpty ){
      stoppedOwner.map{
        case (agent, write) =>
          val name = agent.name
          val paths = getLeafs(write.odf).map(_.path)
          val msg = s"Received write for paths:\n" + paths.mkString("\n") + s"owned by stopped agent." 
          log.warning(msg + s"Agent $name.")
          Future.failed( new Exception(msg) )
      }.toVector
    } else {
      agentsToWrite.map{ 
        case ( agentInfo, write ) =>
          val name = agentInfo.name
          log.debug( s"Asking $name to handle WriteRequest with ${write.odf.paths.length} paths." )
          val future =agentInfo.agent ? write
          future.mapTo[ResponsibleAgentResponse].recover{
            case t: Throwable =>
              FailedWrite( write.odf.paths, Vector(t))
          }
      }      
    }
  }

  protected def handleWrite( senderName: String, write: WriteRequest ) : Unit={
    val senderRef: ActorRef = sender()
    log.debug( s"Received WriteRequest from $senderName.")
    val odfObjects = write.odf
    val allInfoItems : Seq[OdfInfoItem] = odfObjects.infoItems // getInfoItems(odfObjects)

    // Collect metadata 
    val objectsWithMetadata = odfObjects.objectsWithMetadata
    //val allNodes = allInfoItems ++ objectsWithMetadata

    val allPaths = allInfoItems.map( _.path )
    val ownerToPath = getOwners(allPaths:_*)
    
    //Get part that is owned by sender()
    val writesBySender:Future[ResponsibleAgentResponse] ={
      val pathsO = ownerToPath.get(senderName)
      val future = pathsO.map{
        paths: Seq[Path] =>
        val infoItems= allInfoItems.filter{
          infoItem  : OdfInfoItem => paths.contains(infoItem.path) 
        }
        log.debug( s"$senderName writing to paths owned by it"/*: $pathsO"*/)
        writeValues(infoItems)
      }.getOrElse{
        Future.successful{SuccessfulWrite( Vector.empty )}  
      }
      future.recover{
        case t: Throwable =>
          FailedWrite( write.odf.paths, Vector(t))
      }
    }

    val allOwnedPaths : Seq[Path] = ownerToPath.values.flatten.toSeq
    
    //Get part that isn't owned by anyone
    val writesToOwnerless:Future[ResponsibleAgentResponse] = {
      val paths : Seq[Path] = allPaths.filter{ path => !allOwnedPaths.contains(path) }
      val infoItems = allInfoItems.filter{
        infoItem : OdfInfoItem => paths.contains(infoItem.path) 
      }
      log.debug( s"$senderName writing to paths not owned by anyone"/*: $paths"*/)
      writeValues(infoItems, objectsWithMetadata).recover{
        case t: Throwable =>
          FailedWrite( write.odf.paths, Vector(t))
      }
    }

    //Get part that is owned by other agents than sender()
    val writesToOthers: Vector[Future[ResponsibleAgentResponse]]={ 
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
    val resultFs : Vector[Future[ResponsibleAgentResponse]] = Vector(writesToOwnerless, writesBySender) ++ writesToOthers
    resultFs.foldLeft(Future.successful(Vector.empty[ResponsibleAgentResponse])){ 
      case (resultF: Future[Vector[ResponsibleAgentResponse]], future: Future[ResponsibleAgentResponse] ) => 
        resultF.flatMap{ 
          case results : Vector[ResponsibleAgentResponse]=>
            future.map{ 
              case result : ResponsibleAgentResponse => 
              results ++ Vector(result)
            }
        }
    }.onComplete{
      case Success(results: Vector[ResponsibleAgentResponse]) => 
        val result : ResponsibleAgentResponse = results.foldLeft[ResponsibleAgentResponse]( SuccessfulWrite(Vector.empty)){
          case (l: ResponsibleAgentResponse,r: ResponsibleAgentResponse) => 
            l.combine(r)
        }
        senderRef ! result
      case Failure( t ) => 
    
    }

  }


}
