package types
package odf

import scala.collection.immutable.{HashMap, TreeSet}
import scala.collection.{ Map, Seq, SortedSet => CSortedSet, SeqView}
import akka.stream.alpakka.xml._
import akka.stream.scaladsl._
import akka.NotUsed
import akka.util.ByteString
import utils._
import scala.collection.JavaConverters._
import types.Path.PathOrdering
import types.Path

import types.omi.Version._

/** O-DF structure
  */
trait ODF //[M <: Map[Path,Node], S<: SortedSet[Path] ]
{
  /** All nodes(InfoItems and Objects) in O-DF structure.
    */
  protected[odf] def nodes: Map[Path, Node] //= HashMap.empty
  /** SortedSet of all paths in O-DF structure. 
    * Should be ordered by paths with alpabetic ordering so that after a path
    * comes all its descendant:
    * A/, A/a, A/a/1, A/b/, A/b/1, Aa/ ..
    *
    */
  protected[odf] def paths: CSortedSet[Path] //= TreeSet( nodes.keys.toSeq:_* )(PathOrdering)
  //def copy( nodes : scala.collection.Map[Path,Node] ): ODF


  final def isEmpty: Boolean = paths.isEmpty || (paths.size == 1 && paths.contains(Path("Objects")))

  final def nonEmpty: Boolean = !isEmpty

  final def isRootOnly: Boolean = isEmpty

  final def contains(path: Path): Boolean = paths.contains(path)
  final def contains( _paths: Iterable[Path]): Boolean = _paths.forall{ path => paths.contains(path)}


  /*
   * Select exactly the paths in that ODF from this ODF.
   */
  def select(that: ODF): ODF

  def union(that: ODF): ODF

  def removePaths(pathsToRemove: Iterable[Path]): ODF = removePaths(pathsToRemove.toSet)
  
  def removePaths(pathsToRemove: Set[Path]): ODF

  def toImmutable: ImmutableODF

  def toMutable: MutableODF

  def getPaths: Set[Path] = paths.toSet

  def getNodes: Iterable[Node] = nodes.values.toVector

  final def getInfoItems: Iterable[InfoItem] = nodes.values.collect {
    case ii: InfoItem => ii
  }.toVector

  final def getObjects: Iterable[Object] = nodes.values.collect {
    case obj: Object => obj
  }.toVector
  final def getRoot: Option[Objects] = nodes.get(Path("Objects")).collect{
    case obj: Objects => obj
  }

  final def nodesWithStaticData: Vector[Node] = nodes.values.filter(_.hasStaticData).toVector

  final def nodesWithAttributes: Vector[Node] = nodes.values.filter(_.attributes.nonEmpty).toVector

  @deprecated("use nodesMap instead (bad naming)", "1.0.8")
  def getNodesMap: Map[Path, Node] = HashMap(
    nodes.toVector: _*
  )
  def nodesMap: HashMap[Path, Node] = HashMap(
    nodes.toVector: _*
  )

  final def getChildPaths(wantedPath: Path): Set[Path] = {
    val wantedLength = wantedPath.length
    paths
      .keysIteratorFrom( wantedPath )
      .drop(1)
      .takeWhile{
        path => path.length >= wantedLength //&& path.isDescendantOf(wantedPath)
      }
      .filter{
        path => 
          path.isChildOf(wantedPath)
      }
      .toSet
  }

  final def getChilds(path: Path): Iterable[Node] = {
    getChildPaths(path).flatMap { p: Path => nodes.get(p) }.toVector
  }

  final def getLeafs: Vector[Node] = {
    getLeafPaths.flatMap(nodes.get(_)).toVector.sortBy(_.path)(Path.PathOrdering)
  }

