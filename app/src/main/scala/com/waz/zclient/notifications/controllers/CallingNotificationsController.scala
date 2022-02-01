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
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.{AccountsService, GlobalModule}
import com.waz.services.calling.CallWakeService._
import com.waz.services.calling.CallingNotificationsService
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils._
import com.wire.signals.{EventStream, Signal}
import com.waz.utils.wrappers.{Context, Intent}
import com.waz.zclient.Intents.{CallIntent, OpenCallingScreen}
import com.waz.zclient._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.{IncomingCallNotificationsChannelId, OngoingNotificationsChannelId}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.RingtoneUtils

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal
import com.waz.threading.Threading._
import com.waz.zclient.security.ActivityLifecycleCallback

final class CallingNotificationsController(implicit cxt: WireContext, inj: Injector) extends Injectable with DerivedLogTag {
  import CallingNotificationsController._

  private lazy val soundController     = inject[SoundController]
  private lazy val notificationManager = inject[NotificationManager]
  private lazy val callController      = inject[CallController]
  private lazy val convController      = inject[ConversationController]
  private lazy val notController       = inject[MessageNotificationsController]
  private lazy val activityLifecycle   = inject[ActivityLifecycleCallback]

  private val zmsInstances = inject[AccountsService].zmsInstances

  private val filteredGlobalProfile: Signal[(Option[ConvId], Seq[(ConvId, (UserId, UserId))])] =
    for {
      globalProfile <- inject[GlobalModule].calling.globalCallProfile
      curCallId     =  globalProfile.activeCall.map(_.convId)
      allCalls      =  globalProfile
                         .calls
                         .values
                         .filter(c => c.state == OtherCalling || (curCallId.contains(c.convId) && c.state != Ongoing))
                         .map(c => c.convId -> (c.caller, c.selfParticipant.qualifiedId.id))
                         .toSeq
    } yield (curCallId, allCalls)

  val notifications: Signal[Seq[CallNotification]] =
    for {
      zs                     <- zmsInstances
      (curCallId, allCallsF) <- filteredGlobalProfile
      bitmaps                <- Signal.sequence(allCallsF.map { case (conv, (_, account)) =>
                                  zs.find(_.selfUserId == account)
                                    .fold2(
                                      Signal.const(conv -> Option.empty[Bitmap]),
                                      _ => Signal.const(conv -> Option.empty[Bitmap]) // TODO: Use new assets engine to fetch the bitmap
                                    )
                                }: _*).map(_.toMap)
      notInfo                <- Signal.sequence(allCallsF.map { case (conv, (caller, account)) =>
                                  zs.find(_.selfUserId == account)
                                    .fold2(
                                      Signal.const(None, Name.Empty, None, false, Availability.Available),
                                      z => Signal.zip(
                                        z.calling.joinableCallsNotMuted.map(_.get(conv)),
                                        z.usersStorage.optSignal(caller).map(_.map(_.name).getOrElse(Name.Empty)),
                                        z.convsStorage.optSignal(conv),
                                        z.conversations.groupConversation(conv),
                                        z.usersStorage.optSignal(z.selfUserId).map(_.fold[Availability](Availability.Available)(_.availability))
                                      )).map(conv -> _)
                                }: _*)
      notInfoNames           <- Signal.sequence(notInfo.map(n => convController.conversationName(n._1).map(n._1 -> _)).toArray: _*)
      notInfoNamesMap         = notInfoNames.toMap
      notificationData        = notInfo.collect {
                                  case (convId, (Some(callInfo), title, conv, isGroup, availability)) =>
                                    val muteSet = conv.fold(MuteSet.AllMuted)(_.muted)
                                    val allowedByStatus = availability != Availability.Away && (availability != Availability.Busy || muteSet != MuteSet.AllMuted)
                                    val action  = (allowedByStatus, callInfo.state) match {
                                      case (false, _)                                     => NotificationAction.Nothing
                                      case (_, OtherCalling)                              => NotificationAction.DeclineOrJoin
                                      case (_, SelfConnected | SelfCalling | SelfJoining) => NotificationAction.Leave
                                      case _                                              => NotificationAction.Nothing
                                    }
                                    CallNotification(
                                      convId.str.hashCode,
                                      convId,
                                      callInfo.selfParticipant.qualifiedId.id,
                                      callInfo.startTime,
                                      title,
                                      notInfoNamesMap(convId),
                                      bitmaps.getOrElse(convId, None),
                                      curCallId.contains(convId),
                                      action,
                                      callInfo.isVideoCall,
                                      isGroup,
                                      allowedByStatus
                                    )
                                }
    } yield notificationData.sortWith {
      case (cn1, _) if curCallId.contains(cn1.convId) => false
      case (_, cn2) if curCallId.contains(cn2.convId) => true
      case (cn1, cn2)                                 => cn1.convId.str > cn2.convId.str
    }

  notifications.map(_.exists(n => !n.isMainCall && n.allowedByStatus)).onUi(soundController.playRingFromThemInCall)

  callController.currentCallOpt.map(_.isDefined).onUi {
    case true => cxt.startService(new content.Intent(cxt, classOf[CallingNotificationsService]))
    case _ =>
  }

  private val missedCall: EventStream[(UserId, NotificationData)] =
    for {
      zmsInstances <- EventStream.zip(EventStream.from(zmsInstances.head), EventStream.from(zmsInstances)) // TODO: make a change to `EventStream.from` in wire-signals
      missedCalls  =  zmsInstances.toSeq.map(_.messages.missedCall)
      call         <- EventStream.zip(missedCalls: _*)
    } yield
      (call.currentAccount,
       NotificationData(conv = call.convId, user = call.from, msgType = NotificationType.MISSED_CALL, time = call.time)
      )

