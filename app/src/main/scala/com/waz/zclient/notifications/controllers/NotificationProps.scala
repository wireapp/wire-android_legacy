package com.waz.zclient.notifications.controllers

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.app.{NotificationCompat, NotificationCompatExtras, RemoteInput}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.services.notifications.NotificationsHandlerService
import com.waz.utils.returning
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.Intents.CallIntent
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.format
import com.waz.zclient.{Intents, R}

final case class NotificationProps(accountId:                UserId,
                                   convId:                   Option[ConvId] = None,
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
                                  ) extends DerivedLogTag {
  import NotificationManagerWrapper._

  override def toString: String =
    format(className = "NotificationProps", oneLiner = false,
      "when"                     -> when,
      "convId"                   -> convId,
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

  def channelId: String = if (lastIsPing.contains(true)) PingNotificationsChannelId else MessageNotificationsChannelId

  def build()(implicit cxt: Context): Notification = {
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
    builder.addExtras(returning(new Bundle()) { bundle =>
      bundle.putBoolean(NotificationCompatExtras.EXTRA_GROUP_SUMMARY, groupSummary.getOrElse(false))
      bundle.putInt(NotificationProps.NOTIFICATION_HASH, hashCode)
    })
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

object NotificationProps {
  val NOTIFICATION_HASH: String = "NotificationHash"
}
