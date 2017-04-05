package agentSystem

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.immutable.{Map => ImmutableMap}

import types.Path
import types.OmiTypes._
import types.OdfTypes._

object AgentResponsibilities{

  case class AgentResponsibility( agentName: AgentName, path: Path, requestFilter: RequestFilter)
}
import AgentResponsibilities._


class AgentResponsibilities(){

  val pathsToResponsible: MutableMap[Path, AgentResponsibility] = MutableMap.empty
  /*
  def tempName(
    obj: OdfObject,
    filter: (RequestFilter => Boolean) 
  ): Seq[]={
    val repsonsibleForInfoItems = obj.infoItems.map{
      case infoItem: OdfInfoItem => 
        val responsible = pathToResponsible.get(infoItem.path).colllect{
          case AgentResponsibility( agentName, rpath, requestFilter) if filter(requestFilter) => agentName
        }
        (responsible, infoItem)
    }.groupBy{
      case (responsible, infoItem) => responsible 
    }.mapValues{ 
      case pSeq => pSeq.map{
        case (responsible, infoItem) => infoItem
      }
    }
    val responsibleForObjects = obj.objects.flatMap( tempName(_) )

  }
  protected def getOwners( paths: Seq[Path], filter: (RequestFilter => Boolean)) : Map[Option[AgentName],Seq[Path]] = {
    paths.collect{
      case path  => 
      val many = path.getParentsAndSelf
      val keyOption: Option[Path] = pathsToResponsible.keys.find{
        key => many.contains(key)
      }
      val nameOption : Option[AgentName] = keyOption.flatMap{ keyPath: Path => 
        pathsToResponsible.get(keyPath).collect{ 
          case AgentResponsibility( agentName, rpath, requestFilter) if filter(requestFilter) => agentName
        }
      }
      (nameOption,path)
    }.groupBy{
      case (nameOption, path) => nameOption
    }.mapValues{
      seq => seq.map{case (name, path) => path}
    }
  } */
  def splitRequestToResponsible( request: OdfRequest ) : ImmutableMap[Option[AgentName], OdfRequest] = request match{
   // case read: ReadRequest => splitReadToResponsible(read)
    case write: WriteRequest => splitWriteToResponsible(write)
    case call: CallRequest => splitCallToResponsible(call)
  }
  /*
  def splitReadToResponsible( read: ReadRequest ) : ImmutableMap[Option[AgentName], OdfRequest] ={
    def filter: RequestFilter => Boolean = createFilter(read)
    val odf = read.odf
    val leafPathes = getLeafs(odf).map(_.path)
    val pathsWithResponsible= pathsToResponsible.keys.filter{
      case path: Path => 
        leafPathes.exists{
          case readLeafPath => readLeafPath.isAncestorOf(path) 
        }
    }
    val pathsWithoutResponsible = leafPathes.filterNot{
      case path: Path => 
        pathsWithResponsible.exists{
          pwr => 
            pwr.isAncestorOf(path)
//What if Object has one object that has responsible and one that doesn't?
//1. need to know whole o-df?
//2. Send request to DBHandler and speficied request to Agents?
//   Moves problem to DBHandler? Path with responsible, could get two values.
        }
    }
    val neededResponsible = pathsWithResponsible.flatMap{
      case path: Path => 
        pathsToResponsible.get(path)
    }.map{
      case AgentResponsibility(
        agentName: AgentName,
        path: Path,
        requestFilter: RequestFilter
      ) if filter(requestFilter) =>
        (agentName, path)
    }.groupBy{
      case (agentName, path) => agentName
    }.mapValues{
      case pS => pS.flatMap{
        case (agentName, path) => odf.get(path).map(_.createAncestors)
      }.foldLeft(OdfObjects()){
        case (result, obj) =>
          result.union(obj)
      } 
    }

    //TODO: Get objects that do not have responsible
    pathsWithResponsible
    ???
  }
  */
  def splitCallToResponsible( request: CallRequest ) : ImmutableMap[Option[AgentName], OdfRequest] ={
    def filter: RequestFilter => Boolean = createFilter(request)
      
    val odf = request.odf
    val leafPathes = getLeafs(odf).map(_.path)
    //println( s"InfoItems:\n$leafPathes")
    val pathToResponsible: Seq[(Path,Option[AgentName])]= leafPathes.map{
      case path: Path =>
        val allPaths : Seq[Path] = path.getParentsAndSelf.sortBy(_.length).reverse
        val responsibility : Option[AgentResponsibility] = allPaths.find{
          case _path => pathsToResponsible.get(_path).nonEmpty
        }.flatMap{ case _path => pathsToResponsible.get(_path) }
        
        val filteredResponsibility:Option[AgentResponsibility] = responsibility.filter{
          case AgentResponsibility( 
            agentName: AgentName, 
            path: Path, 
            requestFilter: RequestFilter 
          ) =>
            filter(requestFilter)
        }
        val responsible = filteredResponsibility.map(_.agentName)
        (path, responsible)
    }
    //println( s"pToR:\n$pathToResponsible")

    val responsibleToPairSeq: ImmutableMap[Option[AgentName], Seq[(Path, Option[AgentName])]]= pathToResponsible.groupBy{
      case ( path: Path, responsible: Option[AgentName]) =>
        responsible
    }
    //println( s"rTps:\n$responsibleToPairSeq")
    val responsibleToRequest: ImmutableMap[Option[AgentName], OdfRequest] = responsibleToPairSeq.map{
      case (
        optionAgentName: Option[AgentName],
        pathToAgentName: Seq[(Path,Option[AgentName])]
      ) =>
        val objects = pathToAgentName.flatMap{
          case (path: Path,aname: Option[AgentName]) =>
            odf.get(path).map(_.createAncestors)
        }.foldLeft(OdfObjects()){
          case (res, objs) => res.union(objs)
        }
        (optionAgentName,request.replaceOdf(objects))
    }
    //println( s"$responsibleToRequest")
    responsibleToRequest

  }
  
