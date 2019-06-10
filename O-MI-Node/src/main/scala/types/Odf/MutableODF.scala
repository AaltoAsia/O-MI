package types
package odf

import types.Path._

import scala.collection.immutable.{HashMap => ImmutableHashMap}
import scala.collection.mutable.{HashMap => MutableHashMap, TreeSet => MutableTreeSet}
import scala.collection.{Seq, SortedSet}

class MutableODF private[odf](
                               protected[odf] val nodes: MutableHashMap[Path, Node] = MutableHashMap.empty
                             ) extends ODF {
  protected[odf] val paths: MutableTreeSet[Path] = MutableTreeSet(nodes.keys.toSeq: _*)(PathOrdering)

  def readTo(to: ODF): MutableODF = MutableODF(readToNodes(to))

  def update(that: ODF): MutableODF = {
    nodes.valuesIterator.foreach {
      node: Node =>
        (node, that.get(node.path)) match {
          case (ii: InfoItem, Some(iiu: InfoItem)) => nodes.update(node.path,ii.update(iiu))
          case (obj: Object, Some(ou: Object)) =>  nodes.update(node.path,obj.update(ou))
          case (objs: Objects, Some(objsu: Objects)) =>  nodes.update(node.path,objs.update(objsu))
          case (n, None) => 
          case (n, Some(a)) => throw new Exception("Missmatching types in ODF when updating.")
        }
    }
    this
  }

  /*
   * Select exactly the paths in that ODF from this ODF.
   */
  def select(that: ODF): MutableODF = {
    val leafs =  that.getLeafPaths
    MutableODF(
      paths.filter {
        path: Path =>
          that.paths.contains(path) ||  leafs.exists {
            ancestorPath: Path =>
              ancestorPath.isAncestorOf(path)
          }
      }.flatMap {
        path: Path =>
          this.nodes.get(path)
      }.toVector)
  }


  /*
   * Select paths and their ancestors from this ODF.
   */
  def selectUpTree(pathsToGet: Set[Path]): MutableODF = {
    MutableODF(
      paths.filter {
        ancestorPath: Path =>
          pathsToGet.contains(ancestorPath) ||
          pathsToGet.exists {
            path: Path =>
                ancestorPath.isAncestorOf(path)
          }
      }.flatMap {
        path: Path =>
          nodes.get(path)
      }.toVector
    )
  }

  def union(that: ODF): MutableODF = {
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


  def immutable: ImmutableODF = ImmutableODF(
    this.nodes.values.toVector
  )

  //Should be this? or create a copy?
  def mutable: MutableODF = this 

  def valuesRemoved: MutableODF = {
    this.nodes.valuesIterator.foreach {
      case ii: InfoItem => nodes.update(ii.path,ii.copy(values = Vector()))
      case node: Node =>
    }
    this
  }

  def descriptionsRemoved: MutableODF = {
    this.nodes.valuesIterator.foreach {
      case ii: InfoItem => nodes.update(ii.path,ii.copy(descriptions = Set.empty))
      case obj: Object =>  nodes.update(obj.path,obj.copy(descriptions = Set.empty))
      case obj: Objects => 
    }
    this
  }

  def metaDatasRemoved: MutableODF = {
    this.nodes.valuesIterator.foreach {
      case ii: InfoItem => nodes.update(ii.path,ii.copy(metaData = None))
      case obj: Object => 
      case obj: Objects => 
    }
    this
  }

  def attributesRemoved: MutableODF = {
    this.nodes.valuesIterator.foreach {
      case ii: InfoItem => nodes.update(ii.path,ii.copy(typeAttribute = None, attributes = ImmutableHashMap()))
      case obj: Object => nodes.update(obj.path,obj.copy(typeAttribute = None, attributes = ImmutableHashMap()))
      case obj: Objects => nodes.update(obj.path,obj.copy(attributes = ImmutableHashMap()))
    }
    this
  }

  def removePath(path: Path): MutableODF = this.removePaths(Set(path))
  
  def removePaths(pathsToRemove: Set[Path]): MutableODF ={
    val subtreeP = this.subTreePaths(pathsToRemove).toSet
    this.nodes --= subtreeP
    this.paths --= subtreeP
    this
  }


  def add(node: Node): MutableODF = {
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

  def addNodes(nodesToAdd: Seq[Node]): MutableODF = {
    nodesToAdd.foreach {
      node: Node =>
        this.add(node)
    }
    this
  }

  /*
   * Select paths and their descedants from this ODF.
   */
  def selectSubTree(pathsToGet: Set[Path]): MutableODF = {
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
