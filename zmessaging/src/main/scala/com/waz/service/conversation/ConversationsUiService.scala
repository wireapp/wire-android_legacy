/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.conversation

import com.waz.ZLog.ImplicitTag._
import com.waz.api
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.Message
import com.waz.api.NetworkMode.{OFFLINE, WIFI}
import com.waz.api.impl._
import com.waz.content._
import com.waz.log.ZLog2._
import com.waz.model.ConversationData.{ConversationType, getAccessAndRoleForGroupConv}
import com.waz.model.GenericContent.{Location, MsgEdit, Quote}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.model.sync.ReceiptType
import com.waz.service.AccountsService.InForeground
import com.waz.service.ZMessaging.currentBeDrift
import com.waz.service._
import com.waz.service.assets.AssetService
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.conversation.ConversationsService.generateTempConversationId
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.{ConversationsClient, ErrorOr}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.RichFuture.traverseSequential
import com.waz.utils._
import com.waz.utils.events.EventStream

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.NonFatal

trait ConversationsUiService {
  import ConversationsUiService._

  def sendTextMessage(convId: ConvId, text: String, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Some[MessageData]]
  def sendTextMessages(convs: Seq[ConvId], text: String, mentions: Seq[Mention] = Nil, exp: Option[FiniteDuration]): Future[Unit]

  def sendReplyMessage(replyTo: MessageId, text: String, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Option[MessageData]]

  def sendAssetMessage(convId: ConvId, rawInput: RawAssetInput, confirmation: WifiWarningConfirmation = DefaultConfirmation, exp: Option[Option[FiniteDuration]] = None): Future[Option[MessageData]]
  def sendAssetMessages(convs: Seq[ConvId], assets: Seq[RawAssetInput], confirmation: WifiWarningConfirmation = DefaultConfirmation, exp: Option[FiniteDuration] = None): Future[Unit]

  @Deprecated
  def sendMessage(convId: ConvId, audioAsset: AssetForUpload, confirmation: WifiWarningConfirmation = DefaultConfirmation): Future[Option[MessageData]]

  def sendLocationMessage(convId: ConvId, l: api.MessageContent.Location): Future[Some[MessageData]] //TODO remove use of MessageContent.Location

  def updateMessage(convId: ConvId, id: MessageId, text: String, mentions: Seq[Mention] = Nil): Future[Option[MessageData]]

  def deleteMessage(convId: ConvId, id: MessageId): Future[Unit]
  def recallMessage(convId: ConvId, id: MessageId): Future[Option[MessageData]]
  def setConversationArchived(id: ConvId, archived: Boolean): Future[Option[ConversationData]]
  def setConversationMuted(id: ConvId, muted: MuteSet): Future[Option[ConversationData]]
  def setConversationName(id: ConvId, name: Name): Future[Option[ConversationData]]

  def addConversationMembers(conv: ConvId, users: Set[UserId]): Future[Option[SyncId]]
  def removeConversationMember(conv: ConvId, user: UserId): Future[Option[SyncId]]

  def leaveConversation(conv: ConvId): Future[Unit]
  def clearConversation(id: ConvId): Future[Option[ConversationData]]

  def readReceiptSettings(convId: ConvId): Future[ReadReceiptSettings]
  def setReceiptMode(id: ConvId, receiptMode: Int): Future[Option[ConversationData]]

  def knock(id: ConvId): Future[Option[MessageData]]
  def setLastRead(convId: ConvId, msg: MessageData): Future[Option[ConversationData]]

  def setEphemeral(id: ConvId, expiration: Option[FiniteDuration]): Future[Unit]
  def setEphemeralGlobal(id: ConvId, expiration: Option[FiniteDuration]): ErrorOr[Unit]

  //conversation creation methods
  def getOrCreateOneToOneConversation(other: UserId): Future[ConversationData]
  def createGroupConversation(name: Option[Name] = None, members: Set[UserId] = Set.empty, teamOnly: Boolean = false, receiptMode: Int = 0): Future[(ConversationData, SyncId)]

