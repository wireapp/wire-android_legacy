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

import android.app.{Notification, NotificationChannel, NotificationChannelGroup, NotificationManager}
import android.content.Context
import android.graphics.{Color, Typeface}
import android.net.Uri
import android.os.Build
import android.support.v4.app.NotificationCompat.Style
import android.support.v4.app.{NotificationCompat, RemoteInput}
import android.text.style.{ForegroundColorSpan, StyleSpan}
import android.text.{SpannableString, Spanned}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.{verbose, warn}
import com.waz.content.Preferences.PrefKey
import com.waz.content.UserPreferences
import com.waz.model.{ConvId, UserId}
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.Intents.{CallIntent, QuickReplyIntent}
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.{MessageNotificationsChannelId, PingNotificationsChannelId}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.{ResString, RingtoneUtils, format}
import com.waz.zclient.{Injectable, Injector, Intents, R}
import com.waz.zms.NotificationsAndroidService

import scala.collection.JavaConverters._
import scala.concurrent.Future

case class Span(style: Int, range: Range, offset: Int = 0)
trait Range
case class BodyRange(start: Int, end: Int) extends Range
object HeaderRange extends Range
object FullBodyRange extends Range

object Span {
  val ForegroundColorSpanBlack = 1
  val ForegroundColorSpanGray  = 2
  val StyleSpanBold            = 3
  val StyleSpanItalic          = 4
}

