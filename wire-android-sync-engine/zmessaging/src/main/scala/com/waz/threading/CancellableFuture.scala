/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.threading

import java.util.TimerTask

import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.service.tracking.TrackingService.NoReporting
import com.waz.utils.events.{BaseSubscription, EventContext}

import scala.collection.generic.CanBuild
import scala.concurrent._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.ref.WeakReference
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

class CancellableFuture[+A](promise: Promise[A]) extends Awaitable[A] with DerivedLogTag { self =>
  import com.waz.threading.CancellableFuture._
  
  val future = promise.future
  
  def cancel()(implicit tag: LogTag): Boolean =
    promise.tryFailure(new CancelException(s"[$tag] cancel") with NoReporting) // TODO: switch to DefaultCancelFuture for performance (once this is stable)

  def fail(ex: Exception): Boolean =
    promise.tryFailure(ex)

  def onComplete[B](f: Try[A] => B)(implicit executor: ExecutionContext): Unit =
    future.onComplete(f)

  def onSuccess[U](pf: PartialFunction[A, U])(implicit executor: ExecutionContext): Unit =
    future.onSuccess(pf)

  def onFailure[U](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): Unit =
    future.onFailure(pf)

  def onCancelled(body: => Unit)(implicit executor: ExecutionContext): Unit =
    future.onFailure {
      case _: CancelException => body
    }

  def map[B](f: A => B)(implicit executor: ExecutionContext, tag: LogTag = DefaultLogTag): CancellableFuture[B] = {
    val p = Promise[B]()
    @volatile var cancelFunc = Option(self.cancel()(_: LogTag))
    future.onComplete { v =>
      cancelFunc = None
      p.tryComplete(v.flatMap(res => Try(f(res))))
    }
    new CancellableFuture(p) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        if (super.cancel()(tag)) {
          Future(cancelFunc.foreach(_.apply(tag)))(CancellableFuture.internalExecutionContext)
          true
        } else false
      }
    }
  }

  def filter(f: (A) => Boolean)(implicit executor: ExecutionContext, tag: LogTag = DefaultLogTag): CancellableFuture[A] =
    flatMap { res =>
      if (f(res)) CancellableFuture.successful(res)
      else CancellableFuture.failed(new NoSuchElementException(s"[$tag] CancellableFuture.filter failed"))
    } (executor, tag)

  final def withFilter(p: A => Boolean)(implicit executor: ExecutionContext, tag: LogTag = DefaultLogTag): CancellableFuture[A] =
    filter(p)(executor)

  def flatMap[B](f: A => CancellableFuture[B])(implicit executor: ExecutionContext, tag: LogTag = DefaultLogTag): CancellableFuture[B] = {
    val p = Promise[B]()
    @volatile var cancelFunc = Option(self.cancel()(_: LogTag))

    self.future onComplete { res =>
      cancelFunc = None
      if (!p.isCompleted) res match {
        case f: Failure[_] => p tryComplete f.asInstanceOf[Failure[B]]
        case Success(v) =>
          Try(f(v)) match {
            case Success(fut) =>
              cancelFunc = Option(fut.cancel()(_: LogTag))
              fut onComplete { res =>
                cancelFunc = None
                p tryComplete res
              }
              if (p.isCompleted) fut.cancel()(tag)
            case Failure(t) =>
              p tryFailure t
          }
      }
    }

    new CancellableFuture(p) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        if (super.cancel()(tag)) {
          Future(cancelFunc.foreach(_.apply(tag)))(CancellableFuture.internalExecutionContext)
          true
        } else false
      }
    }
  }

  def recover[U >: A](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext, tag: LogTag = DefaultLogTag) =
    recoverWith(pf.andThen(CancellableFuture.successful))(executor, tag)

  def recoverWith[U >: A](pf: PartialFunction[Throwable, CancellableFuture[U]])(implicit executor: ExecutionContext, tag: LogTag = DefaultLogTag): CancellableFuture[U] = {
    val p = Promise[U]()
    @volatile var cancelFunc = Option(self.cancel()(_: LogTag))
    future.onComplete { res =>
      cancelFunc = None
      if (!p.isCompleted) res match {
        case Failure(t) if pf.isDefinedAt(t) =>
          val fut = pf.applyOrElse(t, (_: Throwable) => this)
          cancelFunc = Some(fut.cancel()(_: LogTag))
          fut onComplete { res =>
            cancelFunc = None
            p tryComplete res
          }
          if (p.isCompleted) fut.cancel()(tag)
        case other =>
          p tryComplete other
      }
    }
    new CancellableFuture(p) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        if (super.cancel()(tag)) {
          Future(cancelFunc.foreach(_.apply(tag)))(CancellableFuture.internalExecutionContext)
          true
        } else false
      }
    }
  }

  def flatten[B](implicit executor: ExecutionContext, evidence: A <:< CancellableFuture[B]): CancellableFuture[B] =
    flatMap(x => x)

  def zip[B](other: CancellableFuture[B])(implicit executor: ExecutionContext): CancellableFuture[(A, B)] =
    CancellableFuture.zip(self, other)

  @throws[InterruptedException](classOf[InterruptedException])
  @throws[TimeoutException](classOf[TimeoutException])
  override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
    future.ready(atMost)
    this
  }

  @throws[Exception](classOf[Exception])
  override def result(atMost: Duration)(implicit permit: CanAwait): A = future.result(atMost)

  // TODO: timeout should generate different exception
  def withTimeout(timeout: FiniteDuration)(implicit tag: LogTag = DefaultLogTag): CancellableFuture[A] = {
    implicit val ec = CancellableFuture.internalExecutionContext
    val f = CancellableFuture.delayed(timeout)(this.fail(new TimeoutException(s"[$tag] timedOut($timeout)")))
    onComplete(_ => f.cancel()(tag))
    this
  }

  //TODO: think on this possibility
  def withAutoCanceling(implicit eventContext: EventContext): Unit = {
    eventContext.register(new BaseSubscription(WeakReference(eventContext)) {
      override def onUnsubscribe(): Unit = {
        verbose(l"Cancel this future.")
        cancel()
        eventContext.unregister(this)
      }
      override def onSubscribe(): Unit = {
        verbose(l"We are must never reached this point!")
      }
    })
  }

}

