package utils
import scala.language.higherKinds

import concurrent.{Future, ExecutionContext}


// Just some simple implementation of Monads + Functor
sealed trait SimpleMonad[M[_]] {
  def map[A,B]     : (M[A], (A => B)   ) => M[B] = (fa, f) => flatMap(fa, single compose f)
  def flatMap[A,B] : (M[A], (A => M[B])) => M[B]
  def single[A]: A => M[A]
}

object SimpleMonad {

  implicit def FutureMonad(implicit ec: ExecutionContext) = new SimpleMonad[Future] {
    def single[A] = Future.successful _
    override def map[A, B] = _ map _
    def flatMap[A, B] = _ flatMap _
  }

  implicit def OptionMonad = new SimpleMonad[Option] {
    def single[A] = Option.apply _
    override def map[A, B] = _ map _
    def flatMap[A, B] = _ flatMap _
  }

}



/**
  * Option Monad Transformer
  */
final case class OptionT[M[_], A] (run: M[Option[A]]) {

  def mapO[B](f: Option[A] => B)(implicit M: SimpleMonad[M]) = M.map(run,f)

  def map[B](f: A => B)(implicit M: SimpleMonad[M]): OptionT[M, B] = OptionT(mapO(_ map f))

  def flatMap[B](f: A => OptionT[M,B])(implicit M: SimpleMonad[M]): OptionT[M,B] = OptionT[M,B](
    M.flatMap[Option[A],Option[B]](run, {
      case None    => M.single(None)
      case Some(s) => f(s).run
    }))

  //def orElse[B >: A](other: => Option[B])(implicit M: SimpleMonad[M]): OptionT[M, B] = OptionT(mapO(_.orElse(other)))
  def orElse[B >: A](other: => OptionT[M,B])(implicit M: SimpleMonad[M]): OptionT[M, B] = OptionT(
    M.flatMap[Option[A],Option[B]](run, {
      case None => other.run
      case s: Some[A] => M.single(s)
    }))

  def getOrElse(default: => A)(implicit M: SimpleMonad[M]): M[A] = mapO(_.getOrElse(default))
  
  // Wrap other Option methods as needed
}


trait OptionTInstances {
//  implicit def OptionTMonad[M[_]](implicit M0: SimpleMonad[M]) = new OptionTMonad[OptionT[M, ?]] {
//    implicit def M = M0
//
//    def single[A]: A => OptionT[M, A] = x => OptionT[M, A](M.single(Some(x)))
//    override def map[A, B] = _ map _
//    def flatMap[A, B] = _ flatMap _
//  }
}

object OptionT extends OptionTInstances {
  def optionT[M[_], A](a: A)(implicit M: SimpleMonad[M]) = OptionT[M, A](M.single(Option(a)))
  def some[M[_], A](a: A)(implicit M: SimpleMonad[M]) = OptionT[M, A](M.single(Some(a)))
  def none[M[_], A](implicit M: SimpleMonad[M]) = OptionT[M, A](M.single(None))
}

