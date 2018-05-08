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
package com.waz.zclient.notifications.controllers

import android.app.{Notification, NotificationManager, PendingIntent}
import android.graphics.{Bitmap, Color}
import android.support.v4.app.NotificationCompat
import com.waz.ZLog._
import com.waz.bitmap.BitmapUtils
import com.waz.model.{ConvId, UserId}
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.call.CallInfo.CallState
import com.waz.service.call.CallInfo.CallState._
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest.Regular
import com.waz.utils.LoggedTry
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.{Context, Intent}
import com.waz.zclient.Intents.OpenCallingScreen
import com.waz.zclient._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.views.ImageController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DeprecationUtils
import com.waz.zms.CallWakeService

class CallingNotificationsController(implicit cxt: WireContext, eventContext: EventContext, inj: Injector) extends Injectable {

  import CallingNotificationsController._

  val callImageSizePx = toPx(CallImageSizeDp)

  val notificationManager = inject[NotificationManager]

  val callCtrler = inject[CallController]
  import callCtrler._

  isCallActive.on(Threading.Ui) {
    case (false) =>
      notificationManager.cancel(ZETA_CALL_ONGOING_NOTIFICATION_ID)
      notificationManager.cancel(ZETA_CALL_INCOMING_NOTIFICATION_ID)
    case _ => //
  }

  val bitmap =
    (for {
      z        <- callingZms
      Some(id) <- callerData.map(_.picture)
      bitmap   <- inject[ImageController].imageSignal(z, id, Regular(callImageSizePx))
    } yield
      bitmap match {
        case BitmapLoaded(bmp, _) => Option(BitmapUtils.createRoundBitmap(bmp, callImageSizePx, 0, Color.TRANSPARENT))
        case _ => None
      })
    .orElse(Signal.const(Option.empty[Bitmap]))

  (for {
    z          <- callingZms
    conv       <- conversation
    callerName <- callerData.map(_.name)
    state      <- callState
    group      <- isGroupCall
    video      <- isVideoCall
    bmp        <- bitmap
  } yield (z.selfUserId, conv, callerName, state, group, video, bmp)).on(Threading.Ui) {
    case (account, conv, callerName, state, group, video, bmp) =>
      val message = getCallStateMessage(state, video)
      val title = if (group) getString(R.string.system_notification__group_call_title, callerName, conv.displayName) else conv.displayName

      val builder = DeprecationUtils.getBuilder(cxt)
        .setSmallIcon(R.drawable.ic_menu_logo)
        .setLargeIcon(bmp.orNull)
        .setContentTitle(title)
        .setContentText(message)
        .setContentIntent(OpenCallingScreen())
        .setStyle(new NotificationCompat.BigTextStyle()
          .setBigContentTitle(title)
          .bigText(message))
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)

      state match {
        case OtherCalling => //not in a call, silence or join
          val silence = silenceIntent(account, conv.id)
          builder
            .addAction(R.drawable.ic_menu_silence_call_w, getString(R.string.system_notification__silence_call), silence)
            .addAction(R.drawable.ic_menu_join_call_w, getString(R.string.system_notification__join_call), if (group) joinGroupIntent(account, conv.id) else joinIntent(account, conv.id))
            .setDeleteIntent(silence)

        case SelfConnected |
             SelfCalling |
             SelfJoining => //in a call, leave
          builder.addAction(R.drawable.ic_menu_end_call_w, getString(R.string.system_notification__leave_call), leaveIntent(account, conv.id))

        case _ => //no available action
      }

      def buildNotification = {
        val notification = builder.build
        DeprecationUtils.setPriority(notification, DeprecationUtils.PRIORITY_MAX)
        if (state != OtherCalling) notification.flags |= Notification.FLAG_NO_CLEAR
        notification
      }

      def showNotification() = {
        notificationManager.cancel(ZETA_CALL_INCOMING_NOTIFICATION_ID)
        notificationManager.cancel(ZETA_CALL_ONGOING_NOTIFICATION_ID)
        notificationManager.notify(if (OtherCalling == state) ZETA_CALL_INCOMING_NOTIFICATION_ID else ZETA_CALL_ONGOING_NOTIFICATION_ID, buildNotification)
      }

      LoggedTry(showNotification()).recover { case e =>
        error(s"Notify failed: try without bitmap. Error: $e")
        builder.setLargeIcon(null)
        try showNotification()
        catch {
          case e: Throwable => error("second display attempt failed, aborting")
        }
      }
  }

  private def getCallStateMessage(state: CallState, isVideoCall: Boolean): String = state match {
    case SelfCalling |
         SelfJoining => if (isVideoCall) getString(R.string.system_notification__outgoing_video) else getString(R.string.system_notification__outgoing)
    case OtherCalling => if (isVideoCall) getString(R.string.system_notification__incoming_video) else getString(R.string.system_notification__incoming)
    case SelfConnected => getString(R.string.system_notification__ongoing)
    case _ => ""

  }

  private def silenceIntent(account: UserId, convId: ConvId) = pendingIntent(SilenceRequestCode, CallWakeService.silenceIntent(Context.wrap(cxt), account, convId))

  private def leaveIntent(account: UserId, convId: ConvId) = pendingIntent(LeaveRequestCode, CallWakeService.leaveIntent(Context.wrap(cxt), account, convId))

  private def joinIntent(account: UserId, convId: ConvId) = pendingIntent(JoinRequestCode, CallWakeService.joinIntent(Context.wrap(cxt), account, convId))
  private def joinGroupIntent(account: UserId, convId: ConvId) = pendingIntent(JoinRequestCode, CallWakeService.joinGroupIntent(Context.wrap(cxt), account, convId))

  private def pendingIntent(reqCode: Int, intent: Intent) = PendingIntent.getService(cxt, reqCode, Intent.unwrap(intent), PendingIntent.FLAG_UPDATE_CURRENT)
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
