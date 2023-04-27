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
package com.waz.sync.queue

import java.io.PrintWriter
import com.waz.api.NetworkMode
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.sync.SyncJob
import com.waz.model.{ConvId, SyncId, UserId}
import com.waz.service.AccountsService.{Active, LoggedOut}
import com.waz.service.tracking.TrackingService
import com.waz.service.{AccountContext, AccountsService, NetworkModeService}
import com.waz.sync.{SyncHandler, SyncRequestServiceImpl, SyncResult}
import com.wire.signals.CancellableFuture.CancelException
import com.wire.signals.{CancellableFuture, DispatchQueue, SerialDispatchQueue, Signal}
import com.waz.utils.{WhereAmI, returning}

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try


trait SyncScheduler {

  def await(id: SyncId): Future[SyncResult]
  def await(ids: Set[SyncId]): Future[Set[SyncResult]]

  def withConv[A](job: SyncJob, conv: ConvId)(f: ConvLock => Future[A]): Future[A]
  def awaitPreconditions[A](job: SyncJob)(f: => Future[A]): Future[A]

  def report(pw: PrintWriter): Future[Unit]
  def reportString: Future[String]
}

class SyncSchedulerImpl(accountId:   UserId,
                        content:     SyncContentUpdater,
                        network:     NetworkModeService,
                        service:     SyncRequestServiceImpl,
                        handler:     => SyncHandler,
                        accounts:    AccountsService,
                        tracking:    TrackingService)
                       (implicit accountContext: AccountContext) extends SyncScheduler with DerivedLogTag {

  private implicit val dispatcher: DispatchQueue = SerialDispatchQueue(name = "SyncSchedulerQueue")

  private val queue                 = new SyncSerializer
  private[sync] val executor        = new SyncExecutor(accountId, this, content, network, handler)
  private[sync] val executions      = new mutable.HashMap[SyncId, Future[SyncResult]]()
  private[sync] val executionsCount = Signal(0)

  private val waitEntries  = new mutable.HashMap[SyncId, WaitEntry]
  private val waiting      = Signal(Map.empty[SyncId, Long])
  private val runningCount = Signal.zip(executionsCount, waiting.map(_.size)).map { case (r, w) => r - w }

  content.syncStorage { storage =>
    val tag = UUID.randomUUID()
    verbose(l"SSM9<TAG:$tag> SyncScheduler content.syncStorage. Executing jobs: ${storage.getJobs.map(_.id)}")
    storage.getJobs.toSeq.sortBy(_.timestamp) foreach execute
    storage.onAdded.on(dispatcher) { job =>
      verbose(l"SSM9<TAG:$tag> onAdded ${job.id}, will execute")
      execute(job)
    }
    storage.onUpdated
      .filter { case (prev, job) =>
        verbose(l"SSM9<TAG:$tag> onUpdated step1: prev ${prev.id}, job ${job.id}")
        val shouldKeep = prev.priority != job.priority || prev.startTime != job.startTime
        verbose(l"SSM9<TAG:$tag> shouldKeep = ${shouldKeep}")
        shouldKeep
      }
      .on(dispatcher) {
        case (prev, job) =>
          verbose(l"SSM9<TAG:$tag> onUpdated step2: prev ${prev.id}, job ${job.id}")
          waiting.mutate { jobs =>
            if (jobs.contains(job.id)) {
              verbose(l"SSM9<TAG:$tag> onUpdated step3: job ${job.id} is contained, updating start time")
              jobs.updated(job.id, getStartTime(job))
            } else {
              verbose(l"SSM9<TAG:$tag> onUpdated step3: job ${job.id} is NOT contained")
              jobs
            } }

          verbose(l"SSM9<TAG:$tag> onUpdated step4 for job ${job.id}")
          waitEntries.get(job.id) foreach {
            verbose(l"SSM9<TAG:$tag> onUpdated step5 for job ${job.id}")
            _.onUpdated(job)
          }
      }
  }

  accounts.accountState(accountId).on(dispatcher) {
    case _: Active => waitEntries.foreach(_._2.onRestart())
    case _ =>
  }

  network.networkMode.on(dispatcher) {
    case NetworkMode.OFFLINE => // do nothing
    case _ => waitEntries.foreach(_._2.onOnline())
  }

  override def reportString = Future {
    s"SyncScheduler: executors: ${executions.size}, count: ${executionsCount.currentValue}, running: ${runningCount.currentValue}, waiting: ${waiting.currentValue}"
  }

  override def report(pw: PrintWriter) = reportString.map(pw.println)

  private def execute(job: SyncJob): Unit = {
    val tag = UUID.randomUUID()
    verbose(l"SSM10<TAG:${tag}> execute(${job.id}) step1")
    val future = executor(job)
    executions += job.id -> future
    executionsCount.mutate(_ + 1)
    verbose(l"SSM10<TAG:${tag}> execute(${job.id}) step2")
    future onComplete { res =>
      verbose(l"SSM10<TAG:${tag}> execute(${job.id}) step3")
      executions -= job.id
      executionsCount.mutate(_ - 1)
      res.failed.foreach(t => t.printStackTrace())
    }
  }

  override def await(id: SyncId) = Future { executions.getOrElse(id, Future.successful(SyncResult.Success)) } flatMap identity

  override def await(ids: Set[SyncId]) = Future.sequence(ids.map(await))

  private def countWaiting[A](id: SyncId, startTime: Long)(future: Future[A]) = {
    waiting.mutate(_ + (id -> startTime))
    future.onComplete(_ => waiting.mutate(_ - id))
    future
  }

  override def withConv[A](job: SyncJob, conv: ConvId)(f: ConvLock => Future[A]) =
    countWaiting(job.id, getStartTime(job)) { queue.acquire(conv, job.id) } flatMap { lock =>
      Try(f(lock)).recover { case t => Future.failed[A](t) }.get.andThen { case _ => lock.release(job.id) }
    }

  override def awaitPreconditions[A](job: SyncJob)(f: => Future[A]): Future[A] = {
    val tag = UUID.randomUUID()
    verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M71 ($tag)")
    debug(l"awaitPreconditions($job)")

    val entry = new WaitEntry(job)
    verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M72 ($tag)")
    waitEntries.put(job.id, entry)
    verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M73 ($tag)")

    verbose(l"SSM3<JOB:${job.id}> awaitPrecondition step 1")
    val jobReady = for {
      _ <- accounts.accountState(accountId).filter(_ != LoggedOut).head
      _ = verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M74 ($tag)")
      _ = verbose(l"SSM3<JOB:${job.id}> awaitPrecondition step 2")
      _ <- network.isOnline.onTrue
      _ = verbose(l"SSM3<JOB:${job.id}> awaitPrecondition step 3")
      _ = verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M75 ($tag)")
      _ <- entry.future
      _ = verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M75FF ($tag)")
    } yield {}

    verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M76 ($tag)")
    verbose(l"SSM3<JOB:${job.id}> awaitPrecondition step 4")
    jobReady.onComplete(_ => {
      verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M77 ($tag)")
      verbose(l"SSM3<JOB:${job.id}> awaitPrecondition step CC1")
        waitEntries -= job.id
      }
    )

   countWaiting(job.id, getStartTime(job))(jobReady) flatMap { _ =>
     verbose(l"SSM7<JOB:${job.id}> awaitPreconditions: step M78 ($tag)")
     verbose(l"SSM3<JOB:${job.id}> awaitPrecondition step CC2")
      returning(f)(_.onComplete(_ => queue.release(job.id)))
    }
  }

  private def getStartTime(job: SyncJob): Long = {
    debug(l"getStartTime($job)")
    if (job.offline && network.isOnline.currentValue.getOrElse(false)) {
      verbose(l"Last request failed due to possible network errors, starting job now for: $job")
      0
    }  // start right away if request last failed due to possible network errors
    else {
      debug(l"was not offline for job ($job), returning start time ${job.startTime}")
      job.startTime
    }
  }

  class WaitEntry(private var job: SyncJob) extends DerivedLogTag { self =>
    private val promise = Promise[Unit]()

    private var delayFuture: CancellableFuture[Unit] = setup(job)

    private def setup(job: SyncJob) = {
      debug(l"setup($job)")
      val t = System.currentTimeMillis()
      val startJob = getStartTime(job)
      val d = math.max(0, startJob - t).millis
      val delay = CancellableFuture.delay(d)
      verbose(l"SSM4<JOB:${job.id}> setup delay ${d}ms (future: $delay)")
      for {
        _ <- delay.recover { case CancelException => () } .future
        _ = verbose(l"SSM4<JOB:${job.id}> delay done")
        _ <- Future.traverse(job.dependsOn)(await)
        _ = verbose(l"SSM4<JOB:${job.id}> dependency done")
        _ <- queue.acquire(job.priority, job.id)
        _ = verbose(l"SSM4<JOB:${job.id}> priority acquired")
      } yield {
        if (job == self.job) {
          verbose(l"SSM4<JOB:${job.id}> self job confirmed")
          promise.trySuccess(())
        }
        else {
          verbose(l"SSM4<JOB:${job.id}> self job NOT confirmed")
          queue.release(job.id)
          verbose(l"Entry already updated, releasing acquired lock for job: $job")
        } // this wait entry was already updated, releasing acquired lock
      }
      delay
    }

    def isCompleted = promise.isCompleted
    def onRestart() = {
      verbose(l"SSM8<JOB:${job.id}> onRestart. cancel delayFuture")

      delayFuture.cancel()
    }
    def onOnline() = {
      verbose(l"SSM8<JOB:${job.id}> onOnline. Job is offline: ${job.offline}")
      if (job.offline) {
        verbose(l"SSM8<JOB:${job.id}> cancel delayFuture because offline")
        delayFuture.cancel()
      }
    }
    def onUpdated(updated: SyncJob): Unit = {
      verbose(l"SSM8<JOB:${job.id}> Updated job with ${updated.id}. Calling setup.")
      job = updated
      delayFuture = setup(updated)
    }

    def future = promise.future
  }
}

object SyncScheduler {
  val AlarmRequestCode = 19523
}
