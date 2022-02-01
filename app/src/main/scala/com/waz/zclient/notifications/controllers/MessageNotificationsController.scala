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
import com.waz.service.UiLifeCycle
import com.waz.service.otr.NotificationUiController
import com.waz.threading.Threading
import com.wire.signals.{EventContext, Signal}
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
import com.waz.zclient.{Injectable, Injector, R}
import org.threeten.bp.Instant

import scala.concurrent.Future
import com.waz.threading.Threading._
import com.waz.zms.BuildConfig

import scala.util.Try

object MessageNotificationsController {
  sealed trait NotificationSource
  case object EmptyNotificationSource extends NotificationSource
  final case class OneConvNotificationSource(accountId: UserId, convId: ConvId) extends NotificationSource
  final case class AllAccountNotificationSource(accountId: UserId) extends NotificationSource
}

final class MessageNotificationsController(applicationId: String = BuildConfig.APPLICATION_ID)
                                          (implicit inj: Injector, cxt: Context)
  extends Injectable
    with NotificationUiController
    with DerivedLogTag {
  import Threading.Implicits.Background
  import EventContext.Implicits.global
  import MessageNotificationsController._

  private lazy val notificationManager   = inject[NotificationManagerWrapper]

  private lazy val selfId                = inject[Signal[UserId]]
  private lazy val soundController       = inject[SoundController]
  private lazy val navigationController  = inject[NavigationController]
  private lazy val convController        = inject[ConversationController]
  private lazy val userStorage           = inject[Signal[UsersStorage]]
  private lazy val teamsStorage          = inject[TeamsStorage]
  private lazy val userPrefs             = inject[Signal[UserPreferences]]
  private lazy val accountStorage        = inject[AccountStorage]

  def initialize(): Unit = {
    //Clears notifications already displayed in the tray when the user opens the conversation associated with those notifications.
    notificationsSourceVisible.onUi {
      case OneConvNotificationSource(accountId, convId) =>
        cancelNotifications(accountId, convId)
      case AllAccountNotificationSource(accountId) =>
        cancelNotifications(accountId)
      case EmptyNotificationSource =>
    }

    accountStorage.onDeleted.onUi { removedAccounts =>
      removedAccounts.foreach(userId => cancelNotifications(userId))
    }
  }

  private lazy val notificationsSourceVisible: Signal[NotificationSource] =
    inject[UiLifeCycle].uiActive.flatMap {
      case false =>
        Signal.const(EmptyNotificationSource)
      case true =>
        navigationController.visiblePage.zip(selfId).flatMap {
          case (Page.MESSAGE_STREAM, accountId) =>
            val source = convController.currentConvIdOpt.head.map {
              case Some(convId) => OneConvNotificationSource(accountId, convId)
              case _            => EmptyNotificationSource
            }
            Signal.from(source)
          case (Page.CONVERSATION_LIST, accountId) =>
            Signal.const(AllAccountNotificationSource(accountId))
          case (Page.START, accountId) =>
            Signal.const(AllAccountNotificationSource(accountId))
          case _ =>
            Signal.const(EmptyNotificationSource)
        }
    }

  override def cancelNotifications(accountId: UserId, convId: ConvId): Unit =
    notificationManager.cancelNotifications(accountId, convId)

  override def cancelNotifications(accountId: UserId): Unit =
    notificationManager.cancelNotifications(accountId)

  override def showNotifications(accountId: UserId, nots: Set[NotificationData]): Future[Unit] = {
    verbose(l"showNotifications: $accountId, nots: $nots")
    for {
      teamName  <- fetchTeamName(accountId)
      summaries <- createSummaryNotificationProps(accountId, nots, teamName)
      convNots  <- createConvNotifications(accountId, nots, teamName)
      _         <- Threading.Ui { notificationManager.showNotification(convNots ++ summaries) }.future
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
      accountId <- selfId.head
      color     <- notificationColor(accountId)
    } yield {
      val props = NotificationProps(
        accountId         = accountId,
        when              = Instant.now().toEpochMilli,
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
      notificationManager.showNotification(Seq(props))
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
    if (nots.nonEmpty)
      notificationColor(userId).map { color =>
        Some(NotificationProps(
          accountId         = userId,
          when              = nots.maxBy(_.time.instant).time.instant.toEpochMilli,
          showWhen          = Some(true),
          category          = Some(NotificationCompat.CATEGORY_MESSAGE),
          priority          = Some(NotificationCompat.PRIORITY_HIGH),
          smallIcon         = Some(R.drawable.ic_menu_logo),
          groupSummary      = Some(true),
          group             = Some(userId),
          openAccountIntent = Some(userId),
          contentInfo       = teamName.map(_.str),
          color             = color
        ))
    } else Future.successful(None)
  }

  private def createConvNotifications(accountId: UserId, nots: Set[NotificationData], teamName: Option[Name]): Future[Iterable[NotificationProps]] = {
    verbose(l"createConvNotifications: $accountId, ${nots.size}")
    if (nots.nonEmpty) {
      val groupedConvs = nots.toSeq.sortBy(_.time).groupBy(_.conv)

      val teamNameOpt = if (groupedConvs.keys.size > 1) None else teamName

      Future.sequence(groupedConvs.filter(_._2.nonEmpty).map {
        case (_, ns) =>
          for {
            commonProps   <- commonNotificationProperties(ns, accountId)
            specificProps <-
              if (ns.size == 1) singleNotificationProperties(commonProps, accountId, ns.head, teamNameOpt)
              else              multipleNotificationProperties(commonProps, accountId, ns, teamNameOpt)
          } yield specificProps
      })
    } else Future.successful(Iterable.empty)
  }

  private def commonNotificationProperties(ns: Seq[NotificationData], userId: UserId): Future[NotificationProps] =
    for {
      color <- notificationColor(userId)
      pic   <- getPictureForNotifications(userId, ns)
    } yield {
      NotificationProps(
        accountId     = userId,
        when          = ns.maxBy(_.time.instant).time.instant.toEpochMilli,
        showWhen      = Some(true),
        category      = Some(NotificationCompat.CATEGORY_MESSAGE),
        priority      = Some(NotificationCompat.PRIORITY_HIGH),
        smallIcon     = Some(R.drawable.ic_menu_logo),
        vibrate       = if (soundController.isVibrationEnabled) Some(getIntArray(R.array.new_message_gcm).map(_.toLong)) else Some(Array(0l,0l)),
        autoCancel    = Some(true),
        sound         = getSound(ns),
        onlyAlertOnce = Some(ns.forall(_.hasBeenDisplayed)),
        group         = Some(userId),
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

  private def getAction(account: UserId, n: NotificationData, requestBase: Int, offset: Int)=
    if (n.isConvDeleted) None else Some((account, n.conv, requestBase + 1))

  private def singleNotificationProperties(props: NotificationProps, account: UserId, n: NotificationData, teamName: Option[Name]) = {
    verbose(l"singleNotificationProperties: $account, $n, $teamName")
    for {
      title <- getMessageTitle(account, n, None).map(t => SpannableWrapper(t, List(Span(Span.StyleSpanBold, Span.HeaderRange))))
      body  <- getMessage(account, n, singleConversationInBatch = true)
    } yield {
      val requestBase  = System.currentTimeMillis.toInt
      val bigTextStyle = StyleBuilder(StyleBuilder.BigText, title = title, summaryText = teamName.map(_.str), bigText = Some(body))
      val specProps = props.copy(
        convId                   = Some(n.conv),
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
          action1 = getAction(account, n, requestBase, 1),
          action2 = getAction(account, n, requestBase, 2)
        )
      }
    }
  }

  private def getMessageTitle(account: UserId, n: NotificationData, teamName: Option[String]) =
    if (n.isConvDeleted) Future.successful(ResString(R.string.notification__message__conversation_deleted))
    else if (n.ephemeral) Future.successful(ResString(R.string.notification__message__ephemeral_someone))
    else getConvName(account, n).map { convName =>
      teamName match {
        case Some(name) => ResString (R.string.notification__message__group__prefix__other, convName, name)
        case None       => ResString(convName)
      }
    }

  private def getConvName(account: UserId, n: NotificationData): Future[Name] =
    inject[AccountToConvsService].apply(account).flatMap {
      case Some(service) => service.conversationName(n.conv).head
      case None          => Future.successful(Name.Empty)
    }

  private def getUserName(account: UserId, n: NotificationData) =
    inject[AccountToUserService].apply(account).flatMap {
      case Some(service) if BuildConfig.FEDERATION_USER_DISCOVERY && n.userDomain.isDefined =>
        service.getOrCreateQualifiedUser(QualifiedId(n.user, n.userDomain.str), waitTillSynced = true).map(u => Some(u.name))
      case Some(service) =>
        service.getOrCreateUser(n.user, waitTillSynced = true).map(u => Some(u.name))
      case None =>
        Future.successful(Option.empty[Name])
    }

  private def isGroupConv(account: UserId, n: NotificationData) =
    if (n.isConvDeleted) Future.successful(true)
    else
      inject[AccountToConvsService].apply(account).flatMap {
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
        case RENAME                                   => ResString(R.string.notification__message__group__renamed_conversation, n.msg)
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
          if (!singleConversationInBatch && isGroup) {
            if (n.isSelfMentioned) {
              R.string.notification__message_with_mention__group__prefix__text
            } else if (n.isReply) {
              R.string.notification__message_with_quote__group__prefix__text
            } else {
              R.string.notification__message__group__prefix__text
            }
          } else if (!singleConversationInBatch && !isGroup || singleConversationInBatch && isGroup) {
            if (n.isSelfMentioned) {
              R.string.notification__message_with_mention__name__prefix__text
            } else if (n.isReply) {
              R.string.notification__message_with_quote__name__prefix__text
            } else {
              R.string.notification__message__name__prefix__text
            }
          } else if (singleConversationInBatch && isGroup && n.isReply) {
            R.string.notification__message_with_quote__name__prefix__text_one2one
          } else 0
        if (prefixId > 0) {
          if (convName.isEmpty) {
            ResString(prefixId, List(ResString(userName), ResString(R.string.notification__message__group__default_conversation_name)))
          } else {
            ResString(prefixId, userName, convName)
          }
        }
        else ResString.Empty
      }
    }

  private def getMessageSpannable(header: ResString, body: ResString, isTextMessage: Boolean) = {
    val spans = Span(Span.ForegroundColorSpanBlack, Span.HeaderRange) ::
      (if (!isTextMessage) List(Span(Span.StyleSpanItalic, Span.BodyRange)) else Nil)
    SpannableWrapper(header = header, body = body, spans = spans, separator = "")
  }

  private def getPictureForNotifications(accountId: UserId, nots: Seq[NotificationData]): Future[Option[Bitmap]] = {
    def picture(not: NotificationData): Future[Option[Bitmap]] =
      (for {
        Some(service) <- inject[AccountToUserService].apply(accountId)
        user          <- if (BuildConfig.FEDERATION_USER_DISCOVERY && not.userDomain.isDefined)
                           service.getOrCreateQualifiedUser(QualifiedId(not.user, not.userDomain.str), waitTillSynced = true)
                         else
                           service.getOrCreateUser(not.user, waitTillSynced = true)
        bitmap        <- user.picture.fold(Future.successful(Option.empty[Bitmap]))(loadPicture)
      } yield bitmap).recoverWith {
        case ex: Exception =>
          warn(l"Could not get avatar.", ex)
          Future.successful(None)
      }

    if (nots.length != 1 || nots.head.ephemeral)
      Future.successful(None)
    else
      picture(nots.head)
  }

  private def loadPicture(picture: Picture): Future[Option[Bitmap]] = Try {
    Threading.ImageDispatcher {
      Option(WireGlide(cxt)
        .asBitmap()
        .load(picture)
        .apply(new RequestOptions().circleCrop())
        .submit(128, 128)
        .get()).map(Bitmap.fromAndroid)
    }.future
  }.getOrElse(Future.successful(None))

  private def getSound(ns: Seq[NotificationData]) =
    if (soundController.soundIntensityNone || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) None
    else if (!soundController.soundIntensityFull && (ns.size > 1 && ns.lastOption.forall(_.msgType != KNOCK))) None
    else ns.map(_.msgType).lastOption.fold(Option.empty[Uri]) {
      case IMAGE_ASSET | ANY_ASSET | VIDEO_ASSET | AUDIO_ASSET |
           LOCATION | TEXT | CONNECT_ACCEPTED | CONNECT_REQUEST | RENAME | COMPOSITE |
           LIKE  => Option(getSelectedSoundUri(soundController.currentTonePrefs._2, R.raw.new_message_gcm))
      case KNOCK => Option(getSelectedSoundUri(soundController.currentTonePrefs._3, R.raw.ping_from_them))
      case _     => None
    }


  private def getSelectedSoundUri(value: String, @RawRes defaultResId: Int): Uri =
    getSelectedSoundUri(value, defaultResId, defaultResId)

  private def getSelectedSoundUri(value: String, @RawRes preferenceDefault: Int, @RawRes returnDefault: Int): Uri =
    if (!TextUtils.isEmpty(value) && !RingtoneUtils.isDefaultValue(cxt, value, preferenceDefault)) Uri.parse(value)
    else RingtoneUtils.getUriForRawId(cxt, returnDefault)

  private def multipleNotificationProperties(props: NotificationProps, account: UserId, ns: Seq[NotificationData], teamName: Option[Name]): Future[NotificationProps] = {
    val convIds = ns.map(_.conv).toSet
    val isSingleConv = convIds.size == 1
    val n = ns.head

    for {
      convName <- getConvName(account, n)
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
      val inboxStyle  = StyleBuilder(StyleBuilder.Inbox, title = title, summaryText = (teamName).map(_.str), lines = messages)

      val specProps = props.copy(
        contentTitle = Some(title),
        contentText  = Some(messages.last),
        style        = Some(inboxStyle)
      )

      if (isSingleConv)
        specProps.copy(
          convId                   = Some(n.conv),
          openConvIntent           = getOpenConvIntent(account, n, requestBase),
          clearNotificationsIntent = Some((account, Some(n.conv))),
          action1                  = getAction(account, n, requestBase, 1),
          action2                  = getAction(account, n, requestBase, 2)
        )
      else
        specProps.copy(
          openAccountIntent        = Some(account),
          clearNotificationsIntent = Some((account, None))
        )
    }
  }
}

