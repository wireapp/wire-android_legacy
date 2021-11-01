package com.waz.services.fcm

import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.log.LogUI._

final class FCMLightService extends FirebaseMessagingService with DerivedLogTag {
  override def onNewToken(s: String): Unit = {
    info(l"onNewToken: ${redactedString(s)}")
  }

  override def onMessageReceived(remoteMessage: RemoteMessage): Unit =
    info(l"onMessageReceived($remoteMessage)")
}
