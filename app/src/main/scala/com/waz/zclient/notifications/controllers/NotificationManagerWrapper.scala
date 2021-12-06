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

import android.app.{Notification, NotificationChannel, NotificationManager}
import android.content.Context
import android.graphics.{Color, Typeface}
import android.net.Uri
import android.os.Bundle
import android.text.style.{ForegroundColorSpan, StyleSpan}
import android.text.{SpannableString, Spanned}
import androidx.core.app.NotificationCompat.Style
import androidx.core.app.{NotificationCompat, NotificationCompatExtras, RemoteInput}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.services.notifications.NotificationsHandlerService
import com.waz.utils.returning
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.Intents.CallIntent
import com.waz.zclient.notifications.controllers.MessageNotificationsController.toNotificationConvId
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.{MessageNotificationsChannelId, PingNotificationsChannelId}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.{ResString, format}
import com.waz.zclient.{Injectable, Injector, Intents, R}

final case class Span(style: Int, range: Int, offset: Int = 0)

object Span {
  val ForegroundColorSpanBlack = 1
  val ForegroundColorSpanGray  = 2
  val StyleSpanBold            = 3
  val StyleSpanItalic          = 4

  val HeaderRange = 1
  val BodyRange   = 2
}

final case class SpannableWrapper(header: ResString,
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
      case Span.HeaderRange => (0, headerStr.length)
      case Span.BodyRange   => (headerStr.length + span.offset, wholeStr.length)
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
    val spans     = this.spans ++ sw.spans.map(span => if (span.range == Span.HeaderRange) span.copy(range = Span.BodyRange) else span)
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

final case class StyleBuilder(style: Int,
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

final case class NotificationProps(accountId:                UserId,
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
                                   action1:                  Option[(UserId, ConvId, Int)] = None,
                                   action2:                  Option[(UserId, ConvId, Int)] = None,
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
      "contentText"              -> contentText,
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
    val channelId = if (lastIsPing.contains(true)) PingNotificationsChannelId else MessageNotificationsChannelId
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
      builder.addExtras(returning(new Bundle()) { bundle =>
        bundle.putBoolean(NotificationCompatExtras.EXTRA_GROUP_SUMMARY, summary)
      })
    }

    builder.setGroup(group.fold("")(_.str))

    openAccountIntent.foreach(userId => builder.setContentIntent(Intents.OpenAccountIntent(userId)))

    openConvIntent.foreach {
      case (accountId, convId, requestBase) => builder.setContentIntent(Intents.OpenConvIntent(accountId, convId, requestBase))
    }

    clearNotificationsIntent.foreach { case (uId, convId) =>
      builder.setDeleteIntent(NotificationsHandlerService.clearNotificationsIntent(uId, convId))
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
      case (userId, convId, requestBase) =>
        new NotificationCompat.Action.Builder(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(userId, convId, requestBase)).build()
    }.foreach(builder.addAction)

    action2.map {
      case (userId, convId, requestBase) => createQuickReplyAction(userId, convId, requestBase)
    }.foreach(builder.addAction)

    builder.build()
  }

  private def createQuickReplyAction(userId: UserId, convId: ConvId, requestCode: Int)(implicit cxt: Context) = {
    val remoteInput = new RemoteInput.Builder(NotificationsHandlerService.InstantReplyKey)
      .setLabel(getString(R.string.notification__action__reply))
      .build
    new NotificationCompat.Action.Builder(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), NotificationsHandlerService.quickReplyIntent(userId, convId))
      .addRemoteInput(remoteInput)
      .setAllowGeneratedReplies(true)
      .build()
  }
}

trait NotificationManagerWrapper {
  def showNotification(id: Int, notificationProps: NotificationProps): Unit
  def cancelNotifications(accountId: UserId, convs: Set[ConvId]): Unit
}

object NotificationManagerWrapper {
  val IncomingCallNotificationsChannelId = "INCOMING_CALL_NOTIFICATIONS_CHANNEL_ID"
  val OngoingNotificationsChannelId      = "STICKY_NOTIFICATIONS_CHANNEL_ID"
  val PingNotificationsChannelId         = "PINGS_NOTIFICATIONS_CHANNEL_ID"
  val MessageNotificationsChannelId      = "MESSAGE_NOTIFICATIONS_CHANNEL_ID"

  final class AndroidNotificationsManager(notificationManager: NotificationManager)(implicit inj: Injector, cxt: Context)
    extends NotificationManagerWrapper with Injectable with DerivedLogTag {
    def showNotification(id: Int, notificationProps: NotificationProps): Unit =
      notificationManager.notify(id, notificationProps.build())

    def getNotificationChannel(channelId: String): NotificationChannel =
      notificationManager.getNotificationChannel(channelId)

    override def cancelNotifications(accountId: UserId, convs: Set[ConvId]): Unit =
      if (convs.nonEmpty) {
        val idsToCancel = convs.map(toNotificationConvId(accountId, _))
        val (summaryNots, convNots) =
          notificationManager
            .getActiveNotifications
            .toSeq
            .partition(_.getNotification.extras.getBoolean(NotificationCompatExtras.EXTRA_GROUP_SUMMARY))
        val (toCancel, others) = convNots.partition { n => idsToCancel.contains(n.getId) }
        toCancel.foreach(n => notificationManager.cancel(n.getId))
        if (others.isEmpty)
          notificationManager.cancelAll()
        else
          summaryNots
            .filterNot(n =>
              others.map(_.getNotification.getGroup.hashCode.toString).exists(n.getNotification.getChannelId.contains)
            )
            .foreach(n => notificationManager.cancel(n.getId))
      }

  }
}
