/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package types

import scala.util.matching.Regex

/**
  * Path is a wrapper for Seq[String] representing path to an O-DF Object
  * It abstracts path seperators ('/') from error prone actions such as joining
  * two paths or creating new Paths from user input.
  * Path can be used as a sequence via an implicit conversion or _.toSeq
  */
@SerialVersionUID(-6357227883745065036L)
class Path private(pathSeq: Vector[String]) extends Serializable { // TODO: test the Serializable
  import Path._

  /**
    * Removes extra path elements and holds the Path as Seq[String]
    */
  val toSeq: Vector[String] = {
    val normalized = pathSeq.filterNot(_ == "")
    normalized // make sure that it is Vector, hashcode problems with Seq (Array?)
  }

  /**
    * Removes extra path elements and holds the Path as Seq[String]
    */
  def toArray: Array[String] = {
    val normalized = toSeq.filterNot(_ == "")
    normalized.toArray // make sure that it is Vector, hashcode problems with Seq (Array?)
  }

  def this(pathStr: String) = this {
    (new Regex("([^\\\\/]|\\\\.)+")).findAllIn(pathStr).toVector.map(_.replaceAll("\\\\/", "/")).filterNot(_ == "")
  }

  def this(path: Path) = this {
    path.toSeq
  }

  def this(seq: Seq[String]) = this {
    seq.filterNot(_ == "").toVector
  }

  /**
    * Join two paths.
    *
    * @param otherPath other path to join at the end of this one
    * @return new path with other joined to this path
    */
  def /(otherPath: Path): Path = Path(this.toSeq ++ otherPath.toSeq)

  /**
    * Add new id/name to end of paths
    *
    * @param idStr String of id or name to be added to end of Path.
    * @return new path with added string at end.
    */
  def /(idStr: String): Path = {
    Path(this.toSeq ++ Seq(idStr))
  }

  /**
    * Add new id/name to end of paths
    *
    * @param id QlmID to be added to end of Path.
    * @return new path with added id at end.
    */
  def /(id: odf.QlmID): Path = {
    Path(this.toSeq ++ Seq(id.id))
  }


  def append(str: String): Path = this / str

  //def append(id: QlmID ): Path = this / id

  /**
    * Get list of ancestors from this path, e.g "/a/b/c/d" => "/a", "/a/b", "/a/b/c", "a/b/c/d"
    * Order is from oldest descending.
    */
  def getParentsAndSelf: Seq[Path] = this.inits.map(Path(_)).toList.reverse.tail

  override def equals(that: Any): Boolean = that match {
    case thatPath: Path => thatPath.toSeq.equals(this.toSeq)
    case _ => false
  }

  override lazy val hashCode: Int = this.toSeq.hashCode

  /**
    * Creates a path string which represents this path with '/' separators.
    * Representation doesn't start nor end with a '/'.
    * !!DO NOT MODIFY, THIS IS SERIALIZABLE!!
    */
  override def toString: String = this.toSeq.map(pp => pp.replaceAll("/","""\\/""")).mkString("/")

  def isAncestorOf(that: Path): Boolean = {
    if (length < that.length) {
      that.toSeq.startsWith(toSeq)
    } else false
  }

  def isDescendantOf(that: Path): Boolean = {
    if (length > that.length) {
      toSeq.startsWith(that.toSeq)
    } else false
  }

  def isChildOf(that: Path): Boolean = {
    that.length + 1 == length && toSeq.startsWith(that.toSeq)
  }

  def isParentOf(that: Path): Boolean = {
    length + 1 == that.length && that.toSeq.startsWith(toSeq)
  }

  def nonEmpty: Boolean = toSeq.nonEmpty

  def isEmpty: Boolean = toSeq.isEmpty

  def ancestorsAndSelf: Seq[Path] = toSeq.inits.map(Path(_)).filter { p => p.toSeq.nonEmpty }.toSeq

  def ancestors: Seq[Path] = ancestorsAndSelf.tail

  def length: Int = toSeq.length

  def getAncestorsAndSelf: Seq[Path] = toSeq.inits.map(Path(_)).filter(_.nonEmpty).toVector

  def getAncestors: Seq[Path] = toSeq.inits.drop(1).map(Path(_)).filter(_.nonEmpty).toVector

  def getParent: Path = Path(toSeq.init)
}

/** Helper object for Path, contains implicit conversion between Path and Seq[String]
  */
object Path {

  def apply(pathStr: String): Path = {
    new Path(pathStr)
  }

  def apply(pathStr: String*): Path = {
    new Path(Vector(pathStr).flatten.filterNot(_ == ""))
  }

  /*
  def apply(toSeq: Seq[String]): Path ={
    new Path(toSeq)//.map{
    }*/
  //      idOrName: String  => idOrName.replace("/","\\/")
  //    }.toVector)
  def apply(path: Path): Path = {
    new Path(
      path //.toSeq.map(_.replace("\\/","/"))
    )
  }

  val empty = new Path(Vector.empty)

  object PathOrdering extends scala.math.Ordering[Path] {
    def compare(l: Path, r: Path): Int = {
      val minlen = math.min(l.length,r.length)
      for( i <- 0 until minlen){
        val c = l(i) compare r(i)
        if( c != 0) return c
      }
      l.length compare r.length
    }
  }

  import scala.language.implicitConversions // XXX: maybe a little bit stupid place for this

  implicit def PathAsSeq(p: Path): Vector[String] = p.toSeq

  implicit def SeqAsPath(s: Seq[String]): Path = Path(s.toVector: _*)
}
