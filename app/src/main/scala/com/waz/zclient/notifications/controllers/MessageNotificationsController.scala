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
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import android.text.TextUtils
import androidx.annotation.RawRes
import com.bumptech.glide.request.RequestOptions
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.content.{UserPreferences, _}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.Picture
import com.waz.model._
import com.waz.service.push.NotificationUiController
import com.waz.service.{AccountsService, UiLifeCycle}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.WireApplication._
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.utils.ContextUtils.{getInt, getIntArray}
import com.waz.zclient.utils.{ResString, RingtoneUtils}
import com.waz.zclient.{BuildConfig, Injectable, Injector, R}
import org.threeten.bp.Instant

import scala.concurrent.Future

class MessageNotificationsController(bundleEnabled: Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.M,
                                     applicationId: String = BuildConfig.APPLICATION_ID)
                                    (implicit inj: Injector, cxt: Context, eventContext: EventContext)
  extends Injectable
    with NotificationUiController
    with DerivedLogTag {

  import MessageNotificationsController._
  import Threading.Implicits.Background

  private lazy val notificationManager   = inject[NotificationManagerWrapper]

  private lazy val selfId                = inject[Signal[Option[UserId]]]
  private lazy val soundController       = inject[SoundController]
  private lazy val navigationController  = inject[NavigationController]
  private lazy val convController        = inject[ConversationController]
  private lazy val convsStorage          = inject[Signal[ConversationStorage]]
  private lazy val userStorage           = inject[Signal[UsersStorage]]
  private lazy val teamsStorage          = inject[TeamsStorage]
  private lazy val userPrefs             = inject[Signal[UserPreferences]]

  override val notificationsSourceVisible: Signal[Map[UserId, Set[ConvId]]] =
    for {
      accs         <- inject[Signal[AccountsService]].flatMap(_.accountsWithManagers)
      uiActive     <- inject[UiLifeCycle].uiActive
      Some(selfId) <- selfId
      convId       <- convController.currentConvIdOpt
      convs        <- convsStorage.flatMap(_.contents.map(_.keySet))
      page         <- navigationController.visiblePage
    } yield accs.map { accId =>
      accId ->
        (if (selfId != accId || !uiActive) Set.empty[ConvId]
        else page match {
          case Page.CONVERSATION_LIST => convs
          case Page.MESSAGE_STREAM    => Set(convId).flatten
          case _                      => Set.empty[ConvId]
        })
    }.toMap

  /*
  Clears notifications already displayed in the tray when the user opens the conversation associated
  with those notifications. This is separate from removing notifications from the storage and may
  sometimes be inconsistent (notifications in the tray may stay longer than in the storage).
   */
  Signal(selfId, notificationsSourceVisible).onUi {
    case (Some(selfUserId), sources) =>
      val notIds = sources.getOrElse(selfUserId, Set.empty).map(toNotificationConvId(selfUserId, _))
      if (notIds.nonEmpty) notificationManager.cancelNotifications(notIds)
      notificationManager.cancelNotifications(
        Set(toNotificationGroupId(selfUserId), toEphemeralNotificationGroupId(selfUserId))
      )
    case _ =>
  }

  override def onNotificationsChanged(accountId: UserId, nots: Set[NotificationData]): Future[Unit] = {
    verbose(l"onNotificationsChanged: $accountId, nots: $nots")
    for {
      teamName  <- fetchTeamName(accountId)
      summaries <- createSummaryNotificationProps(accountId, nots, teamName).map(_.map(p => (toNotificationGroupId(accountId), p)))
      convNots  <- createConvNotifications(accountId, nots, teamName).map(_.toMap)
      _         <- Threading.Ui {
                     (convNots ++ summaries).foreach {
                       case (id, props) => notificationManager.showNotification(id, props)
                     }
                   }.future
    } yield {}
  }

  def showAppNotification(title: ResString, body: ResString): Future[Unit] = {
    val contentTitle = SpannableWrapper(title, List(Span(Span.StyleSpanBold, Span.HeaderRange)))
    val contentText = SpannableWrapper(
      header = ResString(""),
      body = body,
      spans = List(Span(Span.ForegroundColorSpanBlack, Span.HeaderRange)),
      separator = ""
    )
    for {
      Some(accountId) <- selfId.head
      color           <- notificationColor(accountId)
    } yield {
      val props = NotificationProps(
        accountId,
        when              = Some(Instant.now().toEpochMilli),
        showWhen          = Some(true),
        category          = Some(NotificationCompat.CATEGORY_MESSAGE),
        priority          = Some(NotificationCompat.PRIORITY_HIGH),
        smallIcon         = Some(R.drawable.ic_menu_logo),
        openAccountIntent = Some(accountId),
        color             = color,
        contentTitle      = Some(contentTitle),
        contentText       = Some(contentText),
        style             = Some(StyleBuilder(StyleBuilder.BigText, title = contentTitle, bigText = Some(contentText)))
      )
      notificationManager.showNotification(accountId.hashCode(), props)
    }
  }

  private def fetchTeamName(userId: UserId) =
    for {
      storage <- userStorage.head
      user    <- storage.get(userId)
      team    <- user.flatMap(_.teamId) match {
        case Some(teamId) => teamsStorage.get(teamId)
        case _            => Future.successful(Option.empty[TeamData])
      }
    } yield team.map(_.name)

  private def createSummaryNotificationProps(userId: UserId, nots: Set[NotificationData], teamName: Option[Name]) = {
    verbose(l"createSummaryNotificationProps: $userId, ${nots.size}")
    if (nots.nonEmpty && bundleEnabled)
      notificationColor(userId).map { color =>
        Some(NotificationProps (userId,
          when                     = Some(nots.minBy(_.time.instant).time.instant.toEpochMilli),
          showWhen                 = Some(true),
          category                 = Some(NotificationCompat.CATEGORY_MESSAGE),
          priority                 = Some(NotificationCompat.PRIORITY_HIGH),
          smallIcon                = Some(R.drawable.ic_menu_logo),
          groupSummary             = Some(true),
          group                    = Some(userId),
          openAccountIntent        = Some(userId),
          contentInfo              = teamName.map(_.str),
          color                    = color
        ))
    } else Future.successful(None)
  }

  private def createConvNotifications(accountId: UserId, nots: Set[NotificationData], teamName: Option[Name]) = {
    verbose(l"createConvNotifications: $accountId, ${nots.size}")
    if (nots.nonEmpty) {
      val (ephemeral, normal) = nots.toSeq.sortBy(_.time).partition(_.ephemeral)

      val groupedConvs =
        if (bundleEnabled)
          normal.groupBy(_.conv).map {
            case (convId, ns) => toNotificationConvId(accountId, convId) -> ns
          } ++ ephemeral.groupBy(_.conv).map {
            case (convId, ns) => toEphemeralNotificationConvId(accountId, convId) -> ns
          }
        else
          Map(toNotificationGroupId(accountId) -> normal, toEphemeralNotificationGroupId(accountId) -> ephemeral)

      val teamNameOpt = if (groupedConvs.keys.size > 1) None else teamName

      Future.sequence(groupedConvs.filter(_._2.nonEmpty).map {
        case (notId, ns) =>
          for {
            commonProps   <- commonNotificationProperties(ns, accountId)
            specificProps <-
              if (ns.size == 1) singleNotificationProperties(commonProps, accountId, ns.head, teamNameOpt)
              else              multipleNotificationProperties(commonProps, accountId, ns, teamNameOpt)
          } yield notId -> specificProps
      })
    } else Future.successful(Iterable.empty)
  }

  private def commonNotificationProperties(ns: Seq[NotificationData], userId: UserId) =
    for {
      color <- notificationColor(userId)
      pic   <- getPictureForNotifications(userId, ns)
    } yield {
      NotificationProps(userId,
        showWhen      = Some(true),
        category      = Some(NotificationCompat.CATEGORY_MESSAGE),
        priority      = Some(NotificationCompat.PRIORITY_HIGH),
        smallIcon     = Some(R.drawable.ic_menu_logo),
        vibrate       = if (soundController.isVibrationEnabled(userId)) Some(getIntArray(R.array.new_message_gcm).map(_.toLong)) else Some(Array(0l,0l)),
        autoCancel    = Some(true),
        sound         = getSound(ns),
        onlyAlertOnce = Some(ns.forall(_.hasBeenDisplayed)),
        group         = Some(userId),
        when          = Some(ns.maxBy(_.time.instant).time.instant.toEpochMilli),
        largeIcon     = pic,
        lights        = Some(color.getOrElse(Color.WHITE), getInt(R.integer.notifications__system__led_on), getInt(R.integer.notifications__system__led_off)),
        color         = color,
        lastIsPing    = ns.map(_.msgType).lastOption.map(_ == KNOCK)
      )
    }

  private def notificationColor(userId: UserId) =
    applicationId match {
      case "com.wire.internal"   => Future.successful(Some(Color.GREEN))
      case "com.waz.zclient.dev" => inject[AccentColorController].colors.head.map(_.get(userId).map(_.color))
      case "com.wire.x"          => Future.successful(Some(Color.RED))
      case "com.wire.qa"         => Future.successful(Some(Color.BLUE))
      case _                     => Future.successful(None)
    }

  private def getOpenConvIntent(account: UserId, n: NotificationData, requestBase: Int) : Option[(UserId, ConvId, Int)] =
    if (n.isConvDeleted) None else Some((account, n.conv, requestBase))

  private def getAction(account: UserId, n: NotificationData, requestBase: Int, offset: Int, bundleEnabled: Boolean)=
    if (n.isConvDeleted) None else Some((account, n.conv, requestBase + 1, bundleEnabled))

  private def singleNotificationProperties(props: NotificationProps, account: UserId, n: NotificationData, teamName: Option[Name]) = {
    verbose(l"singleNotificationProperties: $account, $n, $teamName")
    for {
      title <- getMessageTitle(account, n, None).map(t => SpannableWrapper(t, List(Span(Span.StyleSpanBold, Span.HeaderRange))))
      body  <- getMessage(account, n, singleConversationInBatch = true)
    } yield {
      val requestBase  = System.currentTimeMillis.toInt
      val bigTextStyle = StyleBuilder(StyleBuilder.BigText, title = title, summaryText = teamName.map(_.str), bigText = Some(body))
      val specProps = props.copy(
        contentTitle             = Some(title),
        contentText              = Some(body),
        style                    = Some(bigTextStyle),
        openConvIntent           = getOpenConvIntent(account, n, requestBase),
        clearNotificationsIntent = Some((account, Some(n.conv)))
      )

      if (n.msgType == NotificationType.CONNECT_REQUEST) {
        specProps
      } else {
        specProps.copy(
          action1 = getAction(account, n, requestBase, 1, bundleEnabled),
          action2 = getAction(account, n, requestBase, 2, bundleEnabled)
        )
      }
    }
  }

  private def getMessageTitle(account: UserId, n: NotificationData, teamName: Option[String]) =
    if (n.isConvDeleted) Future.successful(ResString(R.string.notification__message__conversation_deleted))
    else if (n.ephemeral) Future.successful(ResString(R.string.notification__message__ephemeral_someone))
    else getConvName(account, n).map(_.getOrElse(Name.Empty)).map { convName =>
      teamName match {
        case Some(name) => ResString (R.string.notification__message__group__prefix__other, convName, name)
        case None       => ResString(convName)
      }
    }

  private def getConvName(account: UserId, n: NotificationData) =
    inject[AccountToConvsStorage].apply(account).flatMap {
      case Some(storage) => storage.get(n.conv).map(_.map(_.displayName))
      case None          => Future.successful(Option.empty[Name])
    }

  private def getUserName(account: UserId, n: NotificationData) =
    inject[AccountToUsersStorage].apply(account).flatMap {
      case Some(storage) => storage.get(n.user).map(_.map(_.name))
      case None          => Future.successful(Option.empty[Name])
    }

  private def isGroupConv(account: UserId, n: NotificationData) =
    if (n.isConvDeleted) Future.successful(true)
    else inject[AccountToConvsService].apply(account).flatMap {
      case Some(service) => service.isGroupConversation(n.conv)
      case _ => Future.successful(false)
    }

  private def getMessage(account: UserId, n: NotificationData, singleConversationInBatch: Boolean): Future[SpannableWrapper] = {
    val message = n.msg.replaceAll("\\r\\n|\\r|\\n", " ")

    for {
      header         <- n.msgType match {
                          case CONNECT_ACCEPTED => Future.successful(ResString.Empty)
                          case _                => getDefaultNotificationMessageLineHeader(account, n, singleConversationInBatch)
                        }
      convName       <- getConvName(account, n).map(_.getOrElse(Name.Empty))
      userName       <- getUserName(account, n).map(_.getOrElse(Name.Empty))
      messagePreview <- userPrefs.flatMap(_.preference(UserPreferences.MessagePreview).signal).head
    } yield {
      val body = n.msgType match {
        case _ if n.ephemeral && n.isSelfMentioned    => ResString(R.string.notification__message_with_mention__ephemeral)
        case _ if n.ephemeral && n.isReply            => ResString(R.string.notification__message_with_quote__ephemeral)
        case _ if n.ephemeral                         => ResString(R.string.notification__message__ephemeral)
        case TEXT | COMPOSITE if messagePreview       => ResString(message)
        case TEXT | COMPOSITE                         => ResString(R.string.notification__message_one_to_one_message_preview)
        case MISSED_CALL                              => ResString(R.string.notification__message__one_to_one__wanted_to_talk)
        case KNOCK                                    => ResString(R.string.notification__message__one_to_one__pinged)
        case ANY_ASSET                                => ResString(R.string.notification__message__one_to_one__shared_file)
        case IMAGE_ASSET                              => ResString(R.string.notification__message__one_to_one__shared_picture)
        case VIDEO_ASSET                              => ResString(R.string.notification__message__one_to_one__shared_video)
        case AUDIO_ASSET                              => ResString(R.string.notification__message__one_to_one__shared_audio)
        case LOCATION                                 => ResString(R.string.notification__message__one_to_one__shared_location)
        case RENAME                                   => ResString(R.string.notification__message__group__renamed_conversation, convName)
        case MEMBER_LEAVE                             => ResString(R.string.notification__message__group__remove)
        case MEMBER_JOIN                              => ResString(R.string.notification__message__group__add)
        case CONNECT_ACCEPTED                         => ResString(R.string.notification__message__single__accept_request, userName)
        case CONNECT_REQUEST                          => ResString(R.string.people_picker__invite__share_text__header, userName)
        case MESSAGE_SENDING_FAILED                   => ResString(R.string.notification__message__send_failed)
        case CONVERSATION_DELETED if userName.isEmpty => ResString(R.string.notification__message__conversation_deleted)
        case CONVERSATION_DELETED                     => ResString(R.string.notification__message__conversation_deleted_by, userName)
        case LIKE if n.likedContent.nonEmpty =>
          n.likedContent.collect {
            case LikedContent.PICTURE     => ResString(R.string.notification__message__liked_picture)
            case LikedContent.TEXT_OR_URL => ResString(R.string.notification__message__liked, n.msg)
          }.getOrElse(ResString(R.string.notification__message__liked_message))
        case _ => ResString.Empty
      }

      getMessageSpannable(header, body, n.msgType == TEXT || n.msgType == COMPOSITE)
    }
  }

  private def getDefaultNotificationMessageLineHeader(account: UserId, n: NotificationData, singleConversationInBatch: Boolean) =
    for {
      convName <- getConvName(account, n)
      userName <- getUserName(account, n).map(_.getOrElse(Name.Empty))
      isGroup  <- isGroupConv(account, n)
    } yield {
      if (n.ephemeral) ResString.Empty
      else {
        val prefixId =
          if (!singleConversationInBatch && isGroup)
            if (n.isSelfMentioned)
              R.string.notification__message_with_mention__group__prefix__text
            else if (n.isReply)
              R.string.notification__message_with_quote__group__prefix__text
            else
              R.string.notification__message__group__prefix__text
          else if (!singleConversationInBatch && !isGroup || singleConversationInBatch && isGroup)
            if (n.isSelfMentioned)
              R.string.notification__message_with_mention__name__prefix__text
            else if (n.isReply)
              R.string.notification__message_with_quote__name__prefix__text
            else
              R.string.notification__message__name__prefix__text
          else if (singleConversationInBatch && isGroup && n.isReply)
            R.string.notification__message_with_quote__name__prefix__text_one2one
          else 0
        if (prefixId > 0) {
          convName match {
            case Some(cn) => ResString(prefixId, userName, cn)
            case None => ResString(prefixId, List(ResString(userName), ResString(R.string.notification__message__group__default_conversation_name)))
          }
        }
        else ResString.Empty
      }
    }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private def getMessageSpannable(header: ResString, body: ResString, isTextMessage: Boolean) = {
    val spans = Span(Span.ForegroundColorSpanBlack, Span.HeaderRange) ::
      (if (!isTextMessage) List(Span(Span.StyleSpanItalic, Span.BodyRange)) else Nil)
    SpannableWrapper(header = header, body = body, spans = spans, separator = "")
  }

  private def getPictureForNotifications(userId: UserId, nots: Seq[NotificationData]): Future[Option[Bitmap]] =
    if (nots.size == 1 && !nots.exists(_.ephemeral)) {
      val result = for {
        Some(storage) <- inject[AccountToUsersStorage].apply(userId)
        user          <- storage.get(nots.head.user)
        picture        = user.flatMap(_.picture)
        bitmap        <- picture.fold(Future.successful(Option.empty[Bitmap]))(loadPicture)
      } yield bitmap

      result.recoverWith {
        case ex: Exception =>
          warn(l"Could not get avatar.", ex)
          Future.successful(None)
      }
    }
    else Future.successful(None)

  private def loadPicture(picture: Picture): Future[Option[Bitmap]] = {
    Threading.ImageDispatcher {
      Option(WireGlide(cxt)
        .asBitmap()
        .load(picture)
        .apply(new RequestOptions().circleCrop())
        .submit(128, 128)
        .get()).map(Bitmap.fromAndroid)
    }.future
  }

  private def getSound(ns: Seq[NotificationData]) = {
    if (soundController.soundIntensityNone) None
    else if (!soundController.soundIntensityFull && (ns.size > 1 && ns.lastOption.forall(_.msgType != KNOCK))) None
    else ns.map(_.msgType).lastOption.fold(Option.empty[Uri]) {
      case IMAGE_ASSET | ANY_ASSET | VIDEO_ASSET | AUDIO_ASSET |
           LOCATION | TEXT | CONNECT_ACCEPTED | CONNECT_REQUEST | RENAME | COMPOSITE |
           LIKE  => Option(getSelectedSoundUri(soundController.currentTonePrefs._2, R.raw.new_message_gcm))
      case KNOCK => Option(getSelectedSoundUri(soundController.currentTonePrefs._3, R.raw.ping_from_them))
      case _     => None
    }
  }

  private def getSelectedSoundUri(value: String, @RawRes defaultResId: Int): Uri =
    getSelectedSoundUri(value, defaultResId, defaultResId)

  private def getSelectedSoundUri(value: String, @RawRes preferenceDefault: Int, @RawRes returnDefault: Int): Uri = {
    if (!TextUtils.isEmpty(value) && !RingtoneUtils.isDefaultValue(cxt, value, preferenceDefault)) Uri.parse(value)
    else RingtoneUtils.getUriForRawId(cxt, returnDefault)
  }

  private def multipleNotificationProperties(props: NotificationProps, account: UserId, ns: Seq[NotificationData], teamName: Option[Name]): Future[NotificationProps] = {
    verbose(l"multipleNotificationProperties: $account, $ns, $teamName")
    val convIds = ns.map(_.conv).toSet
    val isSingleConv = convIds.size == 1

    val n = ns.head

    for {
      convName <- getConvName(account, n).map(_.getOrElse(Name.Empty))
      messages <- Future.sequence(ns.sortBy(_.time.instant).map(n => getMessage(account, n, singleConversationInBatch = isSingleConv)).takeRight(5).toList)
    } yield {
      val header =
        if (isSingleConv) {
          if (ns.exists(_.ephemeral)) ResString(R.string.notification__message__ephemeral_someone)
          else ResString(convName.str)
        }
        else
          ResString(R.plurals.notification__new_messages__multiple, convIds.size, ns.size)

      val separator = " • "

      val title =
        if (isSingleConv && ns.size > 5)
          SpannableWrapper(
            header = header,
            body = ResString(R.plurals.conversation_list__new_message_count, ns.size),
            spans = List(
              Span(Span.StyleSpanBold, Span.HeaderRange),
              Span(Span.StyleSpanItalic, Span.BodyRange, separator.length),
              Span(Span.ForegroundColorSpanGray, Span.BodyRange)
            ),
            separator = separator
          )
        else
          SpannableWrapper(
            header = header,
            spans = List(Span(Span.StyleSpanBold, Span.HeaderRange))
          )

      val requestBase = System.currentTimeMillis.toInt
      val inboxStyle  = StyleBuilder(StyleBuilder.Inbox, title = title, summaryText = (if (bundleEnabled) teamName else None).map(_.str), lines = messages)

      val specProps = props.copy(
        contentTitle = Some(title),
        contentText  = Some(messages.last),
        style        = Some(inboxStyle)
      )

      if (isSingleConv)
        specProps.copy(
          openConvIntent           = getOpenConvIntent(account, n, requestBase),
          clearNotificationsIntent = Some((account, Some(n.conv))),
          action1                  = getAction(account, n, requestBase, 1, bundleEnabled),
          action2                  = getAction(account, n, requestBase, 2, bundleEnabled)
        )
      else
        specProps.copy(
          openAccountIntent        = Some(account),
          clearNotificationsIntent = Some((account, None))
        )
    }
  }
}

object MessageNotificationsController {

  def toNotificationGroupId(userId: UserId): Int = userId.str.hashCode()
  def toEphemeralNotificationGroupId(userId: UserId): Int = toNotificationGroupId(userId) + 1
  def toNotificationConvId(userId: UserId, convId: ConvId): Int = (userId.str + convId.str).hashCode()
  def toEphemeralNotificationConvId(userId: UserId, convId: ConvId): Int = toNotificationConvId(userId, convId) + 1

  val ZETA_MESSAGE_NOTIFICATION_ID: Int = 1339272
  val ZETA_EPHEMERAL_NOTIFICATION_ID: Int = 1339279
}
