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
  def splitRequestToResponsible( request: OmiRequest ) :ImmutableMap[Option[AgentName], OmiRequest] ={
    request match {
      case write: WriteRequest => splitWriteToResponsible(write)
      case call: CallRequest => splitCallToResponsible(call)
      case other: OmiRequest =>ImmutableMap( None -> other)
    }
  }
  def splitCallToResponsible( request: CallRequest ) : ImmutableMap[Option[AgentName], OdfRequest] ={
    def filter: RequestFilter => Boolean = createFilter(request)
      
    val odf = request.odf
    val objectsWithMetadata = odf.objectsWithMetadata.map{
      case obj: OdfObject =>
        //Remove descendants
        obj.copy(
          infoItems = OdfTreeCollection.empty,
          objects = OdfTreeCollection.empty
        )
    }
          
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
            val leafOption = odf.get(path).map(_.createAncestors)
            leafOption.map{
              case leafOdf: OdfObjects => 
              val lostMetaData = objectsWithMetadata.filter{
                //Get MetaData for Object s in leaf's path 
                case obj: OdfObject => obj.path.isAncestorOf(path)
              }
              lostMetaData.foldLeft(leafOdf){
                case (resultWithMetaData: OdfObjects, objectWithMetaData: OdfObject) =>
                  //Add MetaData
                  resultWithMetaData.union( objectWithMetaData.createAncestors)
              }
            }
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
    val objectsWithMetadata = odf.objectsWithMetadata.map{
      case obj: OdfObject =>
        obj.copy(
          infoItems = OdfTreeCollection.empty,
          objects = OdfTreeCollection.empty
        )
    }
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
            val leafOption = odf.get(path).map(_.createAncestors)
            leafOption.map{
              case leafOdf: OdfObjects => 
              val lostMetaData = objectsWithMetadata.filter{
                case obj: OdfObject => obj.path.isAncestorOf(path)
              }
              lostMetaData.foldLeft(leafOdf){
                case (resultWithMetaData: OdfObjects, objectWithMetaData: OdfObject) =>
                  resultWithMetaData.union( objectWithMetaData.createAncestors)
              }
            }
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