  def splitWriteToResponsible( request: WriteRequest ) : ImmutableMap[Option[AgentName], OdfRequest] ={
    def filter: RequestFilter => Boolean = createFilter(request)
      
    val odf = request.odf
    val leafPathes = getLeafs(odf).map(_.path)
    //println( s"InfoItems:\n$leafPathes")
    val pathToResponsible: Seq[(Path,Option[AgentName])]= leafPathes.map{
      case path: Path =>
        val allPaths : Seq[Path] = path.getParentsAndSelf.sortBy(_.length).reverse
        val responsibility : Option[AgentResponsibility] = allPaths.find{
          case _path => pathsToResponsible.get(_path).nonEmpty
        }.flatMap{ case _path => pathsToResponsible.get(_path) }
        
        val filteredResponsibility:Option[AgentResponsibility] = responsibility.filter{
          case AgentResponsibility( 
            agentName: AgentName, 
            path: Path, 
            requestFilter: RequestFilter 
          ) =>
            filter(requestFilter)
        }
        val responsible = filteredResponsibility.map(_.agentName)
        (path, responsible)
    }
    //println( s"pToR:\n$pathToResponsible")

    val responsibleToPairSeq: ImmutableMap[Option[AgentName], Seq[(Path, Option[AgentName])]]= pathToResponsible.groupBy{
      case ( path: Path, responsible: Option[AgentName]) =>
        responsible
    }
    //println( s"rTps:\n$responsibleToPairSeq")
    val responsibleToRequest: ImmutableMap[Option[AgentName], OdfRequest] = responsibleToPairSeq.map{
      case (
        optionAgentName: Option[AgentName],
        pathToAgentName: Seq[(Path,Option[AgentName])]
      ) =>
        val objects = pathToAgentName.flatMap{
          case (path: Path,aname: Option[AgentName]) =>
            odf.get(path).map(_.createAncestors)
        }.foldLeft(OdfObjects()){
          case (res, objs) => res.union(objs)
        }
        (optionAgentName,request.replaceOdf(objects))
    }
    //println( s"$responsibleToRequest")
    responsibleToRequest

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
      
      
    val odf = request.odf
    val leafPathes = getLeafs(odf).map(_.path)
    //println( s"Pathes of leaf nodes:\n$leafPathes")
    val pathToResponsible: Seq[(Path,Option[AgentName])]= leafPathes.map{
      case path: Path =>
        val allPaths : Seq[Path] = path.getParentsAndSelf.sortBy(_.length).reverse
        val responsibility : Option[AgentResponsibility] = allPaths.find{
          case _path => pathsToResponsible.get(_path).nonEmpty
        }.flatMap{ case _path => pathsToResponsible.get(_path) }
        
        val responsible = responsibility.map(_.agentName)
        (path, responsible)
    }
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