object CancellableFuture {

  val DefaultLogTag: LogTag = LogTag("CancellableFuture")

  private[threading] def internalExecutionContext = Threading.Background

  import language.implicitConversions
  implicit def to_future[A](f: CancellableFuture[A]): Future[A] = f.future

  class CancelException(msg: String) extends Exception(msg) with NoStackTrace

  case object DefaultCancelException extends CancelException("Operation cancelled") with NoReporting

  class PromiseCompletingRunnable[T](body: => T) extends Runnable {
    val promise = Promise[T]()

    override def run() =
      if (!promise.isCompleted) promise.tryComplete(Try(body))
  }
  
  def apply[A](body: => A)(implicit executor: ExecutionContext, tag: LogTag = DefaultLogTag): CancellableFuture[A] = {
    val runnable = new PromiseCompletingRunnable[A](body)
    executor.execute(DispatchQueueStats(s"CancellableFuture_$tag", runnable))
    new CancellableFuture(runnable.promise)
  }

  def lift[A](future: Future[A], onCancel: => Unit = ()): CancellableFuture[A] = {
    val p = Promise[A]()
    p.tryCompleteWith(future)
    new CancellableFuture(p) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        if (super.cancel()(tag)) {
          onCancel
          true
        } else false
      }
    }
  }

  def delay(d: FiniteDuration): CancellableFuture[Unit] = {
    if (d <= Duration.Zero) successful(())
    else {
      val p = Promise[Unit]()
      val task = new TimerTask {
        override def run(): Unit = p.trySuccess(())
      }
      Threading.Timer.schedule(task, d.toMillis)
      new CancellableFuture(p) {
        override def cancel()(implicit tag: LogTag): Boolean = {
          task.cancel()
          super.cancel()(tag)
        }
      }
    }
  }

  def timeout(duration: Duration,
              interrupter: Option[CancellableFuture[_]] = None,
              shouldLoop: () => Boolean = () => false)
             (implicit tag: LogTag, ec: ExecutionContext): CancellableFuture[Unit] = {
    if (duration <= Duration.Zero || !interrupter.flatMap(_.future.value).forall(_.isSuccess)) {
      successful(())
    } else {
      val promise = Promise[Unit]()
      new CancellableFuture(promise) {
        interrupter.foreach(_.onComplete {
          case Success(_) =>
            doCleanup()
            promise.success(())
          case _ => //treat it like we should not interrupt
        })

        @volatile
        private var currentTask: TimerTask = _
        startNewTimeoutLoop()

        private def startNewTimeoutLoop(): Unit = {
          currentTask = createTask
          Threading.Timer.schedule(currentTask, duration.toMillis)
        }

        private def createTask: TimerTask = new TimerTask {
          override def run(): Unit = {
            if (shouldLoop()) startNewTimeoutLoop()
            else {
              doCleanup()
              promise.success(())
            }
          }
        }

        private def doCleanup(): Unit = {
          interrupter.foreach(_.cancel()(tag)) //do not forget to cancel interrupter
          if (currentTask != null) currentTask.cancel() //do not forget to to cancel scheduled task
        }

        override def cancel()(implicit tag: LogTag): Boolean = {
          doCleanup()
          super.cancel()(tag)
        }
      }
    }
  }

  def delayed[A](d: FiniteDuration)(body: => A)(implicit executor: ExecutionContext) =
    if (d <= Duration.Zero) CancellableFuture(body)
    else delay(d) map { _ => body }

  def successful[A](res: A): CancellableFuture[A] = new CancellableFuture[A](Promise.successful(res)) {
    override def toString: String = s"CancellableFuture.successful($res)"
  }

  def failed[A](ex: Throwable): CancellableFuture[A] = new CancellableFuture[A](Promise.failed(ex)) {
    override def toString: String = s"CancellableFuture.failed($ex)"
  }

  def cancelled[A](): CancellableFuture[A] = failed(DefaultCancelException)

  def sequence[A](in: Seq[CancellableFuture[A]])(implicit executor: ExecutionContext): CancellableFuture[Seq[A]] = sequenceB[A, Seq[A]](in)

  def sequenceB[A, B](in: Traversable[CancellableFuture[A]])(implicit executor: ExecutionContext, cbf: CanBuild[A, B]): CancellableFuture[B] =
    in.foldLeft(successful(cbf())) {
      (fr, fa) => for (r <- fr; a <- fa) yield r += a
    } map (_.result())

  def traverse[A, B](in: Seq[A])(f: A => CancellableFuture[B])(implicit executor: ExecutionContext): CancellableFuture[Seq[B]] = sequence(in.map(f))
  
  def traverseSequential[A, B](in: Seq[A])(f: A => CancellableFuture[B])(implicit executor: ExecutionContext): CancellableFuture[Seq[B]] = {
    def processNext(remaining: Seq[A], acc: List[B] = Nil): CancellableFuture[Seq[B]] =
      if (remaining.isEmpty) CancellableFuture.successful(acc.reverse)
      else f(remaining.head) flatMap { res => processNext(remaining.tail, res :: acc) }

    processNext(in)
  }

  def zip[A, B](f1: CancellableFuture[A], f2: CancellableFuture[B])(implicit executor: ExecutionContext): CancellableFuture[(A, B)] = {
    val p = Promise[(A, B)]()

    p.tryCompleteWith((for (r1 <- f1; r2 <- f2) yield (r1, r2)).future)

    new CancellableFuture(p) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        if (super.cancel()(tag)) {
          Future {
            f1.cancel()(tag)
            f2.cancel()(tag)
          }(CancellableFuture.internalExecutionContext)
          true
        } else false
      }
    }
  }
}
