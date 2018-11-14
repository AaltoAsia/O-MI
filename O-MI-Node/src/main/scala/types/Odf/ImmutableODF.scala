package types
package odf

import types.Path._

import scala.collection.immutable.{HashMap => ImmutableHashMap, TreeSet => ImmutableTreeSet}
import scala.collection.mutable.{HashMap => MutableHashMap}
import scala.collection.{Map, Seq, SortedSet}

case class ImmutableODF private[odf](
                                      nodes: ImmutableHashMap[Path, Node]
                                    ) extends ODF {

  override val getNodesMap: ImmutableHashMap[Path, Node] = nodes

  def select(that: ODF): ImmutableODF = {
    ImmutableODF(
      paths.filter {
        path: Path =>
          that.paths.contains(path) || that.getLeafPaths.exists {
            ancestorPath: Path =>
              ancestorPath.isAncestorOf(path)
          }
      }.flatMap {
        path: Path =>
          this.nodes.get(path)
      }.toVector)
  }

  def readTo(to: ODF): ImmutableODF = ImmutableODF(readToNodes(to))

  protected[odf] val paths: ImmutableTreeSet[Path] = ImmutableTreeSet(nodes.keys.toSeq: _*)(PathOrdering)

  def update(that: ODF): ImmutableODF = {
    ImmutableODF(
      nodes.mapValues {
        node: Node =>
          (node, that.get(node.path)) match {
            case (ii: InfoItem, Some(iiu: InfoItem)) => ii.update(iiu)
            case (obj: Object, Some(ou: Object)) => obj.update(ou)
            case (objs: Objects, Some(objsu: Objects)) => objs.update(objsu)
            case (n, None) => n
            case (_, Some(_)) => throw new Exception("Missmatching types in ODF when updating.")
          }
      }.values.toVector
    )
  }

  lazy val typeIndex: Map[Option[String], Seq[Node]] = nodes.values.groupBy{
    case obj: Objects => None
    case ii: InfoItem => ii.typeAttribute
    case obj: Object => obj.typeAttribute
  }.mapValues(_.toSeq)

  def union(that: ODF): ImmutableODF = {
    val pathIntersection: SortedSet[Path] = this.paths.intersect(that.paths)
    val thisOnlyNodes: Set[Node] = (paths -- pathIntersection).flatMap {
      p: Path =>
        nodes.get(p)
    }
    val thatOnlyNodes: Set[Node] = (that.paths -- pathIntersection).flatMap {
      p: Path =>
        that.nodes.get(p)
    }.toSet
    val intersectingNodes: Set[Node] = pathIntersection.flatMap {
      path: Path =>
        (this.nodes.get(path), that.nodes.get(path)) match {
          case (Some(node: Node), Some(otherNode: Node)) =>
            (node, otherNode) match {
              case (ii: InfoItem, oii: InfoItem) =>
                Some(ii.union(oii))
              case (obj: Object, oo: Object) =>
                Some(obj.union(oo))
              case (obj: Objects, oo: Objects) =>
                Some(obj.union(oo))
              case (_, _) =>
                throw new Exception("Found two different types in same Path when tried to create union.")
            }
          case (t, o) => t.orElse(o)
        }
    }.toSet
    val allNodes = thisOnlyNodes ++ thatOnlyNodes ++ intersectingNodes
    ImmutableODF(
      allNodes.toVector
    )
  }

  def removePaths(pathsToRemove: Set[Path]): ImmutableODF = {
    val subTrees = subTreePaths(pathsToRemove)
    this.copy(nodes -- subTrees)
  }

  def removePath(path: Path): ImmutableODF = {
    val subtreeP = subTreePaths(Set(path))
    this.copy(nodes -- subtreeP)
  }

  def add(node: Node): ImmutableODF = {

    val newNodes: ImmutableHashMap[Path, Node] = if (nodes.contains(node.path)) {
      (nodes.get(node.path), node) match {
        case (Some(old: Object), obj: Object) =>
          nodes.updated(node.path, old.union(obj))
        case (Some(old: Objects), objs: Objects) =>
          nodes.updated(node.path, old.union(objs))
        case (Some(old: InfoItem), iI: InfoItem) =>
          nodes.updated(node.path, old.union(iI))
        case (old, n) =>
          throw new Exception(
            "Found two different types in same Path when tried to add a new node"
          )
      }
    } else {
      val mutableHMap: MutableHashMap[Path, Node] = MutableHashMap(nodes.toVector: _*)
      var toAdd = node
      while (!mutableHMap.contains(toAdd.path)) {
        mutableHMap += toAdd.path -> toAdd
        toAdd = toAdd.createParent
      }
      ImmutableHashMap(mutableHMap.toVector: _*)
    }
    this.copy(newNodes)
  }

  def selectUpTree(pathsToGet: Set[Path]): ImmutableODF = {
    ImmutableODF(
      pathsToGet.flatMap{
        path: Path =>
        path.getAncestorsAndSelf
      }.toSet.flatMap {
        path: Path =>
          nodes.get(path)
      }.toVector
    )
  }


  def selectSubTree(pathsToGet: Set[Path]): ImmutableODF = {
    val ps = selectSubTreePaths(pathsToGet)
    ImmutableODF(
      ps.flatMap {
        path: Path =>
          nodes.get(path)
      }.toVector
    )
  }

  def valuesRemoved: ImmutableODF = this.copy(ImmutableHashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(values = Vector())
    case obj: Object => obj
    case obj: Objects => obj
  }.toVector: _*))

  def descriptionsRemoved: ImmutableODF = this.copy(ImmutableHashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(descriptions = Set.empty)
    case obj: Object => obj.copy(descriptions = Set.empty)
    case obj: Objects => obj
  }.toVector: _*))

  def metaDatasRemoved: ImmutableODF = this.copy(ImmutableHashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(metaData = None)
    case obj: Object => obj
    case obj: Objects => obj
  }.toVector: _*))

  def attributesRemoved: ImmutableODF = this.copy(ImmutableHashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(typeAttribute = None, attributes = ImmutableHashMap())
    case obj: Object => obj.copy(typeAttribute = None, attributes = ImmutableHashMap())
    case obj: Objects => obj.copy( attributes = ImmutableHashMap())
  }.toVector: _*))

  def immutable: ImmutableODF = this

  def mutable: MutableODF = MutableODF(
    nodes.values.toVector
  )

  def addNodes(nodesToAdd: Seq[Node]): ImmutableODF = {
    val mutableHMap: MutableHashMap[Path, Node] = MutableHashMap(nodes.toVector: _*)
    val sorted = nodesToAdd.sortBy(_.path)(PathOrdering)
    sorted.foreach {
      node: Node =>
        if (mutableHMap.contains(node.path)) {
          (node, mutableHMap.get(node.path)) match {
            case (ii: InfoItem, Some(oii: InfoItem)) =>
              mutableHMap(ii.path) = ii.union(oii)
            case (obj: Object, Some(oo: Object)) =>
              mutableHMap(obj.path) = obj.union(oo)
            case (obj: Objects, Some(oo: Objects)) =>
              mutableHMap(obj.path) = obj.union(oo)
            case (n, on) =>
              throw new Exception(
                "Found two different types for same Path when tried to create ImmutableODF."
              )
          }
        } else {
          var toAdd = node
          while (!mutableHMap.contains(toAdd.path)) {
            mutableHMap += toAdd.path -> toAdd
            toAdd = toAdd.createParent
          }
        }
    }
    this.copy(
      ImmutableHashMap(
        mutableHMap.toVector: _*
      )
    )
  }

  override lazy val hashCode: Int = this.nodes.hashCode
}

