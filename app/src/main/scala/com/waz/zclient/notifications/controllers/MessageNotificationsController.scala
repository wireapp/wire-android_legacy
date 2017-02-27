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
import com.waz.model.NotId
import com.waz.service.ZMessaging
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.controllers.userpreferences.UserPreferencesController
import com.waz.zclient.controllers.vibrator.VibratorController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.IntentUtils._
import com.waz.zclient.utils.RingtoneUtils
import com.waz.zms.NotificationsAndroidService
import org.threeten.bp.Instant
import scala.concurrent.duration._

class MessageNotificationsController(implicit inj: Injector, cxt: Context, eventContext: EventContext) extends Injectable {

  import MessageNotificationsController._
  def context = cxt

  val zms = inject[Signal[ZMessaging]]

  val notsService = zms.map(_.notifications)

  val notManager = inject[NotificationManager]

  val notifications = notsService.flatMap(_.notifications)

  val shouldBeSilent = notsService.flatMap(_.otherDeviceActiveTime).map { t =>
    val timeDiff = Instant.now.toEpochMilli - t.toEpochMilli
    verbose(s"otherDeviceActiveTime: $t, current time: ${Instant.now}, timeDiff: ${timeDiff.millis.toSeconds}")
    timeDiff < NotificationsAndroidService.checkNotificationsTimeout.toMillis
  }

