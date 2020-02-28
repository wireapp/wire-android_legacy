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
package com.waz.services.fcm

import android.content.Context
import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Uid, UserId}
import com.waz.service.AccountsService.InForeground
import com.waz.service.ZMessaging.clock
import com.waz.service._
import com.waz.service.push.PushService.{FetchFromIdle, SyncHistory}
import com.waz.service.push._
import com.waz.services.ZMessagingService
import com.waz.services.fcm.FCMHandlerService._
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.utils.{JsonDecoder, RichInstant, Serialized}
import com.waz.zclient.WireApplication
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security._
import org.json
import org.threeten.bp.Instant

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try

/**
  * For more information, see: https://firebase.google.com/docs/cloud-messaging/android/receive
  */
class FCMHandlerService extends FirebaseMessagingService with ZMessagingService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  lazy val pushSenderId = ZMessaging.currentGlobal.backend.pushSenderId
  lazy val accounts = ZMessaging.currentAccounts
  lazy val tracking = ZMessaging.currentGlobal.trackingService

  override def onNewToken(s: String): Unit = {
    ZMessaging.globalModule.map {
      info(l"onNewToken: ${redactedString(s)}")
      _.tokenService.setNewToken()
    } (Threading.Background)
  }

  /**
    * According to the docs, we have 10 seconds to process notifications upon receiving the `remoteMessage`.
    * it is sometimes not enough time to process everything - leading to missing messages!
    */
  override def onMessageReceived(remoteMessage: RemoteMessage): Unit = {
    if (WireApplication.ensureInitialized()) processRemoteMessage(remoteMessage)
  }

  private def processRemoteMessage(remoteMessage: RemoteMessage): Unit = {
    getData(remoteMessage).foreach { data =>
      verbose(l"processing remote message with data: ${redactedString(data.toString())}")
      implicit val context: Context = this
      implicit val ec: EventContext = EventContext.Global

      Option(ZMessaging.currentGlobal) match {
        case None =>
          warn(l"No ZMessaging global available - calling too early")
        case Some(globalModule) if !isSenderKnown(globalModule, remoteMessage.getFrom) =>
          warn(l"Received FCM notification from unknown sender: ${redactedString(remoteMessage.getFrom)}. Ignoring...")
        case _ => SecurityPolicyChecker.runBackgroundSecurityChecklist().foreach { allChecksPassed =>
          if (allChecksPassed) {
            getTargetAccount(data) match {
              case None =>
                warn(l"User key missing msg: ${redactedString(UserKeyMissingMsg)}")
                tracking.exception(new Exception(UserKeyMissingMsg), UserKeyMissingMsg)
              case Some(account) =>
                targetAccountExists(account).foreach {
                  case false =>
                    warn(l"Could not find target account for notification")
                  case true =>
                    accounts.getZms(account).foreach {
                      case None => warn(l"Couldn't instantiate zms instance")
                      case Some(zms) => FCMHandler(zms, data, Instant.ofEpochMilli(remoteMessage.getSentTime))
                    }
                }
            }
          }
        }
      }
    }
  }

  private def getData(remoteMessage: RemoteMessage): Option[Map[String, String]] = {
    Option(remoteMessage.getData).map(_.asScala.toMap)
  }

  private def isSenderKnown(globalModule: GlobalModule, pushSenderId: String): Boolean = {
    globalModule.backend.pushSenderId == pushSenderId
  }

  private def getTargetAccount(data: Map[String, String]): Option[UserId] = {
    data.get(UserKey).map(UserId)
  }

  private def targetAccountExists(userId: UserId): Future[Boolean] = {
    accounts.accountsWithManagers.head.map(_.contains(userId))
  }

  /**
    * Called when the device hasn't connected to the FCM server in over 1 month, or there are more than 100 FCM
    * messages available for this device on the FCM servers.
    *
    * Since we have our own missing notification tracking on websocket, we should be able to ignore this.
    */
  override def onDeletedMessages(): Unit = warn(l"onDeleteMessages")
}

object FCMHandlerService {

  val UserKeyMissingMsg = "Notification did not contain user key - discarding"

  class FCMHandler(userId:    UserId,
                   accounts:  AccountsService,
                   push:      PushService,
                   network:   NetworkModeService,
                   fcmPushes: FCMNotificationStatsService) extends DerivedLogTag {

    import com.waz.model.FCMNotification.Pushed
    import com.waz.threading.Threading.Implicits.Background

    def handleMessage(data: Map[String, String]): Future[Unit] = {
      data match {
        case NoticeNotification(nId) =>
          addNotificationToProcess(Some(nId))
        case _ =>
          warn(l"Unexpected notification, sync anyway")
          addNotificationToProcess(None)
      }
    }

    private def addNotificationToProcess(nId: Option[Uid]): Future[Unit] =
      for {
        false <- accounts.accountState(userId).map(_ == InForeground).head
        drift <- push.beDrift.head
        now   =  clock.instant + drift
        idle  =  network.isDeviceIdleMode
        _     <- nId match {
                   case Some(n) => fcmPushes.markNotificationsWithState(Set(n), Pushed)
                   case _       => Future.successful(())
                 }

        /**
          * Warning: Here we want to trigger a direct fetch if we are in doze mode - when we get an FCM in doze mode, it is
          * unlikely that we are competing with other apps for CPU time, and we need to do the request ASAP while we have
          * network connectivity. TODO There is still the chance we can miss messages though
          *
          * When not in doze mode, we want to handle the case where the device might be overwhelmed by lots of apps coming
          * online at once. For that reason, we start a job which can run for as long as we need to avoid the app from being
          * killed mid-processing messages.
          */
        _ <- if (idle) push.syncNotifications(SyncHistory(FetchFromIdle(nId)))
              else Serialized.future("fetch")(Future(FetchJob(userId, nId)))
      } yield {}
  }

  object FCMHandler {

    private val handlers = scala.collection.mutable.HashMap[UserId, FCMHandler]()

    def apply(zms: ZMessaging, data: Map[String, String], sentTime: Instant): Unit =
      handlers.getOrElseUpdate(
        zms.selfUserId,
        new FCMHandler(zms.selfUserId, zms.accounts, zms.push, zms.network, zms.fcmNotStatsService)
      ).handleMessage(data)
  }

  val DataKey = "data"
  val UserKey = "user"
  val TypeKey = "type"

  object NoticeNotification {
    def unapply(data: Map[String, String]): Option[Uid] =
      (data.get(TypeKey), data.get(DataKey)) match {
        case (Some("notice"), Some(content)) => Try(JsonDecoder.decodeUid('id)(new json.JSONObject(content))).toOption
        case _ => None
      }
  }
}
