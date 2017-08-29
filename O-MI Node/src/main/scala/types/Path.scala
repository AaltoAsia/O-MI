
package types
import scala.collection.JavaConversions
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success, Try}
import OmiTypes.ResponseRequest
  /**
   * Path is a wrapper for Seq[String] representing path to an O-DF Object
   * It abstracts path seperators ('/') from error prone actions such as joining
   * two paths or creating new Paths from user input.
   * Path can be used as a sequence via an implicit conversion or _.toSeq
   */
  @SerialVersionUID(-6357227883745065036L)  
  class Path(val pathSeq: Vector[String]) extends Serializable { // TODO: test the Serializable
    import Path._
    /**
     * Removes extra path elements and holds the Path as Seq[String]
     */
    val toSeq: Vector[String] = {
      val normalized = pathSeq.filterNot(_ == "")
      normalized.toVector // make sure that it is Vector, hashcode problems with Seq (Array?)
    }

    /**
     * Removes extra path elements and holds the Path as Seq[String]
     */
    val toArray: Array[String] = {
      val normalized = pathSeq.filterNot(_ == "")
      normalized.toArray // make sure that it is Vector, hashcode problems with Seq (Array?)
    }

    def this(pathStr: String) = this{
      pathStr.split("/").toVector.filterNot( _ == "")
    }

    /**
     * Join two paths.
     * @param otherPath other path to join at the end of this one
     * @return new path with other joined to this path
     */
    def /(otherPath: Path): Path = Path(this.pathSeq ++ otherPath.pathSeq)

    /**
     * Add new id/name to end of paths
     * @param idStr String of id or name to be added to end of Path.
     * @return new path with added string at end.
     */
    @deprecated("Use case is ambiguos. Joining with another string or adding new element. Use with other Path or odf.QlmID instead.", "0.9.2") 
    def /(idStr: String): Path = {
      Path(this.pathSeq ++ Seq(idStr.replace("/","\\/")))
    }

    /**
     * Add new id/name to end of paths
     * @param id QlmID to be added to end of Path.
     * @return new path with added id at end.
     */
    def /( id: types.odf.QlmID): Path = {
      Path(this.pathSeq ++ Seq(id.id.replace("/","\\/")))
    }

    /**
     * Get list of ancestors from this path, e.g "/a/b/c/d" => "/a", "/a/b", "/a/b/c", "a/b/c/d"
     * Order is from oldest descending.
     */
    def getParentsAndSelf: Seq[Path] = this.inits.map(Path(_)).toList.reverse.tail

    override def equals(that: Any): Boolean = that match{
      case thatPath:Path => thatPath.toSeq.equals(this.toSeq)
      case _ => false
    }
    override lazy val hashCode: Int = this.toSeq.hashCode

    /**
     * Creates a path string which represents this path with '/' separators.
     * Representation doesn't start nor end with a '/'.
     */
    override def toString: String = this.mkString("/")
    
  def isAncestorOf( that: Path): Boolean ={
    if( length < that.length ){
      that.pathSeq.startsWith(pathSeq) 
    } else false
  }
  def isDescendantOf( that: Path): Boolean ={
    if( length > that.length ){
      pathSeq.startsWith(that.pathSeq) 
    } else false
  }
  def isChildOf( that: Path ) : Boolean ={
    that.length + 1 == length && pathSeq.startsWith( that.pathSeq )
  }
  def isParentOf( that: Path ) : Boolean ={
    length + 1 == that.length && that.pathSeq.startsWith( pathSeq )
  }
  def nonEmpty: Boolean = pathSeq.nonEmpty
  def isEmpty: Boolean = pathSeq.isEmpty
    def ancestorsAndSelf: Seq[Path] = pathSeq.inits.map( Path(_)).toSeq
    def ancestors: Seq[Path] = ancestorsAndSelf.tail
    def length: Int = pathSeq.length
  def getAncestorsAndSelf: Seq[Path] = pathSeq.inits.map( Path(_) ).filter( _.nonEmpty ).toVector ++ Vector(this)
  def getAncestors: Seq[Path] = pathSeq.inits.map( Path(_) ).filter( _.nonEmpty ).toVector
  def getParent: Path = Path(pathSeq.init)
  }

  /** Helper object for Path, contains implicit conversion between Path and Seq[String]
    */
  object Path {
    def apply(pathStr: String): Path = new Path(pathStr)
    def apply(pathSeq: Seq[String]): Path = new Path(pathSeq.toVector)
    val empty = new Path(Vector.empty)

    object PathOrdering extends scala.math.Ordering[Path] {
      def compare( l: Path, r: Path) : Int ={
        l.toString compare r.toString
      }
    }
    import scala.language.implicitConversions // XXX: maybe a little bit stupid place for this

    implicit def PathAsSeq(p: Path): Vector[String] = p.toSeq
    implicit def SeqAsPath(s: Seq[String]): Path = Path(s.toVector)
  }