  val sharedPreferences = cxt.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE)

  lazy val clearIntent = NotificationsAndroidService.clearNotificationsIntent(context)

  val displayedNots = Signal(Seq.empty[NotId])

  notsService.zip(displayedNots) {
    case (service, displayed) => service.markAsDisplayed(displayed)
  }

  notifications.zip(shouldBeSilent).on(Threading.Ui) {
    case (nots, silent) =>
      val (ephemeral, normal) = nots.partition(_.isEphemeral)
      handleNotifications(normal, silent, ZETA_MESSAGE_NOTIFICATION_ID)
      handleNotifications(ephemeral, silent, ZETA_EPHEMERAL_NOTIFICATION_ID)
  }

  private def handleNotifications(nots: Seq[NotificationInfo], silent: Boolean, notifId: Int): Unit = {
    verbose(s"Notifications updated: shouldBeSilent: $silent, $nots")
    if (nots.isEmpty) notManager.cancel(notifId)
    else if (!nots.forall(_.hasBeenDisplayed)) { //Only show notifications if there are any that haven't yet been displayed to the user
    val notification =
      if (notifId == ZETA_EPHEMERAL_NOTIFICATION_ID) getEphemeralNotification(nots.size, silent, nots.maxBy(_.time).time)
      else if (nots.size == 1) getSingleMessageNotification(nots.head, silent)
      else getMultipleMessagesNotification(nots, silent)

      notification.priority = Notification.PRIORITY_HIGH
      notification.flags |= Notification.FLAG_AUTO_CANCEL
      notification.deleteIntent = clearIntent

      attachNotificationLed(notification)
      attachNotificationSound(notification, nots, silent)

      notManager.notify(notifId, notification)
    }
    displayedNots ! nots.map(_.id)
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

  private def attachNotificationSound(notification: Notification, ns: Seq[NotificationInfo], silent: Boolean) = {
    val soundSetting = sharedPreferences.getString(getString(R.string.pref_options_sounds_key), getString(R.string.pref_options_sounds_default))
    notification.sound =
      if (getString(R.string.pref_sound_value_none) == soundSetting || silent) null
      else if ((getString(R.string.pref_sound_value_some) == soundSetting) && ns.size > 1) null
      else ns.lastOption.fold(null.asInstanceOf[Uri])(getMessageSoundUri)
  }

  private def getMessageSoundUri(n: NotificationInfo): Uri = {
    n.tpe match {
      case ASSET |
           ANY_ASSET |
           VIDEO_ASSET |
           AUDIO_ASSET |
           LOCATION |
           TEXT |
           CONNECT_ACCEPTED |
           CONNECT_REQUEST |
           RENAME |
           LIKE =>
        val value = sharedPreferences.getString(getString(R.string.pref_options_ringtones_text_key), null);
        if (value != null && value.isEmpty) {
          null
        } else {
          getSelectedSoundUri(value, R.raw.new_message_gcm)
        }
      case KNOCK =>
        val value = sharedPreferences.getString(getString(R.string.pref_options_ringtones_ping_key), null)
        if (value != null && value.isEmpty) {
          null
        } else {
          getSelectedSoundUri(value, R.raw.ping_from_them)
        }
      case _ => null
    }
  }

  private def getSelectedSoundUri(value: String, @RawRes defaultResId: Int): Uri = getSelectedSoundUri(value, defaultResId, defaultResId)

  private def getSelectedSoundUri(value: String, @RawRes preferenceDefault: Int, @RawRes returnDefault: Int): Uri = {
    if (!TextUtils.isEmpty(value) && !RingtoneUtils.isDefaultValue(context, value, preferenceDefault)) Uri.parse(value)
    else RingtoneUtils.getUriForRawId(context, returnDefault)
  }

  private def getEphemeralNotification(size: Int, silent: Boolean, displayTime: Instant): Notification = {
    val details = getString(R.string.notification__message__ephemeral_details)
    val title = getQuantityString(R.plurals.notification__message__ephemeral, size, Integer.valueOf(size))

    val builder = new NotificationCompat.Builder(cxt)

    val bigTextStyle = new NotificationCompat.BigTextStyle
    bigTextStyle.setBigContentTitle(title)
    bigTextStyle.bigText(details)

    builder
      .setShowWhen(true)
      .setWhen(displayTime.toEpochMilli)
      .setSmallIcon(R.drawable.ic_menu_logo)
      .setLargeIcon(getAppIcon)
      .setContentTitle(title)
      .setContentText(details)
      .setContentIntent(getNotificationAppLaunchIntent(cxt))
      .setStyle(bigTextStyle)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)


    if (VibratorController.isEnabledInPreferences(cxt) && !silent) {
      builder.setVibrate(VibratorController.resolveResource(cxt.getResources, R.array.new_message_gcm))
    }
    builder.build
  }

  private def getSingleMessageNotification(n: NotificationInfo, silent: Boolean): Notification = {

    val spannableString = getMessage(n, multiple = false, singleConversationInBatch = true, singleUserInBatch = true)
    val title = getMessageTitle(n)

    val builder = new NotificationCompat.Builder(cxt)
    val requestBase = System.currentTimeMillis.toInt

    val bigTextStyle = new NotificationCompat.BigTextStyle
    bigTextStyle.setBigContentTitle(title)
    bigTextStyle.bigText(spannableString)

    builder
      .setSmallIcon(R.drawable.ic_menu_logo)
      .setLargeIcon(getAppIcon)
      .setShowWhen(true)
      .setWhen(n.time.toEpochMilli)
      .setContentTitle(title)
      .setContentText(spannableString)
      .setContentIntent(getNotificationAppLaunchIntent(cxt, n.convId.str, requestBase))
      .setStyle(bigTextStyle)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)


    if (n.tpe != NotificationType.CONNECT_REQUEST) {
      builder
        .addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), getNotificationCallIntent(cxt, n.convId.str, requestBase + 1))
        .addAction(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), getNotificationReplyIntent(cxt, n.convId.str, requestBase + 2))
    }

    if (VibratorController.isEnabledInPreferences(cxt) && !silent) {
      builder.setVibrate(VibratorController.resolveResource(cxt.getResources, R.array.new_message_gcm))
    }
    builder.build
  }

  private def getMultipleMessagesNotification(ns: Seq[NotificationInfo], silent: Boolean): Notification = {

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

    val builder = new NotificationCompat.Builder(cxt)
      .setShowWhen(true)
      .setWhen(ns.maxBy(_.time).time.toEpochMilli)
      .setSmallIcon(R.drawable.ic_menu_logo)
      .setLargeIcon(getAppIcon).setNumber(ns.size)
      .setContentTitle(title)
      .setStyle(inboxStyle)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)

    if (VibratorController.isEnabledInPreferences(cxt) && !silent) {
      builder.setVibrate(VibratorController.resolveResource(cxt.getResources, R.array.new_message_gcm))
    }
    if (isSingleConv) {
      val requestBase = System.currentTimeMillis.toInt
      val conversationId = convIds.head.str
      builder
        .setContentIntent(getNotificationAppLaunchIntent(cxt, conversationId, requestBase))
        .addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), getNotificationCallIntent(cxt, conversationId, requestBase + 1))
        .addAction(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), getNotificationReplyIntent(cxt, conversationId, requestBase + 2))
    }
    else builder.setContentIntent(getNotificationAppLaunchIntent(cxt))

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
      case CONNECT_ACCEPTED       => if (multiple) getString(R.string.notification__message__name__prefix__other, n.convName.getOrElse("")) else ""
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
      case CONNECT_ACCEPTED         => if (multiple) getString(R.string.notification__message__multiple__accept_request)    else getString(R.string.notification__message__single__accept_request)
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
  val ZETA_MESSAGE_NOTIFICATION_ID: Int = 1339272
  val ZETA_EPHEMERAL_NOTIFICATION_ID: Int = 1339279
}
