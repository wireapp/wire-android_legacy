/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
/**
  * Wire
  * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.notifications.controllers

import android.app.{Notification, NotificationManager, PendingIntent}
import com.waz.utils.wrappers.{AndroidIntentBuilder, ContextBuilder, Intent}
import android.support.v4.app.NotificationCompat
import com.waz.ZLog._
import com.waz.api.VoiceChannelState
import com.waz.api.VoiceChannelState._
import com.waz.bitmap.BitmapUtils
import com.waz.model.VoiceChannelData.ChannelState
import com.waz.model.{AssetData, ConvId}
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest.Regular
import com.waz.utils.LoggedTry
import com.waz.utils.events.{EventContext, Signal}
//import com.waz.utils.wrappers.AndroidIntentBuilder._
//import com.waz.utils.wrappers.ContextBuilder._
import com.waz.zclient.calling.controllers.GlobalCallingController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.IntentUtils.getNotificationAppLaunchIntent
import com.waz.zclient.{Injectable, Injector, R, WireContext}
import com.waz.zms.CallService

class CallingNotificationsController(implicit cxt: WireContext, eventContext: EventContext, inj: Injector) extends Injectable {

  import CallingNotificationsController._

  val callImageSizePx = toPx(CallImageSizeDp)

  val notificationManager = inject[NotificationManager]

  val callCtrler = inject[GlobalCallingController]
  import callCtrler._

  (for {
    active <- activeCall
    state <- callStateOpt
  } yield (active, state)).on(Threading.Ui) {
    case (true, Some(OTHER_CALLING)) => notificationManager.cancel(ZETA_CALL_INCOMING_NOTIFICATION_ID)
    case (true, _) => notificationManager.cancel(ZETA_CALL_ONGOING_NOTIFICATION_ID)
    case (false, _) =>
      notificationManager.cancel(ZETA_CALL_ONGOING_NOTIFICATION_ID)
      notificationManager.cancel(ZETA_CALL_INCOMING_NOTIFICATION_ID)
  }

  //TODO use image controller when available from messages rewrite branch
  val bitmap = zms.zip(callerData.map(_.picture)).flatMap {
    case (zms, Some(imageId)) => zms.assetsStorage.signal(imageId).flatMap {
      case data @ AssetData.IsImage() => BitmapSignal(data, Regular(callImageSizePx), zms.imageLoader, zms.imageCache)
      case _ => Signal.empty[BitmapResult]
    }
    case _ => Signal.empty[BitmapResult]
  }.map {
    case BitmapLoaded(bmp, _) => Option(BitmapUtils.cropRect(bmp, callImageSizePx))
    case _ => None
  }

  (for {
    conv <- conversation
    callerName <- callerData.map(_.name)
    state <- callState
    group <- groupCall
    video <- videoCall
    bmp <- bitmap
  } yield (conv, callerName, state, group, video, bmp)).on(Threading.Ui) {
    case (conv, callerName, state, group, video, bmp) =>
      val message = getCallStateMessage(state, video)
      val title = if (group) getString(R.string.system_notification__group_call_title, callerName, conv.displayName) else conv.displayName

      val bigTextStyle = new NotificationCompat.BigTextStyle()
        .setBigContentTitle(conv.displayName)
        .bigText(message)
      val builder = new NotificationCompat.Builder(cxt)
        .setSmallIcon(R.drawable.ic_menu_logo)
        .setLargeIcon(bmp.orNull)
        .setContentTitle(title)
        .setContentText(message)
        .setContentIntent(getNotificationAppLaunchIntent(cxt))
        .setStyle(bigTextStyle)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)

      state match {
        case OTHER_CALLING |
             OTHERS_CONNECTED => //not in a call, silence or join
          val silence = silenceIntent(conv.id)
          builder
            .addAction(R.drawable.ic_menu_silence_call_w, getString(R.string.system_notification__silence_call), silence)
            .addAction(R.drawable.ic_menu_join_call_w, getString(R.string.system_notification__join_call), if (group) joinGroupIntent(conv.id) else joinIntent(conv.id))
            .setDeleteIntent(silence)

        case SELF_CONNECTED |
             SELF_CALLING |
             SELF_JOINING => //in a call, leave
          builder.addAction(R.drawable.ic_menu_end_call_w, getString(R.string.system_notification__leave_call), leaveIntent(conv.id))

        case _ => //no available action
      }

      def buildNotification = {
        val notification = builder.build
        notification.priority = Notification.PRIORITY_MAX
        if (ChannelState.isOngoing(state)) notification.flags |= Notification.FLAG_NO_CLEAR
        notification
      }

      def showNotification() =
        notificationManager.notify(if (ChannelState.isOngoing(state)) ZETA_CALL_ONGOING_NOTIFICATION_ID else ZETA_CALL_INCOMING_NOTIFICATION_ID, buildNotification)

      LoggedTry(showNotification()).recover { case e =>
        error(s"Notify failed: try without bitmap. Error: $e")
        builder.setLargeIcon(null)
        try showNotification()
        catch {
          case e: Throwable => error("second display attempt failed, aborting")
        }
      }
  }

  private def getCallStateMessage(state: VoiceChannelState, isVideoCall: Boolean): String = state match {
    case SELF_CALLING |
         SELF_JOINING => if (isVideoCall) getString(R.string.system_notification__outgoing_video) else getString(R.string.system_notification__outgoing)
    case OTHER_CALLING => if (isVideoCall) getString(R.string.system_notification__incoming_video) else getString(R.string.system_notification__incoming)
    case SELF_CONNECTED => getString(R.string.system_notification__ongoing)
    case _ => ""

  }

  private def silenceIntent(convId: ConvId) = pendingIntent(SilenceRequestCode, CallService.silenceIntent(ContextBuilder.wrap(cxt), convId))

  private def leaveIntent(convId: ConvId) = pendingIntent(LeaveRequestCode, CallService.leaveIntent(ContextBuilder.wrap(cxt), convId))

  private def joinIntent(convId: ConvId) = pendingIntent(JoinRequestCode, CallService.joinIntent(ContextBuilder.wrap(cxt), convId))
  private def joinGroupIntent(convId: ConvId) = pendingIntent(JoinRequestCode, CallService.joinGroupIntent(ContextBuilder.wrap(cxt), convId))

  private def pendingIntent(reqCode: Int, intent: Intent) = PendingIntent.getService(cxt, reqCode, AndroidIntentBuilder.unwrap(intent), PendingIntent.FLAG_UPDATE_CURRENT)
}

object CallingNotificationsController {
  val ZETA_CALL_INCOMING_NOTIFICATION_ID: Int = 1339273
  val ZETA_CALL_ONGOING_NOTIFICATION_ID: Int = 1339276
  val CallImageSizeDp = 64

  val JoinRequestCode = 8912
  val LeaveRequestCode = 8913
  val SilenceRequestCode = 8914
  private implicit val tag: LogTag = logTagFor[CallingNotificationsController]
}
