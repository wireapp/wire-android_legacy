/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

import android.app.{NotificationManager, PendingIntent}
import android.content
import android.graphics.{Bitmap, Color}
import android.os.Build
import android.support.v4.app.NotificationCompat
import com.waz.bitmap.BitmapUtils
import com.waz.content.UserPreferences
import com.waz.model._
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.{AccountManager, AccountsService, GlobalModule, ZMessaging}
import com.waz.services.calling.CallWakeService._
import com.waz.services.calling.CallingNotificationsService
import com.waz.threading.Threading.Implicits.Background
import com.waz.ui.MemoryImageCache.BitmapRequest.Regular
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.{Context, Intent}
import com.waz.utils._
import com.waz.zclient.Intents.{CallIntent, OpenCallingScreen}
import com.waz.zclient._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.views.ImageController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.{IncomingCallNotificationsChannelId, OngoingNotificationsChannelId}
import com.waz.zclient.utils.ContextUtils.{getString, _}
import com.waz.zclient.utils.RingtoneUtils

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

class CallingNotificationsController(implicit cxt: WireContext, eventContext: EventContext, inj: Injector) extends Injectable {

  import CallingNotificationsController._

  val callImageSizePx = toPx(CallImageSizeDp)

  val notificationManager = inject[NotificationManager]

  val callCtrler = inject[CallController]

  import callCtrler._

  val filteredGlobalProfile: Signal[(Option[ConvId], Seq[(ConvId, (UserId, UserId))])] =
    for {
      globalProfile <- inject[GlobalModule].calling.globalCallProfile
      curCallId     =  globalProfile.activeCall.map(_.convId)
      allCalls      =  globalProfile
                         .calls
                         .values
                         .filter(c => c.state == OtherCalling || (curCallId.contains(c.convId) && c.state != Ongoing))
                         .map(c => c.convId -> (c.caller, c.account))
                         .toSeq
    } yield (curCallId, allCalls)

  val notifications =
    for {
      zs                     <- inject[AccountsService].zmsInstances
      (curCallId, allCallsF) <- filteredGlobalProfile
      bitmaps                <- Signal.sequence(allCallsF.map { case (conv, (caller, account)) =>
                                  zs.find(_.selfUserId == account)
                                    .fold2(
                                      Signal.const(conv -> Option.empty[Bitmap]),
                                      z => getBitmapSignal(z, caller).map(conv -> _)
                                    )
                                }: _*).map(_.toMap)
      notInfo                <- Signal.sequence(allCallsF.map { case (conv, (caller, account)) =>
                                  zs.find(_.selfUserId == account)
                                    .fold2(
                                      Signal.const(None, Name.Empty, None, false, Availability.None),
                                      z => Signal(
                                        z.calling.joinableCallsNotMuted.map(_.get(conv)),
                                        z.usersStorage.optSignal(caller).map(_.map(_.name).getOrElse(Name.Empty)),
                                        z.convsStorage.optSignal(conv),
                                        z.conversations.groupConversation(conv),
                                        z.usersStorage.optSignal(z.selfUserId).map(_.fold[Availability](Availability.None)(_.availability))
                                      )).map(conv -> _)
                                }: _*)
      notificationData        = notInfo.collect {
                                  case (convId, (Some(callInfo), title, conv, isGroup, availability)) =>
                                    val muteSet = conv.fold(MuteSet.AllMuted)(_.muted)
                                    val action  = (availability, muteSet, callInfo.state) match {
                                      case (Availability.Away, _, _)                         => NotificationAction.Nothing
                                      case (Availability.Busy, MuteSet.AllMuted, _)          => NotificationAction.Nothing
                                      case (_, _, OtherCalling)                              => NotificationAction.DeclineOrJoin
                                      case (_, _, SelfConnected | SelfCalling | SelfJoining) => NotificationAction.Leave
                                      case _                                                 => NotificationAction.Nothing
                                    }
                                    CallNotification(
                                      convId.str.hashCode,
                                      convId,
                                      callInfo.account,
                                      callInfo.startTime,
                                      title,
                                      conv.fold(Name.Empty)(_.displayName),
                                      bitmaps.getOrElse(convId, None),
                                      curCallId.contains(convId),
                                      action,
                                      callInfo.isVideoCall,
                                      isGroup
                                    )
                                }
    } yield notificationData.sortWith {
      case (cn1, _) if curCallId.contains(cn1.convId) => false
      case (_, cn2) if curCallId.contains(cn2.convId) => true
      case (cn1, cn2)                                 => cn1.convId.str > cn2.convId.str
    }

  private lazy val currentNotificationsPref = inject[Signal[AccountManager]].map(_.userPrefs(UserPreferences.CurrentNotifications))

  notifications.map(_.exists(!_.isMainCall)).onUi(soundController.playRingFromThemInCall)

  callCtrler.currentCallOpt.map(_.isDefined).onUi {
    case true => cxt.startService(new content.Intent(cxt, classOf[CallingNotificationsService]))
    case _ =>
  }

