package agentSystem


import scala.collection.immutable.{Map => ImmutableMap}
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.{Future, ExecutionContext}
import akka.http.scaladsl.model.Uri

import database.SingleStores
import types.omi._
import types.odf._
import types.Path

object AgentResponsibilities {

  case class AgentResponsibility(agentName: AgentName, path: Path, requestFilter: RequestFilter)

}

import agentSystem.AgentResponsibilities._

sealed trait Responsible
case class ResponsibleAgent( name: AgentName ) extends Responsible
case class ResponsibleNode( address: Uri ) extends Responsible



class AgentResponsibilities( val singleStores: SingleStores) {

  val pathsToResponsible: MutableMap[Path, AgentResponsibility] = MutableMap.empty
  def splitRequestToResponsible( request: OdfRequest )(implicit ec: ExecutionContext) :Future[ImmutableMap[Option[Responsible], OdfRequest]] ={
    request match {
      case write: WriteRequest => splitCallAndWriteToResponsible(write)
      case call: CallRequest => splitCallAndWriteToResponsible(call)
      case delete: DeleteRequest => splitDeleteToResponsible(delete)
      case read: ReadRequest => splitReadToResponsible(read)
      case other: OdfRequest =>Future.successful(ImmutableMap( None -> other))
    }
  }
  /*
   * Currently when requesting path ../A/ that has two subpaths
   * ../A/A and ../A/B that have different responsible agents
   * splitting creates two request, one for ../A/A  and other for ../A/B.
   * This works for read but for delete this doesn't actually remove ../A.
   * Also two new request have all descedants of their path ( ../A/A or ../A/B)
   * Todo: split method to read and delete
   *
   */
  def splitReadAndDeleteToResponsible( request: OdfRequest )(implicit ec: ExecutionContext) : Future[ImmutableMap[Option[Responsible], OdfRequest]] ={
    val cachedOdfF: Future[ImmutableODF] = singleStores.getHierarchyTree()
    def filter: RequestFilter => Boolean = createFilter(request)
    val odf = request.odf
    val responsibleToPathsWithoutNone: Map[Option[Responsible],Set[Path]]= pathsToResponsible.filter{
      case (path, AgentResponsibility( agentName, _, rf) ) =>
        lazy val pathFilter = {
          request.odf.getPaths.exists{
            relativePath => 
              relativePath == path || 
              relativePath.isAncestorOf(path) || 
              relativePath.isDescendantOf(path)  
          }
        }
        filter(rf) && pathFilter
    }.groupBy{
      case (path: Path, ar: AgentResponsibility) => ar.agentName
    }.map{
      case (agentName, tuples) => 
        (Some(ResponsibleAgent(agentName)),tuples.map(_._1).toSet)
    }
    val responsiblePaths = responsibleToPathsWithoutNone.values.flatten
    val sharedPaths: Set[Path] = odf.getLeafPaths.filterNot{
      path: Path => 
        responsiblePaths.exists{
          responsiblePath: Path =>
            responsiblePath == path ||
            responsiblePath.isAncestorOf(path)
        }
    }
    val responsibleToPaths: Map[Option[Responsible],Set[Path]] = sharedPaths.headOption.map{
      _ => 
        None -> sharedPaths
      }.toMap ++ responsibleToPathsWithoutNone
    cachedOdfF.map{
      cachedOdf =>
      val result = responsibleToPaths.map{
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          val subResponsible = responsiblePaths.filter{
            responsiblePath => 
              paths.exists{
                path => path.isAncestorOf( responsiblePath)
              }
          }
          val selectedPaths = cachedOdf.subTreePaths(paths).filterNot{
            path => 
              subResponsible.exists{
                responsiblePath => 
                  path == responsiblePath ||
                  responsiblePath.isAncestorOf(path)
              }
          } ++ paths.map( _.getAncestorsAndSelf ).flatten.toSet
          responsible -> selectedPaths
      }
      val r = result.map{
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          val selectedPaths = paths.filter{
            path => 
              result.size == 1 ||
              result.exists{
                case (otherResponsible, otherPaths) => 
                  otherResponsible != responsible &&
                  otherPaths.contains(path.getParent)

              }
          }.flatMap(_.getAncestorsAndSelf).toSet
          val nodes: Vector[Node]= selectedPaths.flatMap{
            path => 
              cachedOdf.get(path).map{
                case obj: Object =>
                  odf.get(path).getOrElse{
                      obj.copy(ids = obj.ids.filter( qlmID => qlmID.id == obj.path.last), typeAttribute = None, descriptions = Set.empty)
                  }
                case ii: InfoItem =>
                  odf.get(path).getOrElse{
                      ii.copy(names = ii.names.filter( qlmID => qlmID.id == ii.path.last), typeAttribute = None, descriptions = Set.empty, metaData = None, values = Vector.empty)
                  }
                case obj: Objects => 
                  odf.get(path).getOrElse( obj )
              }.orElse{
                odf.get(path)
              }
          }.toVector
          responsible -> request.replaceOdf(ImmutableODF(nodes))
      }
       r
    }
  }
  def splitDeleteToResponsible( request: DeleteRequest )(implicit ec: ExecutionContext) : Future[ImmutableMap[Option[Responsible], OdfRequest]] ={
    val cachedOdfF: Future[ImmutableODF] = singleStores.getHierarchyTree()
    def filter: RequestFilter => Boolean = createFilter(request)
    val odf = request.odf
    val requestedLeafPaths = odf.getLeafPaths
    val responsibleToPathsWithoutNone: Map[Option[Responsible],Set[Path]]= pathsToResponsible.filter{
      case (path, AgentResponsibility( agentName, _, rf) ) =>
        lazy val pathFilter = {
          requestedLeafPaths.exists{
            relativePath => 
              relativePath == path || 
              relativePath.isAncestorOf(path) || 
              relativePath.isDescendantOf(path)  
          }
        }
        filter(rf) && pathFilter
    }.groupBy{
      case (path: Path, ar: AgentResponsibility) => ar.agentName
    }.map{
      case (agentName, tuples) => 
        (Some(ResponsibleAgent(agentName)),tuples.map(_._1).toSet)
    }
    val responsiblePaths = responsibleToPathsWithoutNone.values.flatten
    val sharedPaths: Set[Path] = odf.getLeafPaths.filterNot{
      path: Path => 
        responsiblePaths.exists{
          responsiblePath: Path =>
            responsiblePath == path ||
            responsiblePath.isAncestorOf(path)
        }
    }
    val responsibleToPaths: Map[Option[Responsible],Set[Path]] = sharedPaths.headOption.map{
      _ => 
        None -> sharedPaths
      }.toMap ++ responsibleToPathsWithoutNone
    cachedOdfF.map{
      cachedOdf =>
      val result = responsibleToPaths.map{
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          val subResponsible = responsiblePaths.filter{
            responsiblePath => 
              paths.exists{
                path => path.isAncestorOf( responsiblePath)
              }
          }
          val selectedPaths = cachedOdf.subTreePaths(paths).filterNot{
            path => 
              subResponsible.exists{
                responsiblePath => 
                  path == responsiblePath ||
                  responsiblePath.isAncestorOf(path)
              }
          } ++ paths.map( _.getAncestorsAndSelf ).flatten.toSet
          responsible -> selectedPaths
      }
      val requestedPaths =odf.getPaths
      val r = result.map{
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          val selectedPaths = paths.filter{
            path => 
              if( result.size == 1 ){
                requestedPaths.contains(path)
              } else {
                result.exists{
                  case (otherResponsible, otherPaths) => 
                    otherResponsible != responsible &&
                    otherPaths.contains(path.getParent)
                }
              }
          }.flatMap(_.getAncestorsAndSelf).toSet
          val nodes: Vector[Node]= selectedPaths.flatMap{
            path => 
              cachedOdf.get(path).map{
                case obj: Object =>
                  odf.get(path).getOrElse{
                      obj.copy(ids = obj.ids.filter( qlmID => qlmID.id == obj.path.last), typeAttribute = None, descriptions = Set.empty)
                  }
                case ii: InfoItem =>
                  odf.get(path).getOrElse{
                      ii.copy(names = ii.names.filter( qlmID => qlmID.id == ii.path.last), typeAttribute = None, descriptions = Set.empty, metaData = None, values = Vector.empty)
                  }
                case obj: Objects => 
                  odf.get(path).getOrElse( obj )
              }.orElse{
                odf.get(path)
              }
          }.toVector
          responsible -> request.replaceOdf(ImmutableODF(nodes))
      }
       r
    }
  }
  def splitReadToResponsible( request: ReadRequest )(implicit ec: ExecutionContext) : Future[ImmutableMap[Option[Responsible], OdfRequest]] ={
    val cachedOdfF: Future[ImmutableODF] = singleStores.getHierarchyTree()
    def filter: RequestFilter => Boolean = createFilter(request)
    val odf = request.odf
    val requestedLeafPaths = odf.getLeafPaths
    val responsibleToPathsWithoutNone: Map[Option[Responsible],Set[Path]]= pathsToResponsible.filter{
      case (path, AgentResponsibility( agentName, _, rf) ) =>
        lazy val pathFilter = requestedLeafPaths.exists{
            relativePath => 
              relativePath == path || 
              relativePath.isAncestorOf(path) || 
              relativePath.isDescendantOf(path)  
          }
        filter(rf) && pathFilter
    }.groupBy{
      case (path: Path, ar: AgentResponsibility) => ar.agentName
    }.map{
      case (agentName, tuples) => 
        (Some(ResponsibleAgent(agentName)),tuples.map(_._1).toSet)
    }
    val responsiblePaths = responsibleToPathsWithoutNone.values.flatten
    val sharedPaths: Set[Path] = requestedLeafPaths.filterNot{
      path: Path => 
        responsiblePaths.exists{
          responsiblePath: Path =>
            responsiblePath == path ||
            responsiblePath.isAncestorOf(path)
        }
    }
    val responsibleToPaths: Map[Option[Responsible],Set[Path]] = sharedPaths.headOption.map{
      _ => 
        None -> sharedPaths
      }.toMap ++ responsibleToPathsWithoutNone
    cachedOdfF.map{
      cachedOdf =>
      val result = responsibleToPaths.map{
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          val subResponsible = responsiblePaths.filter{
            responsiblePath => 
              paths.exists{
                path => path.isAncestorOf( responsiblePath)
              }
          }
          val selectedPaths = cachedOdf.subTreePaths(paths).filterNot{
            path => 
              subResponsible.exists{
                responsiblePath => 
                  path == responsiblePath ||
                  responsiblePath.isAncestorOf(path)
              }
          } ++ paths.map( _.getAncestorsAndSelf ).flatten.toSet
          responsible -> selectedPaths
      }
      val r = result.map{
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          responsible -> paths.filter{
            path => 
              if( result.size == 1 ){
                requestedLeafPaths.contains(path)
              } else {
                result.exists{
                  case (otherResponsible, otherPaths) => 
                    otherResponsible != responsible &&
                    otherPaths.contains(path.getParent)
                }
              }
          }.flatMap(_.getAncestorsAndSelf).toSet
      }
      r.flatMap{
        case (None, paths: Set[Path]) => 
          if( paths.isEmpty ){
            None
          } else if( result.size == 1 ){
            Some(None -> paths)
          
          } else {
            val filteredChilds = paths.flatMap{ path => cachedOdf.getChildPaths(path)}.filterNot{
              path: Path => 
                r.exists{
                    case (None, _) => false 
                    case (Some(responsible), otherPaths) => 
                      otherPaths.exists{
                        opath: Path => 
                          path == opath || path.isAncestorOf(opath)
                      }

                }
            }
            val f2 = filteredChilds.filterNot{
              path: Path => 
                filteredChilds.exists{
                  apath: Path => 
                   apath.isAncestorOf(path) 
                }
            }.filter{
              path: Path =>
                requestedLeafPaths.exists{
                  leaf: Path =>
                    path.isAncestorOf(leaf) ||
                    leaf.isAncestorOf(path) ||
                    path == leaf
                }
            }
            if( f2.nonEmpty ){
              Some(None -> f2)
            } else None
          }
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          Some(responsible -> paths)
      }.map{
        case (responsible: Option[Responsible], paths: Set[Path]) => 
          val nodes: Vector[Node]= paths.flatMap{
            path => 
              cachedOdf.get(path).map{
                case obj: Object =>
                  odf.get(path).getOrElse{
                      obj.copy(ids = obj.ids.filter( qlmID => qlmID.id == obj.path.last), typeAttribute = None, descriptions = Set.empty)
                  }
                case ii: InfoItem =>
                  odf.get(path).getOrElse{
                      ii.copy(names = ii.names.filter( qlmID => qlmID.id == ii.path.last), typeAttribute = None, descriptions = Set.empty, metaData = None, values = Vector.empty)
                  }
                case obj: Objects => 
                  odf.get(path).getOrElse( obj )
              }.orElse{
                odf.get(path)
              }
          }.toVector
          responsible -> request.replaceOdf(ImmutableODF(nodes))
      }
    }
  }