case class SpannableWrapper(header: ResString,
                            body: ResString,
                            spans: List[Span],
                            separator: String) {

  override def toString: String =
    format(className = "SpannableWrapper", oneLiner = true,
      "header"    -> Some(header),
      "body"      -> Some(body),
      "spans"     -> (if (spans.nonEmpty) Some(spans) else None),
      "separator" -> (if (separator.nonEmpty) Some(separator) else None)
    )

  def build(implicit cxt: Context): SpannableString = {
    val headerStr = header.resolve
    val bodyStr = body.resolve
    val wholeStr = headerStr + separator + bodyStr

    def range(span: Span) = span.range match {
      case HeaderRange => (0, headerStr.length)
      case FullBodyRange   => (headerStr.length + span.offset, wholeStr.length)
      case BodyRange(start, end) => (headerStr.length + span.offset + start, headerStr.length + span.offset + end)
    }

    def style(span: Span) = span.style match {
      case Span.ForegroundColorSpanBlack => new ForegroundColorSpan(Color.BLACK)
      case Span.ForegroundColorSpanGray  => new ForegroundColorSpan(Color.GRAY)
      case Span.StyleSpanBold            => new StyleSpan(Typeface.BOLD)
      case Span.StyleSpanItalic          => new StyleSpan(Typeface.ITALIC)
    }

    returning(new SpannableString(wholeStr)) { ss =>
      spans.map(span => (style(span), range(span))).foreach {
        case (style, (start, end)) if end > start => ss.setSpan(style, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        case _ =>
      }
    }
  }

  def +(span: Span): SpannableWrapper = copy(spans = this.spans ++ List(span))

  def +(sw: SpannableWrapper): SpannableWrapper = {
    val spans     = this.spans ++ sw.spans.map(span => if (span.range == HeaderRange) span.copy(range = FullBodyRange) else span)
    val body      = if (sw.header != ResString.Empty) sw.header else sw.body
    copy(spans = spans, body = body)
  }
}

object SpannableWrapper {
  def apply(header: ResString): SpannableWrapper =
    SpannableWrapper(header = header, body = ResString.Empty, spans = List.empty, separator = "")
  def apply(header: ResString, spans: List[Span]): SpannableWrapper =
    SpannableWrapper(header = header, body = ResString.Empty, spans = spans, separator = "")

  val Empty: SpannableWrapper = SpannableWrapper(ResString.Empty)
}

case class StyleBuilder(style: Int,
                        title: SpannableWrapper,
                        summaryText: Option[String] = None,
                        bigText: Option[SpannableWrapper] = None,
                        lines: List[SpannableWrapper] = List.empty) {
  override def toString: String =
    format(className = "StyleBuilder", oneLiner = true,
      "style"       -> Some(style),
      "title"       -> Some(title),
      "summaryText" -> summaryText,
      "bigText"     -> bigText,
      "lines"       -> (if (lines.nonEmpty) Some(lines) else None)
    )

  def build(implicit cxt: Context): Style = style match {
    case StyleBuilder.BigText =>
      returning(new NotificationCompat.BigTextStyle) { bts =>
        bts.setBigContentTitle(title.build)
        summaryText.foreach(bts.setSummaryText)
        bigText.map(_.build).foreach(bts.bigText(_))
      }
    case StyleBuilder.Inbox =>
      returning(new NotificationCompat.InboxStyle) { is =>
        is.setBigContentTitle(title.build)
        summaryText.foreach(is.setSummaryText)
        lines.map(_.build).foreach(is.addLine(_))
      }
    }
}

object StyleBuilder {
  val BigText = 1
  val Inbox   = 2
}

case class NotificationProps(accountId:                UserId,
                             when:                     Option[Long] = None,
                             showWhen:                 Option[Boolean] = None,
                             category:                 Option[String] = None,
                             priority:                 Option[Int] = None,
                             smallIcon:                Option[Int] = None,
                             contentTitle:             Option[SpannableWrapper] = None,
                             contentText:              Option[SpannableWrapper] = None,
                             style:                    Option[StyleBuilder] = None,
                             groupSummary:             Option[Boolean] = None,
                             group:                    Option[UserId] = None,
                             openAccountIntent:        Option[UserId] = None,
                             clearNotificationsIntent: Option[(UserId, Option[ConvId])] = None,
                             openConvIntent:           Option[(UserId, ConvId, Int)] = None,
                             contentInfo:              Option[String] = None,
                             color:                    Option[Int] = None,
                             vibrate:                  Option[Array[Long]] = None,
                             autoCancel:               Option[Boolean] = None,
                             sound:                    Option[Uri] = None,
                             onlyAlertOnce:            Option[Boolean] = None,
                             lights:                   Option[(Int, Int, Int)] = None,
                             largeIcon:                Option[Bitmap] = None,
                             action1:                  Option[(UserId, ConvId, Int, Boolean)] = None,
                             action2:                  Option[(UserId, ConvId, Int, Boolean)] = None,
                             lastIsPing:               Option[Boolean] = None
                            ) {
  override def toString: String =
    format(className = "NotificationProps", oneLiner = false,
      "when"                     -> when,
      "showWhen"                 -> showWhen,
      "category"                 -> category,
      "priority"                 -> priority,
      "smallIcon"                -> smallIcon,
      "contentTitle"             -> contentTitle,
      "contentText "             -> contentText,
      "style"                    -> style,
      "groupSummary"             -> groupSummary,
      "openAccountIntent"        -> openAccountIntent,
      "clearNotificationsIntent" -> clearNotificationsIntent,
      "openConvIntent"           -> openConvIntent,
      "contentInfo"              -> contentInfo,
      "vibrate"                  -> vibrate,
      "autoCancel"               -> autoCancel,
      "sound"                    -> sound,
      "onlyAlertOnce"            -> onlyAlertOnce,
      "lights"                   -> lights,
      "largeIcon"                -> largeIcon,
      "action1"                  -> action1,
      "action2"                  -> action2,
      "lastIsPing"               -> lastIsPing
    )

  def build()(implicit cxt: Context): Notification = {
    val channelId = if (lastIsPing.contains(true)) PingNotificationsChannelId(accountId) else MessageNotificationsChannelId(accountId)
    val builder = new NotificationCompat.Builder(cxt, channelId)

    when.foreach(builder.setWhen)
    showWhen.foreach(builder.setShowWhen)
    category.foreach(builder.setCategory)
    priority.foreach(builder.setPriority)
    smallIcon.foreach(builder.setSmallIcon)
    contentTitle.map(_.build).foreach(builder.setContentTitle)
    contentText.map(_.build).foreach(builder.setContentText)
    style.map(_.build).foreach(builder.setStyle)
    groupSummary.foreach { summary =>
      builder.setGroupSummary(summary)
      builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
    }
    group.foreach(accountId => builder.setGroup(accountId.str))

    openAccountIntent.foreach(userId => builder.setContentIntent(Intents.OpenAccountIntent(userId)))

    openConvIntent.foreach {
      case (accountId, convId, requestBase) => builder.setContentIntent(Intents.OpenConvIntent(accountId, convId, requestBase))
    }

    clearNotificationsIntent.foreach {
      case (uId, Some(convId)) =>
        builder.setDeleteIntent(NotificationsAndroidService.clearNotificationsIntent(uId, convId, cxt))
      case (uId, None) =>
        builder.setDeleteIntent(NotificationsAndroidService.clearNotificationsIntent(uId, cxt))
    }

    contentInfo.foreach(builder.setContentInfo)
    color.foreach(builder.setColor)
    vibrate.foreach(builder.setVibrate)
    autoCancel.foreach(builder.setAutoCancel)
    sound.foreach(builder.setSound)
    onlyAlertOnce.foreach(builder.setOnlyAlertOnce)
    lights.foreach { case (c, on, off) => builder.setLights(c, on, off) }
    largeIcon.foreach(bmp => builder.setLargeIcon(bmp))

    action1.map {
      case (userId, convId, requestBase, _) =>
        new NotificationCompat.Action.Builder(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(userId, convId, requestBase)).build()
    }.foreach(builder.addAction)

    action2.map {
      case (userId, convId, requestBase, bundleEnabled) => createQuickReplyAction(userId, convId, requestBase, bundleEnabled)
    }.foreach(builder.addAction)

    builder.build()
  }

  private def createQuickReplyAction(userId: UserId, convId: ConvId, requestCode: Int, bundleEnabled: Boolean)(implicit cxt: Context) = {
    if (bundleEnabled) {
      val remoteInput = new RemoteInput.Builder(NotificationsAndroidService.InstantReplyKey)
        .setLabel(getString(R.string.notification__action__reply))
        .build
      new NotificationCompat.Action.Builder(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), NotificationsAndroidService.quickReplyIntent(userId, convId, cxt))
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(true)
        .build()
    } else
      new NotificationCompat.Action.Builder(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), QuickReplyIntent(userId, convId, requestCode)).build()
  }
}

