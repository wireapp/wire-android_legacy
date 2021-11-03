package com.waz.services.fcm

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.{ListenableWorker, Worker, WorkerParameters}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.service.AccountsService.InBackground
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.zclient.WireApplication
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityPolicyChecker

final class FCMNotificationWorker(context: Context, params: WorkerParameters)
  extends Worker(context, params) with DerivedLogTag {
  import Threading.Implicits.Background

  override def doWork(): ListenableWorker.Result = {
    verbose(l"FCM doWork")
    val userId = UserId(params.getInputData.getString(FCMLightService.UserKey))
    if (!WireApplication.isInitialized) {
      verbose(l"FCM not initialized")
      checkSecurityAndProcess(userId)
      Result.success()
    } else {
      verbose(l"FCM initialized")
      ZMessaging.accountsService.flatMap(_.accountState(userId).head).foreach {
        case InBackground =>
          verbose(l"FCM account $userId in background")
          checkSecurityAndProcess(userId)
        case other =>
          verbose(l"FCM account $userId in state $other -- no processing")
      }
      Result.success()
    }
  }

  private def checkSecurityAndProcess(userId: UserId): Unit =
    if (SecurityPolicyChecker.needsBackgroundSecurityChecklist) {
      verbose(l"FCM background security check needed")
      implicit val ctx: Context = context
      SecurityPolicyChecker.runBackgroundSecurityChecklist().map {
        case true =>
          verbose(l"FCM background security check passed")
          process(userId)
        case _ =>
          warn(l"FCM the background security check failed for $userId")
      }
    } else
      process(userId)

  private def process(userId: UserId): Unit = {
    verbose(l"FCM process($userId)")
  }
}

