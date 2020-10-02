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
package com.waz.zclient.conversation

import java.net.URI

import android.app.Activity
import android.content.Context
import android.graphics.{Bitmap, BitmapFactory}
import com.waz.api
import com.waz.api.{IConversation, Verification}
import com.waz.content.{ConversationStorage, OtrClientsStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.model.otr.Client
import com.waz.service.AccountManager
import com.waz.service.assets.{AssetInput, Content, ContentForUpload, FileRestrictionList, UriHelper}
import com.waz.service.conversation.{ConversationsService, ConversationsUiService, SelectedConversationService}
import com.wire.signals.{CancellableFuture, SerialDispatchQueue}
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.wire.signals.{Serialized, EventContext, EventStream, Signal, SourceStream}
import com.waz.utils.{returning, _}
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController.ConversationChange
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter.Folder
import com.waz.zclient.conversationlist.{ConversationListController, FolderStateController}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.Callback
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

class ConversationController(implicit injector: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  private implicit val dispatcher = SerialDispatchQueue(name = "ConversationController")

  private lazy val selectedConv          = inject[Signal[SelectedConversationService]]
  private lazy val convsUi               = inject[Signal[ConversationsUiService]]
  private lazy val conversations         = inject[Signal[ConversationsService]]
  private lazy val convsStorage          = inject[Signal[ConversationStorage]]
  private lazy val otrClientsStorage     = inject[Signal[OtrClientsStorage]]
  private lazy val account               = inject[Signal[Option[AccountManager]]]
  private lazy val callStart             = inject[CallStartController]
  private lazy val convListController    = inject[ConversationListController]
  private lazy val uriHelper             = inject[UriHelper]
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val selfId                = inject[Signal[UserId]]
  private lazy val fileRestrictions      = inject[FileRestrictionList]

  val DefaultDeletedName: Name = Name(getString(R.string.default_deleted_username))

  private var lastConvId = Option.empty[ConvId]

  val currentConvIdOpt: Signal[Option[ConvId]] = selectedConv.flatMap(_.selectedConversationId)

  val currentConvId: Signal[ConvId] = currentConvIdOpt.collect { case Some(convId) => convId }

  val currentConvOpt: Signal[Option[ConversationData]] =
    currentConvIdOpt.flatMap(_.fold(Signal.const(Option.empty[ConversationData]))(conversationData)) // updates on every change of the conversation data, not only on switching

  val currentConv: Signal[ConversationData] =
    currentConvOpt.collect { case Some(conv) => conv }

  val convChanged: SourceStream[ConversationChange] = EventStream[ConversationChange]()

  def conversationData(convId: ConvId): Signal[Option[ConversationData]] =
    convsStorage.flatMap(_.optSignal(convId))

  def getConversation(convId: ConvId): Future[Option[ConversationData]] =
    convsStorage.head.flatMap(_.get(convId))

  def isCurrentUserCreator(convId: ConvId): Future[Boolean] = for {
    convs   <- conversations.head
    selfId  <- selfId.head
    conv    <- getConversation(convId)
    isGroup <- convs.isGroupConversation(convId)
  } yield isGroup && conv.exists(_.creator == selfId)

  def conversationName(convId: ConvId): Signal[Name] =
    for {
      conversations <- conversations
      name          <- conversations.conversationName(convId)
    } yield
      if (name.isEmpty) DefaultDeletedName
      else name

  val currentConvType: Signal[ConversationType] = currentConv.map(_.convType).disableAutowiring()
  val currentConvName: Signal[Name] = currentConvId.flatMap(conversationName).disableAutowiring()
    // the name of the current conversation can be edited (without switching)

  val currentConvIsVerified: Signal[Boolean] = currentConv.map(_.verified == Verification.VERIFIED)
  val currentConvIsGroup: Signal[Boolean] =
    for {
      convs   <- conversations
      convId  <- currentConvIdOpt
      isGroup <- convId.fold(Signal.const(false))(convs.groupConversation)
    } yield isGroup

  val currentConvIsTeamOnly: Signal[Boolean] = currentConv.map(_.isTeamOnly)

  lazy val currentConvOtherMembers: Signal[Map[UserId, ConversationRole]] = for {
    selfId  <- selfId
    members <- currentConvMembers
  } yield members.filter(_._1 != selfId)

  lazy val currentConvMembers: Signal[Map[UserId, ConversationRole]] =
    for {
      convId  <- currentConvIdOpt
      _       =  if (convId.isEmpty) warn(l"Current conversation members queried in the context without the current conversation set")
      members <- convId.fold(Signal.const(Map.empty[UserId, ConversationRole]))(convMembers)
    } yield members

  def convMembers(convId: ConvId): Signal[Map[UserId, ConversationRole]] = for {
    convs   <- conversations
    members <- convs.convMembers(convId)
  } yield members

  lazy val selfRole: Signal[ConversationRole] =
    for {
      selfId  <- selfId
      members <- currentConvMembers
      _       =  if (!members.contains(selfId)) warn(l"No role specified for the self user")
    } yield members.getOrElse(selfId, ConversationRole.MemberRole)

  def selfRoleInConv(convId: ConvId): Signal[ConversationRole] = for {
    selfId  <- selfId
    members <- convMembers(convId)
    _       =  if (!members.contains(selfId)) warn(l"No role specified for the self user")
  } yield members.getOrElse(selfId, ConversationRole.MemberRole)

  def setRoleInCurrentConv(userId: UserId, role: ConversationRole): Future[Unit] = for {
    convs  <- conversations.head
    convId <- currentConvId.head
  } yield convs.setConversationRole(convId, userId, role)

  currentConvIdOpt {
    case Some(convId) =>
      if (!lastConvId.contains(convId)) { // to only catch changes coming from SE (we assume it's an account switch)
        verbose(l"a conversation change bypassed selectConv: last = $lastConvId, current = $convId")
        convChanged ! ConversationChange(from = lastConvId, to = Option(convId), requester = ConversationChangeRequester.ACCOUNT_CHANGE)
        lastConvId = Option(convId)
      }
    case None =>
      convChanged ! ConversationChange(from = lastConvId, to = None, requester = ConversationChangeRequester.DELETE_CONVERSATION)
      lastConvId = None
  }

  // this should be the only UI entry point to change conv in SE
  def selectConv(convId: Option[ConvId], requester: ConversationChangeRequester): Future[Unit] = convId match {
    case None => Future.successful({})
    case Some(id) =>
      val oldId = lastConvId
      lastConvId = convId
      for {
        selectedConv <- selectedConv.head
        convsUi      <- convsUi.head
        conv         <- getConversation(id)
        _            <- if (conv.exists(_.archived)) convsUi.setConversationArchived(id, archived = false) else Future.successful(Option.empty[ConversationData])
        _            <- selectedConv.selectConversation(convId)
      } yield { // catches changes coming from UI
        verbose(l"changing conversation from $oldId to $convId, requester: $requester")
        convChanged ! ConversationChange(from = oldId, to = convId, requester = requester)
      }
  }

  def selectConv(id: ConvId, requester: ConversationChangeRequester): Future[Unit] =
    selectConv(Some(id), requester)

  def switchConversation(convId: ConvId, call: Boolean = false, delayMs: FiniteDuration = 750.millis) =
    CancellableFuture.delay(delayMs).map { _ =>
      selectConv(convId, ConversationChangeRequester.INTENT).foreach { _ =>
        if (call)
          for {
            Some(acc) <- account.map(_.map(_.userId)).head
            _         <- callStart.startCall(acc, convId)
          } yield ()
      }
    } (Threading.Ui).future

  def groupConversation(id: ConvId): Signal[Boolean] =
    conversations.flatMap(_.groupConversation(id))

  def setEphemeralExpiration(expiration: Option[FiniteDuration]): Future[Unit] =
    for {
      id <- currentConvId.head
      _  <- convsUi.head.flatMap(_.setEphemeral(id, expiration))
    } yield ()

  def loadClients(userId: UserId): Future[Seq[Client]] =
    otrClientsStorage.head.flatMap(_.getClients(userId)) // TODO: move to SE maybe?

  def sendMessage(text:     String,
                  mentions: Seq[Mention] = Nil,
                  quote:    Option[MessageId] = None,
                  exp:      Option[Option[FiniteDuration]] = None): Future[Option[MessageData]] =
    convsUiwithCurrentConv({(ui, id) =>
      quote.fold2(ui.sendTextMessage(id, text, mentions, exp), ui.sendReplyMessage(_, text, mentions, exp))
    })

  def sendTextMessage(convs:    Seq[ConvId],
                      text:     String,
                      mentions: Seq[Mention] = Nil,
                      quote:    Option[MessageId] = None,
                      exp:      Option[Option[FiniteDuration]] = None): Future[Seq[Option[MessageData]]] =
    convsUi.head.flatMap { ui =>
      Future.sequence(convs.map(id =>
        quote.fold2(ui.sendTextMessage(id, text, mentions, exp), ui.sendReplyMessage(_, text, mentions, exp))
      ))
    }

  def sendAssetMessage(content: ContentForUpload): Future[Option[MessageData]] =
    convsUiwithCurrentConv((ui, id) => ui.sendAssetMessage(id, content))

  def sendAssetMessage(content:  ContentForUpload,
                       activity: Activity,
                       exp:      Option[Option[FiniteDuration]]): Future[Option[MessageData]] =
    convsUiwithCurrentConv((ui, id) =>
      accentColorController.accentColor.head.flatMap(color =>
        ui.sendAssetMessage(id, content, (s: Long) => showWifiWarningDialog(s, color), exp))
    )

  // NOTE: Rotating makes sense only for images, but at this point we accept any content. If it's
  // not an image, `currentRotation` will return 0 and the method will return the original content.
  def rotateImageIfNeeded(image: Content): Future[Content] =
    (image match {
      case Content.Uri(uri)      => rotateIfNeeded(image, uri.getPath)
      case Content.File(_, file) => rotateIfNeeded(image, file.getPath)
      case _                     => Future.successful(Success(image))
    }).map(_.getOrElse(image))

  // rotation and compression are time-consuming - better not to do it on the Ui thread
  private def rotateIfNeeded(image: Content, path: String): Future[Try[Content]] = Future {
    import AssetInput._
    val cr = currentRotation(path)
    if (cr == 0)
      Success(image)
    else
      Try(BitmapFactory.decodeFile(path, BitmapOptions)).map { bmp => toJpg(rotate(bmp, cr)) }
  }(Threading.ImageDispatcher)

  private def sendAssetMessage(convs:    Seq[ConvId],
                               content:  ContentForUpload,
                               activity: Activity,
                               exp:      Option[Option[FiniteDuration]]): Future[Seq[Option[MessageData]]] =
    for {
      ui    <- convsUi.head
      color <- accentColorController.accentColor.head
      msgs  <- Future.traverse(convs) { id =>
                 ui.sendAssetMessage(id, content, (s: Long) => showWifiWarningDialog(s, color), exp)
               }
    } yield msgs

  def sendAssetMessage(bitmap: Bitmap, assetName: String): Future[Option[MessageData]] =
    for {
      img     <- Future { AssetInput.toJpg(bitmap) }(Threading.Background)
      content =  ContentForUpload(assetName, img)
      data    <- convsUiwithCurrentConv((ui, id) => ui.sendAssetMessage(id, content))
    } yield data

  def sendAssetMessage(uri:      URI,
                       activity: Activity,
                       exp:      Option[Option[FiniteDuration]],
                       convs:    Seq[ConvId] = Seq()): Future[Unit] =
    Future.fromTry(uriHelper.extractFileName(uri)).flatMap {
      case fileName if fileRestrictions.isAllowed(fileName) =>
        val content = ContentForUpload(fileName,  Content.Uri(uri))
        if (convs.isEmpty) sendAssetMessage(content, activity, exp).map(_ => ())
        else sendAssetMessage(convs, content, activity, exp).map(_ => ())
      case fileName =>
        convsUiwithCurrentConv((ui, id) =>
          ui.addRestrictedFileMessage(id, None, Some(fileName.split('.').last))
        ).map(_ => ())
    }

  def sendAssetMessages(uris:     Seq[URI],
                        activity: Activity,
                        exp:      Option[Option[FiniteDuration]],
                        convs:    Seq[ConvId]): Future[Unit] =
    for {
      ui        <- convsUi.head
      color     <- accentColorController.accentColor.head
      names     <- Future.traverse(uris) { uri => Future.fromTry(uriHelper.extractFileName(uri).map(uri -> _)) }
      contents  =  names.collect { case (uri, name) if fileRestrictions.isAllowed(name) => ContentForUpload(name,  Content.Uri(uri)) }
      _         <- if (contents.nonEmpty)
                     Future.traverse(convs) { id =>
                       ui.sendAssetMessages(id, contents, (s: Long) => showWifiWarningDialog(s, color), exp)
                     }
                   else
                     Future.successful(())
    } yield ()

  def sendMessage(location: api.MessageContent.Location): Future[Option[MessageData]] =
    convsUiwithCurrentConv((ui, id) => ui.sendLocationMessage(id, location))

  private def convsUiwithCurrentConv[A](f: (ConversationsUiService, ConvId) => Future[A]): Future[A] =
    for {
      cUi    <- convsUi.head
      convId <- currentConvId.head
      res    <- f(cUi, convId)
    } yield res

  def setCurrentConvName(name: String): Future[Unit] =
    for {
      service     <- convsUi.head
      id          <- currentConvId.head
      currentName <- currentConvName.head
    } yield {
      val newName = Name(name)
      if (!currentName.contains(newName)) service.setConversationName(id, newName)
    }

  def setCurrentConvReadReceipts(readReceiptsEnabled: Boolean): Future[Unit] =
    for {
      service             <- convsUi.head
      id                  <- currentConvId.head
      currentReadReceipts <- currentConv.map(_.readReceiptsAllowed).head
    } yield
      if (currentReadReceipts != readReceiptsEnabled)
        service.setReceiptMode(id, if (readReceiptsEnabled) 1 else 0)

  def addMembers(id: ConvId, members: Set[UserId], defaultRole: ConversationRole = ConversationRole.MemberRole): Future[Unit] =
    convsUi.head.flatMap(_.addConversationMembers(id, members, defaultRole)).map(_ => {})

  def removeMember(user: UserId): Future[Unit] =
    for {
      id <- currentConvId.head
      _  <- convsUi.head.flatMap(_.removeConversationMember(id, user))
    } yield {}

  def leave(convId: ConvId): CancellableFuture[Unit] =
    returning(Serialized(s"Conversations $convId")(CancellableFuture.lift(convsUi.head.flatMap(_.leaveConversation(convId))))) { _ =>
      currentConvId.head.map { id => if (id == convId) setCurrentConversationToNext(ConversationChangeRequester.LEAVE_CONVERSATION) }
    }

  def setCurrentConversationToNext(requester: ConversationChangeRequester): Future[Unit] = {
    def nextConversation(convId: ConvId): Future[Option[ConvId]] =
      convListController.regularConversationListData.head.map { regular =>
        val r = regular.map(_.conv)
        r.lift(r.indexWhere(_.id == convId) + 1).map(_.id)
      } (Threading.Background)

    for {
      currentConvId <- currentConvId.head
      nextConvId    <- nextConversation(currentConvId)
      _             <- selectConv(nextConvId, requester)
    } yield ()
  }

  def archive(convId: ConvId, archive: Boolean): Unit = {
    convsUi.head.flatMap(_.setConversationArchived(convId, archive))
    currentConvId.head.map { id => if (id == convId) CancellableFuture.delayed(ConversationController.ARCHIVE_DELAY){
      if (!archive) selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST_UNARCHIVED_CONVERSATION)
      else setCurrentConversationToNext(ConversationChangeRequester.ARCHIVED_RESULT)
    }}
  }

  def setMuted(id: ConvId, muted: MuteSet): Future[Unit] =
    convsUi.head.flatMap(_.setConversationMuted(id, muted)).map(_ => {})

  def delete(id: ConvId, alsoLeave: Boolean): CancellableFuture[Option[ConversationData]] = {
    def clear(id: ConvId) = Serialized(s"Conversations $id")(CancellableFuture.lift(convsUi.head.flatMap(_.clearConversation(id))))
    if (alsoLeave) leave(id).flatMap(_ => clear(id)) else clear(id)
  }

  def createGuestRoom(): Future[ConversationData] =
    createGroupConversation(context.getString(R.string.guest_room_name), Set.empty, false, false)

  def createGroupConversation(name:         Name,
                              userIds:      Set[UserId],
                              teamOnly:     Boolean,
                              readReceipts: Boolean,
                              defaultRole:  ConversationRole = ConversationRole.MemberRole
                             ): Future[ConversationData] = for {
    convsUi   <- convsUi.head
    _         <- inject[FolderStateController].update(Folder.GroupId, isExpanded = true)
    (conv, _) <- convsUi.createGroupConversation(name, userIds, teamOnly, if (readReceipts) 1 else 0, defaultRole)
  } yield conv

  def withCurrentConvName(callback: Callback[String]): Unit = currentConvName.head.map(_.str).foreach(callback.callback)(Threading.Ui)

  def getCurrentConvId: ConvId = currentConvId.currentValue.orNull
  def withConvLoaded(convId: ConvId, callback: Callback[ConversationData]): Unit = getConversation(convId).foreach {
    case Some(data) => callback.callback(data)
    case None =>
  }(Threading.Ui)

  private var convChangedCallbackSet = Set.empty[Callback[ConversationChange]]
  def addConvChangedCallback(callback: Callback[ConversationChange]): Unit = convChangedCallbackSet += callback
  def removeConvChangedCallback(callback: Callback[ConversationChange]): Unit = convChangedCallbackSet -= callback

  convChanged.onUi { ev => convChangedCallbackSet.foreach(callback => callback.callback(ev)) }

  object messages {

    val ActivityTimeout = 3.seconds

    /**
      * Currently focused message.
      * There is only one focused message, switched by tapping.
      */
    val focused = Signal(Option.empty[MessageId])

    /**
      * Tracks last focused message together with last action time.
      * It's not cleared when message is unfocused, and toggleFocus takes timeout into account.
      * This is used to decide if timestamp view should be shown in footer when message has likes.
      */
    val lastActive = Signal((MessageId.Empty, Instant.EPOCH)) // message showing status info

    currentConv.onChanged { _ => clear() }

    def clear() = {
      focused ! None
      lastActive ! (MessageId.Empty, Instant.EPOCH)
    }

    def isFocused(id: MessageId): Boolean = focused.currentValue.flatten.contains(id)

    /**
      * Switches current msg focus state to/from given msg.
      */
    def toggleFocused(id: MessageId) = {
      verbose(l"toggleFocused($id)")
      focused mutate {
        case Some(`id`) => None
        case _ => Some(id)
      }
      lastActive.mutate {
        case (`id`, t) if !ActivityTimeout.elapsedSince(t) => (id, Instant.now - ActivityTimeout)
        case _ => (id, Instant.now)
      }
    }
  }
}

