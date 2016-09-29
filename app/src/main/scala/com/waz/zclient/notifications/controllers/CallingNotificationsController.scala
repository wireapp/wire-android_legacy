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
package com.waz.zclient.notifications.controllers

import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.Intent
import android.support.v4.app.NotificationCompat
import com.waz.api.VoiceChannelState._
import com.waz.api.{KindOfCall, VoiceChannelState}
import com.waz.bitmap.BitmapUtils
import com.waz.model.{ConvId, ImageAssetData}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapRequest.Regular
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.calling.controllers.GlobalCallingController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.IntentUtils.getNotificationAppLaunchIntent
import com.waz.zclient.{Injectable, Injector, R, WireContext}
import com.waz.zms.CallService

class CallingNotificationsController(implicit cxt: WireContext, eventContext: EventContext, inj: Injector) extends Injectable {

  import CallingNotificationsController._

  val callImageSizePx = toPx(CallImageSizeDp)

  val notificationManager = inject[NotificationManager]

  val zms = inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
  val users = zms.map(_.usersStorage)
  val convs = zms.map(_.convsStorage)
  val callCtrler = inject[GlobalCallingController]

  callCtrler.currentChannel.on(Threading.Ui) {
    case Some(data) if data.ongoing => notificationManager.cancel(ZETA_CALL_INCOMING_NOTIFICATION_ID)
    case Some(data) if !data.ongoing => notificationManager.cancel(ZETA_CALL_ONGOING_NOTIFICATION_ID)
    case None =>
      notificationManager.cancel(ZETA_CALL_ONGOING_NOTIFICATION_ID)
      notificationManager.cancel(ZETA_CALL_INCOMING_NOTIFICATION_ID)
  }

  val currentChannel = callCtrler.currentChannel.collect { case Some(data) => data }

  val caller = users.zip(currentChannel.map(_.caller).collect { case Some(id) => id }).flatMap {
    case (users, id) => users.signal(id)
  }

  val conv = convs.zip(currentChannel.map(_.id)).flatMap {
    case (convs, id) => convs.signal(id)
  }

  //TODO use image controller when available from messages rewrite branch
  val bitmap = zms.zip(caller.map(_.picture)).flatMap {
    case (zms, Some(imageId)) => zms.assetsStorage.signal(imageId).flatMap {
      case data: ImageAssetData => BitmapSignal(data, Regular(callImageSizePx), zms.imageLoader, zms.imageCache)
      case _ => Signal.empty[BitmapResult]
    }
    case _ => Signal.empty[BitmapResult]
  }.map {
    case BitmapLoaded(bmp, _, _) => Option(BitmapUtils.cropRect(bmp, callImageSizePx))
    case _ => None
  }

  Signal(conv.map(_.displayName), caller.map(_.name), currentChannel, bitmap).on(Threading.Ui) {
    case (conv, caller, data, bitmap) =>
      val message = getCallStateMessage(data.state, data.video.isVideoCall)
      val title = if (data.tracking.kindOfCall == KindOfCall.GROUP) getString(R.string.system_notification__group_call_title, caller, conv) else conv

      val bigTextStyle = new NotificationCompat.BigTextStyle()
        .setBigContentTitle(conv)
        .bigText(message)
      val builder = new NotificationCompat.Builder(cxt)
        .setSmallIcon(R.drawable.ic_menu_logo)
        .setLargeIcon(bitmap.orNull)
        .setContentTitle(title)
        .setContentText(message)
        .setContentIntent(getNotificationAppLaunchIntent(cxt))
        .setStyle(bigTextStyle)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)

      data.state match {
        case OTHER_CALLING |
             OTHERS_CONNECTED => //not in a call, silence or join
          val silence = silenceIntent(data.id)
          builder
            .addAction(R.drawable.ic_menu_silence_call_w, getString(R.string.system_notification__silence_call), silence)
            .addAction(R.drawable.ic_menu_join_call_w, getString(R.string.system_notification__join_call), joinIntent(data.id))
            .setDeleteIntent(silence)

        case SELF_CONNECTED |
             SELF_CALLING |
             SELF_JOINING => //in a call, leave
          builder.addAction(R.drawable.ic_menu_end_call_w, getString(R.string.system_notification__leave_call), leaveIntent(data.id))

        case _ => //no available action
      }


      val notification = builder.build
      notification.priority = Notification.PRIORITY_MAX
      if (data.ongoing) notification.flags |= Notification.FLAG_NO_CLEAR
      notificationManager.notify(if (data.ongoing) ZETA_CALL_ONGOING_NOTIFICATION_ID else ZETA_CALL_INCOMING_NOTIFICATION_ID, notification)
  }

  private def getCallStateMessage(state: VoiceChannelState, isVideoCall: Boolean): String = state match {
    case SELF_CALLING |
         SELF_JOINING => if (isVideoCall) getString(R.string.system_notification__outgoing_video) else getString(R.string.system_notification__outgoing)
    case OTHER_CALLING => if (isVideoCall) getString(R.string.system_notification__incoming_video) else getString(R.string.system_notification__incoming)
    case SELF_CONNECTED => getString(R.string.system_notification__ongoing)
    case _ => ""

  }

  private def silenceIntent(convId: ConvId) = pendingIntent(SilenceRequestCode, CallService.silenceIntent(cxt, convId))

  private def leaveIntent(convId: ConvId) = pendingIntent(LeaveRequestCode, CallService.leaveIntent(cxt, convId))

  private def joinIntent(convId: ConvId) = pendingIntent(JoinRequestCode, CallService.joinIntent(cxt, convId))

  private def pendingIntent(reqCode: Int, intent: Intent) = PendingIntent.getService(cxt, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}

object CallingNotificationsController {
  val ZETA_CALL_INCOMING_NOTIFICATION_ID: Int = 1339273
  val ZETA_CALL_ONGOING_NOTIFICATION_ID: Int = 1339276
  val CallImageSizeDp = 64

  val JoinRequestCode = 8912
  val LeaveRequestCode = 8913
  val SilenceRequestCode = 8914
}
