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

import android.annotation.TargetApi
import android.app.{Notification, NotificationManager}
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.{BitmapDrawable, Drawable}
import android.graphics.{Bitmap, BitmapFactory, Canvas}
import android.net.Uri
import android.os.Build
import android.support.annotation.RawRes
import android.support.v4.app.NotificationCompat
import android.text.style.{ForegroundColorSpan, TextAppearanceSpan}
import android.text.{SpannableString, Spanned, TextUtils}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.bitmap
import com.waz.model.AccountId
import com.waz.service.ZMessaging
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStreamWithAuxSignal, Signal}
import com.waz.zclient.Intents._
import com.waz.zclient._
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.controllers.userpreferences.UserPreferencesController
import com.waz.zclient.media.SoundController
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RingtoneUtils
import com.waz.zms.NotificationsAndroidService
import org.threeten.bp.Instant

class MessageNotificationsController(implicit inj: Injector, cxt: Context, eventContext: EventContext) extends Injectable { self =>

  import MessageNotificationsController._
  def context = cxt
  implicit val ec = Threading.Background

  val notManager = inject[NotificationManager]
  val sharedPreferences = cxt.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE)
  lazy val soundController = inject[SoundController]
  lazy val navigationController = inject[NavigationController]

  val currentAccount = ZMessaging.currentAccounts.activeAccount

  Signal.future(ZMessaging.globalModule).flatMap(_.notifications.groupedNotifications).onUi { _.foreach {
    case (account, (shouldBeSilent, nots)) => handleNotifications(account, shouldBeSilent, nots)
  }}

  (for {
    gl   <- Signal.future(ZMessaging.globalModule)
    page <- navigationController.visiblePage
  } yield (gl, page)) {
    case (gl, page) =>
      gl.notifications.messageStreamVisible ! (page == Page.MESSAGE_STREAM)
      gl.notifications.conversationListVisible ! (page == Page.CONVERSATION_LIST)
  }

  private def handleNotifications(account: AccountId, silent: Boolean, nots: Seq[NotificationInfo]): Unit = {
    verbose(s"Notifications updated for account: $account: shouldBeSilent: $silent, $nots")
    val (ephemeral, normal) = nots.partition(_.isEphemeral)

    val (normNotId, ephNotId) = {
      val id = toNotificationGroupId(account)
      (id, id + 1)
    }

    val ephNotification = {
      if (ephemeral.isEmpty) {
        notManager.cancel(ephNotId)
        Option.empty[Notification]
      }
      else if (ephemeral.forall(_.hasBeenDisplayed)) None
      else Some(getEphemeralNotification(account, ephemeral.size, silent, ephemeral.maxBy(_.time).time))
    }.map(n => (normNotId, n))

    val normalNotification = {
      if (normal.isEmpty) {
        notManager.cancel(normNotId)
        Option.empty[Notification]
      }
      else if (normal.forall(_.hasBeenDisplayed)) None
      else if (normal.size == 1) Some(getSingleMessageNotification(account, nots.head, silent))
      else Some(getMultipleMessagesNotification(account, nots, silent))
    }.map(n => (normNotId, n))

    Seq(ephNotification, normalNotification).flatten.foreach { case (id, notification) =>
      notification.priority = Notification.PRIORITY_HIGH
      notification.flags |= Notification.FLAG_AUTO_CANCEL
      notification.deleteIntent = NotificationsAndroidService.clearNotificationsIntent(account, context)

      attachNotificationLed(notification)
      attachNotificationSound(notification, nots, silent)

      notManager.notify(id, notification)
    }

    Option(ZMessaging.currentGlobal.notifications.markAsDisplayed(account, nots.map(_.id)))
  }

  private def attachNotificationLed(notification: Notification) = {
    var color = sharedPreferences.getInt(UserPreferencesController.USER_PREFS_LAST_ACCENT_COLOR, -1)
    if (color == -1) {
      color = getColor(R.color.accent_default)
    }
    notification.ledARGB = color
    notification.ledOnMS = getInt(R.integer.notifications__system__led_on)
    notification.ledOffMS = getInt(R.integer.notifications__system__led_off)
    notification.flags |= Notification.FLAG_SHOW_LIGHTS
  }

  private def attachNotificationSound(notification: Notification, ns: Seq[NotificationInfo], silent: Boolean) =
    notification.sound =
      if (soundController.soundIntensityNone || silent) null
      else if (!soundController.soundIntensityFull && ns.size > 1) null
      else ns.lastOption.fold(null.asInstanceOf[Uri])(getMessageSoundUri)

  private def getMessageSoundUri(n: NotificationInfo): Uri = n.tpe match {
    case ASSET |
         ANY_ASSET |
         VIDEO_ASSET |
         AUDIO_ASSET |
         LOCATION |
         TEXT |
         CONNECT_ACCEPTED |
         CONNECT_REQUEST |
         RENAME |
         LIKE  => getSelectedSoundUri(soundController.currentTonePrefs._2, R.raw.new_message_gcm)
    case KNOCK => getSelectedSoundUri(soundController.currentTonePrefs._3, R.raw.ping_from_them)
    case _     => null
  }

  private def getSelectedSoundUri(value: String, @RawRes defaultResId: Int): Uri = getSelectedSoundUri(value, defaultResId, defaultResId)

  private def getSelectedSoundUri(value: String, @RawRes preferenceDefault: Int, @RawRes returnDefault: Int): Uri = {
    if (!TextUtils.isEmpty(value) && !RingtoneUtils.isDefaultValue(context, value, preferenceDefault)) Uri.parse(value)
    else RingtoneUtils.getUriForRawId(context, returnDefault)
  }

  private def commonBuilder(title: String, displayTime: Instant, style: NotificationCompat.Style, silent: Boolean) = {
    val builder = new NotificationCompat.Builder(cxt)
      .setShowWhen(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setSmallIcon(R.drawable.ic_menu_logo)
      .setLargeIcon(getAppIcon)
      .setContentTitle(title)
      .setWhen(displayTime.toEpochMilli)
      .setStyle(style)

    if (!silent && soundController.isVibrationEnabled)
      builder.setVibrate(getIntArray(R.array.new_message_gcm).map(_.toLong))
    else
      builder.setVibrate(Array(0,0))

    builder
  }

  private def getEphemeralNotification(accountId: AccountId, size: Int, silent: Boolean, displayTime: Instant): Notification = {
    val details = getString(R.string.notification__message__ephemeral_details)
    val title = getQuantityString(R.plurals.notification__message__ephemeral, size, Integer.valueOf(size))

    val bigTextStyle = new NotificationCompat.BigTextStyle
    bigTextStyle.setBigContentTitle(title)
    bigTextStyle.bigText(details)

    val builder = commonBuilder(title, displayTime, bigTextStyle, silent)
      .setContentText(details)
      .setContentIntent(OpenAccountIntent(accountId))

    builder.build
  }

  private def getSingleMessageNotification(accountId: AccountId, n: NotificationInfo, silent: Boolean): Notification = {
    val spannableString = getMessage(n, multiple = false, singleConversationInBatch = true, singleUserInBatch = true)
    val title = getMessageTitle(n)

    val requestBase = System.currentTimeMillis.toInt

    val bigTextStyle = new NotificationCompat.BigTextStyle
    bigTextStyle.setBigContentTitle(title)
    bigTextStyle.bigText(spannableString)

    val builder = commonBuilder(title, n.time, bigTextStyle, silent)
      .setContentText(spannableString)
      .setContentIntent(OpenConvIntent(accountId, n.convId, requestBase))

    if (n.tpe != NotificationType.CONNECT_REQUEST) {
      builder
        .addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(accountId, n.convId, requestBase + 1))
        .addAction(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), QuickReplyIntent(accountId, n.convId, requestBase + 2))
    }

    builder.build
  }

  private def getMultipleMessagesNotification(accountId: AccountId, ns: Seq[NotificationInfo], silent: Boolean): Notification = {

    val convIds = ns.map(_.convId).toSet
    val users = ns.map(_.userName).toSet

    val isSingleConv = convIds.size == 1

    val (convDesc, headerRes) =
      if (isSingleConv) {
        if (ns.head.isGroupConv) (ns.head.convName.getOrElse(""), R.plurals.notification__new_messages_groups)
        else (ns.head.userName.getOrElse(""), R.plurals.notification__new_messages)
      }
      else (convIds.size.toString, R.plurals.notification__new_messages__multiple)

    val title = getQuantityString(headerRes, ns.size, Integer.valueOf(ns.size), convDesc)

    val inboxStyle = new NotificationCompat.InboxStyle()
      .setBigContentTitle(title)

    val builder = commonBuilder(title, ns.maxBy(_.time).time, inboxStyle, silent)
      .setNumber(ns.size)

    if (isSingleConv) {
      val requestBase = System.currentTimeMillis.toInt
      val conversationId = convIds.head.str
      builder
        .setContentIntent(OpenConvIntent(accountId, convIds.head, requestBase))
        .addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(accountId, convIds.head, requestBase + 1))
        .addAction(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), QuickReplyIntent(accountId, convIds.head, requestBase + 2))
    }
    else builder.setContentIntent(OpenAccountIntent(accountId))

    val messages = ns.map(n => getMessage(n, multiple = true, singleConversationInBatch = isSingleConv, singleUserInBatch = users.size == 1 && isSingleConv)).takeRight(5)
    builder.setContentText(messages.last) //the collapsed notification should have the last message
    messages.foreach(inboxStyle.addLine)

    builder.build
  }

  private[notifications] def getMessage(n: NotificationInfo, multiple: Boolean, singleConversationInBatch: Boolean, singleUserInBatch: Boolean) = {
    val message = n.message.replaceAll("\\r\\n|\\r|\\n", " ")

    def getHeader(testPrefix: Boolean = false, singleUser: Boolean = false) = getDefaultNotificationMessageLineHeader(n, multiple, textPrefix = testPrefix, singleConversationInBatch = singleConversationInBatch, singleUser = singleUser)

    val header = n.tpe match {
      case TEXT | CONNECT_REQUEST => getHeader(testPrefix = true, singleUser = singleUserInBatch)
      case CONNECT_ACCEPTED       => ""
      case _                      => getHeader()
    }

    val body = n.tpe match {
      case TEXT | CONNECT_REQUEST   => message
      case MISSED_CALL              => getString(R.string.notification__message__one_to_one__wanted_to_talk)
      case KNOCK                    => if (n.isGroupConv) getString(R.string.notification__message__group__pinged)          else getString(R.string.notification__message__one_to_one__pinged)
      case ANY_ASSET                => if (n.isGroupConv) getString(R.string.notification__message__group__shared_file)     else getString(R.string.notification__message__one_to_one__shared_file)
      case ASSET                    => if (n.isGroupConv) getString(R.string.notification__message__group__shared_picture)  else getString(R.string.notification__message__one_to_one__shared_picture)
      case VIDEO_ASSET              => if (n.isGroupConv) getString(R.string.notification__message__group__shared_video)    else getString(R.string.notification__message__one_to_one__shared_video)
      case AUDIO_ASSET              => if (n.isGroupConv) getString(R.string.notification__message__group__shared_audio)    else getString(R.string.notification__message__one_to_one__shared_audio)
      case LOCATION                 => if (n.isGroupConv) getString(R.string.notification__message__group__shared_location) else getString(R.string.notification__message__one_to_one__shared_location)
      case RENAME                   => getString(R.string.notification__message__group__renamed_conversation, message)
      case MEMBER_LEAVE             => getString(R.string.notification__message__group__remove)
      case MEMBER_JOIN              => getString(R.string.notification__message__group__add)
      case LIKE                     => getString(R.string.notification__message__group__liked)
      case CONNECT_ACCEPTED         => if (multiple || n.userName.isEmpty) getString(R.string.notification__message__generic__accept_request)    else getString(R.string.notification__message__single__accept_request, n.userName.getOrElse(""))
      case _ => ""
    }
    getMessageSpannable(header, body)
  }

  private def getMessageTitle(n: NotificationInfo) = {
    val userName = n.userName.getOrElse("")
    if (n.isGroupConv) {
      val convName = n.convName.filterNot(_.isEmpty).getOrElse(getString(R.string.notification__message__group__default_conversation_name))
      getString(R.string.notification__message__group__prefix__other, userName, convName)
    }
    else userName
  }

  @TargetApi(21)
  private def getMessageSpannable(header: String, body: String) = {
    val messageSpannable = new SpannableString(header + body)
    val textAppearance =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) android.R.style.TextAppearance_Material_Notification_Title
      else android.R.style.TextAppearance_StatusBar_EventContent_Title
    messageSpannable.setSpan(new ForegroundColorSpan(new TextAppearanceSpan(cxt, textAppearance).getTextColor.getDefaultColor), 0, header.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    messageSpannable
  }

  private def getDefaultNotificationMessageLineHeader(n: NotificationInfo, multiple: Boolean, textPrefix: Boolean, singleConversationInBatch: Boolean, singleUser: Boolean) = {
    val prefixId = if (multiple) {
      if (n.isGroupConv && !singleConversationInBatch) if (textPrefix) R.string.notification__message__group__prefix__text else R.string.notification__message__group__prefix__other
      else if (!singleUser || n.isGroupConv) if (textPrefix) R.string.notification__message__name__prefix__text else R.string.notification__message__name__prefix__other
      else 0
    }
    else 0
    getStringOrEmpty(prefixId, n.userName.getOrElse(""), n.convName.filterNot(_.isEmpty).getOrElse(getString(R.string.notification__message__group__default_conversation_name)))
  }

  private def getAppIcon: Bitmap = {
    try {
      val icon: Drawable = cxt.getPackageManager.getApplicationIcon(cxt.getPackageName)
      icon match {
        case null => bitmap.EmptyBitmap
        case drawable: BitmapDrawable =>
          drawable.getBitmap
        case _ =>
          val bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth, icon.getIntrinsicHeight, Bitmap.Config.ARGB_8888)
          val canvas = new Canvas(bitmap)
          icon.setBounds(0, 0, canvas.getWidth, canvas.getHeight)
          icon.draw(canvas)
          bitmap
      }
    }
    catch {
      case e: PackageManager.NameNotFoundException => BitmapFactory.decodeResource(cxt.getResources, R.drawable.ic_launcher_wire)
    }
  }
}

object MessageNotificationsController {

  def toNotificationGroupId(accountId: AccountId): Int = accountId.str.hashCode()

  val ZETA_MESSAGE_NOTIFICATION_ID: Int = 1339272
  val ZETA_EPHEMERAL_NOTIFICATION_ID: Int = 1339279
}