trait NotificationManagerWrapper {
  def getActiveNotificationIds: Seq[Int]
}

object NotificationManagerWrapper {

  val IncomingCallNotificationsChannelId = "INCOMING_CALL_NOTIFICATIONS_CHANNEL_ID"
  val OngoingNotificationsChannelId      = "STICKY_NOTIFICATIONS_CHANNEL_ID"

  def PingNotificationsChannelId(userId: UserId)         = s"PINGS_NOTIFICATIONS_CHANNEL_ID_${userId.str.hashCode}"
  def MessageNotificationsChannelId(userId: UserId)      = s"MESSAGE_NOTIFICATIONS_CHANNEL_ID_${userId.str.hashCode}"

  case class ChannelGroup(id: String, name: String, channels: Set[ChannelInfo])

  case class ChannelInfo(id: String, name: String, description: String, sound: Uri, vibration: Boolean)
  object ChannelInfo {
    def apply(id: String, name: Int, description: Int, sound: Uri, vibration: Boolean)(implicit cxt: Context): ChannelInfo = ChannelInfo(id, getString(name), getString(description), sound, vibration)
  }

  class AndroidNotificationsManager(notificationManager: NotificationManager)(implicit inj: Injector, cxt: Context, eventContext: EventContext) extends NotificationManagerWrapper with Injectable {

