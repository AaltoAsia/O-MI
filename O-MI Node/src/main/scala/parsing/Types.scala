package parsing
package Types
import java.sql.Timestamp
  /** case class that represents parsing error
   *  @param msg error message that describes the problem.
   */
  case class ParseError(msg: String) 

  /**
   * Path is a wrapper for Seq[String] representing path to an O-DF Object
   * It abstracts path seperators ('/') from error prone actions such as joining
   * two paths or creating new Paths from user input.
   * Path can be used as a sequence via an implicit conversion or _.toSeq
   */
  class Path(pathSeq: Seq[String]){
    import Path._
    /**
     * Removes extra path elements and holds the Path as Seq[String]
     */
    val toSeq = {
      val normalized = pathSeq.filterNot(_ == "")
      normalized.toSeq
    }

    def this(pathStr: String) = this{
      pathStr.split("/")
    }

    /**
     * Join two paths.
     * @param otherPath other path to join at the end of this one
     * @return new path with other joined to this path
     */
    def /(otherPath: Path): Path = Path(this ++ otherPath)

    def /(otherPathStr: String): Path = {
      this / Path(otherPathStr)
    }

    /**
     * Get list of ancestors from this path, e.g "/a/b/c/d" => "/a", "/a/b", "/a/b/c", "a/b/c/d"
     * Order is from oldest descending.
     */
    def getParentsAndSelf: Seq[Path] = this.inits.map(Path(_)).toList.reverse.tail

    override def equals(that: Any): Boolean = that match{
      case thatPath: Path => thatPath.toSeq.equals(this.toSeq)
      case _ => false
    }

    /**
     * Creates a path string which represents this path with '/' seperators.
     * Representation doesn't start nor end with a '/'.
     */
    override def toString: String = this.mkString("/")
  }

  /** Helper object for Path, contains implicit conversion between Path and Seq[String]
    */
  object Path {
    def apply(pathStr: String): Path = new Path(pathStr)
    def apply(pathSeq: Seq[String]): Path = new Path(pathSeq)
    val empty = new Path(Seq.empty)


    import scala.language.implicitConversions // XXX: maybe a little bit stupid place for this

    implicit def PathAsSeq(p: Path): Seq[String] = p.toSeq
    implicit def SeqAsPath(s: Seq[String]): Path = Path(s)
  }