  private def cancelNots(nots: Seq[CallingNotificationsController.CallNotification]): Unit = {
    val notsIds = nots.map(_.id).toSet
    verbose(l"cancelNots($notsIds)")
    val toCancel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val activeIds = notificationManager.getActiveNotifications.map(_.getId).toSet
      Future.successful(activeIds -- notsIds)
    } else
      for {
        pref      <- currentNotificationsPref.head
        activeIds <- pref.apply()
        _         <-  pref := Set.empty[Int]
      } yield activeIds -- notsIds

    toCancel.foreach(_.foreach(notificationManager.cancel(CallNotificationTag, _)))
  }

  notifications.map(_.filter(!_.isMainCall)).onUi { nots =>
    verbose(l"${nots.size} call notifications")

    cancelNots(nots)
    nots.foreach {
      case not if not.action != NotificationAction.Nothing =>
        val builder = androidNotificationBuilder(not)

        def showNotification() = {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            verbose(l"Adding not: ${not.id}")
            currentNotificationsPref.head.foreach(_.mutate(_ + not.id))
          }
          notificationManager.notify(CallNotificationTag, not.id, builder.build())
        }

        Try(showNotification()).recover {
          case NonFatal(e) =>
            error(l"Notify failed: try without bitmap", e)
            builder.setLargeIcon(null)
            Try(showNotification()).recover {
              case NonFatal(e2) => error(l"second display attempt failed, aborting", e2)
            }
        }
      case _ =>
    }
  }

  private def getBitmapSignal(z: ZMessaging, caller: UserId) = for {
      Some(id) <- z.usersStorage.optSignal(caller).map(_.flatMap(_.picture))
      bitmap   <- inject[ImageController].imageSignal(z, id, Regular(callImageSizePx))
    } yield
      bitmap match {
        case BitmapLoaded(bmp, _) => Option(BitmapUtils.createRoundBitmap(bmp, callImageSizePx, 0, Color.TRANSPARENT))
        case _ => None
      }
}

object CallingNotificationsController {

  case class CallNotification(id:            Int,
                              convId:        ConvId,
                              accountId:     UserId,
                              callStartTime: LocalInstant,
                              caller:        Name,
                              convName:      Name,
                              bitmap:        Option[Bitmap],
                              isMainCall:    Boolean,
                              action:        NotificationAction,
                              videoCall:     Boolean,
                              isGroup:       Boolean)


  object NotificationAction extends Enumeration {
    val DeclineOrJoin, Leave, Nothing = Value
  }
  type NotificationAction = NotificationAction.Value

  val CallNotificationTag = "call_notification"

  val CallImageSizeDp = 64

  def androidNotificationBuilder(not: CallNotification)(implicit cxt: content.Context): NotificationCompat.Builder = {
    val title = if (not.isGroup) not.convName else not.caller
    val message = (not.isGroup, not.videoCall) match {
      case (true, true)   => getString(R.string.system_notification__video_calling_group, not.caller)
      case (true, false)  => getString(R.string.system_notification__calling_group, not.caller)
      case (false, true)  => getString(R.string.system_notification__video_calling_one)
      case (false, false) => getString(R.string.system_notification__calling_one)
    }

    val builder = new NotificationCompat.Builder(cxt, if (not.isMainCall) OngoingNotificationsChannelId else IncomingCallNotificationsChannelId)
      .setSmallIcon(R.drawable.call_notification_icon)
      .setLargeIcon(not.bitmap.orNull)
      .setContentTitle(title)
      .setContentText(message)
      .setContentIntent(OpenCallingScreen())
      .setStyle(new NotificationCompat.BigTextStyle()
        .setBigContentTitle(title)
        .bigText(message))
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(if (not.isMainCall) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_MAX) //incoming calls go higher up in the list)
      .setOnlyAlertOnce(true)
      .setOngoing(true)

    if (!not.isMainCall) {
      builder.setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
      builder.setSound(RingtoneUtils.getUriForRawId(cxt, R.raw.empty_sound))
    }

    not.action match {
      case NotificationAction.DeclineOrJoin =>
        builder
          .addAction(R.drawable.ic_menu_silence_call_w, getString(R.string.system_notification__silence_call), createEndIntent(not.accountId, not.convId))
          .addAction(R.drawable.ic_menu_join_call_w, getString(R.string.system_notification__join_call), if (not.isMainCall) createJoinIntent(not.accountId, not.convId) else CallIntent(not.accountId, not.convId))

      case NotificationAction.Leave =>
        builder.addAction(R.drawable.ic_menu_end_call_w, getString(R.string.system_notification__leave_call), createEndIntent(not.accountId, not.convId))

      case _ => //no available action
    }
    builder
  }

  def createJoinIntent(account: UserId, convId: ConvId)(implicit cxt: content.Context) = pendingIntent((account.str + convId.str).hashCode, joinIntent(Context.wrap(cxt), account, convId))
  def createEndIntent(account: UserId, convId: ConvId)(implicit cxt: content.Context) = pendingIntent((account.str + convId.str).hashCode, endIntent(Context.wrap(cxt), account, convId))

  def pendingIntent(reqCode: Int, intent: Intent)(implicit cxt: content.Context) = PendingIntent.getService(cxt, reqCode, Intent.unwrap(intent), PendingIntent.FLAG_UPDATE_CURRENT)
}
