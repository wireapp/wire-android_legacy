package com.waz.services.fcm

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.{ListenableWorker, Worker, WorkerParameters}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.log.LogUI._

final class FCMNotificationWorker(context: Context, params: WorkerParameters)
  extends Worker(context, params) with DerivedLogTag {
  override def doWork(): ListenableWorker.Result = {
    info(l"doWork")
    Result.success()
  }
}