    val accountChannels = inject[AccountsService].accountManagers.flatMap(ams => Signal.sequence(ams.map { am =>

      def getSound(pref: PrefKey[String], default: Int): Future[Uri] =
        am.userPrefs.preference(pref).apply().map {
          case ""  => RingtoneUtils.getUriForRawId(cxt, default)
          case str => Uri.parse(str)
        } (Threading.Ui)

      for {
        msgSound <- Signal.future(getSound(UserPreferences.TextTone, R.raw.new_message_gcm))
        pingSound <- Signal.future(getSound(UserPreferences.PingTone, R.raw.ping_from_them))
        vibration <- Signal.future(am.userPrefs.preference(UserPreferences.VibrateEnabled).apply())
        channel <- am.storage.usersStorage.signal(am.userId).map(user => ChannelGroup(user.id.str, user.getDisplayName, Set(
            ChannelInfo(MessageNotificationsChannelId(am.userId), R.string.message_notifications_channel_name, R.string.message_notifications_channel_description, msgSound, vibration),
            ChannelInfo(PingNotificationsChannelId(am.userId), R.string.ping_notifications_channel_name, R.string.ping_notifications_channel_description, pingSound, vibration)
          )))
      } yield channel
    }.toSeq:_*))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      accountChannels { channels =>

        notificationManager.getNotificationChannels.asScala.filter { ch =>
          !channels.flatMap(_.channels).exists(_.id == ch.getId) && !Set(OngoingNotificationsChannelId, IncomingCallNotificationsChannelId).contains(ch.getId)
        }.foreach(ch => notificationManager.deleteNotificationChannel(ch.getId))

        notificationManager.getNotificationChannelGroups.asScala.filter { ch =>
          !channels.map(_.id).contains(ch.getId)
        }.foreach(ch => notificationManager.deleteNotificationChannelGroup(ch.getId))

        channels.foreach {
          case ChannelGroup(groupId, groupName, channelInfos) =>
            notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(groupId, groupName))
            channelInfos.foreach {
              case ChannelInfo(id, name, description, sound, vibration) =>
                notificationManager.createNotificationChannel(
                  returning(new NotificationChannel(id, name, NotificationManager.IMPORTANCE_MAX)) { ch =>
                    ch.setDescription(description)
                    ch.setShowBadge(true)
                    ch.enableVibration(vibration)
                    ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
                    ch.setSound(sound, Notification.AUDIO_ATTRIBUTES_DEFAULT)
                    ch.setGroup(groupId)
                    ch.enableLights(true)
                  })
          }
        }
      }

      notificationManager.createNotificationChannel(
        returning(new NotificationChannel(OngoingNotificationsChannelId, getString(R.string.ongoing_channel_name), NotificationManager.IMPORTANCE_LOW)) { ch =>
          ch.setDescription(getString(R.string.ongoing_channel_description))
          ch.enableVibration(false)
          ch.setShowBadge(false)
          ch.setSound(null, null)
        })

      notificationManager.createNotificationChannel(
        returning(new NotificationChannel(IncomingCallNotificationsChannelId, getString(R.string.incoming_call_notifications_channel_name), NotificationManager.IMPORTANCE_HIGH)) { ch =>
          ch.setDescription(getString(R.string.ongoing_channel_description))
          ch.enableVibration(false)
          ch.setShowBadge(false)
          ch.setSound(null, null)
        })
    }

    private val controller = inject[MessageNotificationsController]

    controller.notificationsToCancel.onUi { ids =>
      verbose(s"cancel: $ids")
      ids.foreach(notificationManager.cancel)
    }

    controller.notificationToBuild.onUi { case (id, props) =>
      verbose(s"build: $id")

      notificationManager.notify(id, props.build())
    }

    override def getActiveNotificationIds: Seq[Int] =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        notificationManager.getActiveNotifications.toSeq.map(_.getId)
      else {
        warn(s"Tried to access method getActiveNotifications from api level: ${Build.VERSION.SDK_INT}")
        Seq.empty
      }

    def getNotificationChannel(channelId: String) = notificationManager.getNotificationChannel(channelId)
  }
}

