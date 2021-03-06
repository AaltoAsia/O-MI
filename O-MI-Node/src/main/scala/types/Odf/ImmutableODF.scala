package types
package odf

import types.Path._

import scala.collection.immutable.{HashMap, TreeSet}
import scala.collection.mutable
import scala.collection.{Map}

case class ImmutableODF private[odf](
                                      nodes: HashMap[Path, Node],
                                      protected[odf] val paths: TreeSet[Path]
                                    ) extends ODF {

  override def getPaths = paths


  /** Assume sorted nodes
    */
  protected[odf] def this(sortedNodes: HashMap[Path, Node]) =
    this(sortedNodes, TreeSet(sortedNodes.keys.toSeq: _*)(PathOrdering))

  @deprecated("use nodesMap instead (bad naming)", "1.0.8")
  override val getNodesMap: HashMap[Path, Node] = nodes

  override val nodesMap: HashMap[Path, Node] = nodes

  /*
   * Select exactly the paths in that ODF from this ODF.
   */
  def select(that: ODF): ImmutableODF = {
    val leafs =  that.getLeafPaths
    ImmutableODF(
      paths.filter {
        path: Path =>
          that.paths.contains(path) || leafs.exists {
            ancestorPath: Path =>
              ancestorPath.isAncestorOf(path)
          }
      }.flatMap{
        path: Path =>
          this.nodes.get(path)
      }.toVector)
  }

  def readTo(to: ODF, generationDifference: Option[Int] = None): ImmutableODF = {
    //val timer = LapTimer(println)
    val leafs = to.getLeafPaths
    //timer.step("leafs")
    val sstp =selectSubTreePaths(leafs, generationDifference)
    //timer.step("sstp")
    val intersect = sstp.intersect(paths)
    //timer.step("intersect")
    val wantedPaths: TreeSet[Path] = intersect match {
      case its: TreeSet[Path] =>
        its.ordering match{
          case op: PathOrdering.type => its
          case el: Ordering[Path] =>
            val vec = intersect.toSeq
            //timer.step("to seq,wrong ordering")
            TreeSet(vec:_*)(PathOrdering)
        }
    }
    //timer.step("wanted paths")
    val wantedNodes = wantedPaths.view.map {
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
    //timer.step("wanted nodes")
    val nodesmap = HashMap(
      wantedNodes.map{
      case node: Node =>
        node.path -> node
    }.toSeq: _*)
    //timer.step("nodes map")
    //timer.total()
    new ImmutableODF(nodesmap, wantedPaths)
  }
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


  lazy val typeIndex: Map[Option[String], Iterable[Node]] = nodes.values.groupBy{
    case obj: Objects => None
    case ii: InfoItem => ii.typeAttribute
    case obj: Object => obj.typeAttribute
  }.mapValues(_.toSeq)



  /** Merge two ODF trees together.
    */
  def union(that: ODF): ImmutableODF = {

    val newNodes = nodes.merged(that.nodesMap){
      case ((path, node), (_, otherNode)) =>
        (node, otherNode) match {
          case (ii: InfoItem, oii: InfoItem) =>
            (path, ii.union(oii))
          case (obj: Object, oo: Object) =>
            (path, obj.union(oo))
          case (obj: Objects, oo: Objects) =>
            (path, obj.union(oo))
          case (_, _) =>
            throw new Exception("Found two different elements in the same Path when tried to create union of O-DF trees.")
        }
    }
    val newPaths = paths ++ that.paths

    new ImmutableODF(newNodes, newPaths)
  }

  def removePaths(pathsToRemove: Set[Path]): ImmutableODF = {
    val subTrees = subTreePaths(pathsToRemove)
    this.copy(nodes -- subTrees, paths -- subTrees)
  }

  def removePath(path: Path): ImmutableODF = {
    val subtreeP = subTreePaths(Set(path))
    this.copy(nodes -- subtreeP, paths -- subtreeP)
  }

  def add(node: Node): ImmutableODF = {

    val newNodes: HashMap[Path, Node] = if (nodes.contains(node.path)) {
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
      val mutableHMap: mutable.HashMap[Path, Node] = mutable.HashMap(nodes.toVector: _*)
      var toAdd = node
      while (!mutableHMap.contains(toAdd.path)) {
        mutableHMap += toAdd.path -> toAdd
        toAdd = toAdd.createParent
      }
      HashMap(mutableHMap.toVector: _*)
    }
    new ImmutableODF(newNodes)
  }

  /*
   * Select paths and their ancestors from this ODF.
   */
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

  /*
   * Select paths and their descedants from this ODF.
   */
  def selectSubTree(pathsToGet: Set[Path], generationDifference: Option[Int] = None): ImmutableODF = {
    val ps = selectSubTreePaths(pathsToGet,generationDifference)
    ImmutableODF(
      ps.flatMap {
        path: Path =>
          nodes.get(path)
      }.toVector
    )
  }

  def valuesRemoved: ImmutableODF = new ImmutableODF(HashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(values = Vector())
    case obj: Object => obj
    case obj: Objects => obj
  }.toVector: _*))

  def descriptionsRemoved: ImmutableODF = new ImmutableODF(HashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(descriptions = Set.empty)
    case obj: Object => obj.copy(descriptions = Set.empty)
    case obj: Objects => obj
  }.toVector: _*))

  def metaDatasRemoved: ImmutableODF = new ImmutableODF(HashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(metaData = None)
    case obj: Object => obj
    case obj: Objects => obj
  }.toVector: _*))

  def attributesRemoved: ImmutableODF = new ImmutableODF(HashMap(nodes.mapValues {
    case ii: InfoItem => ii.copy(typeAttribute = None, attributes = HashMap())
    case obj: Object => obj.copy(typeAttribute = None, attributes = HashMap())
    case obj: Objects => obj.copy( attributes = HashMap())
  }.toVector: _*))

  def replaceValues( pathToValues: Iterable[(Path,Iterable[Value[_]])]): ImmutableODF={
    val updates = pathToValues.flatMap{
      case ( path: Path, values: Iterable[Value[_]] ) =>
        nodes.get(path).collect{
          case ii: InfoItem =>
            path -> ii.copy( values = values.toVector)
        }
    }
    new ImmutableODF(nodes ++ updates)
  }
  def toImmutable: ImmutableODF = this

  def toMutable: MutableODF = MutableODF(
    nodes.values.toVector
  )

  def addNodes(nodesToAdd: Iterable[Node]): ImmutableODF = {
    val mutableHMap: mutable.HashMap[Path, Node] = mutable.HashMap(nodes.toVector: _*)
    val sorted = nodesToAdd.toSeq.sortBy(_.path)(PathOrdering)
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
      HashMap(
        mutableHMap.toVector: _*
      )
    )
  }

  override lazy val hashCode: Int = this.nodes.hashCode
}

