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
import android.graphics._
import android.net.Uri
import android.os.Build
import android.support.annotation.RawRes
import android.support.v4.app.NotificationCompat
import android.text.TextUtils
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.verbose
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.bitmap.BitmapUtils
import com.waz.content.{AssetsStorage, ConversationStorage, TeamsStorage, UsersStorage}
import com.waz.model._
import com.waz.service.images.ImageLoader
import com.waz.service.push.GlobalNotificationsService
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.service.{AccountsService, UiLifeCycle}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils._
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.WireApplication.{AccountToAssetsStorage, AccountToImageLoader}
import com.waz.zclient._
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.ConversationListAdapter
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{ResString, RingtoneUtils}

import scala.concurrent.Future
import scala.concurrent.duration._

class MessageNotificationsController(bundleEnabled: Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.M,
                                     applicationId: String = BuildConfig.APPLICATION_ID)
                                    (implicit inj: Injector, cxt: Context, eventContext: EventContext) extends Injectable { self =>

  import MessageNotificationsController._
  import Threading.Implicits.Background

  private lazy val notificationsService  = inject[GlobalNotificationsService]
  private lazy val accounts              = inject[AccountsService]
  private lazy val selfId                = inject[Signal[UserId]]
  private lazy val soundController       = inject[SoundController]
  private lazy val navigationController  = inject[NavigationController]
  private lazy val convController        = inject[ConversationController]
  private lazy val convsStorage          = inject[Signal[ConversationStorage]]
  private lazy val userStorage           = inject[Signal[UsersStorage]]
  private lazy val teamsStorage          = inject[TeamsStorage]

  val notificationsToCancel = EventStream[Set[Int]]()
  val notificationToBuild   = EventStream[(Int, NotificationProps)]()

  private var accentColors = Map[UserId, Int]()

  inject[AccentColorController].colors { cs =>
    accentColors = cs.map { case (userId, color) => userId -> color.color }
  }

  private val newNotifications =
    for {
      notifications <- notificationsService.groupedNotifications.map(_.toSeq.sortBy(_._1.str.hashCode))
      _             =  verbose(s"grouped notifications: ${notifications.map {case (userId, (_, nots)) => userId -> nots.size}}")
      tn            <- Signal.future(Future.sequence(notifications.map { case (userId, _) => fetchTeamName(userId) }))
      teamNames     =  tn.toMap
    } yield notifications.map {
      case (userId, (shouldBeSilent, nots)) => (userId, shouldBeSilent, nots, teamNames(userId))
    }

  newNotifications.onUi {
    _.foreach {
      case (userId, shouldBeSilent, nots, teamName) if nots.nonEmpty =>
        createConvNotifications(userId, shouldBeSilent, nots, teamName)
      case (userId, _, _, _) =>
        notificationsToCancel ! Set(toNotificationGroupId(userId), toEphemeralNotificationGroupId(userId))
    }
  }

  newNotifications { notifications =>
    if (bundleEnabled) {
      val notIdSet = notifications.flatMap { case (userId, _, nots, _) =>
        nots.flatMap(n => Seq(toNotificationConvId(userId, n.convId), toEphemeralNotificationConvId(userId, n.convId))) ++ Seq(toNotificationGroupId(userId))
      }.toSet

      val activeIds = inject[NotificationManagerWrapper].getActiveNotificationIds.toSet

      val toCancel = activeIds -- notIdSet
      if (toCancel.nonEmpty) notificationsToCancel ! toCancel
    }
  }

  (for {
    accs     <- accounts.accountsWithManagers
    uiActive <- inject[UiLifeCycle].uiActive
    selfId   <- selfId
    convId   <- convController.currentConvIdOpt
    convs    <- convsStorage.flatMap(_.convsSignal.map(_.conversations.filter(ConversationListAdapter.Normal.filter).map(_.id).toSet))
    page     <- navigationController.visiblePage
  } yield
    accs.map { accId =>
      accId ->
        (if (selfId != accId || !uiActive) Set.empty[ConvId]
        else page match {
          case Page.CONVERSATION_LIST => convs
          case Page.MESSAGE_STREAM    => Set(convId).flatten
          case _                      => Set.empty[ConvId]
        })
    }.toMap) { displayedConversations =>
    notificationsService.notificationsSourceVisible ! displayedConversations
  }

  private def fetchTeamName(userId: UserId) =
    for {
      storage <- userStorage.head
      user    <- storage.get(userId)
      team    <- user.flatMap(_.teamId) match {
        case Some(teamId) => teamsStorage.get(teamId)
        case _            => Future.successful(Option.empty[TeamData])
      }
    } yield userId -> team.map(_.name)

  private def getPictureForNotifications(userId: UserId, nots: Seq[NotificationInfo]): Future[Option[Bitmap]] =
    if (nots.exists(_.isEphemeral)) Future.successful(None)
    else {
      val pictures  = nots.flatMap(_.userPicture).distinct
      val assetId   = if (pictures.size == 1) pictures.headOption else None

      verbose(s"getPictureForNotifications, assetId: $assetId")

      val imageLoader   = inject[AccountToImageLoader]
      val assetsStorage = inject[AccountToAssetsStorage]

      Future.sequence(List(imageLoader(userId), assetsStorage(userId))).flatMap {
        case Some(imageLoader: ImageLoader) :: Some(assetsStorage: AssetsStorage) :: Nil =>
          for {
            assetData <- assetId.fold(Future.successful(Option.empty[AssetData]))(assetsStorage.get)
            bmp       <- assetData.fold(Future.successful(Option.empty[Bitmap])){ ad =>
              imageLoader.loadBitmap(ad, BitmapRequest.Single(toPx(64)), forceDownload = false).map(Option(_)).withTimeout(500.millis).recoverWith {
                case _ : Throwable => CancellableFuture.successful(None)
              }.future
            }
          } yield
            bmp.map { original => Bitmap.fromAndroid(BitmapUtils.createRoundBitmap(original, toPx(64), 0, Color.TRANSPARENT)) }
        case _ => Future.successful(None)
      }
    }

  private def createSummaryNotificationProps(userId: UserId, silent: Boolean, nots: Seq[NotificationInfo], teamName: Option[String]) =
    NotificationProps (userId,
      when                     = Some(nots.minBy(_.time.instant).time.instant.toEpochMilli),
      showWhen                 = Some(true),
      category                 = Some(NotificationCompat.CATEGORY_MESSAGE),
      priority                 = Some(NotificationCompat.PRIORITY_HIGH),
      smallIcon                = Some(R.drawable.ic_menu_logo),
      groupSummary             = Some(true),
      group                    = Some(userId),
      openAccountIntent        = Some(userId),
      clearNotificationsIntent = Some((userId, None)),
      contentInfo              = teamName,
      color                    = notificationColor(userId)
    )

  private def createConvNotifications(userId: UserId, silent: Boolean, nots: Seq[NotificationInfo], teamName: Option[String]): Unit = {
      if (bundleEnabled) {
        val summary = createSummaryNotificationProps(userId, silent, nots, teamName)
        notificationToBuild ! (toNotificationGroupId(userId), summary)
      }

      val (ephemeral, normal) = nots.partition(_.isEphemeral)
      verbose(s"createConvNotifications($userId, ${normal.map(_.convId)}, ${ephemeral.map(_.convId)})")

      val groupedConvs =
        if (bundleEnabled)
          normal.groupBy(_.convId).map {
            case (convId, ns) => toNotificationConvId(userId, convId) -> ns
          } ++ ephemeral.groupBy(_.convId).map {
              case (convId, ns) => toEphemeralNotificationConvId(userId, convId) -> ns
            }
        else
          Map(toNotificationGroupId(userId) -> normal, toEphemeralNotificationGroupId(userId) -> ephemeral)

      val teamNameOpt = if (groupedConvs.keys.size > 1) None else teamName

      val notFutures = groupedConvs.map { case (notId, ns) =>
        getPictureForNotifications(userId, ns).map { pic =>
          val commonProps   = commonNotificationProperties(ns, userId, silent, pic)
          val specificProps =
            if (ns.size == 1) singleNotificationProperties(commonProps, userId, ns.head, teamNameOpt)
            else              multipleNotificationProperties(commonProps, userId, ns, teamNameOpt)

          notId -> (specificProps, ns.map(_.id))
        }
      }

      Future.sequence(notFutures.map {
        _.map { case (notId, (props, notsDisplayed)) =>
          verbose(s"a notification to build: $notId -> $props")
          notificationToBuild ! (notId, props)
          notsDisplayed
        }
      }).foreach { notsDisplayed =>
        val ids = notsDisplayed.flatten.toSeq
        verbose(s"notifications marked as displayed: $ids")
        notificationsService.markAsDisplayed(userId, ids)
      } (Threading.Ui)
    }

  private def singleNotificationProperties(props: NotificationProps, userId: UserId, n: NotificationInfo, teamName: Option[String]): NotificationProps = {
    val title        = SpannableWrapper(getMessageTitle(n, None), List(Span(Span.StyleSpanBold, Span.HeaderRange)))
    val body         = getMessage(n, singleConversationInBatch = true)
    val requestBase  = System.currentTimeMillis.toInt
    val bigTextStyle = StyleBuilder(StyleBuilder.BigText, title = title, summaryText = teamName, bigText = Some(body))

    val specProps = props.copy(
      contentTitle             = Some(title),
      contentText              = Some(body),
      style                    = Some(bigTextStyle),
      openConvIntent           = Some((userId, n.convId, requestBase)),
      clearNotificationsIntent = Some((userId, Some(n.convId)))
    )

    if (n.tpe != CONNECT_REQUEST)
      specProps.copy(
        action1 = Some((userId, n.convId, requestBase + 1, bundleEnabled)),
        action2 = Some((userId, n.convId, requestBase + 2, bundleEnabled))
      )
    else specProps
  }

  private def multipleNotificationProperties(props: NotificationProps, userId: UserId, ns: Seq[NotificationInfo], teamName: Option[String]): NotificationProps = {
    val convIds = ns.map(_.convId).toSet
    val isSingleConv = convIds.size == 1

    val header =
      if (isSingleConv) {
        if (ns.exists(_.isEphemeral))
          ResString(R.string.notification__message__ephemeral_someone)
        else if (ns.head.isGroupConv)
          ns.head.convName.map(ResString(_)).getOrElse(ResString.Empty)
        else
          ns.head.convName.orElse(ns.head.userName).map(ResString(_)).getOrElse(ResString.Empty)
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
    val messages    = ns.sortBy(_.time.instant).map(n => getMessage(n, singleConversationInBatch = isSingleConv)).takeRight(5).toList
    val inboxStyle  = StyleBuilder(StyleBuilder.Inbox, title = title, summaryText = if (bundleEnabled) teamName else None, lines = messages)

    val specProps = props.copy(
      contentTitle = Some(title),
      contentText  = Some(messages.last),
      style        = Some(inboxStyle)
    )

    if (isSingleConv)
      specProps.copy(
        openConvIntent           = Some((userId, convIds.head, requestBase)),
        clearNotificationsIntent = Some((userId, Some(convIds.head))),
        action1                  = Some((userId, convIds.head, requestBase + 1, bundleEnabled)),
        action2                  = Some((userId, convIds.head, requestBase + 2, bundleEnabled))
      )
    else
      specProps.copy(
        openAccountIntent        = Some(userId),
        clearNotificationsIntent = Some((userId, None))
      )
  }

  private def getSelectedSoundUri(value: String, @RawRes defaultResId: Int): Uri =
    getSelectedSoundUri(value, defaultResId, defaultResId)

  private def getSelectedSoundUri(value: String, @RawRes preferenceDefault: Int, @RawRes returnDefault: Int): Uri = {
    if (!TextUtils.isEmpty(value) && !RingtoneUtils.isDefaultValue(cxt, value, preferenceDefault)) Uri.parse(value)
    else RingtoneUtils.getUriForRawId(cxt, returnDefault)
  }

  private def commonNotificationProperties(ns: Seq[NotificationInfo], userId: UserId, silent: Boolean, pic: Option[Bitmap]) = {
    val color = notificationColor(userId)
    NotificationProps(userId,
      showWhen      = Some(true),
      category      = Some(NotificationCompat.CATEGORY_MESSAGE),
      priority      = Some(NotificationCompat.PRIORITY_HIGH),
      smallIcon     = Some(R.drawable.ic_menu_logo),
      vibrate       = if (!silent && soundController.isVibrationEnabled(userId)) Some(getIntArray(R.array.new_message_gcm).map(_.toLong)) else Some(Array(0l,0l)),
      autoCancel    = Some(true),
      sound         = getSound(ns, silent),
      onlyAlertOnce = Some(ns.forall(_.hasBeenDisplayed)),
      group         = Some(userId),
      when          = Some(ns.maxBy(_.time.instant).time.instant.toEpochMilli),
      largeIcon     = pic,
      lights        = Some(color.getOrElse(Color.WHITE), getInt(R.integer.notifications__system__led_on), getInt(R.integer.notifications__system__led_off)),
      color         = color,
      lastIsPing    = ns.map(_.tpe).lastOption.map(_ == KNOCK)
    )
  }

  private def getSound(ns: Seq[NotificationInfo], silent: Boolean) = {
    if (soundController.soundIntensityNone || silent) None
    else if (!soundController.soundIntensityFull && (ns.size > 1 && ns.lastOption.forall(_.tpe != KNOCK))) None
    else ns.map(_.tpe).lastOption.fold(Option.empty[Uri]) {
      case ASSET | ANY_ASSET | VIDEO_ASSET | AUDIO_ASSET |
           LOCATION | TEXT | CONNECT_ACCEPTED | CONNECT_REQUEST | RENAME |
           LIKE  => Option(getSelectedSoundUri(soundController.currentTonePrefs._2, R.raw.new_message_gcm))
      case KNOCK => Option(getSelectedSoundUri(soundController.currentTonePrefs._3, R.raw.ping_from_them))
      case _     => None
    }
  }

  private[notifications] def getMessage(n: NotificationInfo, singleConversationInBatch: Boolean): SpannableWrapper = {
    val message = n.message.replaceAll("\\r\\n|\\r|\\n", " ")
    val header  = n.tpe match {
      case CONNECT_ACCEPTED => ResString.Empty
      case _                => getDefaultNotificationMessageLineHeader(n, singleConversationInBatch)
    }

    val body = n.tpe match {
      case _ if n.isEphemeral && n.isUserMentioned => ResString(R.string.notification__message_with_mention__ephemeral)
      case _ if n.isEphemeral && n.isQuote => ResString(R.string.notification__message_with_quote__ephemeral)
      case _ if n.isEphemeral => ResString(R.string.notification__message__ephemeral)
      case TEXT               => ResString(message)
      case MISSED_CALL        => ResString(R.string.notification__message__one_to_one__wanted_to_talk)
      case KNOCK              => ResString(R.string.notification__message__one_to_one__pinged)
      case ANY_ASSET          => ResString(R.string.notification__message__one_to_one__shared_file)
      case ASSET              => ResString(R.string.notification__message__one_to_one__shared_picture)
      case VIDEO_ASSET        => ResString(R.string.notification__message__one_to_one__shared_video)
      case AUDIO_ASSET        => ResString(R.string.notification__message__one_to_one__shared_audio)
      case LOCATION           => ResString(R.string.notification__message__one_to_one__shared_location)
      case RENAME             => ResString(R.string.notification__message__group__renamed_conversation, n.convName.getOrElse(""))
      case MEMBER_LEAVE       => ResString(R.string.notification__message__group__remove)
      case MEMBER_JOIN        => ResString(R.string.notification__message__group__add)
      case LIKE if n.likedContent.nonEmpty =>
        n.likedContent.collect {
          case LikedContent.PICTURE =>
            ResString(R.string.notification__message__liked_picture)
          case LikedContent.TEXT_OR_URL =>
            ResString(R.string.notification__message__liked, n.message)
        }.getOrElse(ResString(R.string.notification__message__liked_message))
      case CONNECT_ACCEPTED       => ResString(R.string.notification__message__single__accept_request, n.userName.getOrElse(""))
      case CONNECT_REQUEST        => ResString(R.string.people_picker__invite__share_text__header, n.userName.getOrElse(""))
      case MESSAGE_SENDING_FAILED => ResString(R.string.notification__message__send_failed)
      case _ => ResString.Empty
    }

    getMessageSpannable(header, body, n.tpe == TEXT)
  }

  private def getMessageTitle(n: NotificationInfo, teamName: Option[String]): ResString = {
    if (n.isEphemeral)
      ResString(R.string.notification__message__ephemeral_someone)
    else {
      val convName = n.convName.orElse(n.userName).getOrElse("")
      teamName match {
        case Some(name) =>
          ResString(R.string.notification__message__group__prefix__other, convName, name)
        case None =>
          ResString(convName)
      }
    }
  }

  @TargetApi(21)
  private def getMessageSpannable(header: ResString, body: ResString, isTextMessage: Boolean) = {
    val spans = Span(Span.ForegroundColorSpanBlack, Span.HeaderRange) ::
      (if (!isTextMessage) List(Span(Span.StyleSpanItalic, Span.BodyRange)) else Nil)
    SpannableWrapper(header = header, body = body, spans = spans, separator = "")
  }

  private def getDefaultNotificationMessageLineHeader(n: NotificationInfo, singleConversationInBatch: Boolean): ResString =
    if (n.isEphemeral) ResString.Empty
    else {
      val prefixId =
        if (!singleConversationInBatch && n.isGroupConv)
          if (n.isUserMentioned)
            R.string.notification__message_with_mention__group__prefix__text
          else if (n.isQuote)
            R.string.notification__message_with_quote__group__prefix__text
          else
            R.string.notification__message__group__prefix__text
        else if (!singleConversationInBatch && !n.isGroupConv || singleConversationInBatch && n.isGroupConv)
          if (n.isUserMentioned)
            R.string.notification__message_with_mention__name__prefix__text
          else if (n.isQuote)
            R.string.notification__message_with_quote__name__prefix__text
          else
            R.string.notification__message__name__prefix__text
        else 0
      if (prefixId > 0) {
        val userName = n.userName.getOrElse("")
        n.convName match {
          case Some(convName) => ResString(prefixId, userName, convName)
          case None           => ResString(prefixId, List(ResString(userName), ResString(R.string.notification__message__group__default_conversation_name)))
        }
      }
      else ResString.Empty
    }

  private def notificationColor(userId: UserId) = applicationId match {
    case "com.wire.internal"   => Some(Color.GREEN)
    case "com.waz.zclient.dev" => accentColors.get(userId)
    case "com.wire.x"          => Some(Color.RED)
    case "com.wire.qa"         => Some(Color.BLUE)
    case _                     => None
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
