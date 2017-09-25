package agentSystem

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.immutable.{Map => ImmutableMap}

import types.OmiTypes._
import types.odf._
import types.Path._
import types.Path

object AgentResponsibilities{

  case class AgentResponsibility( agentName: AgentName, path: Path, requestFilter: RequestFilter)
}
import AgentResponsibilities._


class AgentResponsibilities(){

  val pathsToResponsible: MutableMap[Path, AgentResponsibility] = MutableMap.empty
  def splitRequestToResponsible( request: OmiRequest ) :ImmutableMap[Option[AgentName], OmiRequest] ={
    request match {
      case write: WriteRequest => splitCallAndWriteToResponsible(write)
      case call: CallRequest => splitCallAndWriteToResponsible(call)
      case other: OmiRequest =>ImmutableMap( None -> other)
    }
  }
  def splitCallAndWriteToResponsible( request: OdfRequest ) : ImmutableMap[Option[AgentName], OdfRequest] ={
    def filter: RequestFilter => Boolean = createFilter(request)
    val odf = OldTypeConverter.convertOdfObjects(request.odf)
      
    val agentToResponsibilities = pathsToResponsible.values.collect{
      case AgentResponsibility(
        agentName: AgentName,
        path: Path,
        requestFilter: RequestFilter
      ) if  filter(requestFilter) =>
        odf.get(path).map{
          case node: Node =>
            agentName -> odf.getSubTreeAsODF( node.path)
        }
    }.flatten.groupBy( _._1 ).mapValues{
      case tupleSeq =>
        tupleSeq.map( _._2 ).reduceOption( _ union _ )
    }.collect{
      case (agent, Some(odf) ) =>agent -> odf
    }

    val pathsInResponsibilities = agentToResponsibilities.values.flatMap{
      case odf =>  odf.getPaths
    }.toSet
    val agentOptionToODF: Map[ Option[AgentName], ImmutableODF ] = ImmutableMap(
      (agentToResponsibilities.map{
        case ( agent, odf ) => 
          Some(agent) -> odf.immutable
      }.toVector ++ Vector(Option.empty[String] -> odf.cutOut(pathsInResponsibilities).immutable)):_*
    )
    agentOptionToODF.mapValues{
      case odf => 
        request.replaceOdf( NewTypeConverter.convertODF(odf))
    }
  }
  
  private def createFilter( request: OdfRequest ): RequestFilter => Boolean ={
    val filter = request match {
      case write: WriteRequest =>
        rf: RequestFilter  => rf match{
          case w: Write => true
          case o: RequestFilter => false
        } 
      case read: ReadRequest => ReadFilter
        rf: RequestFilter  => rf match{
          case r: Read => true
          case o: RequestFilter => false
        } 
      case call: CallRequest => CallFilter
        rf: RequestFilter => rf match{
          case c: Call => true
          case o: RequestFilter => false
        } 
    }
    filter
  }
  def removeAgent( agentName: AgentName ) ={
    val agentsResponsibilities = pathsToResponsible.values.collect{
      case AgentResponsibility( aN: AgentName, path: Path, rf: RequestFilter ) if agentName == aN =>
        path
    }
  
    pathsToResponsible --= agentsResponsibilities
  
  }
  def add( agentResponsibilities: Seq[AgentResponsibility] ) = {
    val newMappings = agentResponsibilities.map{
      case ar @ AgentResponsibility( agentName: AgentName, path: Path, requestFilter: RequestFilter) =>
        path -> ar
    }
    pathsToResponsible ++= newMappings
  }

  def checkResponsibilityFor(agentName: AgentName, request:OdfRequest): Boolean ={
    if( agentName.isEmpty ) checkResponsibilityFor(None, request) 
    else checkResponsibilityFor(Some(agentName), request) 
  }
  def checkResponsibilityFor(optionAgentName: Option[AgentName], request:OdfRequest): Boolean ={
    val odf = OldTypeConverter.convertOdfObjects(request.odf)
    val leafPathes = odf.getLeafPaths
    //println( s"Pathes of leaf nodes:\n$leafPathes")
    val pathToResponsible: Seq[(Path,Option[AgentName])]= leafPathes.map{
      case path: Path =>
        val allPaths : Seq[Path] = path.getAncestorsAndSelf.sortBy(_.length).reverse
        val responsibility : Option[AgentResponsibility] = allPaths.find{
          case _path => pathsToResponsible.get(_path).nonEmpty
        }.flatMap{ case _path => pathsToResponsible.get(_path) }
        
        val responsible = responsibility.map(_.agentName)
        (path, responsible)
    }.toSeq
    //println( s"Pathes to responsible Agent's name:\n$leafPathes")

    val responsibleToPairSeq: ImmutableMap[Option[AgentName], Seq[(Path, Option[AgentName])]]= pathToResponsible.groupBy{
      case ( path: Path, responsible: Option[AgentName]) =>
        responsible
    }
    //println( s"Responsible Agent's name to sequences of pairs:\n$responsibleToPairSeq")

    val responsibleToPaths: ImmutableMap[Option[AgentName], Seq[Path]] = responsibleToPairSeq.map{
      case (
        agentName: Option[AgentName],
        pathToAgentName: Seq[(Path,Option[AgentName])]
      ) =>
        val paths: Seq[Path] = pathToAgentName.map{
          case (path: Path,aname: Option[AgentName]) => path
        }
        agentName -> paths
    }
    //println( s"Responsible Agent's names:\n${responsibleToPaths.keys}")

    val result = responsibleToPaths.keys.flatten.filter{
      case keyname: AgentName => optionAgentName.forall{ name => name != keyname }
    }.isEmpty
    //println( s"Permissien check:$result")
    result
  }


}

