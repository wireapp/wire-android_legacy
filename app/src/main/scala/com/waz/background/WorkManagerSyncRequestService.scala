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
package com.waz.background

import java.util.UUID
import java.util.concurrent.{TimeUnit, TimeoutException}

import android.content.Context
import androidx.lifecycle.{LiveData, Observer}
import androidx.work._
import com.waz.api.SyncState
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.log.BasicLogging.LogTag
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync.{SyncCommand, SyncRequest}
import com.waz.model.{SyncId, UserId}
import com.waz.service.NetworkModeService
import com.waz.sync.SyncHandler.RequestInfo
import com.waz.sync.{SyncHandler, SyncRequestService, SyncResult}
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.{RichInstant, returning}
import com.waz.zclient.{Injectable, Injector, WireApplication, WireContext}
import com.waz.zclient.log.LogUI._
import org.json.JSONObject
import org.threeten.bp.{Clock, Instant}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class WorkManagerSyncRequestService (implicit inj: Injector, cxt: Context)
  extends SyncRequestService with Injectable {

  import WorkManagerSyncRequestService._
  import com.waz.threading.Threading.Implicits.Background

  private lazy val wm = WorkManager.getInstance()

  override def addRequest(account:    UserId,
                          req:        SyncRequest,
                          priority:   Int            = Priority.Normal,
                          dependsOn:  Seq[SyncId]    = Nil,
                          forceRetry: Boolean        = false,
                          delay:      FiniteDuration = Duration.Zero) = {

    val work = new OneTimeWorkRequest.Builder(classOf[SyncJobWorker])
      .setConstraints(
        new Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
      .setInitialDelay(delay.toMillis, TimeUnit.MILLISECONDS)
      .setInputData(
        new Data.Builder()
          .putString(AccountId, account.str)
          .putString(SyncRequestCmd, req.cmd.name)
          .putString(Json, SyncRequest.Encoder(req).toString)
          .putLong(ScheduledTime, (inject[Clock].instant() + delay).toEpochMilli)
          .build())
      .addTag(account.str)
      .addTag(req.cmd.name)
      .build()

    import SyncRequest._
    val uniqueGroupName = req match {
      case r: RequestForConversation with Serialized => Some(r.convId.str)
      case r: RequestForUser         with Serialized => Some(r.userId.str)
      case _ => None
    }

    implicit val logTag: LogTag = jobLogTag(account)
    val commandTag = commandId(req.cmd, work.getId)
    verbose(l"${showString(commandTag)} scheduling...")

    Future {
      (uniqueGroupName.map(n => s"${account.str}--$n") match {
        case Some(n) => wm.enqueueUniqueWork(n, ExistingWorkPolicy.APPEND, work)
        case None    => wm.enqueue(work)
      }).getResult.get()
      verbose(l"${showString(commandTag)} scheduled successfully")
      SyncId(work.getId.toString)
    }
  }

  override def await(ids: Set[SyncId]): Future[Set[SyncResult]] =
    Future.sequence(ids.map(await))

  @volatile
  private var signalRefs = Map.empty[SyncId, Signal[SyncResult]]
  override def await(id: SyncId): Future[SyncResult] = {
    implicit val logTag: LogTag = LogTag("WorkManager#await")
    val signal = new LiveDataSignal(wm.getWorkInfoByIdLiveData(UUID.fromString(id.str)))
      .collect[SyncResult] { case status if status.getState.isFinished =>
        import androidx.work.WorkInfo.State._
        status.getState match {
          case SUCCEEDED =>
            decodeError(status.getOutputData) match {
              case Some(e) => SyncResult.Failure(e)
              case _       => SyncResult.Success
            }
          case CANCELLED => SyncResult.Failure(ErrorResponse.Cancelled)
          case _         => SyncResult.Failure("unexpected failure!")
        }
      }

    signalRefs += (id -> signal)

    signal.head.onComplete(_ => signalRefs -= id)
    signal.head
  }

  override def syncState(account: UserId, matchers: Seq[SyncCommand]): Signal[SyncState] = {
    implicit val logTag: LogTag = LogTag("WorkManager#syncState")
    new LiveDataSignal(wm.getWorkInfosByTagLiveData(account.str))
      .map(_.filter(_.getTags.exists(tag => matchers.map(_.name).toSet.contains(tag))))
      .map { statuses =>
        returning {
          if (statuses.isEmpty) SyncState.COMPLETED
          else statuses.map { s =>
            import androidx.work.WorkInfo.State._
            s.getState match {
              case ENQUEUED |
                   BLOCKED =>
                SyncState.WAITING

              case RUNNING =>
                SyncState.SYNCING

              case FAILED |
                   SUCCEEDED if s.getOutputData.getBoolean(Failure, false) =>
                SyncState.FAILED

              case SUCCEEDED |
                   CANCELLED =>
                SyncState.COMPLETED
            }
          }.minBy(_.ordinal())
        }(s => verbose(l"matchers: $matchers => state: $s"))
      }
  }
}

object WorkManagerSyncRequestService {

  def jobLogTag(acc: UserId): LogTag = LogTag(s"WorkManager:${acc.str.take(8)}")
  def commandId(cmd: SyncCommand, id: UUID): String = commandId(cmd.name, id)
  def commandId(cmdName: String, id: UUID): String = s"$cmdName (jobId: ${id.toString.take(8)}...) =>"

  def decodeError(d: Data): Option[ErrorResponse] =
    if (d.getBoolean(Failure, false)) {
      val code  = d.getInt(ErrorCode, ErrorResponse.InternalErrorCode)
      val msg   = d.getString(ErrorMessage)
      val label = d.getString(ErrorMessage)
      Some(ErrorResponse(code, msg, label))
    } else None

  //Inputs
  val SyncRequestCmd = "SyncRequestCmd"
  val AccountId      = "AccountId"
  val ScheduledTime  = "ScheduledTime"
  val Json           = "Json"

  //Outputs
  val Failure      = "Failure"
  val ErrorCode    = "ErrorCode"
  val ErrorMessage = "ErrorMessage"
  val ErrorLabel   = "ErrorLabel"

  val MaxSyncAttempts = 20
  val SyncJobTimeout  = 10.minutes

  class SyncJobWorker(context: Context, params: WorkerParameters, jobId: SyncId) extends Worker(context, params) with Injectable {

    implicit val wireContext = WireContext(context)
    implicit val injector    = wireContext.injector

    lazy val clock    = inject[Clock]
    lazy val network  = inject[NetworkModeService].networkMode

    override def doWork(): ListenableWorker.Result = {

      import ListenableWorker._
      val input = getInputData
      val account = UserId(input.getString(AccountId))
      val cmd = input.getString(SyncRequestCmd)

      implicit val logTag: LogTag = jobLogTag(account)
      val commandTag = commandId(cmd, getId)
      verbose(l"${showString(commandTag)} doWork")

      def onFailure(err: ErrorResponse) = {
        //we need to return the SUCCESS code so as not to block appended unique work
        Result.success(new Data.Builder()
          .putBoolean(Failure, true)
          .putInt(ErrorCode, err.code)
          .putString(ErrorMessage, err.message)
          .putString(ErrorLabel, err.label)
          .build())
      }

      if (!WireApplication.ensureInitialized())
        onFailure(internalError("The app is not initialized yet"))
      else {
        val scheduledTime = input.getLong(ScheduledTime, 0)
        val request = SyncRequest.Decoder(new JSONObject(input.getString(Json)))
        val syncHandler = inject[SyncHandler]
        val requestInfo = RequestInfo(getRunAttemptCount, Instant.ofEpochMilli(scheduledTime), network.currentValue)
        try {
          Await.result(syncHandler(account, request, jobId)(requestInfo), SyncJobTimeout) match {
            case SyncResult.Success =>
              verbose(l"${showString(commandTag)} completed successfully")
              Result.success()

            case SyncResult.Failure(error) =>
              warn(l"${showString(commandTag)} failed permanently with error: $error")
              onFailure(error)

            case SyncResult.Retry(error) if getRunAttemptCount > MaxSyncAttempts =>
              warn(l"${showString(commandTag)} failed more than the maximum $MaxSyncAttempts times, final time was with error: $error")
              onFailure(error)

            case SyncResult.Retry(error) =>
              warn(l"${showString(commandTag)} failed non-fatally with $error, retrying...")
              Result.retry()
          }
        } catch {
          case e: TimeoutException =>
            error(l"${showString(commandTag)} doWork timed out after $SyncJobTimeout, the job seems to be blocked", e)
            onFailure(ErrorResponse.timeout(s"$logTag $commandTag timed out after $SyncJobTimeout, aborting"))

          case NonFatal(e) =>
            error(l"${showString(commandTag)} failed unexpectedly", e)
            onFailure(internalError(e.getMessage))
        }
      }
    }
  }
}

class LiveDataSignal[T](liveData: LiveData[T]) extends Signal[T] {

  private val observer = new Observer[T] {
    override def onChanged(t: T): Unit =
      set(Option(t), Some(Threading.Ui))
  }

  override protected def onWire(): Unit =
    Threading.Ui {
      liveData.observeForever(observer)
      set(Option(liveData.getValue), Some(Threading.Ui))
    }


  override protected def onUnwire(): Unit =
    Threading.Ui {
      liveData.removeObserver(observer)
      value = None
    }
}

