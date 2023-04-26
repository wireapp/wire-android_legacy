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
import com.waz.log.LogSE.{verbose, _}
import com.waz.model.UserId
import com.waz.model.sync.SyncJob
import com.waz.model.sync.SyncRequest.Serialized
import com.waz.service.NetworkModeService
import com.waz.sync.SyncHandler.RequestInfo
import com.waz.sync.SyncResult._
import com.waz.sync.{SyncHandler, SyncResult}
import com.waz.utils._
import com.wire.signals.{DispatchQueue, SerialDispatchQueue}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

class SyncExecutor(account:     UserId,
                   scheduler:   SyncScheduler,
                   content:     SyncContentUpdater,
                   network:     NetworkModeService,
                   handler: =>  SyncHandler) extends DerivedLogTag {

  import SyncExecutor._
  private implicit val dispatcher: DispatchQueue = SerialDispatchQueue(name = "SyncExecutorQueue")

  def apply(job: SyncJob): Future[SyncResult] = {
    verbose(l"SSM6<${job.id}> apply: SyncExecutor apply")
    def withJob(f: SyncJob => Future[SyncResult]) =
      content.getSyncJob(job.id) flatMap {
        case Some(job) =>
          verbose(l"SSM6<${job.id}> apply:withJob: found job Id")
          f(job)
        case None =>
          verbose(l"SSM6<${job.id}> apply:withJob: job Id NOT FOUND")
          Future.successful(SyncResult(ErrorResponse.internalError(s"No sync job found with id: ${job.id}")))
      }

    withJob { job =>
      verbose(l"SSM6<${job.id}> apply: await preconditions")
      scheduler.awaitPreconditions(job) {
        verbose(l"SSM6<${job.id}> apply: about to execute")
        withJob(execute)
      }.flatMap {
        case Retry(_) => {
          verbose(l"SSM6<${job.id}> apply: attempt failed, trying again!")
          apply(job)
        }
        case res      => {
          verbose(l"SSM6<${job.id}> apply: success!")
          Future.successful(res)
        }
      }
    }
  }

  private def execute(job: SyncJob): Future[SyncResult] = {
    verbose(l"SSM6<${job.id}> execute: step 0")
    for {
      online     <- network.isOnline.head
      _          = verbose(l"SSM6<${job.id}> execute: step 1")
      syncJob    <- content.updateSyncJob(job.id)(job => job.copy(attempts = job.attempts + 1, state = SyncState.SYNCING, error = None, offline = !online))
      _          = verbose(l"SSM6<${job.id}> execute: step 2")
      syncResult <- syncJob match {
        case None =>
          verbose(l"SSM6<${job.id}> execute: step 3A")
          Future.successful(SyncResult(ErrorResponse.internalError(s"Could not update job: $job")))
        case Some(updated) =>
          verbose(l"SSM6<${job.id}> execute: step 3B")
          handler(account, updated.request)(RequestInfo(updated.attempts, Instant.ofEpochMilli(updated.startTime), network.networkMode.currentValue))
            .recover {
              case e: Throwable =>
                verbose(l"SSM6<${job.id}> execute: error 93")
                SyncResult(ErrorResponse.internalError(s"syncHandler($updated) failed with unexpected error: ${e.getMessage}"))
            }
            .flatMap(res => {
              verbose(l"SSM6<${job.id}> execute: step 4 with result $res")
              processSyncResult(updated, res)
            })
      }
    } yield syncResult
  }

  private def processSyncResult(job: SyncJob, result: SyncResult): Future[SyncResult] = result match {
    case Success =>
      debug(l"SyncRequest: $job completed successfully")
      content.removeSyncJob(job.id).map(_ => result)
    case res @ SyncResult.Failure(error) =>
      warn(l"SyncRequest: $job, failed permanently with error: $error")
      content.removeSyncJob(job.id).map(_ => res)
    case Retry(error) =>
      warn(l"SyncRequest: $job, failed with error: $error")
      if (job.attempts > MaxSyncAttempts) {
        content.removeSyncJob(job.id).map(_ => SyncResult.Failure(error))
      } else {
        verbose(l"will schedule retry for: $job")
        val nextTryTime = System.currentTimeMillis() + SyncExecutor.failureDelay(job)
        for {
          online  <- network.isOnline.head
          _       <- content.updateSyncJob(job.id)(job => job.copy(state = SyncState.FAILED, startTime = nextTryTime, error = Some(error), offline = job.offline || !online))
        } yield result
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
