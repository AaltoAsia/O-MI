package types
package odf

import scala.collection.{ Seq, Map }
/*
case class Path(
  val path: collection.immutable.Seq[String]
  ) {
    import Path._
  def toSeq: Seq[String] = path.filterNot{ _ == ""}.toVector
  def toArray: Array[String] = path.filterNot{ _ == ""}.toArray
  def isAncestorOf( that: Path): Boolean ={
    if( length < that.length ){
      that.path.startsWith(path) 
    } else false
  }
  def isDescendant( that: Path): Boolean ={
    if( length > that.length ){
      path.startsWith(that.path) 
    } else false
  }
  def isChildOf( that: Path ) : Boolean ={
    that.length + 1 == length && path.startsWith( that.path )
  }
  def isParentOf( that: Path ) : Boolean ={
    length + 1 == that.length && that.path.startsWith( path )
  }
  def / ( id: String ) : Path = Path( path ++ Vector(id) )
  def / ( that : odf.Path ) : Path = Path( path ++ that.path )

  def this(pathStr: String ) = this{
    pathStr.split("/").toVector
  }

  def nonEmpty: Boolean = path.nonEmpty
  def isEmpty: Boolean = path.isEmpty
  def getAncestorsAndSelf: Seq[Path] = path.inits.map( Path(_) ).filter( _.nonEmpty ).toVector ++ Vector(this)
  def getAncestors: Seq[Path] = path.inits.map( Path(_) ).filter( _.nonEmpty ).toVector
  def getParent: Path = Path(path.init)
  def length: Int = path.length
  override def toString: String = path.mkString("/")
  override lazy val hashCode: Int = this.toSeq.hashCode
  override def equals(that: Any ): Boolean ={
    that match {
      case pThat: Path => pThat.toSeq.equals(this.toSeq)
      case _ => false
    }
  }
}

object Path {
  def apply(pathStr: String): Path = new Path(pathStr)
  def apply(pathSeq: Seq[String]): Path = new Path(pathSeq.toVector)
  val empty = new Path(Vector.empty)


  import scala.language.implicitConversions // XXX: maybe a little bit stupid place for this

  implicit def PathAsSeq(p: Path): Seq[String] = p.toSeq
  implicit def SeqAsPath(s: Seq[String]): Path = Path(s.toVector)
}

object PathOrdering extends scala.math.Ordering[Path] {
  def compare( l: Path, r: Path) : Int ={
    l.toString compare r.toString
  }
}*/