  final def getLeafPaths: TreeSet[Path] = {
    val ps: Seq[(Path, Int)] = paths.toSeq.zipWithIndex
    TreeSet(ps.collect {
      case (path: Path, index: Int) if{
        val nextIndex = index + 1
        if (nextIndex < ps.size) {
          val nextPath: Path = ps(nextIndex)._1
          !path.isAncestorOf(nextPath)
        } else true
      } => path
    }:_*)(PathOrdering)
  }

  final def pathsOfInfoItemsWithMetaData: Set[Path] = {
    nodes.values.collect {
      case ii: InfoItem if ii.metaData.nonEmpty => ii.path
    }.toSet
  }

  final def infoItemsWithMetaData: Set[InfoItem] = {
    nodes.values.collect {
      case ii: InfoItem if ii.metaData.nonEmpty => ii
    }.toSet
  }

  final def nodesWithDescription: Set[Node] = {
    nodes.values.collect {
      case ii: InfoItem if ii.descriptions.nonEmpty => ii
      case obj: Object if obj.descriptions.nonEmpty => obj
    }.toSet
  }

  final def pathsOfNodesWithDescription: Set[Path] = {
    nodes.values.collect {
      case ii: InfoItem if ii.descriptions.nonEmpty => ii.path
      case obj: Object if obj.descriptions.nonEmpty => obj.path
    }.toSet
  }

  final def objectsWithType(typeStr: String): Vector[Object] = {
    nodes.values.collect {
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toVector
  }

  final def pathsWithType(typeStr: String): Set[Path] = {
    nodes.values.collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii.path
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj.path
    }.toSet
  }
  final def childsWithType(path:Path, typeStr: String): Set[Node] = {
    getChilds(path).collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toSet
  }
  final def descendantsWithType(paths: Set[Path], typeStr: String): Set[Node] = {
    subTreePaths(paths).flatMap( nodes.get(_) ).collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toSet
  }

  final def nodesWithType(typeStr: String): Set[Node] = {
    nodes.values.collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toSet
  }

  def replaceValues[C <: java.util.Collection[Value[java.lang.Object]]]( pathToValues: java.util.Map[Path,C]): ODF = {
    replaceValues(pathToValues.asScala.mapValues{ col => col.asScala})
  }
  def replaceValues( pathToValues: Iterable[(Path,Iterable[Value[_]])]): ODF
  def replaceValues( pathToValues: Map[Path,Iterable[Value[_]]]): ODF = {
    replaceValues( pathToValues.toIterable )
  }
  final def get(path: Path): Option[Node] = nodes.get(path)

  /**
    * Find given paths and all paths of the descendants.
    * If generationDifference is given then each descendant can be at most given
    * amount of generations younger.
    * Same as [[selectSubTreePaths]] but doesn't add ancestors of the subtrees
    */
   final def subTreePaths(pathsToGet: Set[Path], generationDifference: Option[Int] = None): TreeSet[Path] = {
     val results = TreeSet( pathsToGet.flatMap {
       wantedPath: Path =>
         val wantedLength = wantedPath.toSeq.length
         paths.keysIteratorFrom(wantedPath).takeWhile {
           path: Path => path.startsWith(wantedPath)
         }.filter{
           path: Path => 
             generationDifference.forall{
               n =>
                 path.toSeq.length - wantedLength <= n
            }
         }
     }.toSeq:_*)(PathOrdering)
     results
  }

   /**
    * Same as [[subTreePaths]] but adds ancestors of the subtrees
    */
   final def selectSubTreePaths(pathsToGet: Set[Path], generationDifference: Option[Int] = None): TreeSet[Path] = {
     subTreePaths(pathsToGet, generationDifference) ++ pathsToGet.flatMap{
       path => path.getAncestors
       //case path: Path if (paths.contains(path)) =>
       //  path.getAncestors
       //case _ => Set()
     }
   }

   /*
    * Select paths and their descedants from this ODF.
    */
   def selectSubTree(pathsToGet: Set[Path], generationDifference: Option[Int] = None): ODF

