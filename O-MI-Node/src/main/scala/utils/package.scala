

package object utils {
  def merge[A, B](a: Map[A, B], b: Map[A, B])(mergef: (B, Option[B]) => B): Map[A, B] = {
    val (bigger, smaller) = if (a.size > b.size) (a, b) else (b, a)
    smaller.foldLeft(bigger) { case (z, (k, v)) => z + (k -> mergef(v, z.get(k))) }
  }

  implicit def asOption[A](a: A): Option[A] = Option(a)
}
