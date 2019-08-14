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

import com.waz.api.SyncState
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.UserId
import com.waz.model.sync.SyncJob
import com.waz.model.sync.SyncRequest.Serialized
import com.waz.service.NetworkModeService
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncHandler.RequestInfo
import com.waz.sync.SyncResult._
import com.waz.sync.{SyncHandler, SyncResult}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils._
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}
import scala.util.Failure
import scala.util.control.NoStackTrace

class SyncExecutor(account:     UserId,
                   scheduler:   SyncScheduler,
                   content:     SyncContentUpdater,
                   network:     NetworkModeService,
                   handler: =>  SyncHandler,
                   tracking:    TrackingService) extends DerivedLogTag {

  import SyncExecutor._
  private implicit val dispatcher = new SerialDispatchQueue(name = "SyncExecutorQueue")

  def apply(job: SyncJob): Future[SyncResult] = {
    def withJob(f: SyncJob => Future[SyncResult]) =
      content.getSyncJob(job.id) flatMap {
        case Some(job) => f(job)
        case None =>
          Future.successful(SyncResult(ErrorResponse.internalError(s"No sync job found with id: ${job.id}")))
      }

    withJob { job =>
      scheduler.awaitPreconditions(job) {
        withJob(execute)
      }.flatMap {
        case Retry(_) => apply(job)
        case res      => Future.successful(res)
      }
    }
  }

  private def execute(job: SyncJob): Future[SyncResult] = {
    verbose(l"executeJob: $job")
    val future =
      content.updateSyncJob(job.id)(job => job.copy(attempts = job.attempts + 1, state = SyncState.SYNCING, error = None, offline = !network.isOnlineMode))
      .flatMap {
        case None => Future.successful(SyncResult(ErrorResponse.internalError(s"Could not update job: $job")))
        case Some(updated) =>
          handler(account, updated.request)(RequestInfo(updated.attempts, Instant.ofEpochMilli(updated.startTime), network.networkMode.currentValue))
            .recover {
              case e: Throwable =>
                SyncResult(ErrorResponse.internalError(s"syncHandler($updated) failed with unexpected error: ${e.getMessage}"))
            }
            .flatMap(res => processSyncResult(updated, res))
      }

    // this is only to check for any long running sync requests, which could mean very serious problem
    CancellableFuture.lift(future).withTimeout(10.minutes).onComplete {
      case Failure(e: TimeoutException) =>
        tracking.exception(new RuntimeException(s"SyncRequest: ${job.request.cmd} runs for over 10 minutes", e), s"SyncRequest taking too long: $job")
        // TODO: Think about removing the sync job at this point
      case _ =>
    }
    future
  }

  private def processSyncResult(job: SyncJob, result: SyncResult): Future[SyncResult] = {

    result match {
      case Success =>
        debug(l"SyncRequest: $job completed successfully")
        content.removeSyncJob(job.id).map(_ => result)
      case res @ SyncResult.Failure(error) =>
        warn(l"SyncRequest: $job, failed permanently with error: $error")
        if (error.shouldReportError) {
          tracking.exception(new RuntimeException(s"Request ${job.request.cmd} failed permanently with error: ${error.code}") with NoStackTrace, s"Got fatal error, dropping request: $job\n error: $error")
        }
        content.removeSyncJob(job.id).map(_ => res)
      case Retry(error) =>
        warn(l"SyncRequest: $job, failed with error: $error")
        if (job.attempts > MaxSyncAttempts) {
          tracking.exception(new RuntimeException(s"Request ${job.request.cmd} failed with error: ${error.code}") with NoStackTrace, s"MaxSyncAttempts exceeded, dropping request: $job\n error: $error")
          content.removeSyncJob(job.id).map(_ => SyncResult.Failure(error))
        } else {
          verbose(l"will schedule retry for: $job")
          val nextTryTime = System.currentTimeMillis() + SyncExecutor.failureDelay(job)
          content
            .updateSyncJob(job.id)(job => job.copy(state = SyncState.FAILED, startTime = nextTryTime, error = Some(error), offline = job.offline || !network.isOnlineMode))
            .map(_ => result)
        }
    }
  }
}

object SyncExecutor {
  val MaxSyncAttempts = 20
  val RequestRetryBackoff = new ExponentialBackoff(5.seconds, 1.day)
  val ConvRequestRetryBackoff = new ExponentialBackoff(5.seconds, 1.hour)

  def failureDelay(job: SyncJob) = job.request match {
    case _: Serialized => ConvRequestRetryBackoff.delay(job.attempts).toMillis
    case _ => RequestRetryBackoff.delay(job.attempts).toMillis
  }
}