  def assetUploadCancelled : EventStream[Mime]
  def assetUploadFailed    : EventStream[ErrorResponse]
}


object ConversationsUiService {
  type WifiWarningConfirmation = Long => Future[Boolean]
  val DefaultConfirmation = (_: Long) => Future.successful(true)

  val LargeAssetWarningThresholdInBytes = 3145728L // 3MiB
}

class ConversationsUiServiceImpl(selfUserId:      UserId,
                                 teamId:          Option[TeamId],
                                 assets:          AssetService,
                                 usersStorage:    UsersStorage,
                                 messages:        MessagesService,
                                 messagesStorage: MessagesStorage,
                                 messagesContent: MessagesContentUpdater,
                                 members:         MembersStorage,
                                 assetStorage:    AssetsStorage,
                                 convsContent:    ConversationsContentUpdater,
                                 convStorage:     ConversationStorage,
                                 network:         NetworkModeService,
                                 convs:           ConversationsService,
                                 sync:            SyncServiceHandle,
                                 client:          ConversationsClient,
                                 accounts:        AccountsService,
                                 tracking:        TrackingService,
                                 errors:          ErrorsService,
                                 propertiesService: PropertiesService) extends ConversationsUiService {
  import ConversationsUiService._
  import Threading.Implicits.Background

  override val assetUploadCancelled = EventStream[Mime]() //size, mime
  override val assetUploadFailed    = EventStream[ErrorResponse]()

  override def sendTextMessage(convId: ConvId, text: String, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None) =
    for {
      rr  <- readReceiptSettings(convId)
      msg <- messages.addTextMessage(convId, text, rr, mentions, exp)
      _   <- updateLastRead(msg)
      _   <- sync.postMessage(msg.id, convId, msg.editTime)
    } yield Some(msg)

  override def sendTextMessages(convs: Seq[ConvId], text: String, mentions: Seq[Mention] = Nil, exp: Option[FiniteDuration]) =
    Future.sequence(convs.map(id => sendTextMessage(id, text, mentions, Some(exp)))).map(_ => {})

  override def sendReplyMessage(quote: MessageId, text: String, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None) =
    for {
      Some(q) <- messagesStorage.get(quote)
      rr <- readReceiptSettings(q.convId)
      res <- messages.addReplyMessage(quote, text, rr, mentions, exp).flatMap {
        case Some(m) =>
          for {
            _ <- updateLastRead(m)
            _ <- sync.postMessage(m.id, m.convId, m.editTime)
          } yield Some(m)
        case None =>
          Future.successful(None)
      }
    } yield res

  override def sendAssetMessage(convId: ConvId, rawInput: RawAssetInput, confirmation: WifiWarningConfirmation = DefaultConfirmation, exp: Option[Option[FiniteDuration]] = None) =
    assets.addAsset(rawInput).flatMap {
      case Some(asset) => postAssetMessage(convId, asset, confirmation, exp)
      case _ => Future.successful(Option.empty[MessageData])
    }

  override def sendAssetMessages(convs: Seq[ConvId], uris: Seq[RawAssetInput], confirmation: WifiWarningConfirmation, exp: Option[FiniteDuration] = None) =
    traverseSequential(convs)(conv => traverseSequential(uris)(sendAssetMessage(conv, _, confirmation, Some(exp)))).map(_ => {})

  override def sendMessage(convId: ConvId, audioAsset: AssetForUpload, confirmation: WifiWarningConfirmation = DefaultConfirmation) =
    assets.addAssetForUpload(audioAsset).flatMap {
      case Some(asset) => postAssetMessage(convId, asset, confirmation)
      case _ => Future.successful(None)
    }

  private def postAssetMessage(convId: ConvId, asset: AssetData, confirmation: WifiWarningConfirmation, exp: Option[Option[FiniteDuration]] = None) = {
    verbose(l"postAssetMessage: $convId, $asset")
    for {
      rr         <- readReceiptSettings(convId)
      message    <- messages.addAssetMessage(convId, asset, rr, exp)
      _          <- updateLastRead(message)
      _          <- Future.successful(tracking.assetContribution(asset.id, selfUserId))
      shouldSend <- checkSize(convId, Some(asset.size), asset.mime, message, confirmation)
      _ <- if (shouldSend) sync.postMessage(message.id, convId, message.editTime) else Future.successful(())
    } yield Some(message)
  }

  override def sendLocationMessage(convId: ConvId, l: api.MessageContent.Location): Future[Some[MessageData]] = {
    for {
      rr  <- readReceiptSettings(convId)
      msg <- messages.addLocationMessage(convId, Location(l.getLongitude, l.getLatitude, l.getName, l.getZoom, rr.selfSettings))
      _   <- updateLastRead(msg)
      _   <- sync.postMessage(msg.id, convId, msg.editTime)
    } yield Some(msg)
  }

  override def updateMessage(convId: ConvId, id: MessageId, text: String, mentions: Seq[Mention] = Nil): Future[Option[MessageData]] = {
    verbose(l"updateMessage($convId, $id, $mentions")
    messagesStorage.update(id, {
      case m if m.convId == convId && m.userId == selfUserId =>
        val (tpe, ct) = MessageData.messageContent(text, mentions, weblinkEnabled = true)
        verbose(l"updated content: ${(tpe, ct)}")
        m.copy(
          msgType = tpe,
          content = ct,
          protos = Seq(GenericMessage(Uid(), MsgEdit(id, GenericContent.Text(text, ct.flatMap(_.mentions), Nil, m.protoQuote, m.protoReadReceipts.getOrElse(false))))),
          state = Message.Status.PENDING,
          editTime = (m.time max m.editTime) + 1.millis max LocalInstant.Now.toRemote(currentBeDrift)
        )
      case m =>
        warn(l"Can not update msg: $m")
        m
    }) flatMap {
      case Some((_, m)) => sync.postMessage(m.id, m.convId, m.editTime) map { _ => Some(m) } // using PostMessage sync request to use the same logic for failures and retrying
      case None => Future successful None
    }
  }

  override def deleteMessage(convId: ConvId, id: MessageId): Future[Unit] = for {
    _ <- messagesContent.deleteOnUserRequest(Seq(id))
    _ <- sync.postDeleted(convId, id)
  } yield ()

  override def recallMessage(convId: ConvId, id: MessageId): Future[Option[MessageData]] =
    messages.recallMessage(convId, id, selfUserId, time = LocalInstant.Now.toRemote(currentBeDrift)) flatMap {
      case Some(msg) =>
        sync.postRecalled(convId, msg.id, id) map { _ => Some(msg) }
      case None =>
        warn(l"could not recall message $convId, $id")
        Future successful None
    }

  private def updateLastRead(msg: MessageData) = convsContent.updateConversationLastRead(msg.convId, msg.time)

  override def setConversationArchived(id: ConvId, archived: Boolean): Future[Option[ConversationData]] = convs.setConversationArchived(id, archived)

  override def setConversationMuted(id: ConvId, muted: MuteSet): Future[Option[ConversationData]] =
    convsContent.updateConversationMuted(id, muted) map {
      case Some((_, conv)) =>
        sync.postConversationState(
          id,
          ConversationState(muted = Some(conv.muted.oldMutedFlag), muteTime = Some(conv.muteTime), mutedStatus = Some(conv.muted.toInt))
        )
        Some(conv)
      case None => None
    }

  override def setConversationName(id: ConvId, name: Name): Future[Option[ConversationData]] = {
    verbose(l"setConversationName($id, $name)")
    convsContent.updateConversationName(id, name) flatMap {
      case Some((_, conv)) if conv.name.contains(name) =>
        sync.postConversationName(id, conv.name.getOrElse(Name.Empty))
        messages.addRenameConversationMessage(id, selfUserId, name).map(_ => Some(conv))
      case conv =>
        warn(l"Conversation name could not be changed for: $id, conv: $conv")
        CancellableFuture.successful(None)
    }
  }

  override def addConversationMembers(conv: ConvId, users: Set[UserId]) = {
    (for {
      true   <- canModifyMembers(conv)
      added  <- members.add(conv, users) if added.nonEmpty
      _      <- messages.addMemberJoinMessage(conv, selfUserId, added.map(_.userId))
      syncId <- sync.postConversationMemberJoin(conv, added.map(_.userId).toSeq)
    } yield Option(syncId))
      .recover {
        case NonFatal(e) =>
          warn(l"Failed to add members: $users to conv: $conv", e)
          Option.empty[SyncId]
      }
  }

  override def removeConversationMember(conv: ConvId, user: UserId) = {
    (for {
      true    <- canModifyMembers(conv)
      Some(_) <- members.remove(conv, user)
      _       <- messages.addMemberLeaveMessage(conv, selfUserId, user)
      syncId  <- sync.postConversationMemberLeave(conv, user)
    } yield Option(syncId))
      .recover {
        case NonFatal(e) =>
          warn(l"Failed to remove member: $user from conv: $conv", e)
          Option.empty[SyncId]
      }
  }

  private def canModifyMembers(convId: ConvId) =
    for {
      selfActive    <- members.isActiveMember(convId, selfUserId)
      isGroup       <- convs.isGroupConversation(convId)
      isWithService <- convs.isWithService(convId)
    } yield selfActive && (isGroup || isWithService)

  override def leaveConversation(conv: ConvId) = {
    verbose(l"leaveConversation($conv)")
    for {
      _ <- convsContent.setConvActive(conv, active = false)
      _ <- removeConversationMember(conv, selfUserId)
      _ <- convsContent.updateConversationArchived(conv, archived = true)
    } yield {}
  }

  override def clearConversation(id: ConvId): Future[Option[ConversationData]] = convsContent.convById(id) flatMap {
    case Some(conv) if conv.convType == ConversationType.Group || conv.convType == ConversationType.OneToOne =>
      verbose(l"clearConversation($conv)")

      convsContent.updateConversationCleared(conv.id, conv.lastEventTime) flatMap {
        case Some((_, c)) =>
          for {
            _ <- convsContent.updateConversationLastRead(c.id, c.cleared.getOrElse(RemoteInstant.Epoch))
            _ <- convsContent.updateConversationArchived(c.id, archived = true)
            _ <- c.cleared.fold(Future.successful({}))(sync.postCleared(c.id, _).map(_ => ()))
          } yield Some(c)
        case None =>
          verbose(l"updateConversationCleared did nothing - already cleared")
          Future successful None
      }
    case Some(conv) =>
      warn(l"conversation of type ${conv.convType} can not be cleared")
      Future successful None
    case None =>
      warn(l"conversation to be cleared not found: $id")
      Future successful None
  }

  override def getOrCreateOneToOneConversation(other: UserId) = {

    def createReal1to1() =
      convsContent.convById(ConvId(other.str)) flatMap {
        case Some(conv) => Future.successful(conv)
        case _ => usersStorage.get(other).flatMap {
          case Some(u) if u.connection == ConnectionStatus.Ignored =>
            for {
              conv <- convsContent.createConversationWithMembers(ConvId(other.str), u.conversation.getOrElse(RConvId()), ConversationType.Incoming, other, Set(selfUserId), hidden = true)
              _ <- messages.addMemberJoinMessage(conv.id, other, Set(selfUserId), firstMessage = true)
              _ <- u.connectionMessage.fold(Future.successful(conv))(messages.addConnectRequestMessage(conv.id, other, selfUserId, _, u.name).map(_ => conv))
            } yield conv
          case _ =>
            for {
              _ <- sync.postConversation(ConvId(other.str), Set(other), None, None, Set(Access.PRIVATE), AccessRole.PRIVATE, None)
              conv <- convsContent.createConversationWithMembers(ConvId(other.str), RConvId(), ConversationType.OneToOne, selfUserId, Set(other))
              _ <- messages.addMemberJoinMessage(conv.id, selfUserId, Set(other), firstMessage = true)
            } yield conv
        }
      }

    def createFake1To1(tId: TeamId) = {
      verbose(l"Checking for 1:1 conversation with user: $other")
      (for {
        allConvs   <- this.members.getByUsers(Set(other)).map(_.map(_.convId))
        allMembers <- this.members.getByConvs(allConvs.toSet).map(_.map(m => m.convId -> m.userId))
        onlyUs     = allMembers.groupBy { case (c, _) => c }.map { case (cid, us) => cid -> us.map(_._2).toSet }.collect { case (c, us) if us == Set(other, selfUserId) => c }
        convs      <- convStorage.getAll(onlyUs).map(_.flatten)
      } yield {
        if (convs.size > 1)
          warn(l"Found ${convs.size} available team conversations with user: $other, returning first conversation found")
        else verbose(l"Found ${convs.size} convs with other user: $other")
        convs.find(c => c.team.contains(tId) && c.name.isEmpty)
      }).flatMap {
        case Some(conv) => Future.successful(conv)
        case _ => createAndPostConversation(ConvId(), None, Set(other)).map(_._1)
      }
    }

    teamId match {
      case Some(tId) =>
        for {
          user <- usersStorage.get(other)
          conv <- if (user.exists(_.isGuest(tId))) createReal1to1() else createFake1To1(tId)
        } yield conv
      case None => createReal1to1()
    }
  }

  override def createGroupConversation(name: Option[Name] = None, members: Set[UserId] = Set.empty, teamOnly: Boolean = false, receiptMode: Int = 0) =
    createAndPostConversation(ConvId(), name, members, teamOnly, receiptMode)

  private def createAndPostConversation(id: ConvId, name: Option[Name], members: Set[UserId], teamOnly: Boolean = false, receiptMode: Int = 0) = {
    val (ac, ar) = getAccessAndRoleForGroupConv(teamOnly, teamId)
    for {
      conv <- convsContent.createConversationWithMembers(id, generateTempConversationId(members + selfUserId), ConversationType.Group, selfUserId, members, name, access = ac, accessRole = ar, receiptMode = receiptMode)
      _    = verbose(l"created: $conv")
      _    <- messages.addConversationStartMessage(conv.id, selfUserId, members, name, conv.readReceiptsAllowed)
      syncId <- sync.postConversation(id, members, conv.name, teamId, ac, ar, Some(receiptMode))
    } yield (conv, syncId)
  }

  override def readReceiptSettings(convId: ConvId): Future[ReadReceiptSettings] = {
    for {
      selfSetting <- propertiesService.readReceiptsEnabled.head
      isGroup     <- convs.isGroupConversation(convId)
      convSetting <- if (isGroup) convStorage.get(convId).map(_.exists(_.readReceiptsAllowed)).map(Option(_))
                     else Future.successful(Option.empty[Boolean])
    } yield ReadReceiptSettings(selfSetting, convSetting.map(if(_) 1 else 0))
  }

  override def setReceiptMode(id: ConvId, receiptMode: Int): Future[Option[ConversationData]] = {
    messages.addReceiptModeChangeMessage(id, selfUserId, receiptMode).flatMap(_ => convs.setReceiptMode(id, receiptMode))
  }

  override def knock(id: ConvId): Future[Option[MessageData]] = for {
    rr  <- readReceiptSettings(id)
    msg <- messages.addKnockMessage(id, selfUserId, rr)
    _   <- sync.postMessage(msg.id, id, msg.editTime)
  } yield Some(msg)

  override def setLastRead(convId: ConvId, msg: MessageData): Future[Option[ConversationData]] = {

    def sendReadReceipts(from: RemoteInstant, to: RemoteInstant, readReceiptSettings: ReadReceiptSettings): Future[Seq[SyncId]] = {
      verbose(l"sendReadReceipts($from, $to, $readReceiptSettings)")
      if (!readReceiptSettings.selfSettings && readReceiptSettings.convSetting.isEmpty) {
        Future.successful(Seq())
      } else {
        messagesStorage.findMessagesBetween(convId, from, to).flatMap { messages =>
          val msgs = messages.filter { m =>
            m.userId != selfUserId && m.expectsRead.contains(true)
          }
          RichFuture.traverseSequential(msgs.groupBy(_.userId).toSeq)( { case (u, ms) if ms.nonEmpty =>
            sync.postReceipt(convId, ms.map(_.id), u, ReceiptType.Read)
          })
        }
      }
    }

    for {
      readReceipts <- readReceiptSettings(convId)
      update       <- convsContent.updateConversationLastRead(convId, msg.time)
      _            <- update.fold(Future.successful({})) {
                        case (_, newConv) => sync.postLastRead(convId, newConv.lastRead).map(_ => {})
                      }
      _            <- update.fold(Future.successful({})) {
                        case (oldConv, newConv) =>
                          sendReadReceipts(oldConv.lastRead, newConv.lastRead, readReceipts).map(_ => {})
                      }
    } yield update.map(_._2)
  }

  override def setEphemeral(id: ConvId, expiration: Option[FiniteDuration]) = {
    convStorage.update(id, _.copy(localEphemeral = expiration)).map(_ => {})
  }

  override def setEphemeralGlobal(id: ConvId, expiration: Option[FiniteDuration]) =
    for {
      Some(conv) <- convsContent.convById(id) if conv.globalEphemeral != expiration
      resp       <- client.postMessageTimer(conv.remoteId, expiration).future
      _          <- resp.mapFuture(_ => convStorage.update(id, _.copy(globalEphemeral = expiration)))
      _          <- resp.mapFuture(_ => messages.addTimerChangedMessage(id, selfUserId, expiration, LocalInstant.Now.toRemote(currentBeDrift)))
    } yield resp

  private def checkSize(convId: ConvId, size: Option[Long], mime: Mime, message: MessageData, confirmation: WifiWarningConfirmation) = {
    def isFileTooLarge(size: Long, mime: Mime) = mime match {
      case Mime.Video() => false
      case _ => size > AssetData.maxAssetSizeInBytes(teamId.isDefined)
    }

    size match {
      case None => Future successful true
      case Some(s) if isFileTooLarge(s, mime) =>
        for {
          _ <- messages.updateMessageState(convId, message.id, Message.Status.FAILED)
          _ <- errors.addAssetTooLargeError(convId, message.id)
          _ <- Future.successful(assetUploadFailed ! ErrorResponse.internalError("asset too large"))
        } yield false

      case Some(s) if s > LargeAssetWarningThresholdInBytes =>
        for {
          mode         <- network.networkMode.head
          inForeground <- accounts.accountState(selfUserId).map(_ == InForeground).head
          res <- if (!Set(OFFLINE, WIFI).contains(mode) && inForeground)
          // will mark message as failed and ask user if it should really be sent
          // marking as failed ensures that user has a way to retry even if he doesn't respond to this warning
          // this is possible if app is paused or killed in meantime, we don't want to be left with message in state PENDING without a sync request
            messages.updateMessageState(convId, message.id, Message.Status.FAILED).map { _ =>
              confirmation(s).foreach {
                case true  => messages.retryMessageSending(convId, message.id)
                case false => messagesContent.deleteMessage(message).map(_ => assetUploadCancelled ! mime)
              }
              false
            }(Threading.Ui)
          else Future.successful(true)
        } yield res

      case Some(_) => Future.successful(true)
    }
  }
}
