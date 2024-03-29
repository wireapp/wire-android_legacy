package com.waz.services.fcm

import android.content.Context
import androidx.work.{Data, ExistingWorkPolicy, OneTimeWorkRequest, WorkManager}
import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.push.{GlobalTokenService, GlobalTokenServiceImpl}
import com.waz.service.{DefaultNetworkModeService, ZMessaging}
import com.waz.services.gps.GoogleApiImpl
import com.waz.utils.returning
import com.waz.zclient.WireApplication
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.BackendController

import scala.util.Try

final class FCMService extends FirebaseMessagingService with DerivedLogTag {
  import FCMService._
  implicit lazy val context: Context = this

  private lazy val backend =
    if (WireApplication.isInitialized)
      Option(ZMessaging.currentGlobal.backend)
    else
      (new BackendController).getStoredBackendConfig

  override def onNewToken(s: String): Unit = {
    verbose(l"onNewToken: ${redactedString(s)}")
    tokenService.foreach(_.setNewToken())
  }

  private def tokenService: Option[GlobalTokenService] = Try {
    if (WireApplication.isInitialized) {
      Option(ZMessaging.currentGlobal.tokenService)
    } else {
      backend.map { config =>
        val prefs = GlobalPreferences(this)
        val network = new DefaultNetworkModeService(this)
        val googleApi = GoogleApiImpl(this, config, prefs)
        new GlobalTokenServiceImpl(googleApi, prefs, network)
      }
    }
  }.toOption.flatten

  override def onMessageReceived(remoteMessage: RemoteMessage): Unit = {
    verbose(l"onMessageReceived($remoteMessage)")
    if (isSenderKnown(remoteMessage.getFrom)) {
      val userKey = remoteMessage.getData.getOrDefault(UserKey, "")
      if (userKey.isEmpty) {
        warn(l"User key missing msg: ${redactedString(UserKeyMissingMsg)}")
      } else {
        val workRequest =
          new OneTimeWorkRequest.Builder(classOf[FCMNotificationWorker])
            .setInputData(new Data.Builder().putString(UserKey, userKey).build())
            .build()
        WorkManager.getInstance().enqueueUniqueWork(NotificationWorkName, ExistingWorkPolicy.APPEND, workRequest)
      }
    } else {
      warn(l"Received an FCM notification from an unknown sender: ${redactedString(remoteMessage.getFrom)}. Ignoring...")
    }
  }

  private def isSenderKnown(pushSenderId: String): Boolean =
    returning(backend.exists(_.pushSenderId == pushSenderId)) {
      case false =>
        warn(l"A remote message from an unknown sender: $pushSenderId (our sender is ${backend.map(_.pushSenderId)})")
      case _ =>
    }
}

object FCMService {
  val UserKeyMissingMsg: String = "Notification did not contain user key - discarding"
  val UserKey: String = "user"
  val NotificationWorkName: String = "Notifications"
}