   /*
    * Select paths and their ancestors from this ODF.
    */
   def selectUpTree(pathsToGet: Set[Path]): ODF

   def --(removedPaths: Iterable[Path]): ODF = removePaths(removedPaths)

   def removePath(path: Path): ODF

   def add(node: Node): ODF

   def addNodes(nodesToAdd: Iterable[Node]): ODF

   def update(that: ODF): ODF

   def valuesRemoved: ODF

   def descriptionsRemoved: ODF

   def metaDatasRemoved: ODF
   def attributesRemoved: ODF

   //TODO: remove if newer is faster
   /*
   final def createObjectType(obj: Object): ObjectType = {
     val (objects, infoItems) = getChilds(obj.path).partition {
       case obj: Object => true
       case ii: InfoItem => false
     }
     obj.asObjectType(
       infoItems.collect {
         case ii: InfoItem => ii.asInfoItemType
       },
       objects.collect {
         case obj: Object =>
           createObjectType(obj)
       }
       )
   }*/

  final implicit def asXMLDocumentSource(odfVersion: Option[OdfVersion]=None): Source[String, NotUsed] = {
    parseEventsToByteSource(asXMLDocument(odfVersion)).map[String](_.utf8String)
  }
  final implicit def asXMLByteSource(odfVersion: Option[OdfVersion]=None): Source[ByteString, NotUsed] = {
    parseEventsToByteSource(asXMLEvents(odfVersion))
  }
  
  final implicit def asXMLSource(odfVersion: Option[OdfVersion]=None): Source[String, NotUsed] = asXMLByteSource(odfVersion).map[String](_.utf8String)
  final def asXMLDocument(odfVersion: Option[OdfVersion]=None): SeqView[ParseEvent, Iterable[_]] = {
    Seq(StartDocument).view ++
    asXMLEvents(odfVersion) ++
    Seq(EndDocument)
  }
  final def asXMLEvents(odfVersion: Option[OdfVersion]=None): SeqView[ParseEvent, Iterable[_]] = {
   def sort = {
     //val timer = LapTimer(println)
     var iis: List[InfoItem] = List.empty
     var objs: List[Node] = List.empty
     nodes.values.foreach{
       case ii: InfoItem => iis = ii :: iis
       case node: Node => objs = node :: objs
     }
     //timer.step("Partition")
     val parent2IIs: Map[Path,Iterable[InfoItem]] = iis.groupBy{ ii: InfoItem => ii.path.getParent}
     //timer.step("Group by")
     val sorted = objs.sortBy(_.path)(PathOrdering)
     //timer.step("sort")
     val res = sorted.flatMap{
       case node: Node =>
         Seq(node) ++ parent2IIs.get(node.path).toSeq.flatten
     }.toVector
     //timer.step("flat map")
     //timer.total()
     res
   }

   val sortedNodes = sort

   var parentStack: List[Path] = Nil
   def handleEnd( index: Int ) = {
     if( index == sortedNodes.size - 1 ){
       val count = Math.max(parentStack.length-1,0)
       Vector.fill(count)( EndElement("Object") ) ++ Vector(EndElement("Objects"))
     } else {
       Vector.empty[ParseEvent]
     }
   }
   val events = sortedNodes.zipWithIndex.view.flatMap{
     case (objs: Objects, index: Int) =>
       parentStack = objs.path +: parentStack //parentStack.push(objs.path)
       Vector(
         StartElement(
           "Objects",
           (odfVersion.map(_.number.toString) orElse objs.version).map{
             ver: String =>
               Attribute("version", ver)
               }.toList ++ objs.attributes.map{
                 case (key: String, value: String) => 
                   Attribute(key,value)
               },
               namespaceCtx = List(
                 //Namespace(s"http://www.opengroup.org/xsd/odf/${objs.version.getOrElse("1.0")}/",None))
                 Namespace(odfVersion.getOrElse(OdfVersion1).namespace,None))
               )
       ) ++ handleEnd(index)
      case (obj: Object, index: Int) =>
        var count: Int = 0
        while(parentStack.nonEmpty && parentStack.head != obj.path.getParent){
          val first = parentStack.headOption
          parentStack = parentStack.tail
          first match{
            case Some(path) =>
              count = count + 1
            case None =>
          }
        }
        parentStack = obj.path +: parentStack
        Vector.fill(count)( EndElement("Object") ) ++ Vector(
          StartElement(
            "Object",
            obj.typeAttribute.map{
              str: String =>
                Attribute("type",str)
                }.toList ++obj.attributes.map{
                  case (key: String, value: String) => Attribute(key,value)
                }.toList

                )) ++ obj.ids.view.flatMap{
                  case id: OdfID => id.asXMLEvents("id")
                  } ++ obj.descriptions.view.flatMap{
                    case desc: Description =>
                      desc.asXMLEvents
                  } ++ handleEnd(index)      
                    case (ii: InfoItem, index: Int) =>

                      var count: Int = 0
                      while(parentStack.nonEmpty && parentStack.head != ii.path.getParent){
                        val first = parentStack.headOption
                        parentStack = parentStack.tail
                        first match{
                          case Some(path) =>
                            count = count + 1
                          case None =>
                        }
                      }
                      Vector.fill(count)( EndElement("Object") ) ++ ii.asXMLEvents ++ handleEnd(index)


   } 
   events 
  }

