package com.waz.services.fcm

import java.util.concurrent.TimeUnit

import android.content.Context
import androidx.work.{Data, OneTimeWorkRequest, WorkManager}
import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.push.{GlobalTokenService, GlobalTokenServiceImpl}
import com.waz.service.{BackendConfig, DefaultNetworkModeService, ZMessaging}
import com.waz.services.gps.GoogleApiImpl
import com.waz.utils.returning
import com.waz.zclient.WireApplication
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.BackendController

import scala.collection.JavaConversions._
import scala.util.Try

final class FCMLightService extends FirebaseMessagingService with DerivedLogTag {
  import FCMLightService._
  implicit lazy val context: Context = this

  private lazy val backendConfig: Option[BackendConfig] =
    if (WireApplication.isInitialized) {
      Option(ZMessaging.currentGlobal.backend)
    } else {
      (new BackendController).getStoredBackendConfig
    }

  override def onNewToken(s: String): Unit = {
    info(l"onNewToken: ${redactedString(s)}")
    tokenService.foreach(_.setNewToken())
  }

  private def tokenService: Option[GlobalTokenService] = Try {
    if (WireApplication.isInitialized) {
      Option(ZMessaging.currentGlobal.tokenService)
    } else {
      backendConfig.map { config =>
        val prefs = GlobalPreferences(this)
        val network = new DefaultNetworkModeService(this)
        val googleApi = GoogleApiImpl(this, config, prefs)
        new GlobalTokenServiceImpl(googleApi, prefs, network)
      }
    }
  }.toOption.flatten

  override def onMessageReceived(remoteMessage: RemoteMessage): Unit = {
    info(l"onMessageReceived($remoteMessage)")
    if (!remoteMessage.getData.isEmpty && isSenderKnown(remoteMessage.getFrom)) {
      val data = remoteMessage.getData.toMap[String, String]
      data.get(UserKey) match {
        case None =>
          warn(l"User key missing msg: ${redactedString(UserKeyMissingMsg)}")
        case Some(userKey) =>
          val input = new Data.Builder().putString(UserKey, userKey).build()
          val workRequest =
            new OneTimeWorkRequest.Builder(classOf[FCMNotificationWorker])
              .setInputData(input)
              .setInitialDelay(10L, TimeUnit.MILLISECONDS)
              .build()
          WorkManager.getInstance().enqueue(workRequest)
      }
    } else {
      warn(l"Received an invalid FCM notification from a sender: ${redactedString(remoteMessage.getFrom)}. Ignoring...")
    }
  }

  private def isSenderKnown(pushSenderId: String): Boolean =
    returning(backendConfig.exists(_.pushSenderId == pushSenderId)) {
      case false => warn(l"A remote message from an unknown sender: $pushSenderId (our sender is ${backendConfig.map(_.pushSenderId)})")
      case _ =>
    }
}

object FCMLightService {
  val UserKeyMissingMsg: String = "Notification did not contain user key - discarding"
  val UserKey: String = "user"
}
