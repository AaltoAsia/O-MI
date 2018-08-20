package types
package odf


import parsing.xmlGen.xmlTypes.{ObjectType, ObjectsType}
import parsing.xmlGen.{odfDefaultScope, scalaxb}

import scala.collection.immutable.{HashMap => ImmutableHashMap}
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


  def isEmpty: Boolean = paths.isEmpty || (paths.size == 1 && paths.contains(Path("Objects")))

  def nonEmpty: Boolean = !isEmpty

  def isRootOnly: Boolean = isEmpty

  def contains(path: Path): Boolean = paths.contains(path)


  def select(that: ODF): ODF

  def union[TM <: Map[Path, Node], TS <: SortedSet[Path]](that: ODF): ODF

  def removePaths(pathsToRemove: Seq[Path]): ODF = {
    removePaths(pathsToRemove.toSet)
  }
  def removePaths(pathsToRemove: Set[Path]): ODF

  def immutable: ImmutableODF

  def mutable: MutableODF

  def getPaths: Seq[Path] = paths.toVector

  def getNodes: Seq[Node] = nodes.values.toVector

  def getInfoItems: Seq[InfoItem] = nodes.values.collect {
    case ii: InfoItem => ii
  }.toVector

  def getObjects: Seq[Object] = nodes.values.collect {
    case obj: Object => obj
  }.toVector

  def nodesWithStaticData: Vector[Node] = nodes.values.filter(_.hasStaticData).toVector

  def nodesWithAttributes: Vector[Node] = nodes.values.filter(_.attributes.nonEmpty).toVector

  def getNodesMap: Map[Path, Node] = ImmutableHashMap(
    nodes.toVector: _*
  )

  def getChildPaths(wantedPath: Path): Seq[Path] = {
    val wantedLength = wantedPath.length +1
    paths
      .keysIteratorFrom( wantedPath )
      .drop(1)
      .takeWhile{
        path => path.length >= wantedLength //path.isChildOf(wantedPath)
      }
      .filter{
        path => path.length == wantedLength
      }
      .toSeq
  }

  def getChilds(path: Path): Seq[Node] = {
    getChildPaths(path).flatMap { p: Path => nodes.get(p) }.toVector
  }

  def getLeafs: Vector[Node] = {
    getLeafPaths.flatMap(nodes.get(_)).toVector.sortBy(_.path)(Path.PathOrdering)
  }

  def getLeafPaths: Set[Path] = {
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

  def pathsOfInfoItemsWithMetaData: Set[Path] = {
    nodes.values.collect {
      case ii: InfoItem if ii.metaData.nonEmpty => ii.path
    }.toSet
  }

  def infoItemsWithMetaData: Set[InfoItem] = {
    nodes.values.collect {
      case ii: InfoItem if ii.metaData.nonEmpty => ii
    }.toSet
  }

  def nodesWithDescription: Set[Node] = {
    nodes.values.collect {
      case ii: InfoItem if ii.descriptions.nonEmpty => ii
      case obj: Object if obj.descriptions.nonEmpty => obj
    }.toSet
  }

  def pathsOfNodesWithDescription: Set[Path] = {
    nodes.values.collect {
      case ii: InfoItem if ii.descriptions.nonEmpty => ii.path
      case obj: Object if obj.descriptions.nonEmpty => obj.path
    }.toSet
  }

  def objectsWithType(typeStr: String): Vector[Object] = {
    nodes.values.collect {
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toVector
  }

  def pathsWithType(typeStr: String): Set[Path] = {
    nodes.values.collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii.path
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj.path
    }.toSet
  }
  def childsWithType(path:Path, typeStr: String): Set[Node] = {
    getChilds(path).collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toSet
  }
  def descendantsWithType(paths: Set[Path], typeStr: String): Set[Node] = {
    subTreePaths(paths).flatMap( nodes.get(_) ).collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toSet
  }

  def nodesWithType(typeStr: String): Set[Node] = {
    nodes.values.collect {
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toSet
  }

  def get(path: Path): Option[Node] = nodes.get(path)

  /**
    * Find given paths and all paths of the descendants.
    * Same as [[selectSubTreePaths]] but doesn't add ancestors of the subtrees
    */
  def subTreePaths(pathsToGet: Set[Path]): Set[Path] = (pathsToGet.flatMap {
    wantedPath: Path =>
      paths.keysIteratorFrom(wantedPath).takeWhile {
        path: Path => path == wantedPath || path.isDescendantOf(wantedPath)
      }
  })

  /**
    * Same as [[subTreePaths]] but adds ancestors of the subtrees
    */
  def selectSubTreePaths(pathsToGet: Set[Path]): Set[Path] = {
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
    }
  }

  def update[TM <: Map[Path, Node], TS <: SortedSet[Path]](that: ODF): ODF

  def valuesRemoved: ODF

  def descriptionsRemoved: ODF

  def metaDatasRemoved: ODF

  def createObjectType(obj: Object): ObjectType = {
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
  }

  implicit def asXML: NodeSeq = {
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
