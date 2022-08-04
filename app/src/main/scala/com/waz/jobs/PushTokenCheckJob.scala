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
package com.waz.jobs

import com.wire.android.job.Job.Result
import com.wire.android.job.util.support.PersistableBundleCompat
import com.wire.android.job._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.services.fcm.FetchJob
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.log.LogUI._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class PushTokenCheckJob extends Job with DerivedLogTag {

  import PushTokenCheckJob._
  import Threading.Implicits.Background

  override def onRunJob(params: Job.Params): Job.Result = {
    Option(params.getExtras.getString(AccountExtra, null)).map(UserId(_)) match {
      case Some(acc) =>
        val res = ZMessaging.currentGlobal.accountsService.getZms(acc).flatMap {
          case Some(zms) =>
            zms.pushToken.checkCurrentUserTokens().map {
              case Right(performFetch) =>
                //trigger a fetch in case we missed any notifications
                //TODO, this is a bit rigid - we should chain the work with WorkManager and provide and interface so that
                //TODO the SE can decide this for itself
                if (performFetch)
                  FetchJob(acc, nId = None)

                Result.SUCCESS
              case Left(err) => if (err.isFatal) Result.FAILURE else Result.RESCHEDULE
            }
          case _ => Future.successful(Result.FAILURE)
        }
        try {
          Await.result(res, 1.minute) //Give the job a long time to complete
        } catch {
          case NonFatal(e) =>
            error(l"PushTokenCheckJob failed", e)
            Result.RESCHEDULE
        }

      case None => Result.FAILURE
    }
  }
}

object PushTokenCheckJob extends DerivedLogTag {
  val Tag = "PushTokenCheckJob"
  val AccountExtra = "accounts"

  val MinExecutionDelay = 1.millis //must be greater than 0
  val MaxExecutionDelay = 15.seconds
  val InitialBackoffDelay = 500.millis

  def apply(): Unit =
    ZMessaging.currentGlobal.accountsService.zmsInstances.head.foreach { zs =>
      zs.map(_.selfUserId).foreach(PushTokenCheckJob(_))
    }(Threading.Background)

  def apply(userId: UserId): Unit = {
    val tag = s"$Tag#${userId.str}"

    val manager = JobManager.instance()
    val currentJobs = manager.getAllJobsForTag(tag).asScala.toSet
    val currentJob = returning(currentJobs.find(!_.isFinished)) { j =>
      verbose(l"currentJob: $j")
    }

    val hasPendingRequest = returning(manager.getAllJobRequestsForTag(tag).asScala.toSet) { v =>
      if (v.size > 1) error(l"Shouldn't be more than one fetch job for account: $userId")
    }.nonEmpty

    if (!(hasPendingRequest || currentJob.isDefined)) {
      val args = returning(new PersistableBundleCompat()) { b =>
        b.putString(AccountExtra, userId.str)
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
