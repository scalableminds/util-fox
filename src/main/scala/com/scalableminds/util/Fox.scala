/*
 * Copyright (C) 2013-2014 scalable minds UG (haftungsbeschränkt) & Co. KG <http://www.scm.io>
 */

package braingames.util

import scala.concurrent.{ExecutionContext, Future}
import net.liftweb.common.{Failure, Empty, Full, Box}
import net.liftweb.common.Empty

object FoxImplicits extends FoxImplicits

trait FoxImplicits {
  implicit def futureBox2Fox[T](future: Future[Box[T]])(implicit ec: ExecutionContext) =
    Fox(future)

  implicit def box2Fox[T](b: Box[T])(implicit ec: ExecutionContext) =
    Fox(Future.successful(b))

  implicit def futureOption2Fox[T](f: Future[Option[T]])(implicit ec: ExecutionContext) =
    Fox(f.map(Box(_)))

  implicit def option2Fox[T](b: Option[T])(implicit ec: ExecutionContext) =
    Fox(Future.successful(Box(b)))

  implicit def future2Fox[T](f: Future[T])(implicit ec: ExecutionContext) =
    Fox(f.map(Full(_)))
}

object Fox{
  def apply[A](future: Future[Box[A]])(implicit ec: ExecutionContext)  =
    new Fox(future)

  def empty(implicit ec: ExecutionContext) = 
    Fox(Future.successful(Empty))

  def successful[A](e: A)(implicit ec: ExecutionContext)  =
    Fox(Future.successful(Full(e)))

  def failure(message: String, ex: Box[Throwable] = Empty, chain: Box[Failure] = Empty)(implicit ec: ExecutionContext)  =
    Fox(Future.successful(Failure(message, ex, chain)))

  def sequence[T](l: List[Fox[T]])(implicit ec: ExecutionContext): Future[List[Box[T]]] =
    Future.sequence(l.map(_.unwrap))

  def combined[T](l: List[Fox[T]])(implicit ec: ExecutionContext): Fox[List[T]] = Fox(
    Future.sequence(l.map(_.unwrap)).map{ results =>
      results.find(_.isEmpty) match {
        case Some(Empty) => Empty
        case Some(failure : Failure) => failure
        case _ => Full(results.map(_.get))
      }
    })

  def sequenceOfFulls[T](l: List[Fox[T]])(implicit ec: ExecutionContext): Future[List[T]] =
    Future.sequence(l.map(_.unwrap)).map{ results =>
      results.foldRight(List.empty[T]){
        case (_ : Failure, l) => l
        case (Empty, l) => l
        case (Full(e), l) => e :: l
      }
    }
}

class Fox[+A](underlying: Future[Box[A]])(implicit ec: ExecutionContext) {
  val self = this

  def ?~>(s: String) =
    new Fox(underlying.map(_ ?~ s))

  def ~>[T](errorCode: => T) =
    new Fox(underlying.map(_ ~> errorCode))

  def orElse[B >: A](fox: Fox[B]): Fox[B] =
    new Fox(underlying.flatMap{
      case Full(t) => underlying
      case _ => fox.unwrap
    })

  def getOrElse[B >: A](b: B): Future[B] =
    underlying.map(_.getOrElse(b))

  def map[B](f: A => B): Fox[B] =
    new Fox(underlying.map(_.map(f)))

  def flatMap[B](f: A => Fox[B]): Fox[B] =
    new Fox(underlying.flatMap {
      case Full(t) =>
        f(t).unwrap
      case Empty =>
        Future.successful(Empty)
      case fail: Failure =>
        Future.successful(fail)
    })

  def filter(f: A => Boolean): Fox[A] = {
    new Fox(underlying.map(_.filter(f)))
  }

  def foreach(f: A => _): Unit = {
    underlying.map(_.map(f))
  }

  /**
   * Helper to force an implicit conversation. If you have the FoxImplicits in 
   * scope you can force a conversion to a Fox by calling toFox on every type
   * there is an implicit conversion for.
   */
  def toFox = this

  /**
   * Alias for underlying
   */
  def unwrap = {
    underlying.recover{
      case e: Throwable => Failure(e.getMessage, Full(e), Empty)
    }
  }

  /**
   * Makes Fox play better with Scala 2.8 for comprehensions
   */
  def withFilter(p: A => Boolean): WithFilter = new WithFilter(p)

  /**
   * Play NiceLike with the Scala 2.8 for comprehension
   */
  class WithFilter(p: A => Boolean) {
    def map[B](f: A => B): Fox[B] = self.filter(p).map(f)

    def flatMap[B](f: A => Fox[B]): Fox[B] = self.filter(p).flatMap(f)

    def foreach[U](f: A => U): Unit = self.filter(p).foreach(f)

    def withFilter(q: A => Boolean): WithFilter =
      new WithFilter(x => p(x) && q(x))
  }

}