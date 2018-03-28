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
import android.app.Notification
import android.content.Context
import android.graphics._
import android.net.Uri
import android.os.Build
import android.support.annotation.RawRes
import android.support.v4.app.{NotificationCompat, RemoteInput}
import android.text.style.{ForegroundColorSpan, StyleSpan}
import android.text.{SpannableString, Spanned, TextUtils}
import com.waz.NotificationManagerWrapper
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.bitmap.BitmapUtils
import com.waz.content.{AccountsStorage, AssetsStorage, ConversationStorage, TeamsStorage}
import com.waz.model._
import com.waz.service.images.ImageLoader
import com.waz.service.push.GlobalNotificationsService
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.service.{AccountsService, UiLifeCycle}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.Bitmap
import com.waz.utils.{returning, _}
import com.waz.zclient.Intents._
import com.waz.zclient._
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RingtoneUtils
import com.waz.zms.NotificationsAndroidService
import com.waz.zms.NotificationsAndroidService.clearNotificationsIntent

import scala.concurrent.Future
import scala.concurrent.duration._

class MessageNotificationsController(bundleEnabled: Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
  (implicit inj: Injector, cxt: Context, eventContext: EventContext) extends Injectable { self =>
  import MessageNotificationsController._
  import Threading.Implicits.Background

  private lazy val accounts              = inject[AccountsService]
  private lazy val globalNotifications   = inject[GlobalNotificationsService]

  private val notManager                 = inject[NotificationManagerWrapper]
  private lazy val soundController       = inject[SoundController]
  private lazy val navigationController  = inject[NavigationController]
  private lazy val convController        = inject[ConversationController]
  private lazy val accentColorController = inject[AccentColorController]

  private var accentColors = Map[AccountId, Int]()

  accentColorController.colors { cs =>
    accentColors = cs.mapValues(_.getColor)
  }

  globalNotifications.groupedNotifications.onUi { notifications =>
    notifications.toSeq.sortBy(_._1.str.hashCode).foreach {
      case (accountId, (shouldBeSilent, nots)) =>
        verbose(s"notifications received: ${nots.map(n => (n.id, n.message, n.convId, n.userName))}")
        for {
          accountData <- inject[AccountsStorage].get(accountId)
          team        <- accountData.map(_.teamId) match {
            case Some(Right(Some(teamId))) => inject[TeamsStorage].get(teamId)
            case _                         => Future.successful(Option.empty[TeamData])
          }
          _           = createConvNotifications(accountId, shouldBeSilent, nots, team.map(_.name))
        } yield ()
    }
  }

  val conversationsBeingDisplayed = for {
    accs        <- accounts.loggedInAccounts
    convStorage <- inject[Signal[ConversationStorage]]
    accountId   <- inject[Signal[AccountId]]
    uiActive    <- inject[UiLifeCycle].uiActive
    convId      <- convController.currentConvIdOpt.orElse(Signal.const(Option.empty[ConvId]))
    convs       <- convStorage.convsSignal.map(_.conversations)
    page        <- navigationController.visiblePage
  } yield
    accs.map { acc =>
      (acc.id,
        if (accountId != acc.id || !uiActive) Set.empty[ConvId]
        else page match {
          case Page.CONVERSATION_LIST => convs.map(_.id)
          case Page.MESSAGE_STREAM    => Set(convId).flatten
          case _                      => Set.empty[ConvId]
        })
    }.toMap

  conversationsBeingDisplayed { displayed =>
    globalNotifications.notificationsSourceVisible ! displayed

    displayed.foreach {
      case (accountId, convIds) =>
        convIds.foreach { convId => notManager.cancel(toNotificationConvId(accountId, convId)) }
    }

  }

  private def createConvNotifications(accountId: AccountId, silent: Boolean, nots: Seq[NotificationInfo], teamName: Option[String]): Unit =
    if (nots.isEmpty) Future.successful(notManager.cancel(toNotificationGroupId(accountId)))
    else {
      val groupedConvs = if (bundleEnabled)
        nots.groupBy(_.convId).map {
          case (conv, ns) => toNotificationConvId(accountId, conv) -> ns
        }
      else
        Map(toNotificationGroupId(accountId) -> nots)

      val teamNameOpt = if (groupedConvs.keys.size > 1) None else teamName

      if (bundleEnabled) {
        val notIdSet = groupedConvs.keys.toSet
        notManager.getActiveNotificationIds
          .collect { case id if !notIdSet.contains(id) => id }
          .foreach(notManager.cancel)

        val summaryId = toNotificationGroupId(accountId)
        if (notIdSet.contains(summaryId)) notManager.cancel(summaryId)

        val summary = createSummaryNotification(accountId, silent, nots, teamName)
        notManager.notify(summaryId, summary)
      }

      val notFutures = groupedConvs.map { case (notId, ns) =>
        getPictureForNotifications(accountId, ns).map { pic =>
          val builder = commonBuilder(ns, accountId, silent, pic)
          val notification = if (ns.size == 1)
            getSingleMessageNotification(accountId, builder, ns.head, teamNameOpt)
          else
            getMultipleMessagesNotification(accountId, ns, builder, teamNameOpt)
          notId -> (notification, ns.map(_.id))
        }
      }

      Future.sequence(notFutures).foreach {
        _.foreach { case (notId, (notification, notsToCancel)) =>
          notManager.notify(notId, notification)
          globalNotifications.markAsDisplayed(accountId, notsToCancel)
        }
      }(Threading.Ui)
    }

  private def getPictureForNotifications(accountId: AccountId, nots: Seq[NotificationInfo]): Future[Option[Bitmap]] =
    if (nots.exists(_.isEphemeral)) Future.successful(None)
    else {
      val pictures  = nots.flatMap(_.userPicture).distinct
      val iconWidth = toPx(64)
      val assetId   = if (pictures.size == 1) pictures.headOption else None

      for {
        storage   <- inject[Signal[AssetsStorage]].head
        loader    <- inject[Signal[ImageLoader]].head
        assetData <- assetId.fold(Future.successful(Option.empty[AssetData]))(storage.get)
        bmp       <- assetData.fold(Future.successful(Option.empty[Bitmap])){ ad =>
          loader.loadBitmap(ad, BitmapRequest.Single(iconWidth), forceDownload = false).map(Option(_)).withTimeout(500.millis).recoverWith {
            case _ : Throwable => CancellableFuture.successful(None)
          }.future
        }
      } yield {
        bmp.map { original => BitmapUtils.createRoundBitmap(original, iconWidth, 0, Color.TRANSPARENT) }
      }
    }

  private def createSummaryNotification(accountId: AccountId, silent: Boolean, nots: Seq[NotificationInfo], teamName: Option[String]): Notification = {
    val builder = new NotificationCompat.Builder(cxt, null)
      .setWhen(nots.minBy(_.time).time.toEpochMilli)
      .setShowWhen(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setSmallIcon(R.drawable.ic_menu_logo)
      .setContentTitle("")
      .setContentText("")
      .setStyle(new NotificationCompat.InboxStyle())
      .setGroupSummary(true)
      .setGroup(accountId.str)
      .setContentIntent(OpenAccountIntent(accountId))
      .setDeleteIntent(clearNotificationsIntent(accountId, cxt))

    teamName.foreach(builder.setContentInfo)
    notificationColor(accountId).foreach(builder.setColor)

    builder.build()
  }

  private def getSelectedSoundUri(value: String, @RawRes defaultResId: Int): Uri =
    getSelectedSoundUri(value, defaultResId, defaultResId)

  private def getSelectedSoundUri(value: String, @RawRes preferenceDefault: Int, @RawRes returnDefault: Int): Uri = {
    if (!TextUtils.isEmpty(value) && !RingtoneUtils.isDefaultValue(cxt, value, preferenceDefault)) Uri.parse(value)
    else RingtoneUtils.getUriForRawId(cxt, returnDefault)
  }

  private def commonBuilder(ns: Seq[NotificationInfo], accountId: AccountId, silent: Boolean, pic: Option[Bitmap]) = {
    val builder = new NotificationCompat.Builder(cxt, null)
    builder.setShowWhen(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setSmallIcon(R.drawable.ic_menu_logo)
      .setVibrate(if (!silent && soundController.isVibrationEnabled) getIntArray(R.array.new_message_gcm).map(_.toLong) else Array(0,0))
      .setAutoCancel(true)
      .setSound(getSound(ns, silent))
      .setOnlyAlertOnce(ns.forall(_.hasBeenDisplayed))
      .setGroup(accountId.str)
      .setWhen(ns.maxBy(_.time).time.toEpochMilli)

    notificationColor(accountId).foreach { color =>
      builder
        .setLights(color, getInt(R.integer.notifications__system__led_on), getInt(R.integer.notifications__system__led_off))
        .setColor(color)
    }

    pic.foreach(builder.setLargeIcon(_))
    builder
  }

  private def getSingleMessageNotification(accountId: AccountId, builder: NotificationCompat.Builder, n: NotificationInfo, teamName: Option[String]): Notification = {
    val title = returning(new SpannableString(getMessageTitle(n, None))) { sp =>
      sp.setSpan(new StyleSpan(Typeface.BOLD), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    val body = getMessage(n, singleConversationInBatch = true)

    val requestBase = System.currentTimeMillis.toInt
    val bigTextStyle = returning(new NotificationCompat.BigTextStyle) { s =>
      s.setBigContentTitle(title)
      s.bigText(body)
      teamName.foreach(s.setSummaryText)
    }

    builder
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(bigTextStyle)
      .setContentIntent(OpenConvIntent(accountId, n.convId, requestBase))
      .setDeleteIntent(clearNotificationsIntent(accountId, n.convId, cxt))

    if (n.tpe != NotificationType.CONNECT_REQUEST) {
      builder.addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(accountId, n.convId, requestBase + 1))
      addQuickReplyAction(builder, accountId, n.convId, requestBase + 2)
    }

    builder.build
  }

  private def getMultipleMessagesNotification(accountId: AccountId, ns: Seq[NotificationInfo], builder: NotificationCompat.Builder, teamName: Option[String]): Notification = {
    val convIds = ns.map(_.convId).toSet
    val isSingleConv = convIds.size == 1

    val titleText =
      if (isSingleConv) {
        if (ns.exists(_.isEphemeral)) getString(R.string.notification__message__ephemeral_someone)
        else if (ns.head.isGroupConv) ns.head.convName.getOrElse("")
        else ns.head.convName.orElse(ns.head.userName).getOrElse("")
      }
      else
        getQuantityString(R.plurals.notification__new_messages__multiple, ns.size, Integer.valueOf(ns.size), convIds.size.toString)

    val separator = " • "

    val title = if (isSingleConv && ns.size > 5)
      returning(new SpannableString(titleText + separator + getQuantityString(R.plurals.conversation_list__new_message_count, ns.size, Integer.valueOf(ns.size)))) { sp =>
        sp.setSpan(new StyleSpan(Typeface.BOLD), 0, titleText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(new StyleSpan(Typeface.ITALIC), titleText.length + separator.length, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(new ForegroundColorSpan(Color.GRAY), titleText.length, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    else
      returning(new SpannableString(titleText)) { titleSpan =>
        titleSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, titleText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }

    val requestBase = System.currentTimeMillis.toInt

    if (isSingleConv) {
      builder
        .addAction(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(accountId, convIds.head, requestBase + 1))
        addQuickReplyAction(builder, accountId, convIds.head, requestBase + 2)
    }

    val messages = ns.sortBy(_.time).map(n => getMessage(n, singleConversationInBatch = isSingleConv)).takeRight(5)

    val inboxStyle = returning(new NotificationCompat.InboxStyle()) { s =>
      s.setBigContentTitle(title)
      if (bundleEnabled) teamName.foreach(s.setSummaryText)
      messages.foreach(s.addLine)
    }

    builder
      .setContentTitle(title)
      .setContentText(messages.last)
      .setStyle(inboxStyle)
      .setContentIntent(if (isSingleConv) OpenConvIntent(accountId, convIds.head, requestBase) else OpenAccountIntent(accountId))
      .setDeleteIntent(if (isSingleConv) clearNotificationsIntent(accountId, convIds.head, cxt) else clearNotificationsIntent(accountId, cxt))
      .build()
  }

  def addQuickReplyAction(builder: NotificationCompat.Builder, accountId: AccountId, convId: ConvId, requestCode: Int): Unit = {
    if (bundleEnabled) {
      val remoteInput = new RemoteInput.Builder(NotificationsAndroidService.InstantReplyKey)
        .setLabel(getString(R.string.notification__action__reply))
        .build
      val replyAction = new NotificationCompat.Action.Builder(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), NotificationsAndroidService.quickReplyIntent(accountId, convId, cxt))
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(true)
        .build()
      builder.addAction(replyAction)
    } else
      builder.addAction(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), QuickReplyIntent(accountId, convId, requestCode))

  }

  private def getSound(ns: Seq[NotificationInfo], silent: Boolean) = {
      if (soundController.soundIntensityNone || silent) null
      else if (!soundController.soundIntensityFull && (ns.size > 1 && ns.lastOption.forall(_.tpe != KNOCK))) null
      else ns.map(_.tpe).lastOption.fold(null.asInstanceOf[Uri]) {
        case ASSET | ANY_ASSET | VIDEO_ASSET | AUDIO_ASSET |
             LOCATION | TEXT | CONNECT_ACCEPTED | CONNECT_REQUEST | RENAME |
             LIKE  => getSelectedSoundUri(soundController.currentTonePrefs._2, R.raw.new_message_gcm)
        case KNOCK => getSelectedSoundUri(soundController.currentTonePrefs._3, R.raw.ping_from_them)
        case _     => null
      }
  }

  private[notifications] def getMessage(n: NotificationInfo, singleConversationInBatch: Boolean) = {
    val message = n.message.replaceAll("\\r\\n|\\r|\\n", " ")

    val header = n.tpe match {
      case CONNECT_ACCEPTED => ""
      case _                => getDefaultNotificationMessageLineHeader(n, singleConversationInBatch)
    }

    val body = n.tpe match {
      case _ if n.isEphemeral                     => getString(R.string.conversation_list__ephemeral)
      case TEXT                                   => message
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
      case CONNECT_REQUEST                        => getString(R.string.notification__message__connection_request, n.userName.getOrElse(""))
      case CONNECT_ACCEPTED                       => getString(R.string.notification__message__single__accept_request, n.userName.getOrElse(""))
      case MESSAGE_SENDING_FAILED                 => getString(R.string.notification__message__send_failed)
      case LIKE if n.likedContent.nonEmpty =>
        n.likedContent match {
          case Some(LikedContent.PICTURE)     => getString(R.string.notification__message__liked_picture)
          case Some(LikedContent.TEXT_OR_URL) => getString(R.string.notification__message__liked, n.message)
          case _                              => getString(R.string.notification__message__liked_message)
        }
      case _ => ""
    }

    getMessageSpannable(header, body, n.tpe == TEXT, n.isEphemeral)
  }

  private def getMessageTitle(n: NotificationInfo, teamName: Option[String]) =
    if (n.isEphemeral) getString(R.string.notification__message__ephemeral_someone)
    else {
      val convName = n.convName.orElse(n.userName).getOrElse("")
      teamName.fold(convName)(getString(R.string.notification__message__group__prefix__other, convName, _))
    }

  @TargetApi(21)
  private def getMessageSpannable(header: String, body: String, isTextMessage: Boolean, isEphemeral: Boolean) =
    returning(new SpannableString(header + body)) { sp =>
      sp.setSpan(new ForegroundColorSpan(Color.BLACK), 0, header.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      if (!isTextMessage || isEphemeral) sp.setSpan(new StyleSpan(Typeface.ITALIC), header.length, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

  private def getDefaultNotificationMessageLineHeader(n: NotificationInfo, singleConversationInBatch: Boolean) =
    if (n.isEphemeral) ""
    else {
      val prefixId =
        if (!singleConversationInBatch && n.isGroupConv) R.string.notification__message__group__prefix__text
        else if (!singleConversationInBatch && !n.isGroupConv || singleConversationInBatch && n.isGroupConv) R.string.notification__message__name__prefix__text
        else 0
      if (prefixId > 0)
        getString(prefixId, n.userName.getOrElse(""), n.convName.filterNot(_.isEmpty).getOrElse(getString(R.string.notification__message__group__default_conversation_name)))
      else ""
    }

  private def notificationColor(accountId: AccountId) = BuildConfig.APPLICATION_ID match {
    case "com.wire.internal"   => Some(Color.GREEN)
    case "com.waz.zclient.dev" => accentColors.get(accountId)
    case "com.wire.x"          => Some(Color.RED)
    case "com.wire.qa"         => Some(Color.BLUE)
    case _                     => None
  }
}

object MessageNotificationsController {

  def toNotificationGroupId(accountId: AccountId): Int = accountId.str.hashCode()
  def toNotificationConvId(accountId: AccountId, convId: ConvId): Int = (accountId.str + convId.str).hashCode()
  def channelId(accountId: AccountId): String = accountId.str

  val ZETA_MESSAGE_NOTIFICATION_ID: Int = 1339272
  val ZETA_EPHEMERAL_NOTIFICATION_ID: Int = 1339279
}
