
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
import database._
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes.MetaData
import responses.CallbackHandler._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes.{OmiResult,Results,WriteRequest, Responses, ResponseRequest}
import types.Path

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

  private def callAgentsForResponsibility(
    ttl: Duration,
    ownerToObjects: Map[AgentName,OdfObjects]
  ): Vector[Future[ResponseRequest]]={
    val allExists = ownerToObjects.map{
      case (name: AgentName, objects: OdfObjects) =>
        (name,
        agents.get(name).map{
          agent : AgentInfo => 
          val write = WriteRequest( objects, None,ttl) 
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
          future.mapTo[ResponseRequest].recover{
            case t: Throwable =>
              Responses.InternalError(t)
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
    val writesBySender:Future[ResponseRequest] ={
      val pathsO = ownerToPath.get(senderName)
      val future : Future[ResponseRequest] = pathsO.map{
        paths: Seq[Path] =>
        val infoItems= allInfoItems.filter{
          infoItem  : OdfInfoItem => paths.contains(infoItem.path) 
        }
        log.debug( s"$senderName writing to paths owned by it"/*: $pathsO"*/)
        writeValues(infoItems)
      }.getOrElse{
        Future.successful(Responses.Success())  
      }
      future.recover{
        case t: Throwable =>
          Responses.InternalError(t)
      }
    }

    val allOwnedPaths : Seq[Path] = ownerToPath.values.flatten.toSeq
    
    //Get part that isn't owned by anyone
    val writesToOwnerless:Future[ResponseRequest] = {
      val paths : Seq[Path] = allPaths.filter{ path => !allOwnedPaths.contains(path) }
      val infoItems = allInfoItems.filter{
        infoItem : OdfInfoItem => paths.contains(infoItem.path) 
      }
      log.debug( s"$senderName writing to paths not owned by anyone"/*: $paths"*/)
      writeValues(infoItems, objectsWithMetadata).recover{
        case t: Throwable =>
          Responses.InternalError(t)
      }
    }

    //Get part that is owned by other agents than sender()
    val writesToOthers: Vector[Future[ResponseRequest]]={ 
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
    val responsesFs : Seq[Future[ResponseRequest]] = Vector(writesToOwnerless, writesBySender) ++ writesToOthers
    val resultsF : Future[Vector[OmiResult]] = responsesFs.
      foldLeft(Future.successful(Vector.empty[OmiResult])){ 
      case ( resultsF: Future[Vector[OmiResult]], responseF: Future[ResponseRequest] ) => 
        val newF: Future[Vector[OmiResult]] = resultsF.flatMap{ 
          case results : Vector[OmiResult] =>
            val resF: Future[Vector[OmiResult]] = responseF.map{ 
              case response : ResponseRequest => results ++ response.results
            }
            resF
        }
        newF
    }
    val responseF: Future[ResponseRequest] = resultsF.map{
      case results: Vector[OmiResult] => 
        ResponseRequest(Results.unionReduce(results))
    }
    responseF.onComplete{
      case Success(response: ResponseRequest) => 
        senderRef ! response
      case Failure( t ) => 
        senderRef ! Responses.InternalError(t)
    }

  }


}
