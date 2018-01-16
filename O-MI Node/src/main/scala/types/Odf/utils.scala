package types
package odf
import scala.collection.mutable.{Map => MutableMap}
import scala.collection.immutable.HashMap
import scala.collection.JavaConverters._

object OdfTreeCollection {
  def apply[T](): OdfTreeCollection[T] = Vector()
  def empty[T]: OdfTreeCollection[T] = Vector()
  def apply[T](elems: T*): OdfTreeCollection[T] = Vector(elems:_*)
  def fromIterable[T](elems: Iterable[T]): OdfTreeCollection[T] = elems.toVector
  def toJava[T](c: OdfTreeCollection[T]): java.util.List[T] = c.toBuffer.asJava
  def fromJava[T](i: java.lang.Iterable[T]): OdfTreeCollection[T] = fromIterable(i.asScala)
  import scala.language.implicitConversions
  implicit def seqToOdfTreeCollection[E](s: Iterable[E]): OdfTreeCollection[E] = OdfTreeCollection.fromIterable(s)
}