object ConversationController extends DerivedLogTag {
  val ARCHIVE_DELAY = 500.millis
  val MaxParticipants: Int = 500

  case class ConversationChange(from: Option[ConvId], to: Option[ConvId], requester: ConversationChangeRequester) {
    def toConvId: ConvId = to.orNull // TODO: remove when not used anymore
    lazy val noChange: Boolean = from == to
  }

  def getOtherParticipantForOneToOneConv(conv: ConversationData): UserId = {
    if (conv != ConversationData.Empty &&
        conv.convType != IConversation.Type.ONE_TO_ONE &&
        conv.convType != IConversation.Type.WAIT_FOR_CONNECTION &&
        conv.convType != IConversation.Type.INCOMING_CONNECTION)
      error(l"unexpected call, most likely UI error", new UnsupportedOperationException(s"Can't get other participant for: ${conv.convType} conversation"))
    UserId(conv.id.str) // one-to-one conversation has the same id as the other user, so we can access it directly
  }

  lazy val PredefinedExpirations =
    Seq(
      None,
      Some(10.seconds),
      Some(5.minutes),
      Some(1.hour),
      Some(1.day),
      Some(7.days),
      Some(28.days)
    )

  import com.waz.model.EphemeralDuration._
  def getEphemeralDisplayString(exp: Option[FiniteDuration])(implicit context: Context): String = {
    exp.map(EphemeralDuration(_)) match {
      case None              => getString(R.string.ephemeral_message__timeout__off)
      case Some((l, Second)) => getQuantityString(R.plurals.unit_seconds, l.toInt, l.toString)
      case Some((l, Minute)) => getQuantityString(R.plurals.unit_minutes, l.toInt, l.toString)
      case Some((l, Hour))   => getQuantityString(R.plurals.unit_hours,   l.toInt, l.toString)
      case Some((l, Day))    => getQuantityString(R.plurals.unit_days,    l.toInt, l.toString)
      case Some((l, Week))   => getQuantityString(R.plurals.unit_weeks,   l.toInt, l.toString)
      case Some((l, Year))   => getQuantityString(R.plurals.unit_years,   l.toInt, l.toString)
    }
  }

  lazy val MuteSets = Seq(MuteSet.AllAllowed, MuteSet.OnlyMentionsAllowed, MuteSet.AllMuted)

  def muteSetDisplayStringId(muteSet: MuteSet): Int = muteSet match {
    case MuteSet.AllMuted            => R.string.conversation__action__notifications_nothing
    case MuteSet.OnlyMentionsAllowed => R.string.conversation__action__notifications_mentions_and_replies
    case _                           => R.string.conversation__action__notifications_everything
  }
}
