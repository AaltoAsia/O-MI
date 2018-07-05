package types
package odf

import scala.collection.{ Seq, Map, SortedSet }
import scala.collection.immutable.{ HashMap => ImmutableHashMap }
import scala.collection.mutable.{TreeSet => MutableTreeSet, HashMap => MutableHashMap }
import types.Path._

class MutableODF private[odf](
                               protected[odf] val nodes: MutableHashMap[Path, Node] = MutableHashMap.empty
                             ) extends ODF {
  //[MutableHashMap[Path,Node],MutableTreeSet[Path]] {
  type M = MutableHashMap[Path, Node]
  type S = MutableTreeSet[Path]
  protected[odf] val paths: MutableTreeSet[Path] = MutableTreeSet(nodes.keys.toSeq: _*)(PathOrdering)

  def readTo(to: ODF): ODF = MutableODF(readToNodes(to))

  def update[TM <: Map[Path, Node], TS <: SortedSet[Path]](that: ODF): ODF = {
    nodes.mapValues {
      node: Node =>
        (node, that.get(node.path)) match {
          case (ii: InfoItem, Some(iiu: InfoItem)) => ii.update(iiu)
          case (obj: Object, Some(ou: Object)) => obj.update(ou)
          case (objs: Objects, Some(objsu: Objects)) => objs.update(objsu)
          case (n, None) => n
          case (n, Some(a)) => throw new Exception("Missmatching types in ODF when updating.")
        }
    }
    this
  }

  def select(that: ODF): ODF = {
    MutableODF(
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


  def selectUpTree(pathsToGet: Seq[Path]): ODF = {
    MutableODF(
      paths.filter {
        ancestorPath: Path =>
          pathsToGet.exists {
            path: Path =>
              path == ancestorPath ||
                ancestorPath.isAncestorOf(path)
          }
      }.flatMap {
        path: Path =>
          nodes.get(path)
      }.toVector
    )
  }

  def union[TM <: Map[Path, Node], TS <: SortedSet[Path]](that: ODF): ODF = {
    val pathIntersection: SortedSet[Path] = this.paths.intersect(that.paths)
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
              case (n, on) =>
                throw new Exception(
                  "Found two different types in same Path when tried to create union."
                )
            }
          case (t, o) => t.orElse(o)
        }
    }.toSet
    val allNodes = thatOnlyNodes ++ intersectingNodes
    this.nodes ++= allNodes.map { node => node.path -> node }
    this
  }

  def removePaths(removedPaths: Seq[Path]): ODF = {
    val subtrees = paths.filter {
      p =>
        removedPaths.contains(p) ||
          removedPaths.exists(_.isAncestorOf(p))
    }.toSet
    this.nodes --= subtrees
    this.paths --= subtrees
    this
  }

  def immutable: ImmutableODF = ImmutableODF(
    this.nodes.values.toVector
  )

  //Should be this? or create a copy?
  def mutable: MutableODF = MutableODF(
    this.nodes.values.toVector
  )

  def valuesRemoved: ODF = {
    this.nodes.mapValues {
      case ii: InfoItem => ii.copy(values = Vector())
      case obj: Object => obj
      case obj: Objects => obj
    }
    this
  }

  def descriptionsRemoved: ODF = {
    this.nodes.mapValues {
      case ii: InfoItem => ii.copy(descriptions = Set.empty)
      case obj: Object => obj.copy(descriptions = Set.empty)
      case obj: Objects => obj
    }
    this
  }

  def metaDatasRemoved: ODF = {
    this.nodes.mapValues {
      case ii: InfoItem => ii.copy(metaData = None)
      case obj: Object => obj
      case obj: Objects => obj
    }
    this
  }

  def attributesRemoved: ODF = {
    this.nodes.mapValues {
      case ii: InfoItem => ii.copy(typeAttribute = None, attributes = ImmutableHashMap())
      case obj: Object => obj.copy(typeAttribute = None, attributes = ImmutableHashMap())
      case obj: Objects => obj.copy(attributes = ImmutableHashMap())
    }
    this
  }

  def removePath(path: Path): ODF = {
    val subtreeP = paths.filter {
      p =>
        path.isAncestorOf(p) || p == path
    }.toSet
    this.nodes --= subtreeP
    this.paths --= subtreeP
    this
  }


  def add(node: Node): ODF = {
    if (!nodes.contains(node.path)) {
      nodes(node.path) = node
      paths += node.path
      if (!nodes.contains(node.path.init)) {
        this.add(node.createParent)
      }
    } else {
      (nodes.get(node.path), node) match {
        case (Some(old: Object), obj: Object) =>
          nodes(node.path) = old.union(obj)
        case (Some(old: Objects), objs: Objects) =>
          nodes(node.path) = old.union(objs)
        case (Some(old: InfoItem), iI: InfoItem) =>
          nodes(node.path) = old.union(iI)
        case (old, n) =>
          throw new Exception(
            "Found two different types in same Path when tried to add a new node"
          )
      }
    }
    this
  }

  def addNodes(nodesToAdd: Seq[Node]): ODF = {
    nodesToAdd.foreach {
      node: Node =>
        this.add(node)
    }
    this
  }

  def selectSubTree(pathsToGet: Seq[Path]): ODF = {
    MutableODF(
      nodes.values.filter {
        node: Node =>
          pathsToGet.exists {
            filter: Path =>
              filter.isAncestorOf(node.path) || filter == node.path
          }
      }.toVector
    )
  }

  override def equals(that: Any): Boolean = {
    that match {
      case another: ODF =>
        (paths equals another.paths) && (nodes equals another.nodes)
      case _: Any =>
        false
    }
  }

  override lazy val hashCode: Int = this.nodes.hashCode
}

object MutableODF {
  def apply(
             _nodes: Iterable[Node] = Vector.empty
           ): MutableODF = {
    val mutableHMap: MutableHashMap[Path, Node] = MutableHashMap.empty
    val sorted = _nodes.toSeq.sortBy {
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
    new MutableODF(
      mutableHMap
    )
  }
}
