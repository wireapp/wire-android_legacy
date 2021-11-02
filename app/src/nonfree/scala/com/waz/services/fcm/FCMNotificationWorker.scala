package com.waz.services.fcm

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.{ListenableWorker, Worker, WorkerParameters}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.threading.Threading
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityPolicyChecker

final class FCMNotificationWorker(context: Context, params: WorkerParameters)
  extends Worker(context, params) with DerivedLogTag {

  override def doWork(): ListenableWorker.Result = {
    info(l"doWork")
    val userId = UserId(params.getInputData.getString(FCMLightService.UserKey))
    if (!SecurityPolicyChecker.needsBackgroundSecurityChecklist) processFor(userId)
    else {
      implicit val ctx: Context = context
      SecurityPolicyChecker.runBackgroundSecurityChecklist().foreach {
        case true => processFor(userId)
        case _ =>
      }(Threading.Background)
    }
    Result.success()
  }

  private def processFor(userId: UserId): Unit = {
    verbose(l"FCM userId: $userId")
  }
}

