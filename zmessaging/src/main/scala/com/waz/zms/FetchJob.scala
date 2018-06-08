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
package com.waz.zms

import com.evernote.android.job.Job.Result
import com.evernote.android.job._
import com.evernote.android.job.util.support.PersistableBundleCompat
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, verbose}
import com.waz.model.UserId
import com.waz.service.AccountsService.InBackground
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.returning

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class FetchJob extends Job {

  import FetchJob._
  import Threading.Implicits.Background

  override def onRunJob(params: Job.Params) = {
    val account = Option(params.getExtras.getString(AccountExtra, null)).map(UserId)
    verbose(s"onStartJob, account: $account")
    def syncAccount(userId: UserId): Future[Unit] =
      for {
        Some(zms) <- ZMessaging.accountsService.flatMap(_.getZms(userId))
        _ <- zms.push.syncHistory("fetch job", withRetries = false)
      } yield {}

    val result = account.fold(Future.successful({})) { id =>
      ZMessaging.accountsService.flatMap(_.accountState(id).head).flatMap {
        case InBackground => syncAccount(id)
        case _            =>
          verbose("account active, no point in executing fetch job")
          Future.successful({})
      }
    }

    try {
      Await.result(result, 1.minute) //Give the job a long time to complete
      Result.SUCCESS
    } catch {
      case NonFatal(e) =>
        error("FetchJob failed", e)
        Result.RESCHEDULE
    }
  }
}

object FetchJob {

  val Tag = "FetchJob"
  val AccountExtra = "accounts"

  val MaxExecutionDelay = 30.seconds
  val InitialBackoffDelay = 500.millis

  def apply(userId: UserId): Unit = {
    val tag = s"$Tag#${userId.str}"

    val manager = JobManager.instance()
    val currentJobs = manager.getAllJobsForTag(tag).asScala.toSet
    val currentJob = returning(currentJobs.find(!_.isFinished)) { j =>
      verbose(s"currentJob: $j")
    }

    val hasPendingRequest = returning(JobManager.instance().getAllJobRequestsForTag(tag).asScala.toSet) { v =>
      if (v.size > 1) error(s"Shouldn't be more than one fetch job for account: $userId")
    }.nonEmpty

    if (!(hasPendingRequest || currentJob.isDefined)) {
      new JobRequest.Builder(tag)
        .setBackoffCriteria(InitialBackoffDelay.toMillis, JobRequest.BackoffPolicy.EXPONENTIAL)
        .setExtras(returning(new PersistableBundleCompat())(_.putString(AccountExtra, userId.str)))
        .startNow()
        .build()
        .schedule()
    }
  }
}
