package agentSystem

import types.OmiTypes._
import types.Path

import scala.collection.immutable.{Map => ImmutableMap}
import scala.collection.mutable.{Map => MutableMap}

object AgentResponsibilities {

  case class AgentResponsibility(agentName: AgentName, path: Path, requestFilter: RequestFilter)

}

import agentSystem.AgentResponsibilities._


class AgentResponsibilities() {

  val pathsToResponsible: MutableMap[Path, AgentResponsibility] = MutableMap.empty
  def splitRequestToResponsible( request: OdfRequest ) :ImmutableMap[Option[AgentName], OdfRequest] ={
    request match {
      case write: WriteRequest => splitCallAndWriteToResponsible(write)
      case call: CallRequest => splitCallAndWriteToResponsible(call)
      case delete: DeleteRequest => splitReadAndDeleteToResponsible(delete)
      case read: ReadRequest => splitReadAndDeleteToResponsible(read)
      case other: OdfRequest =>ImmutableMap( None -> other)
    }
  }
  def splitReadAndDeleteToResponsible( request: OdfRequest ) : ImmutableMap[Option[AgentName], OdfRequest] ={
    def filter: RequestFilter => Boolean = createFilter(request)
    val odf = request.odf
    ???
  }

  def splitCallAndWriteToResponsible( request: OdfRequest ) : ImmutableMap[Option[AgentName], OdfRequest] ={
    if( pathsToResponsible.isEmpty ){
      return ImmutableMap( None -> request )
    }
    def filter: RequestFilter => Boolean = createFilter(request)

    val odf = request.odf
    val resp = odf.getLeafPaths.groupBy {
      path: Path =>
        val ancestorKeyPaths: Iterable[Path] = pathsToResponsible.keys.filter {
          keyPath: Path =>
            keyPath.isAncestorOf(path) || keyPath == path
        }
        val keyPathsToAgentName: Iterable[Option[(Path, AgentName)]] = ancestorKeyPaths.map {
          keyPath: Path =>
            pathsToResponsible.get(keyPath).collect {
              case AgentResponsibility(
              agentName: AgentName,
              path: Path,
              requestFilter: RequestFilter
              ) if filter(requestFilter) =>
                keyPath -> agentName
            }
        }.filter { tuple => tuple.nonEmpty }
        val responsiblesTuple: Option[(Path, AgentName)] = keyPathsToAgentName.fold(Option.empty[(Path, AgentName)]) {
          case (Some(currentTuple: (Path, AgentName)), Some(tuple: (Path, AgentName))) =>
            currentTuple match {
              case (longestPath: Path, currentAgent: AgentName) =>
                tuple match {
                  case (keyPath: Path, agentName: AgentName) =>
                    if (longestPath.isAncestorOf(keyPath)) {
                      Some((keyPath, agentName))
                    } else {
                      Some((longestPath, currentAgent))
                    }
                }
            }
          case (Some(tuple: (Path, AgentName)), None) =>
            Some(tuple)
          case (None, Some(tuple: (Path, AgentName))) =>
            Some(tuple)
          case (None, None) => None
        }
        responsiblesTuple.map {
          case (path: Path, agentName: AgentName) =>
            agentName
        }
    }

    if( resp.size > 1 ){
      resp.map {
        case (responsible: Option[AgentName], paths: Set[Path]) =>
          responsible -> request.replaceOdf(odf.selectUpTree(paths))
      }
    } else {
      resp.headOption.map{
        case (responsible: Option[AgentName], paths: Set[Path]) =>
          responsible -> request
      }.toMap
    }
  }

  private def createFilter(request: OdfRequest): RequestFilter => Boolean = {
    val filter = request match {
      case write: WriteRequest =>
        rf: RequestFilter  => rf match{
          case w: Write => true
          case o: RequestFilter => false
        } 
      case call: CallRequest =>
        rf: RequestFilter => rf match{
          case c: Call => true
          case o: RequestFilter => false
        } 
      case read: ReadRequest => 
        rf: RequestFilter  => rf match{
          case r: Read => true
          case o: RequestFilter => false
        } 
      case delete: DeleteRequest => 
        rf: RequestFilter  => rf match{
          case r: Delete => true
          case o: RequestFilter => false
        } 
      case other: OdfRequest => 
        rf: RequestFilter  => rf match{
          case o: RequestFilter => false
        } 
    }
    filter
  }

  def removeAgent(agentName: AgentName) = {
    val agentsResponsibilities = pathsToResponsible.values.collect {
      case AgentResponsibility(aN: AgentName, path: Path, rf: RequestFilter) if agentName == aN =>
        path
    }

    pathsToResponsible --= agentsResponsibilities

  }

  def add(agentResponsibilities: Seq[AgentResponsibility]) = {
    val newMappings = agentResponsibilities.map {
      case ar@AgentResponsibility(agentName: AgentName, path: Path, requestFilter: RequestFilter) =>
        path -> ar
    }
    pathsToResponsible ++= newMappings
  }

  def checkResponsibilityFor(agentName: AgentName, request: OdfRequest): Boolean = {
    if (agentName.isEmpty) checkResponsibilityFor(None, request)
    else checkResponsibilityFor(Some(agentName), request)
  }

  def checkResponsibilityFor(optionAgentName: Option[AgentName], request: OdfRequest): Boolean = {
    val odf = request.odf
    val leafPathes = odf.getLeafPaths
    //println( s"Pathes of leaf nodes:\n$leafPathes")
    val pathToResponsible: Seq[(Path, Option[AgentName])] = leafPathes.map {
      path: Path =>
        val allPaths: Seq[Path] = path.getAncestorsAndSelf.sortBy(_.length).reverse
        val responsibility: Option[AgentResponsibility] = allPaths.find(_path => pathsToResponsible.get(_path).nonEmpty)
          .flatMap(_path => pathsToResponsible.get(_path))

        val responsible = responsibility.map(_.agentName)
        (path, responsible)
    }.toSeq
    //println( s"Pathes to responsible Agent's name:\n$leafPathes")

    val responsibleToPairSeq: ImmutableMap[Option[AgentName], Seq[(Path, Option[AgentName])]] = pathToResponsible
      .groupBy {
        case (path: Path, responsible: Option[AgentName]) =>
          responsible
      }
    //println( s"Responsible Agent's name to sequences of pairs:\n$responsibleToPairSeq")

    val responsibleToPaths: ImmutableMap[Option[AgentName], Seq[Path]] = responsibleToPairSeq.map {
      case (
        agentName: Option[AgentName],
        pathToAgentName: Seq[(Path, Option[AgentName])]
        ) =>
        val paths: Seq[Path] = pathToAgentName.map {
          case (path: Path, aname: Option[AgentName]) => path
        }
        agentName -> paths
    }
    //println( s"Responsible Agent's names:\n${responsibleToPaths.keys}")

    val result = responsibleToPaths.keys.flatten.filter {
      keyname: AgentName => !optionAgentName.contains(keyname)
    }.isEmpty
    //println( s"Permissien check:$result")
    result
  }


}