  def splitCallAndWriteToResponsible( request: OdfRequest ) : Future[ImmutableMap[Option[Responsible], OdfRequest]] ={
    if( pathsToResponsible.isEmpty ){
      return Future.successful(ImmutableMap( None -> request ))
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
            ResponsibleAgent(agentName)
        }
    }

    val results: Map[Option[Responsible],OdfRequest] = if( resp.size > 1 ){
      resp.map {
        case (responsible: Option[Responsible], paths: Set[Path]) =>
          responsible -> request.replaceOdf(odf.selectUpTree(paths))
      }
    } else {
      resp.headOption.map{
        case (responsible: Option[Responsible], paths: Set[Path]) =>
          responsible -> request
      }.toMap
    }
    Future.successful(results)
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

  def removeAgent(agentName: AgentName): MutableMap[Path, AgentResponsibility] = {
    val agentsResponsibilities = pathsToResponsible.values.collect {
      case AgentResponsibility(aN: AgentName, path: Path, rf: RequestFilter) if agentName == aN =>
        path
    }

    pathsToResponsible --= agentsResponsibilities

  }

  def add(agentResponsibilities: Seq[AgentResponsibility]): Unit = {
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

    val result = !responsibleToPaths.keys.flatten.exists {
      keyname: AgentName => !optionAgentName.contains(keyname)
    }
    //println( s"Permissien check:$result")
    result
  }

}

