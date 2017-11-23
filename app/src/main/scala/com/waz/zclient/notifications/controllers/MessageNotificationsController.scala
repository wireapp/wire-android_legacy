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

import android.annotation.TargetApi
import android.app.{Notification, NotificationManager}
import android.content.Context
import android.graphics._
import android.net.Uri
import android.os.Build
import android.support.annotation.RawRes
import android.support.v4.app.{NotificationCompat, RemoteInput}
import android.text.style.{ForegroundColorSpan, StyleSpan}
import android.text.{SpannableString, Spanned, TextUtils}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.api.impl.AccentColor
import com.waz.bitmap.BitmapUtils
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.{returning, _}
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.Intents._
import com.waz.zclient._
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.controllers.userpreferences.UserPreferencesController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RingtoneUtils, ViewUtils}
import com.waz.zms.NotificationsAndroidService
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

class MessageNotificationsController(implicit inj: Injector, cxt: Context, eventContext: EventContext) extends Injectable { self =>

  import MessageNotificationsController._
  def context = cxt
  implicit val ec = Threading.Background

  val zms = inject[Signal[ZMessaging]]
  val notManager = inject[NotificationManager]
  val sharedPreferences = cxt.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE)
  lazy val soundController = inject[SoundController]
  lazy val navigationController = inject[NavigationController]
  lazy val convController = inject[ConversationController]

  val currentAccount = ZMessaging.currentAccounts.activeAccount

  var accentColors = Map[AccountId, Int]()

  val colors = for {
    users <- ZMessaging.currentAccounts.loggedInAccounts.map(_.map(acc => acc.id -> acc.userId).toSeq)
    collectedUsers = users.collect {
      case (accId, Some(userId)) => accId -> userId
    }
    zms <- zms
    userData <- Signal.sequence(collectedUsers.map {
      case (accId, userId) => zms.usersStorage.signal(userId).map(accId -> _)
    }: _*)
  } yield userData.map(u => u._1 -> AccentColor(u._2.accent).getColor).toMap

  colors { accentColors = _ }


  ZMessaging.globalModule.map { global =>
    global.notifications.groupedNotifications.onUi { notifications =>
      verbose(s"groupedNotifications received: ${notifications.map { case (acc, (_, nots)) => acc -> nots.size }}")
      notifications.toSeq.sortBy(_._1.str.hashCode).foreach {
        case (account, (shouldBeSilent, nots)) =>
          (for {
            zms <- zms.head
            accountData <- zms.accountsStorage.get(account)
            team <- accountData.map(_.teamId) match {
              case Some(Right(Some(teamId))) => zms.teamsStorage.get(teamId)
              case _ => Future.successful(Option.empty[TeamData])
            }
          } yield team.map(_.name)).map {
            teamName => createConvNotifications(account, shouldBeSilent, nots, teamName)
          }(Threading.Ui)
      }

      if (BundleEnabled) {
        val currentBundles = notifications.keys.map(toNotificationGroupId).toSeq
        val currentNotifications = notifications.toSeq.flatMap {
          case (account, (_, nots)) => nots.map(_.convId).distinct.map(cId => toNotificationConvId(account, cId))
        } ++ currentBundles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
          notManager.getActiveNotifications
            .collect { case notification if !currentNotifications.contains(notification.getId) => notification.getId }
            .foreach(notManager.cancel)
      }
    }
  }

  val conversationsBeingDisplayed = for {
    gl <- Signal.future(ZMessaging.globalModule)
    accounts <- ZMessaging.currentAccounts.loggedInAccounts
    zms <- zms
    uiActive <- gl.lifecycle.uiActive
    convId <- convController.currentConvId.map(Option(_)).orElse(Signal.const(Option.empty[ConvId]))
    conversationsSet <- zms.convsStorage.convsSignal
    page <- navigationController.visiblePage
  } yield (gl, accounts.map { acc =>
    val convs =
      if (zms.accountId != acc.id || !uiActive) {
        Set.empty[ConvId]
      } else {
        page match {
          case Page.CONVERSATION_LIST =>
            conversationsSet.conversations.map(_.id)
          case Page.MESSAGE_STREAM =>
            Set(convId).flatten
          case _ =>
            Set.empty[ConvId]
        }
      }
    acc.id -> convs
  }.toMap)

  conversationsBeingDisplayed {
    case (gl, convs) =>
      verbose(s"conversationsBeingDisplayed: $convs")
      gl.notifications.notificationsSourceVisible ! convs
  }

  private def getPictureForNotifications(accountId: AccountId, nots: Seq[NotificationInfo]): Future[Option[Bitmap]] =
    if (nots.exists(_.isEphemeral)) Future.successful(None)
    else {
      val pictures = nots.flatMap(_.userPicture).distinct

      val iconWidth = ViewUtils.toPx(context, 64)

      val assetId = if (pictures.size == 1) {
        pictures.headOption
      } else {
        None
      }

      for {
        zms <- ZMessaging.currentAccounts.getZMessaging(accountId)
        assetData <- (zms, assetId) match {
          case (Some(z), Some(aId)) => z.assetsStorage.get(aId)
          case _ => Future.successful(None)
        }
        bmp <- (zms, assetData) match {
          case (Some(z), Some(ad)) => z.imageLoader.loadBitmap(ad, BitmapRequest.Single(iconWidth), forceDownload = false).map(Option(_)).withTimeout(500.millis).recoverWith {
            case _ : Throwable => CancellableFuture.successful(None)
          }.future
          case _ => Future.successful(None)
        }
      } yield {
        bmp.map { original => BitmapUtils.createRoundBitmap(original, iconWidth, 0, Color.TRANSPARENT) }
      }
    }

  private def createSummaryNotification(account: AccountId, silent: Boolean, nots: Seq[NotificationInfo], teamName: Option[String]): Option[Notification] =
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || !notManager.getActiveNotifications.exists(_.getId == toNotificationGroupId(account))) {
      verbose(s"creating summary notification")

      val inboxStyle = new NotificationCompat.InboxStyle()

      val builder = new NotificationCompat.Builder(cxt)
        .setWhen(nots.minBy(_.time).time.toEpochMilli)
        .setShowWhen(true)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSmallIcon(R.drawable.ic_menu_logo)
        .setContentTitle("")
        .setContentText("")
        .setStyle(inboxStyle)
        .setGroupSummary(true)
        .setGroup(account.str)
        .setContentIntent(OpenAccountIntent(account))
        .setDeleteIntent(NotificationsAndroidService.clearNotificationsIntent(account, context))

      teamName.foreach(builder.setContentInfo)
      notificationColor(account).foreach(builder.setColor)

      Option(builder.build())
    } else None

  private def createConvNotifications(account: AccountId, silent: Boolean, nots: Seq[NotificationInfo], teamName: Option[String]): Future[Unit] =
    if (nots.isEmpty) {
      notManager.cancel(toNotificationGroupId(account))
      Future.successful({})
    } else {
      verbose(s"createConvNotifications for nots: ${nots.map(n => (n.id, n.convId, n.message, n.isEphemeral, n.isGroupConv))}")
      val groupedConvs = nots.groupBy(_.convId).map {
        case (convId, values) => toNotificationConvId(account, convId) -> values
      }

      val teamNameOpt = if (groupedConvs.keys.size > 1) None else teamName

      val notFutures = groupedConvs.map {
        case (notId, convNots) if convNots.size == 1 =>
          getPictureForNotifications(account, convNots).map { pic =>
            notId -> getSingleMessageNotification(account, convNots.head, silent, teamNameOpt, noTicker = convNots.forall(_.hasBeenDisplayed), pic)
          }
        case (notId, convNots) =>
          getPictureForNotifications(account, convNots).map { pic =>
            notId -> getMultipleMessagesNotification(account, convNots, silent, noTicker = nots.forall(_.hasBeenDisplayed), teamNameOpt, pic)
          }
      }

     Future.sequence(notFutures).map { ns =>
       val summaryOpt = if (BundleEnabled) createSummaryNotification(account, silent, nots, teamName) else None
       val nMap = ns.toMap
       summaryOpt.fold(nMap)(summary => nMap + (toNotificationGroupId(account) -> summary))
      }.map {
       _.foreach { case (id, not) if groupedConvs(id).exists(!_.hasBeenDisplayed) => notManager.notify(id, not) }
     }.map {
       _ => Option(ZMessaging.currentGlobal.notifications.markAsDisplayed(account, nots.map(_.id)))
     }
    }

  private def attachNotificationSoundAndLed(builder: NotificationCompat.Builder, ns: Seq[NotificationInfo], silent: Boolean) = {
    val sound = if (soundController.soundIntensityNone || silent) null
    else if (!soundController.soundIntensityFull && (ns.size > 1 && ns.lastOption.forall(_.tpe != KNOCK))) null
    else ns.lastOption.fold(null.asInstanceOf[Uri])(getMessageSoundUri)

    var color = sharedPreferences.getInt(UserPreferencesController.USER_PREFS_LAST_ACCENT_COLOR, -1)
    if (color == -1) {
      color = getColor(R.color.accent_default)
    }

    builder
      .setSound(sound)
      .setLights(color, getInt(R.integer.notifications__system__led_on), getInt(R.integer.notifications__system__led_off))
  }

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

  private def commonBuilder(accountId: AccountId, title: CharSequence, displayTime: Instant, style: NotificationCompat.Style, silent: Boolean) = {
    val builder = new NotificationCompat.Builder(cxt)
      .setShowWhen(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setSmallIcon(R.drawable.ic_menu_logo)
      .setContentTitle(title)
      .setWhen(displayTime.toEpochMilli)
      .setStyle(style)
      .setAutoCancel(true)

    notificationColor(accountId).foreach(builder.setColor)

    if (!silent && soundController.isVibrationEnabled)
      builder.setVibrate(getIntArray(R.array.new_message_gcm).map(_.toLong))
    else
      builder.setVibrate(Array(0,0))

    builder
  }

  private def getSingleMessageNotification(accountId: AccountId, n: NotificationInfo, silent: Boolean, teamName: Option[String], noTicker: Boolean, pic: Option[Bitmap] = None): Notification = {
    verbose(s"getSingleMessageNotification for ${(n.id, n.convId, n.message, n.isEphemeral, n.isGroupConv)}")
    val titleText =
      if (n.isEphemeral) getString(R.string.notification__message__ephemeral_username)
      else n.convName.orElse(n.userName).getOrElse("")

    val title = returning(new SpannableString(titleText)) { title =>
      title.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    val body = getMessage(n, singleConversationInBatch = true)
    val requestBase = System.currentTimeMillis.toInt

    val bigTextStyle = new NotificationCompat.BigTextStyle
    bigTextStyle.setBigContentTitle(title)
    bigTextStyle.bigText(body)
    teamName.foreach(bigTextStyle.setSummaryText)

    val builder = commonBuilder(accountId, title, n.time, bigTextStyle, silent)
      .setContentIntent(OpenConvIntent(accountId, n.convId, requestBase))
      .setDeleteIntent(NotificationsAndroidService.clearNotificationsIntent(accountId, n.convId, context))

    builder.setContentText(body)

    if (n.tpe != NotificationType.CONNECT_REQUEST && !n.isEphemeral) {
      builder.addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(accountId, n.convId, requestBase + 1))
      addQuickReplyAction(builder, accountId, n.convId, requestBase + 2)
    }

    attachNotificationSoundAndLed(builder, Seq(n), silent)

    pic.foreach(builder.setLargeIcon(_))

    builder
      .setOnlyAlertOnce(noTicker)
      .setGroup(accountId.str)
      .build
  }

  private def getMultipleMessagesNotification(accountId: AccountId, ns: Seq[NotificationInfo], silent: Boolean, noTicker: Boolean, teamName: Option[String], pic: Option[Bitmap] = None): Notification = {
    val convIds = ns.map(_.convId).toSet
    val users = ns.map(_.userName).toSet
    val isSingleConv = convIds.size == 1

    val isEphemeral = ns.exists(_.isEphemeral)

    verbose(s"getMultipleMessagesNotification: convs: $convIds, ns: ${ns.map(_.message)}, isEphemeral: $isEphemeral ")

    val titleText = if (isSingleConv) {
      if (isEphemeral) getString(R.string.notification__message__ephemeral_username)
      else if (ns.head.isGroupConv) ns.head.convName.getOrElse("")
      else ns.head.convName.orElse(ns.head.userName).getOrElse("")
    } else
      getQuantityString(R.plurals.notification__new_messages__multiple, ns.size, Integer.valueOf(ns.size), convIds.size.toString)

    verbose(s"titleText: $titleText")

    val separator = " • "

    val title = if (isSingleConv && ns.size > 5)
      returning(new SpannableString(titleText + separator + getQuantityString(R.plurals.conversation_list__new_message_count, ns.size, Integer.valueOf(ns.size)))) { titleSpan =>
        titleSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, titleText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSpan.setSpan(new StyleSpan(Typeface.ITALIC), titleText.length + separator.length, titleSpan.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSpan.setSpan(new ForegroundColorSpan(Color.GRAY), titleText.length, titleSpan.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    else
      returning(new SpannableString(titleText)) { titleSpan =>
        titleSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, titleText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }

    val inboxStyle = new NotificationCompat.InboxStyle()
      .setBigContentTitle(title)

    if (BundleEnabled)
      teamName.foreach(inboxStyle.setSummaryText)

    val builder = commonBuilder(accountId, title, ns.maxBy(_.time).time, inboxStyle, silent)

    if (isSingleConv)
      builder.setDeleteIntent(NotificationsAndroidService.clearNotificationsIntent(accountId, convIds.head, context))
    else
      builder.setDeleteIntent(NotificationsAndroidService.clearNotificationsIntent(accountId, context))

    if (isSingleConv) {
      val requestBase = System.currentTimeMillis.toInt
      builder.setContentIntent(OpenConvIntent(accountId, convIds.head, requestBase))
      if(!isEphemeral) {
        builder.addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(accountId, convIds.head, requestBase + 1))
        addQuickReplyAction(builder, accountId, convIds.head, requestBase + 2)
      }
    }
    else builder.setContentIntent(OpenAccountIntent(accountId))

    val messages = ns.sortBy(_.time).map(n => getMessage(n, singleConversationInBatch = isSingleConv)).takeRight(5)
    builder.setContentText(messages.last)
    messages.foreach(inboxStyle.addLine)

    attachNotificationSoundAndLed(builder, ns, silent)
    pic.foreach(builder.setLargeIcon(_))
    builder
      .setOnlyAlertOnce(noTicker)
      .setGroup(accountId.str)
      .build
  }

  private[notifications] def getMessage(n: NotificationInfo, singleConversationInBatch: Boolean) = {
    val message = n.message.replaceAll("\\r\\n|\\r|\\n", " ")
    val isTextMessage = n.tpe == TEXT

    val header = n.tpe match {
      case CONNECT_ACCEPTED => ""
      case _                => getDefaultNotificationMessageLineHeader(n, singleConversationInBatch, singleConversationInBatch)
    }

    val body = n.tpe match {
      case _ if n.isEphemeral                     => getString(R.string.conversation_list__ephemeral)
      case TEXT | CONNECT_REQUEST                 => message
      case MISSED_CALL                            => getString(R.string.notification__message__one_to_one__wanted_to_talk)
      case KNOCK                                  => getString(R.string.notification__message__one_to_one__pinged)
      case ANY_ASSET                              => getString(R.string.notification__message__one_to_one__shared_file)
      case ASSET                                  => getString(R.string.notification__message__one_to_one__shared_picture)
      case VIDEO_ASSET                            => getString(R.string.notification__message__one_to_one__shared_video)
      case AUDIO_ASSET                            => getString(R.string.notification__message__one_to_one__shared_audio)
      case LOCATION                               => getString(R.string.notification__message__one_to_one__shared_location)
      case RENAME                                 => getString(R.string.notification__message__group__renamed_conversation, message)
      case MEMBER_LEAVE                           => getString(R.string.notification__message__group__remove)
      case MEMBER_JOIN                            => getString(R.string.notification__message__group__add)
      case CONNECT_ACCEPTED if n.userName.isEmpty => getString(R.string.notification__message__generic__accept_request)
      case CONNECT_ACCEPTED                       => getString(R.string.notification__message__single__accept_request, n.userName.getOrElse(""))
      case MESSAGE_SENDING_FAILED                 => getString(R.string.notification__message__send_failed)

      case LIKE if n.likedContent.nonEmpty        => n.likedContent.collect {
        case LikedContent.PICTURE     => getString(R.string.notification__message__liked_picture)
        case LikedContent.TEXT_OR_URL => getString(R.string.notification__message__liked, n.message)
      }.getOrElse(getString(R.string.notification__message__liked_message))

      case _ => ""
    }

    getMessageSpannable(header, body, isTextMessage, n.isEphemeral)
  }

  @TargetApi(21)
  private def getMessageSpannable(header: String, body: String, isTextMessage: Boolean, isEphemeral: Boolean) = {
    val messageSpannable = new SpannableString(header + body)
    if (!isTextMessage || isEphemeral) {
      messageSpannable.setSpan(new StyleSpan(Typeface.ITALIC), 0, messageSpannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    messageSpannable.setSpan(new ForegroundColorSpan(Color.BLACK), 0, header.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    messageSpannable
  }

  private def getDefaultNotificationMessageLineHeader(n: NotificationInfo, singleConversationInBatch: Boolean, singleUser: Boolean) = if (n.isEphemeral) "" else {
    val prefixId = if (!singleConversationInBatch && n.isGroupConv) {
      R.string.notification__message__group__prefix__text
    } else if (!singleConversationInBatch && !n.isGroupConv || singleConversationInBatch && n.isGroupConv) {
      R.string.notification__message__name__prefix__text
    } else 0

    getStringOrEmpty(prefixId, n.userName.getOrElse(""), n.convName.filterNot(_.isEmpty).getOrElse(getString(R.string.notification__message__group__default_conversation_name)))
  }

  def addQuickReplyAction(builder: NotificationCompat.Builder, accountId: AccountId, convId: ConvId, requestCode: Int): Unit = {
    if (BundleEnabled) {
      val remoteInput = new RemoteInput.Builder(NotificationsAndroidService.InstantReplyKey)
        .setLabel(getString(R.string.notification__action__reply))
        .build
      val replyAction = new NotificationCompat.Action.Builder(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), NotificationsAndroidService.quickReplyIntent(accountId, convId, cxt))
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(true)
        .build()
      builder.addAction(replyAction)
    } else {
      builder.addAction(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), QuickReplyIntent(accountId, convId, requestCode))
    }
  }

  private def notificationColor(accountId: AccountId) = {
    BuildConfig.APPLICATION_ID match {
      case "com.wire.internal" => Some(Color.GREEN)
      case "com.waz.zclient.dev" => accentColors.get(accountId)
      case "com.wire.x" => Some(Color.RED)
      case "com.wire.qa" => Some(Color.BLUE)
      case _ => None
    }
  }
}

object MessageNotificationsController {

  def toNotificationGroupId(accountId: AccountId): Int = accountId.str.hashCode()
  def toNotificationConvId(accountId: AccountId, convIdStr:String): Int = (accountId.str + convIdStr).hashCode()
  def toNotificationConvId(accountId: AccountId, convId: ConvId): Int = toNotificationConvId(accountId, convId.str)
  def channelId(accountId: AccountId): String = accountId.str

  val ZETA_MESSAGE_NOTIFICATION_ID: Int = 1339272
  val ZETA_EPHEMERAL_NOTIFICATION_ID: Int = 1339279
  val BundleEnabled = Build.VERSION.SDK_INT > Build.VERSION_CODES.M
}
