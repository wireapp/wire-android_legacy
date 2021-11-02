package com.waz.services.fcm

import java.util.concurrent.TimeUnit

import android.content.Context
import androidx.work.{OneTimeWorkRequest, WorkManager}
import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{DefaultNetworkModeService, ZMessaging}
import com.waz.service.push.{GlobalTokenService, GlobalTokenServiceImpl}
import com.waz.services.gps.GoogleApiImpl
import com.waz.zclient.WireApplication
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.BackendController

import scala.util.Try

final class FCMLightService extends FirebaseMessagingService with DerivedLogTag {
  implicit lazy val context: Context = this

  override def onNewToken(s: String): Unit = {
    info(l"onNewToken: ${redactedString(s)}")
    tokenService.foreach(_.setNewToken())
  }

  private def tokenService: Option[GlobalTokenService] = Try {
    if (WireApplication.isInitialized) {
      Option(ZMessaging.currentGlobal.tokenService)
    } else {
      (new BackendController).getStoredBackendConfig.map { config =>
        val prefs = GlobalPreferences(this)
        val network = new DefaultNetworkModeService(this)
        val googleApi = GoogleApiImpl(this, config, prefs)
        new GlobalTokenServiceImpl(googleApi, prefs, network)
      }
    }
  }.toOption.flatten

  override def onMessageReceived(remoteMessage: RemoteMessage): Unit = {
    info(l"onMessageReceived($remoteMessage)")
    if (!remoteMessage.getData.isEmpty) {
      val workRequest =
        (new OneTimeWorkRequest.Builder(classOf[FCMNotificationWorker]))
          .setInitialDelay(100L, TimeUnit.MILLISECONDS)
          .build()

      WorkManager.getInstance().enqueue(workRequest)
    }
  }
}
