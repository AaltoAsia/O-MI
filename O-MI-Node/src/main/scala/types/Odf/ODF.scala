package types
package odf

import java.util.{Set => JavaSet}

import scala.collection.{ Seq, Map, SortedSet }
import scala.collection.immutable.{TreeSet => ImmutableTreeSet, HashMap => ImmutableHashMap }
import scala.collection.mutable.{TreeSet => MutableTreeSet, HashMap => MutableHashMap }
import scala.xml.NodeSeq
import parsing.xmlGen.xmlTypes.{ObjectsType, ObjectType}
import parsing.xmlGen.{odfDefaultScope, scalaxb, defaultScope}

  /** O-DF structure
   */
trait ODF//[M <: Map[Path,Node], S<: SortedSet[Path] ]
{
  /** All nodes(InfoItems and Objects) in O-DF structure.
   */
  protected[odf] def nodes : Map[Path,Node]//= HashMap.empty
  /** SortedSet of all paths in O-DF structure. 
   * Should be ordered by paths with alpabetic ordering so that after a path
   * comes all its descendant: 
   * A/, A/a, A/a/1, A/b/, A/b/1, Aa/ ..
   *
  */
  protected[odf] def paths : SortedSet[Path] //= TreeSet( nodes.keys.toSeq:_* )(PathOrdering)
  //def copy( nodes : scala.collection.Map[Path,Node] ): ODF


  def isEmpty:Boolean = paths.isEmpty || (paths.size == 1 && paths.contains(Path("Objects")))
  def nonEmpty:Boolean = !isEmpty
  def isRootOnly: Boolean = isEmpty
  def contains( path: Path ): Boolean = paths.contains(path)


  def select( that: ODF ): ODF 
  def union[TM <: Map[Path,Node], TS <: SortedSet[Path]]( that: ODF ): ODF 

  def removePaths( removedPaths: Seq[Path]) : ODF  

  def immutable: ImmutableODF
  def mutable: MutableODF