object ImmutableODF {
  def apply(
             nodes: Iterable[Node] = Vector.empty
           ): ImmutableODF = {
    val mutableHMap: MutableHashMap[Path, Node] = MutableHashMap.empty
    val sorted = nodes.toSeq.sortBy {
      n: Node => n.path
    }(PathOrdering)
    sorted.foreach {
      node: Node =>
        if (mutableHMap.contains(node.path)) {
          (node, mutableHMap.get(node.path)) match {
            case (ii: InfoItem, Some(oii: InfoItem)) =>
              mutableHMap(ii.path) = ii.union(oii)
            case (obj: Object, Some(oo: Object)) =>
              mutableHMap(obj.path) = obj.union(oo)
            case (obj: Objects, Some(oo: Objects)) =>
              mutableHMap(obj.path) = obj.union(oo)
            case (n, on) =>
              throw new Exception(
                "Found two different types for same Path when tried to create ImmutableODF."
              )
          }
        } else {
          var toAdd = node
          while (!mutableHMap.contains(toAdd.path)) {
            mutableHMap += toAdd.path -> toAdd
            toAdd = toAdd.createParent
          }
        }
    }
    new ImmutableODF(
      ImmutableHashMap(
        mutableHMap.toVector: _*
      )
    )
  }
  def apply(single: Node): ImmutableODF = apply(Vector(single))
}
