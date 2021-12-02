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
    verbose(l"doWork")
    val userId = UserId(params.getInputData.getString(FCMService.UserKey))
    if (!WireApplication.isInitialized) {
      verbose(l"not initialized")
      checkSecurityAndProcess(userId)
      Result.success()
    } else {
      ZMessaging.accountsService.flatMap(_.accountState(userId).head).foreach {
        case InBackground =>
          verbose(l"account $userId in background")
          checkSecurityAndProcess(userId)
        case other =>
          verbose(l"account $userId in state $other -- no processing")
      }
      Result.success()
    }
  }

  private def checkSecurityAndProcess(userId: UserId): Unit =
    if (SecurityPolicyChecker.needsBackgroundSecurityChecklist) {
      implicit val ctx: Context = context
      SecurityPolicyChecker.runBackgroundSecurityChecklist().map {
        case true =>
          process(userId)
        case _ =>
          warn(l"the background security check failed for $userId")
      }
    } else
      process(userId)

  private def process(userId: UserId): Unit = {
    // It's too difficult to work without the initialized app from this point.
    // In SQCORE-1146, ensureInitialized() will be split into two - the essential part,
    // and other components that should be initialized only when the app comes to the front.
    // Here we only need the essentials.
    WireApplication.ensureInitialized()
    val handler = for {
      global     <- ZMessaging.globalModule
      accounts   <- ZMessaging.accountsService
      Some(zms)  <- accounts.getZms(userId)
      clientId   =  zms.clientId
      client     =  zms.pushNotificationsClient
      storage    =  zms.eventStorage
      decrypter  =  zms.eventDecrypter
      decoder    =  zms.otrEventDecoder
      parser     =  zms.notificationParser
      controller = WireApplication.APP_INSTANCE.messageNotificationsController
    } yield
      FCMPushHandler(userId, clientId, client,  storage, decrypter, decoder, parser, controller, () => zms.calling, global.prefs, zms.userPrefs)
    handler.foreach(_.syncNotifications())
  }
}