  def getPaths: Seq[Path] = paths.toVector
  def getNodes: Seq[Node] = nodes.values.toVector
  def getInfoItems: Seq[InfoItem] = nodes.values.collect{ 
    case ii: InfoItem => ii
  }.toVector
  def getObjects: Seq[Object] = nodes.values.collect{ 
    case obj: Object => obj
  }.toVector
  def nodesWithStaticData: Vector[Node] = nodes.values.filter( _.hasStaticData ).toVector
  def nodesWithAttributes: Vector[Node] = nodes.values.filter( _.attributes.nonEmpty ).toVector
  def getNodesMap: Map[Path,Node] = ImmutableHashMap(
    nodes.toVector:_*
  )
  def getChildPaths( path: Path): Seq[Path] = {
    paths.filter {
      p: Path => path.isParentOf(p)
    }.toVector
  }
  def getChilds( path: Path): Seq[Node] = {
    getChildPaths(path).flatMap { p: Path => nodes.get(p) }.toVector
  }
  def getLeafs: Vector[Node] = {
    getLeafPaths.flatMap( nodes.get(_)).toVector.sortBy(_.path)(Path.PathOrdering)
  }
  def getLeafPaths: Set[Path] = {
    val ps = paths.toSeq
    ps.filter{
      path: Path => 
        val index = ps.indexOf( path) 
        val nextIndex = index +1
        if( nextIndex < ps.size ){
          val nextPath: Path = ps(nextIndex) 
          !path.isAncestorOf( nextPath )
        } else true
    }.toSet
  }
  def pathsOfInfoItemsWithMetaData: Set[Path] ={
    nodes.values.collect{
      case ii: InfoItem if ii.metaData.nonEmpty => ii.path
    }.toSet
  }
  def infoItemsWithMetaData: Set[InfoItem] ={
    nodes.values.collect{
      case ii: InfoItem if ii.metaData.nonEmpty => ii
    }.toSet
  }
  def nodesWithDescription: Set[Node] ={
    nodes.values.collect{
      case ii: InfoItem if ii.descriptions.nonEmpty => ii
      case obj: Object if obj.descriptions.nonEmpty => obj
    }.toSet
  }
  def pathsOfNodesWithDescription: Set[Path] ={
    nodes.values.collect{
      case ii: InfoItem if ii.descriptions.nonEmpty => ii.path
      case obj: Object if obj.descriptions.nonEmpty => obj.path
    }.toSet
  }
  def objectsWithType( typeStr: String ): Vector[Object] ={
    nodes.values.collect{
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toVector
  }
  def pathsWithType( typeStr: String ): Set[Path] ={
    nodes.values.collect{
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii.path
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj.path
    }.toSet
  }
  def nodesWithType( typeStr: String ): Set[Node] ={
    nodes.values.collect{
      case ii: InfoItem if ii.typeAttribute.contains(typeStr) => ii
      case obj: Object if obj.typeAttribute.contains(typeStr) => obj
    }.toSet
  }

  def get( path: Path): Option[Node] = nodes.get(path)

  def selectSubTree( pathsToGet: Seq[Path]): ODF
  def selectUpTree( pathsToGet: Seq[Path]): ODF
  def --( removedPaths: Seq[Path] ) : ODF = removePaths( removedPaths )
  def removePath( path: Path) : ODF
  def add( node: Node ) : ODF
  def addNodes( nodesToAdd: Seq[Node] ) : ODF 

  implicit def asObjectsType : ObjectsType ={
    val firstLevelObjects= getChilds( new Path("Objects") )
    val objectTypes= firstLevelObjects.map{
      case obj: Object => 
        createObjectType( obj )
    }
    nodes.get(new Path("Objects")).collect{
      case objs: Objects =>
        objs.asObjectsType(objectTypes) 
    }.getOrElse{
      Objects().asObjectsType(objectTypes)
    }
  }

  def update[TM <: Map[Path,Node], TS <: SortedSet[Path]]( that: ODF ): ODF
  def valuesRemoved: ODF
  def descriptionsRemoved: ODF
  def metaDatasRemoved: ODF

  def createObjectType( obj: Object ): ObjectType ={
    val (objects, infoItems ) = getChilds( obj.path ).partition{
      case obj: Object => true
      case ii: InfoItem => false
    }
    obj.asObjectType(
      infoItems.collect{
        case ii: InfoItem => ii.asInfoItemType 
      },
      objects.collect{
        case obj: Object =>
          createObjectType( obj ) 
      }
    )
  }
  implicit def asXML : NodeSeq= {
    val xml  = scalaxb.toXML[ObjectsType](asObjectsType, None, Some("Objects"), odfDefaultScope)
    xml//.asInstanceOf[Elem] % new UnprefixedAttribute("xmlns","odf.xsd", Node.NoAttributes)
  }
  override def toString: String = {
    "ODF{\n" +
    nodes.map{
      case (p, node) => 
        s"$p --> $node" 
    }.mkString("\n") + "\n}"
  }
  override def equals( that: Any ) : Boolean ={
    that match{
      case another: ODF =>
        (paths equals another.paths) && (nodes equals another.nodes)
      case a: Any => 
        false
    }
  }
  override lazy val hashCode: Int = this.nodes.hashCode
  def readTo(to: ODF) : ODF
  def readToNodes(to: ODF) : Seq[Node] ={
    val wantedPaths: SortedSet[Path] = this.paths.filter{
      path: Path =>
        to.paths.contains(path) ||
        to.getLeafPaths.exists{
          requestedPath: Path =>
            requestedPath.isAncestorOf(path)  
        }
    }
    val wantedNodes: Seq[Node] = wantedPaths.toSeq.map{
      path: Path =>
        (nodes.get(path),to.nodes.get(path)) match{
          case (None,_) => throw new Exception("Existing path does not map to node.")
          case (Some(obj: Object),None) => 
            obj.copy(
              descriptions = {if( obj.descriptions.nonEmpty ) Set(Description("")) else Set.empty}
            )
          case (Some(ii: InfoItem),None) => 
            ii.copy(
              names = {if( ii.names.nonEmpty ) Vector(QlmID("")) else Vector.empty},
              descriptions = {if( ii.descriptions.nonEmpty ) Set(Description("")) else Set.empty},
              metaData ={ if( ii.metaData.nonEmpty ) Some( MetaData.empty) else None}
            )
          case (Some(obj:Object),Some(toObj:Object)) => obj.readTo(toObj)
          case (Some(ii:InfoItem),Some(toIi:InfoItem)) => ii.readTo(toIi)
          case (Some(obj:Objects),Some(toObj:Objects)) => obj.readTo(toObj)
          case (Some(f), Some(t)) => throw new Exception("Missmatching types in ODF when reading.")
        }
    }
    wantedNodes
  }
}

object ODF{
  /*
  def apply[M <: scala.collection.Map[Path,Node], S<: scala.collection.SortedSet[Path] ]( 
    nodes: M
  ) : ODF ={
    nodes match {
      case mutable: 
    }
  }*/
}
