package types
package odf

import scala.collection.{ Seq, Map, SortedSet }
import scala.collection.immutable.{TreeSet => ImmutableTreeSet, HashMap => ImmutableHashMap }
import scala.collection.mutable.{TreeSet => MutableTreeSet, HashMap => MutableHashMap }
import scala.xml.NodeSeq
import parsing.xmlGen.xmlTypes.{ObjectsType, ObjectType}
import parsing.xmlGen.{odfDefaultScope, scalaxb, defaultScope}
import types.Path._

class MutableODF private[odf](
  protected[odf] val nodes: MutableHashMap[Path,Node] = MutableHashMap.empty
) extends ODF{//[MutableHashMap[Path,Node],MutableTreeSet[Path]] {
  type M = MutableHashMap[Path,Node]
  type S = MutableTreeSet[Path]
  protected[odf] val paths: MutableTreeSet[Path] = MutableTreeSet( nodes.keys.toSeq:_* )(PathOrdering)
  def isEmpty:Boolean = paths.size == 1 && paths.contains(Path("Objects"))
  def nonEmpty:Boolean = paths.size > 1 
  def update[TM <: Map[Path,Node], TS <: SortedSet[Path]]( that: ODF ): ODF ={
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

  def select( that: ODF ): ODF ={
    MutableODF(
    paths.filter{
      path: Path => 
        that.paths.contains( path ) || that.getLeafPaths.exists{
          ancestorPath: Path => 
            ancestorPath.isAncestorOf( path) 
        } 
    }.flatMap{
      path: Path => 
        this.nodes.get( path )
    }.toVector )
  } 

  def cutOut( cutPaths: Set[Path] ): ODF ={
  
    //Remove intersecting paths.
    //Generate ancestors for remaining paths, so that no parentless paths remains. 
    //Remove duplicates
    val newPaths = (paths -- cutPaths).flatMap {
      path: Path => path.getAncestorsAndSelf
    }.toSet
    val removedPaths = cutPaths -- newPaths
    this.nodes --= removedPaths
    this.paths --= removedPaths
    this
  }
  def cutOut[TM <: Map[Path,Node], TS <: SortedSet[Path]]( that: ODF ): ODF ={
    cutOut( that.paths.toSet )
  }

  def getUpTreeAsODF( pathsToGet: Seq[Path]): ODF = {
    MutableODF(
      getUpTree( pathsToGet )
    )
  }
  def getTree( selectingPaths: Seq[Path] ) : ODF ={
    val ancestorsPaths = selectingPaths.flatMap{ p => p.getAncestors }.toSet
    val subTreePaths = getSubTreePaths( selectingPaths ).toSet
    val nodesSelected = (subTreePaths ++ ancestorsPaths).flatMap{
      path => nodes.get( path )
    }
    MutableODF( nodesSelected.toSeq )
  }
  def union[TM <: Map[Path,Node], TS <: SortedSet[Path]]( that: ODF ): ODF = {
    val pathIntersection: SortedSet[Path] = this.paths.intersect( that.paths)
    val thatOnlyNodes: Set[Node] = (that.paths -- pathIntersection ).flatMap {
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
                println(s"ERROR:\n $n is not same type as $on.\n")
                throw new Exception(
                  "Found two different types in same Path when tried to create union."
                )
            }
          case (t, o) => t.orElse(o)
        }
    }.toSet
    val allPaths = paths ++ that.paths
    val allNodes = thatOnlyNodes ++ intersectingNodes
    this.nodes ++= allNodes.map{ node => node.path -> node }
    this
  }
  def removePaths( removedPaths: Iterable[Path]) : ODF = {
    val subtrees = removedPaths.flatMap( getSubTreePaths( _ ) )
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

  def valuesRemoved : ODF ={
    this.nodes.mapValues{
      case ii: InfoItem => ii.copy( values = Vector() )
      case obj: Object => obj 
      case obj: Objects => obj
    }
    this
  }
  def descriptionsRemoved: ODF = {
    this.nodes.mapValues{
      case ii: InfoItem => ii.copy( descriptions = Vector() )
      case obj: Object => obj.copy( descriptions = Vector() )
      case obj: Objects => obj
    }
    this
  }
  def metaDatasRemoved: ODF ={
    this.nodes.mapValues{
      case ii: InfoItem => ii.copy( metaData = None )
      case obj: Object => obj
      case obj: Objects => obj
    }
  this
  }
  def attributesRemoved: ODF={
    this.nodes.mapValues{
      case ii: InfoItem => ii.copy( typeAttribute = None, attributes = ImmutableHashMap() )
      case obj: Object => obj.copy( typeAttribute = None, attributes = ImmutableHashMap() )
      case obj: Objects => obj.copy( attributes = ImmutableHashMap() )
    }
  this
  }
  
  def removePath( path: Path) : ODF ={
    val subtreeP = getSubTreePaths( path )
    this.nodes --= subtreeP
    this.paths --= subtreeP
    this
  }


  def add( node: Node) : ODF ={
    if( !nodes.contains( node.path ) ){
      nodes( node.path) = node
      paths += node.path
      if( !nodes.contains(node.path.init) ){
        this.add( node.createParent )
      }
    } else {
      (nodes.get(node.path), node ) match{
        case (Some(old:Object), obj: Object ) =>
          nodes( node.path) = old.union(obj)
        case (Some(old:Objects), objs: Objects ) =>
          nodes( node.path) = old.union(objs)
        case (Some(old:InfoItem), iI: InfoItem ) => 
          nodes( node.path) = old.union(iI)
        case (old, n ) => 
          throw new Exception(
            "Found two different types in same Path when tried to add a new node" 
          )
      }
    }
    this
  }
  
  def addNodes( nodesToAdd: Seq[Node] ) : ODF ={
    nodesToAdd.foreach {
      node: Node =>
        this.add(node)
    }
    this
  }
  def getSubTreeAsODF( path: Path): ODF = {
    val subtree: Seq[Node] = getSubTree( path)
    val ancestors: Seq[Node] = path.getAncestors.flatMap {
      ap: Path =>
        nodes.get(ap)
    }
    MutableODF(
        (subtree ++ ancestors).toVector
    )
  }
  def intersection[TM <: Map[Path,Node], TS <: SortedSet[Path]]( that: ODF ) : ODF={
    val iPaths = this.intersectingPaths(that)
    MutableODF(
      iPaths.map {
        path: Path =>
          (nodes.get(path), that.nodes.get(path)) match {
            case (None, _) => throw new Exception(s"Not found element in intersecting path $path")
            case (_, None) => throw new Exception(s"Not found element in intersecting path $path")
            case (Some(ii: InfoItem), Some(oii: InfoItem)) => ii union oii
            case (Some(obj: Object), Some(oObj: Object)) => obj union oObj
            case (Some(objs: Objects), Some(oObjs: Objects)) => objs union oObjs
            case (Some(l), Some(r)) => throw new Exception(s"Found nodes with different types in intersecting path $path")
          }
      }.toVector
    )
  
  }
  def getSubTreeAsODF( pathsToGet: Seq[Path]): ODF = {
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

  override def equals( that: Any ) : Boolean ={
    that match{
      case another: ODF =>
        //println( s"Path equals: ${paths equals another.paths}\n Nodes equals:${nodes equals another.nodes}" )
        (paths equals another.paths) && (nodes equals another.nodes)
      case _: Any =>
        //println( s" Comparing ODF with something: $a")
        false
    }
  }
  override lazy val hashCode: Int = this.nodes.hashCode
}

object MutableODF{
  def apply(
      _nodes: Iterable[Node]  = Vector.empty
  ) : MutableODF ={
    val mutableHMap : MutableHashMap[Path,Node] = MutableHashMap.empty
    val sorted = _nodes.toSeq.sortBy{ 
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