object ImmutableODF {

  /** Warning this constructor might be very slow on large trees!
    *
    *  Constructs ImmutableODF from O-DF Nodes, which involves sorting of
    *  Paths, automatic duplicate handling and automatic parent creation.
    */
  def apply(
             nodes: Iterable[Node] = Vector.empty
           ): ImmutableODF = {
    val mutableHMap: mutable.HashMap[Path, Node] = mutable.HashMap.empty
    //val timer = LapTimer(println)
    val sorted = nodes.toSeq.sortBy {
      n: Node => n.path
    }(PathOrdering)
    //timer.step(s"IODF ${nodes.size} paths sorted")
    sorted.foreach {
      node: Node =>
        if (mutableHMap.contains(node.path)) {
          (node, mutableHMap.get(node.path)) match {
            case (ii: InfoItem, Some(oii: InfoItem)) =>
              val nii = ii.union(oii)
              mutableHMap.update(nii.path,nii.copy(values = nii.values.sortBy(_.timestamp.getTime)))
            case (obj: Object, Some(oo: Object)) =>
              mutableHMap.update(obj.path,obj.union(oo))
            case (obj: Objects, Some(oo: Objects)) =>
              mutableHMap.update(obj.path,obj.union(oo))
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
    //timer.step("IODF populated MMap")
    new ImmutableODF(
      HashMap(
        mutableHMap.toVector: _*
      )
    )
  }
  def createFromNodes(nodes:Iterable[Node]):ImmutableODF = {
    new ImmutableODF(HashMap(nodes.map(node => node.path -> node).toSeq:_*))
  }
  /** Unoptimized, but easy to use constructor for single O-DF Node.
    *
    *  Automatically creates parents.
    */
  def apply(single: Node): ImmutableODF = apply(Vector(single))
}