  missedCall.onUi { case (selfId, not) =>
    (for {
      zmses       <- zmsInstances.head
      selfZms     =  zmses.find(_.selfUserId == selfId)
      self        <- selfZms.map(_.users.getSelfUser).getOrElse(Future.successful(None))
      av          =  self.map(_.availability).getOrElse(Availability.None)
      true        = av == Availability.None || av == Availability.Available
      curConvId   <- convController.currentConvIdOpt.head
      (inBack, _) <- activityLifecycle.appInBackground.head
      true        =  inBack || curConvId.isEmpty || !curConvId.contains(not.conv)
      conv        <- convController.getConversation(not.conv)
      true        =  conv.map(_.muted).getOrElse(MuteSet.AllAllowed) == MuteSet.AllAllowed
    } yield ()).foreach { _ =>
      notController.showNotifications(selfId, Set(not))
      notificationManager
        .getActiveNotifications
        .toSeq
        .filter { n =>
          val not = n.getNotification
          not.getChannelId == IncomingCallNotificationsChannelId || not.getChannelId == OngoingNotificationsChannelId
        }.foreach { n =>
          notificationManager.cancel(n.getId)
        }
    }
  }

  private def cancelNotifications(nots: Seq[CallingNotificationsController.CallNotification]): Unit = {
    val notsIds = nots.map(_.id).toSet
    val activeIds = notificationManager.getActiveNotifications.map(_.getId).toSet
    val toCancel = Future.successful(activeIds -- notsIds)

    toCancel.foreach(_.foreach(notificationManager.cancel(CallNotificationTag, _)))
  }

  notifications.map(_.filter(!_.isMainCall)).onUi { nots =>
    cancelNotifications(nots)
    nots.foreach { not =>
        val builder = androidNotificationBuilder(not)

        def showNotification(): Unit = notificationManager.notify(CallNotificationTag, not.id, builder.build())

        Try(showNotification()).recover {
          case NonFatal(e) =>
            error(l"Notify failed: try without bitmap", e)
            builder.setLargeIcon(null)
            Try(showNotification()).recover {
              case NonFatal(e2) => error(l"second display attempt failed, aborting", e2)
            }
        }
    }
  }

}

object CallingNotificationsController {
  final case class CallNotification(id:            Int,
                                    convId:        ConvId,
                                    accountId:     UserId,
                                    callStartTime: LocalInstant,
                                    caller:        Name,
                                    convName:      Name,
                                    bitmap:        Option[Bitmap],
                                    isMainCall:    Boolean,
                                    action:        NotificationAction,
                                    videoCall:     Boolean,
                                    isGroup:       Boolean,
                                    allowedByStatus: Boolean
                                   )

  object NotificationAction extends Enumeration {
    val DeclineOrJoin, Leave, Nothing = Value
  }

  type NotificationAction = NotificationAction.Value

  val CallNotificationTag = "call_notification"

  val isAndroid10OrAbove: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

  def androidNotificationBuilder(not: CallNotification, treatAsIncomingCall: Boolean = false)
                                (implicit cxt: content.Context): NotificationCompat.Builder = {
    val title = if (not.isGroup) not.convName else not.caller

    val message = (not.isGroup, not.videoCall) match {
      case (true, true)   => getString(R.string.system_notification__video_calling_group, not.caller)
      case (true, false)  => getString(R.string.system_notification__calling_group, not.caller)
      case (false, true)  => getString(R.string.system_notification__video_calling_one)
      case (false, false) => getString(R.string.system_notification__calling_one)
    }

    val style = new NotificationCompat.BigTextStyle()
      .setBigContentTitle(title)
      .bigText(message)

    val isIncomingCall = !not.isMainCall || treatAsIncomingCall
    val channelId = if (isIncomingCall) IncomingCallNotificationsChannelId else OngoingNotificationsChannelId
    val priority = if (isIncomingCall) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_MAX

    val builder = new NotificationCompat.Builder(cxt, channelId)
      .setSmallIcon(R.drawable.call_notification_icon)
      .setLargeIcon(not.bitmap.orNull)
      .setContentTitle(title)
      .setContentText(message)
      .setContentIntent(OpenCallingScreen())
      .setStyle(style)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(priority)
      .setOnlyAlertOnce(true)
      .setAutoCancel(true)

    if (!not.isMainCall) {
      builder.setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
      builder.setSound(RingtoneUtils.getUriForRawId(cxt, R.raw.empty_sound))
    } else if (isAndroid10OrAbove) {
      builder.setFullScreenIntent(OpenCallingScreen(), true)
    }

    not.action match {
      case NotificationAction.DeclineOrJoin =>
        builder
          .addAction(
            R.drawable.ic_menu_silence_call_w,
            getString(R.string.system_notification__silence_call),
            createEndIntent(not.accountId, not.convId)
          )
          .addAction(
            R.drawable.ic_menu_join_call_w,
            getString(R.string.system_notification__join_call),
            if (not.isMainCall) OpenCallingScreen() else CallIntent(not.accountId, not.convId)
          )

      case NotificationAction.Leave =>
        builder.addAction(
          R.drawable.ic_menu_end_call_w,
          getString(R.string.system_notification__leave_call),
          createEndIntent(not.accountId, not.convId)
        )

      case _ => //no available action
    }
    builder
  }

  private def createEndIntent(account: UserId, convId: ConvId)(implicit cxt: content.Context) =
    pendingIntent((account.str + convId.str).hashCode, endIntent(Context.wrap(cxt), account, convId))

  private def pendingIntent(reqCode: Int, intent: Intent)(implicit cxt: content.Context) =
    PendingIntent.getService(cxt, reqCode, Intent.unwrap(intent), PendingIntent.FLAG_UPDATE_CURRENT)
}
