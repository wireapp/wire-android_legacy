/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.services.fcm

import com.wire.android.job.Job.Result
import com.wire.android.job.util.support.PersistableBundleCompat
import com.wire.android.job.{Job, JobManager, JobRequest}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Uid, UserId}
import com.waz.service.AccountsService.InBackground
import com.waz.service.ZMessaging
import com.waz.service.push.PushService.{FetchFromJob, SyncHistory}
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.log.LogUI._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class FetchJob extends Job with DerivedLogTag {

  import FetchJob._
  import Threading.Implicits.Background

  override def onRunJob(params: Job.Params) = {
    val account = Option(params.getExtras.getString(AccountExtra, null)).map(UserId(_))
    val notification = Option(params.getExtras.getString(NotificationExtra, null)).map(Uid(_))
    verbose(l"onStartJob, account: $account")
    def syncAccount(userId: UserId, nId: Option[Uid]): Future[Unit] =
      for {
        Some(zms) <- ZMessaging.accountsService.flatMap(_.getZms(userId))
        _         <- zms.push.syncNotifications(SyncHistory(FetchFromJob(nId), withRetries = false))
      } yield {}

    val result = account.fold(Future.successful({})) { id =>
      ZMessaging.accountsService.flatMap(_.accountState(id).head).flatMap {
        case InBackground => syncAccount(id, notification)
        case _            =>
          verbose(l"account active, no point in executing fetch job")
          Future.successful({})
      }
    }

    try {
      Await.result(result, 1.minute) //Give the job a long time to complete
      Result.SUCCESS
    } catch {
      case NonFatal(e) =>
        error(l"FetchJob failed", e)
        Result.RESCHEDULE
    }
  }
}

object FetchJob extends DerivedLogTag {

  val Tag = "FetchJob"
  val AccountExtra = "accounts"
  val NotificationExtra = "notification"

  val MinExecutionDelay = 1.millis //must be greater than 0
  val MaxExecutionDelay = 2.seconds
  val InitialBackoffDelay = 500.millis

  def apply(userId: UserId, nId: Option[Uid]): Unit = {
    val tag = s"$Tag#${userId.str}"

    val manager = JobManager.instance()
    val currentJobs = manager.getAllJobsForTag(tag).asScala.toSet
    val currentJob = returning(currentJobs.find(!_.isFinished)) { j =>
      verbose(l"currentJob: $j")
    }

    val hasPendingRequest = returning(JobManager.instance().getAllJobRequestsForTag(tag).asScala.toSet) { v =>
      if (v.size > 1) error(l"Shouldn't be more than one fetch job for account: $userId")
    }.nonEmpty

    if (!(hasPendingRequest || currentJob.isDefined)) {
      val args = returning(new PersistableBundleCompat()) { b =>
        b.putString(AccountExtra, userId.str)
        b.putString(NotificationExtra, nId.map(_.str).orNull)
      }

      new JobRequest.Builder(tag)
        .setBackoffCriteria(InitialBackoffDelay.toMillis, JobRequest.BackoffPolicy.EXPONENTIAL)
        .setExecutionWindow(MinExecutionDelay.toMillis, MaxExecutionDelay.toMillis)
        .setExtras(args)
        .build()
        .schedule()
    }
  }
}
