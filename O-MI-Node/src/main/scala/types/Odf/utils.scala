package types
package odf

import scala.collection.JavaConverters._


trait Unionable[T] {
  def union(t: T): T
}

object OdfCollection {
  def apply[T](): OdfCollection[T] = Vector()

  def empty[T]: OdfCollection[T] = Vector()

  def apply[T](elems: T*): OdfCollection[T] = Vector(elems: _*)

  def fromIterable[T](elems: Iterable[T]): OdfCollection[T] = elems.toVector

  def toJava[T](c: OdfCollection[T]): java.util.List[T] = c.toBuffer.asJava

  def fromJava[T](i: java.lang.Iterable[T]): OdfCollection[T] = fromIterable(i.asScala)

  import scala.language.implicitConversions

  implicit def seqToOdfCollection[E](s: Iterable[E]): OdfCollection[E] = OdfCollection.fromIterable(s)
}