  override def toString: String = {
    "ODF{\n" +
    nodes.map {
      case (p, node) =>
        s"$p --> $node"
    }.mkString("\n") + "\n}"
  }

  override def equals(that: Any): Boolean = {
    that match {
      case another: ODF =>
        (paths equals another.paths) && (nodes equals another.nodes)
      case a: Any =>
        false
    }
  }

  override lazy val hashCode: Int = this.nodes.hashCode

  def readTo(to: ODF, generationDifference: Option[Int] = None): ODF

  def readToNodes(to: ODF, generationDifference: Option[Int] = None): Iterable[Node] = {
    val wantedPaths: TreeSet[Path] = TreeSet(selectSubTreePaths(to.getLeafPaths, generationDifference).intersect(paths).toSeq:_*)(PathOrdering)
    val wantedNodes: Iterable[Node] = wantedPaths.map {
      path: Path =>
        (nodes.get(path), to.nodes.get(path)) match {
          case (None, _) => throw new Exception(s"Existing path does not map to node. $path")
          case (Some(obj: Object), None) =>
            obj.copy(
              descriptions = {
                if (obj.descriptions.nonEmpty) Set(Description("")) else Set.empty
              }
            )
          case (Some(ii: InfoItem), None) =>
            ii.copy(
              names = {
                if (ii.names.nonEmpty) Vector(OdfID("")) else Vector.empty
              },
              descriptions = {
                if (ii.descriptions.nonEmpty) Set(Description("")) else Set.empty
              },
              metaData = {
                if (ii.metaData.nonEmpty) Some(MetaData.empty) else None
              }
            )
          case (Some(obj:Objects),None) => obj.copy()
          case (Some(obj:Object),Some(toObj:Object)) => obj.readTo(toObj)
          case (Some(ii:InfoItem),Some(toIi:InfoItem)) => ii.readTo(toIi)
          case (Some(obj:Objects),Some(toObj:Objects)) => obj.readTo(toObj)
          case (Some(f:Node), Some(t:Node)) => throw new Exception("Missmatching types in ODF when reading.")
        }
    }
    wantedNodes
  }
}

object ODF {
  /*
  def apply[M <: scala.collection.Map[Path,Node], S<: scala.collection.SortedSet[Path] ]( 
    nodes: M
  ) : ODF ={
    nodes match {
      case mutable: 
    }
  }*/
  def apply(n: Node): ODF = ImmutableODF(n)
  def apply(n: Node*): ODF = ImmutableODF(n)
}
