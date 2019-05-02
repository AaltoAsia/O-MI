package types
package odf


import parsing.xmlGen.xmlTypes.{ObjectType, ObjectsType, InfoItemType}
import parsing.xmlGen.{odfDefaultScope, scalaxb}

import scala.collection.immutable.{HashMap => ImmutableHashMap}
import scala.collection.mutable.{ Buffer }
import scala.collection.{Map, Seq, SortedSet}
import scala.xml.NodeSeq

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
  protected[odf] def paths: SortedSet[Path] //= TreeSet( nodes.keys.toSeq:_* )(PathOrdering)
  //def copy( nodes : scala.collection.Map[Path,Node] ): ODF


  final def isEmpty: Boolean = paths.isEmpty || (paths.size == 1 && paths.contains(Path("Objects")))

  final def nonEmpty: Boolean = !isEmpty

  final def isRootOnly: Boolean = isEmpty

  final def contains(path: Path): Boolean = paths.contains(path)


  def select(that: ODF): ODF

  def union(that: ODF): ODF

  def removePaths(pathsToRemove: Seq[Path]): ODF = removePaths(pathsToRemove.toSet)
  
  def removePaths(pathsToRemove: Set[Path]): ODF

  def immutable: ImmutableODF

  def mutable: MutableODF

  final def getPaths: Seq[Path] = paths.toVector

  final def getNodes: Seq[Node] = nodes.values.toVector

  final def getInfoItems: Seq[InfoItem] = nodes.values.collect {
    case ii: InfoItem => ii
  }.toVector

  final def getObjects: Seq[Object] = nodes.values.collect {
    case obj: Object => obj
  }.toVector

  final def nodesWithStaticData: Vector[Node] = nodes.values.filter(_.hasStaticData).toVector

  final def nodesWithAttributes: Vector[Node] = nodes.values.filter(_.attributes.nonEmpty).toVector

  @deprecated("use nodesMap instead (bad naming)", "1.0.8")
  def getNodesMap: Map[Path, Node] = ImmutableHashMap(
    nodes.toVector: _*
  )
  def nodesMap: ImmutableHashMap[Path, Node] = ImmutableHashMap(
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

  final def getChilds(path: Path): Seq[Node] = {
    getChildPaths(path).flatMap { p: Path => nodes.get(p) }.toVector
  }

  final def getLeafs: Vector[Node] = {
    getLeafPaths.flatMap(nodes.get(_)).toVector.sortBy(_.path)(Path.PathOrdering)
  }

  final def getLeafPaths: Set[Path] = {
    val ps: Seq[(Path, Int)] = paths.toSeq.zipWithIndex
    ps.collect {
      case (path: Path, index: Int) if{
        val nextIndex = index + 1
        if (nextIndex < ps.size) {
          val nextPath: Path = ps(nextIndex)._1
          !path.isAncestorOf(nextPath)
        } else true
      } => path
    }.toSet
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

  final def get(path: Path): Option[Node] = nodes.get(path)

  /**
    * Find given paths and all paths of the descendants.
    * Same as [[selectSubTreePaths]] but doesn't add ancestors of the subtrees
    */
  final def subTreePaths(pathsToGet: Set[Path]): Set[Path] = (pathsToGet.flatMap {
    wantedPath: Path =>
      paths.keysIteratorFrom(wantedPath).takeWhile {
        path: Path => path.startsWith(wantedPath)
      }
  })

  /**
    * Same as [[subTreePaths]] but adds ancestors of the subtrees
    */
  final def selectSubTreePaths(pathsToGet: Set[Path]): Set[Path] = {
    subTreePaths(pathsToGet) ++ pathsToGet.flatMap{
        path => path.getAncestors
        //case path: Path if (paths.contains(path)) =>
        //  path.getAncestors
        //case _ => Set()
    }
  }

  def selectSubTree(pathsToGet: Set[Path]): ODF

  def selectUpTree(pathsToGet: Set[Path]): ODF

  def --(removedPaths: Seq[Path]): ODF = removePaths(removedPaths)

  def removePath(path: Path): ODF

  def add(node: Node): ODF

  def addNodes(nodesToAdd: Seq[Node]): ODF

  implicit def asObjectsType: ObjectsType = {
    val iis: Buffer[InfoItem] = Buffer.empty
    val objs: Buffer[Object] = Buffer.empty
    var objects = Objects()
    for( n <- nodes.values ){
      n match {
        case ii: InfoItem => iis += ii
        case obj: Object => objs += obj
        case obj: Objects => objects = obj
      }
    }
    val parentPath2IIt: Map[Path,Seq[InfoItemType]] = iis.map{
        case ii: InfoItem => 
          ii.path -> ii.asInfoItemType
      }.groupBy(_._1.getParent).mapValues{
        case iis: Seq[Tuple2[Path,InfoItemType]] => 
          iis.map(_._2)
      }
    val parentPath2Objs: Map[Path,Seq[Tuple2[Path,ObjectType]]] = objs.map{
      case obj: Object => 
        obj.path -> obj.asObjectType( parentPath2IIt.get(obj.path).toSeq.flatten, Seq.empty)
    }.groupBy(_._1.getParent)  
    def temp(path:Path, obj: ObjectType): ObjectType ={
      val cobjs: Seq[ObjectType] = parentPath2Objs.get(path).toSeq.flatten.map{
        case ( p: Path, ot: ObjectType) => temp(p,ot)
      }
      obj.copy( ObjectValue = cobjs ) 
    }
    val topObjects: Seq[ObjectType] = parentPath2Objs.get( Path("Objects") ).toSeq.flatten.map{
      case (path: Path, obj: ObjectType) =>  temp(path, obj) 
        
    }
    objects.asObjectsType( topObjects)
    //TODO: remove if newer is faster
    /*
    val firstLevelObjects = getChilds(new Path("Objects"))
    val objectTypes = firstLevelObjects.map {
      case obj: Object =>
        createObjectType(obj)
    }
    nodes.get(new Path("Objects")).collect {
      case objs: Objects =>
        objs.asObjectsType(objectTypes)
    }.getOrElse {
      Objects().asObjectsType(objectTypes)
    }*/
  }

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

  final implicit def asXML: NodeSeq = {
    val xml = scalaxb.toXML[ObjectsType](asObjectsType, None, Some("Objects"), odfDefaultScope)
    xml //.asInstanceOf[Elem] % new UnprefixedAttribute("xmlns","odf.xsd", Node.NoAttributes)
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

  def readTo(to: ODF): ODF

  def readToNodes(to: ODF): Seq[Node] = {
    val wantedPaths: SortedSet[Path] =
      to.paths.intersect(paths) ++ selectSubTreePaths(to.getLeafPaths).intersect(paths)

    val wantedNodes: Seq[Node] = wantedPaths.toSeq.map {
      path: Path =>
        (nodes.get(path), to.nodes.get(path)) match {
          case (None, _) => throw new Exception("Existing path does not map to node.")
          case (Some(obj: Object), None) =>
            obj.copy(
              descriptions = {
                if (obj.descriptions.nonEmpty) Set(Description("")) else Set.empty
              }
            )
          case (Some(ii: InfoItem), None) =>
            ii.copy(
              names = {
                if (ii.names.nonEmpty) Vector(QlmID("")) else Vector.empty
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
          case (Some(f:Node),None) => throw new Exception("Found unknown Node type.")
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
