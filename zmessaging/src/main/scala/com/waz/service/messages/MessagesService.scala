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
package com.waz.service.messages

import com.waz.api.Message
import com.waz.api.Message.{Status, Type}
import com.waz.api.impl.ErrorResponse
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent._
import com.waz.model.otr.ClientId
import com.waz.model.{Mention, MessageId, _}
import com.waz.service.ZMessaging.clock
import com.waz.service._
import com.waz.service.assets.UploadAsset
import com.waz.service.call.CallingService.MissedCallInfo
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.otr.NotificationParser
import com.waz.service.otr.VerificationStateUpdater.{ClientUnverified, MemberAdded, VerificationChange}
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.AssetClient
import com.wire.signals.{CancellableFuture, EventStream, RefreshingSignal, Serialized, Signal, SourceSignal, SourceStream}
import com.waz.threading.Threading
import com.waz.utils.RichFuture.traverseSequential
import com.waz.utils._
import com.waz.utils.crypto.ReplyHashing
import org.threeten.bp.Instant.now

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.Future.{successful, traverse}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Success

trait MessagesService {
  def msgEdited: EventStream[(MessageId, MessageId)]
  def missedCall: EventStream[MissedCallInfo]

  def addTextMessage(convId: ConvId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[MessageData]
  def addKnockMessage(convId: ConvId, selfUserId: UserId, expectsReadReceipt: ReadReceiptSettings = AllDisabled): Future[MessageData]
  def addAssetMessage(convId: ConvId, msgId: MessageId, asset: UploadAsset, expectsReadReceipt: ReadReceiptSettings = AllDisabled, exp: Option[Option[FiniteDuration]] = None): Future[MessageData]
  def addLocationMessage(convId: ConvId, content: Location, expectsReadReceipt: ReadReceiptSettings = AllDisabled): Future[MessageData]
  def addReplyMessage(quote: MessageId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Option[MessageData]]

  def addMissedCallMessage(rConvId: RConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]]
  def addMissedCallMessage(convId: ConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]]
  def addSuccessfulCallMessage(convId: ConvId, from: UserId, time: RemoteInstant, duration: FiniteDuration): Future[Option[MessageData]]

  def addConnectRequestMessage(convId: ConvId, fromUser: UserId, toUser: UserId, message: String, name: Name, fromSync: Boolean = false): Future[MessageData]
  def addConversationStartMessage(convId: ConvId, creator: UserId, users: Set[UserId], name: Option[Name], readReceiptsAllowed: Boolean, time: Option[RemoteInstant] = None): Future[Unit]

  //TODO forceCreate is a hacky workaround for a bug where previous system messages are not marked as SENT. Do NOT use!
  def addMemberJoinMessage(convId: ConvId, creator: UserId, users: Set[UserId], firstMessage: Boolean = false, forceCreate: Boolean = false): Future[Option[MessageData]]
  def addMemberLeaveMessage(convId: ConvId, remover: UserId, users: Set[UserId], reason: Option[MemberLeaveReason]): Future[Unit]
  def addRenameConversationMessage(convId: ConvId, selfUserId: UserId, name: Name): Future[Option[MessageData]]
  def addRestrictedFileMessage(convId: ConvId, from: Option[UserId] = None, extension: Option[String] = None): Future[Option[MessageData]]
  def addReceiptModeChangeMessage(convId: ConvId, from: UserId, receiptMode: Int): Future[Option[MessageData]]
  def addTimerChangedMessage(convId: ConvId, from: UserId, duration: Option[FiniteDuration], time: RemoteInstant): Future[Unit]
  def addHistoryLostMessages(cs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]]
  def addReceiptModeIsOnMessage(convId: ConvId): Future[MessageData]

  def addDeviceStartMessages(convs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]]
  def addOtrVerifiedMessage(convId: ConvId): Future[Option[MessageData]]
  def addOtrUnverifiedMessage(convId: ConvId, users: Seq[UserId], change: VerificationChange): Future[Option[MessageData]]

  def addLegalHoldEnabledMessage(convId: ConvId, time: Option[RemoteInstant]): Future[Option[MessageData]]
  def addLegalHoldDisabledMessage(convId: ConvId, time: Option[RemoteInstant]): Future[Option[MessageData]]

  def retryMessageSending(conv: ConvId, msgId: MessageId): Future[Option[SyncId]]
  def updateMessageState(convId: ConvId, messageId: MessageId, state: Message.Status): Future[Option[MessageData]]
  def markMessageRead(convId: ConvId, id: MessageId): Future[Option[MessageData]]

  def recallMessage(convId: ConvId, msgId: MessageId, userId: UserId, systemMsgId: MessageId = MessageId(), time: RemoteInstant, state: Message.Status = Message.Status.PENDING): Future[Option[MessageData]]
  def applyMessageEdit(convId: ConvId, userId: UserId, time: RemoteInstant, gm: GenericMessage): Future[Option[MessageData]]

  def removeLocalMemberJoinMessage(convId: ConvId, users: Set[UserId]): Future[Unit]

  def messageSent(convId: ConvId, msgId: MessageId, newTime: RemoteInstant): Future[Option[MessageData]]
  def messageDeliveryFailed(convId: ConvId, msg: MessageData, error: ErrorResponse): Future[Option[MessageData]]
  def retentionPolicy2ById(convId: ConvId): Future[AssetClient.Retention]
  def retentionPolicy2(convData: ConversationData): Future[AssetClient.Retention]

  def findMessageIds(convId: ConvId): Future[Set[MessageId]]

  def getAssetIds(messageIds: Set[MessageId]): Future[Set[GeneralAssetId]]

  def buttonsForMessage(msgId: MessageId): Signal[Seq[ButtonData]]
  def clickButton(messageId: MessageId, buttonId: ButtonId): Future[Unit]
  def setButtonError(messageId: MessageId, buttonId: ButtonId): Future[Unit]

  def fixErrorMessages(userId: UserId, clientId: ClientId): Future[Unit]
}

final class MessagesServiceImpl(selfUserId:     UserId,
                                teamId:         Option[TeamId],
                                replyHashing:   ReplyHashing,
                                storage:        MessagesStorage,
                                updater:        MessagesContentUpdater,
                                edits:          EditHistoryStorage,
                                convs:          ConversationsContentUpdater,
                                network:        NetworkModeService,
                                members:        MembersStorage,
                                usersStorage:   UsersStorage,
                                buttonsStorage: ButtonsStorage,
                                sync:           SyncServiceHandle) extends MessagesService with DerivedLogTag {
  import Threading.Implicits.Background

  override val msgEdited = EventStream[(MessageId, MessageId)]()

  override val missedCall = EventStream[MissedCallInfo]()

  override def recallMessage(convId: ConvId,
                             msgId: MessageId,
                             userId: UserId,
                             systemMsgId: MessageId = MessageId(),
                             time: RemoteInstant,
                             state: Message.Status = Message.Status.PENDING): Future[Option[MessageData]] =
    updater.getMessage(msgId) flatMap {
      case Some(msg) if msg.convId != convId =>
        error(l"can not recall message belonging to other conversation: $msg, requested by $userId")
        Future successful None
      case Some(msg) if msg.canRecall(convId, userId) =>
        updater.deleteOnUserRequest(Seq(msgId)) flatMap { _ =>
          val recall =
            MessageData(
              systemMsgId,
              convId,
              Message.Type.RECALLED,
              time = msg.time,
              editTime = time max msg.time,
              userId = userId,
              state = state,
              genericMsgs = Seq(GenericMessage(systemMsgId.uid, MsgRecall(msgId)))
            )
          if (userId == selfUserId) Future successful Some(recall) // don't save system message for self user
          else updater.addMessage(recall)
        }
      case Some(msg) if msg.isEphemeral =>
        // ephemeral message expired on other device, or on receiver side
        updater.deleteOnUserRequest(Seq(msgId)) map { _ => None }
      case msg =>
        warn(l"can not recall $msg, requested by $userId")
        Future successful None
    }

  override def applyMessageEdit(convId: ConvId, userId: UserId, time: RemoteInstant, gm: GenericMessage): Future[Option[MessageData]] =
    Serialized.future(s"applyMessageEdit $convId") {

      def findLatestUpdate(id: MessageId): Future[Option[MessageData]] = updater.getMessage(id).flatMap {
        case Some(msg) => Future successful Some(msg)
        case None =>
          edits.get(id).flatMap {
            case Some(EditHistory(_, updated, _)) => findLatestUpdate(updated)
            case None => Future successful None
          }
      }

      gm.unpack match {
        case (newId, edit: MsgEdit) =>
          edit.unpack match {
            case Some((oldId, oldTextMessage)) =>
              val (text, mentions, links, _, _) = oldTextMessage.unpack

              def applyEdit(msg: MessageData) = {
                val newMsgId = MessageId(newId.str)
                for {
                  _ <- edits.insert(EditHistory(msg.id, newMsgId, time))
                  (tpe, ct) = MessageData.messageContent(text, mentions, links, weblinkEnabled = true)
                  edited = msg.copy(id = newMsgId, msgType = tpe, content = ct, genericMsgs = Seq(gm), editTime = time)
                  res <- updater.addMessage(edited.adjustMentions(false).getOrElse(edited))
                  quotesOf <- storage.findQuotesOf(msg.id)
                  _ <- storage.updateAll2(quotesOf.map(_.id), _.replaceQuote(newMsgId))
                  _ = msgEdited ! (msg.id, newMsgId)
                  _ <- updater.deleteOnUserRequest(Seq(msg.id))
                } yield res
              }

              updater.getMessage(oldId) flatMap {
                case Some(msg) if msg.userId == userId && msg.convId == convId =>
                  applyEdit(msg)
                case _ =>
                  // original message was already deleted, let's check if it was already updated
                  edits.get(oldId) flatMap {
                    case Some(EditHistory(_, _, editTime)) if editTime <= time =>
                      verbose(l"message $oldId has already been updated, discarding later update")
                      Future successful None

                    case Some(EditHistory(_, updated, _)) =>
                      // this happens if message has already been edited locally,
                      // but that edit is actually newer than currently received one, so we should revert it
                      // we always use only the oldest edit for given message (as each update changes the message id)
                      verbose(l"message $oldId has already been updated, will overwrite new message")
                      findLatestUpdate(updated) flatMap {
                        case Some(msg) => applyEdit(msg)
                        case None =>
                          error(l"Previously updated message was not found for: $gm")
                          Future successful None
                      }

                    case None =>
                      verbose(l"didn't find the original message for edit: $gm")
                      Future successful None
                  }
              }
            case _ =>
              error(l"invalid message for applyMessageEdit (1): $gm")
              Future successful None
          }
        case _ =>
          error(l"invalid message for applyMessageEdit (2): $gm")
          Future successful None
      }
  }

  override def addTextMessage(convId: ConvId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[MessageData] = {
    verbose(l"addTextMessage($convId, ${content.length}, $mentions, $exp)")

    convs.storage.getLegalHoldHint(convId).flatMap { legalHoldStatus =>
      val (tpe, ct) = MessageData.messageContent(content, mentions, weblinkEnabled = true)
      val id = MessageId()
      val gm = GenericMessage(id.uid, Text(content, ct.flatMap(_.mentions), Nil, expectsReadReceipt.selfSettings, legalHoldStatus))
      updater.addLocalMessage(
        MessageData(
          id, convId, tpe, selfUserId,
          content = ct,
          genericMsgs = Seq(gm),
          forceReadReceipts = expectsReadReceipt.convSetting
        ),
        exp = exp
      ) // FIXME: links
    }
  }

  override def addReplyMessage(quote: MessageId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Option[MessageData]] = {

    verbose(l"addReplyMessage($quote, ${content.length}, $mentions, $exp)")
    updater.getMessage(quote).flatMap {
      case Some(original) =>
        val (tpe, ct) = MessageData.messageContent(content, mentions, weblinkEnabled = true)
        verbose(l"parsed content: $ct")
        val id = MessageId()
        val localTime = LocalInstant.Now
        replyHashing.hashMessage(original).flatMap { hash =>
          verbose(l"hash before sending: $hash, original time: ${original.time}")
          convs.storage.getLegalHoldHint(original.convId).flatMap { legalHoldStatus =>
            updater.addLocalMessage(
              MessageData(
                id, original.convId, tpe, selfUserId,
                content = ct,
                genericMsgs = Seq(GenericMessage(id.uid, Text(content, ct.flatMap(_.mentions), Nil, Some(Quote(quote, Some(hash))), expectsReadReceipt.selfSettings, legalHoldStatus))),
                quote = Some(QuoteContent(quote, validity = true, hash = Some(hash))),
                forceReadReceipts = expectsReadReceipt.convSetting
              ),
              exp = exp,
              localTime = localTime
            ).map(Option(_))
          }
        }
        .recover {
          case e@(_:IllegalArgumentException|_:replyHashing.MissingAssetException) =>
            error(l"Got exception when checking reply hash, skipping", e)
            None
        }
      case None =>
        error(l"A reply to a non-existent message: $quote")
        Future.successful(None)
    }
  }

  override def addLocationMessage(convId: ConvId, content: Location, expectsReadReceipt: ReadReceiptSettings = AllDisabled) = {
    verbose(l"addLocationMessage($convId, $content)")
    val id = MessageId()
    updater.addLocalMessage(MessageData(id, convId, Type.LOCATION, selfUserId, genericMsgs = Seq(GenericMessage(id.uid, content)), forceReadReceipts = expectsReadReceipt.convSetting))
  }

  override def addAssetMessage(convId: ConvId,
                               msgId: MessageId,
                               asset: UploadAsset,
                               expectsReadReceipt: ReadReceiptSettings = AllDisabled,
                               exp: Option[Option[FiniteDuration]] = None): Future[MessageData] = {
    import com.waz.model.GenericContent.{Asset => GenericAsset}
    import com.waz.service.assets.Asset

    val tpe = asset.details match {
      case _: Asset.Image => Message.Type.IMAGE_ASSET
      case _: Asset.Video => Message.Type.VIDEO_ASSET
      case _: Asset.Audio => Message.Type.AUDIO_ASSET
      case _              => Message.Type.ANY_ASSET
    }

    convs.storage.getLegalHoldHint(convId).flatMap { legalHoldStatus =>
      val msgData = MessageData(
        msgId,
        convId,
        tpe,
        selfUserId,
        content = Seq(),
        genericMsgs = Seq(GenericMessage(msgId.uid, GenericAsset(asset, None, expectsReadConfirmation = expectsReadReceipt.selfSettings, legalHoldStatus))),
        forceReadReceipts = expectsReadReceipt.convSetting,
        assetId = Some(asset.id)
      )
      updater.addLocalMessage(msgData, exp = exp)
    }

  }

  override def addRenameConversationMessage(convId: ConvId, from: UserId, name: Name) = {
    def update(msg: MessageData) = msg.copy(name = Some(name))
    def create = MessageData(MessageId(), convId, Message.Type.RENAME, from, name = Some(name))
    updater.updateOrCreateLocalMessage(convId, Message.Type.RENAME, update, create)
  }

  override def addRestrictedFileMessage(convId: ConvId, from: Option[UserId] = None, extension: Option[String] = None): Future[Option[MessageData]] = {
    val userId = from.getOrElse(selfUserId)
    def update(msg: MessageData) = msg.copy(name = extension.map(Name(_)), userId = userId)
    def create = MessageData(MessageId(), convId, Message.Type.RESTRICTED_FILE, userId, name = extension.map(Name(_)))
    updater.updateOrCreateLocalMessage(convId, Message.Type.RESTRICTED_FILE, update, create)
  }

  override def addReceiptModeChangeMessage(convId: ConvId, from: UserId, receiptMode: Int): Future[Option[MessageData]] = {
    val msgType = if (receiptMode > 0) Message.Type.READ_RECEIPTS_ON else Message.Type.READ_RECEIPTS_OFF
    def create = MessageData(MessageId(), convId, msgType, from)
    updater.updateOrCreateLocalMessage(convId, msgType, msg => msg, create)
  }

  override def addReceiptModeIsOnMessage(convId: ConvId): Future[MessageData] =
    updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.READ_RECEIPTS_ON, selfUserId, firstMessage = true))


  override def addTimerChangedMessage(convId: ConvId, from: UserId, duration: Option[FiniteDuration], time: RemoteInstant) =
    updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.MESSAGE_TIMER, from, time = time, duration = duration)).map(_ => {})

  override def addConnectRequestMessage(convId: ConvId, fromUser: UserId, toUser: UserId, message: String, name: Name, fromSync: Boolean = false) = {
    val msg = MessageData(
      MessageId(), convId, Message.Type.CONNECT_REQUEST, fromUser, content = MessageData.textContent(message), name = Some(name), recipient = Some(toUser),
      //time = if (fromSync) RemoteInstant.Epoch else now(clock))
      time = RemoteInstant.Epoch )

    if (fromSync) storage.insert(msg) else updater.addLocalMessage(msg)
  }

  override def addKnockMessage(convId: ConvId, selfUserId: UserId, expectsReadReceipt: ReadReceiptSettings = AllDisabled) = {
    debug(l"addKnockMessage($convId, $selfUserId)")
    updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.KNOCK, selfUserId, forceReadReceipts = expectsReadReceipt.convSetting))
  }

  override def addDeviceStartMessages(convs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]] =
    Serialized.future("addDeviceStartMessages")(traverse(convs filter isGroupOrOneToOne) { conv =>
      storage.getLastMessage(conv.id) map {
        case None =>    Some(MessageData(MessageId(), conv.id, Message.Type.STARTED_USING_DEVICE, selfUserId, time = RemoteInstant.Epoch))
        case Some(_) => None
      }
    } flatMap { msgs =>
      storage.insertAll(msgs.flatten)
    })

  private def isGroupOrOneToOne(conv: ConversationData) = conv.convType == ConversationType.Group || conv.convType == ConversationType.OneToOne

  def addHistoryLostMessages(cs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]] = {
    // TODO: those messages should include information about what was actually changed
    traverseSequential(cs) { conv =>
      storage.getLastMessage(conv.id) map {
        case Some(msg) if msg.msgType != Message.Type.STARTED_USING_DEVICE =>
          Some(MessageData(MessageId(), conv.id, Message.Type.HISTORY_LOST, selfUserId, time = msg.time + 1.millis))
        case _ =>
          // conversation has no messages or has STARTED_USING_DEVICE msg,
          // it means that conv was just created and we don't need to add history lost msg
          None
      }
    } flatMap { msgs =>
      storage.insertAll(msgs.flatten) flatMap { added =>
        // mark messages read if there is no other unread messages
        val times: Map[ConvId, RemoteInstant] = added.map(m => m.convId -> m.time) (breakOut)
        convs.storage.updateAll2(times.keys, { c =>
          val t = times(c.id)
          if (c.lastRead.toEpochMilli == t.toEpochMilli - 1) c.copy(lastRead = t) else c
        }) map { _ => added }
      }
    }
  }

  def addConversationStartMessage(convId: ConvId, creator: UserId, users: Set[UserId], name: Option[Name], readReceiptsAllowed: Boolean, time: Option[RemoteInstant]) = {
    val eventTime = time.getOrElse(LocalInstant.Now.toRemote(ZMessaging.currentBeDrift))
    convs.updateLastEvent(convId, eventTime)

    updater
      .addLocalSentMessage(MessageData(MessageId(), convId, Message.Type.MEMBER_JOIN, creator, name = name, members = users, firstMessage = true), Some(eventTime))
      .flatMap(_ =>
        if (readReceiptsAllowed)
          updater.addLocalSentMessage(MessageData(MessageId(), convId, Message.Type.READ_RECEIPTS_ON, creator, firstMessage = true), Some(eventTime + 1.millis)).map(_ => ())
        else
          Future.successful({})
      )
  }

  override def addMemberJoinMessage(convId: ConvId, creator: UserId, users: Set[UserId], firstMessage: Boolean = false, forceCreate: Boolean = false) = {
    def update(msg: MessageData) = msg.copy(members = msg.members ++ users)
    def create = MessageData(MessageId(), convId, Message.Type.MEMBER_JOIN, creator, members = users, firstMessage = firstMessage)

    if (forceCreate) updater.addLocalSentMessage(create).map(Some(_))
    else updater.updateOrCreateLocalMessage(convId, Message.Type.MEMBER_JOIN, update, create)
  }

  override def removeLocalMemberJoinMessage(convId: ConvId, users: Set[UserId]): Future[Unit] =
    storage.getLastSystemMessage(convId, Message.Type.MEMBER_JOIN).flatMap {
      case Some(msg) if msg.isLocal =>
        val members = msg.members -- users
        if (members.isEmpty) updater.deleteMessage(msg)
        else updater.updateMessage(msg.id) { _.copy(members = members) }.map(_ => ()) // FIXME: possible race condition with addMemberJoinMessage or sync
      case _ =>
        Future.successful(())
    }

  override def addMemberLeaveMessage(convId: ConvId, remover: UserId, users: Set[UserId], reason: Option[MemberLeaveReason]): Future[Unit] = {
    val messageType = reason match {
      case Some(MemberLeaveReason.LegalHoldPolicyConflict) => Message.Type.MEMBER_LEAVE_DUE_TO_LEGAL_HOLD
      case _                                               => Message.Type.MEMBER_LEAVE
    }

    val newMessage = MessageData(MessageId(), convId, messageType, remover, members = users)
    def update(msg: MessageData) = msg.copy(members = msg.members ++ users)
    updater.updateOrCreateLocalMessage(convId, messageType, update, newMessage).map(_ => ())
  }


  override def addOtrVerifiedMessage(convId: ConvId) =
    storage.getLastMessage(convId) flatMap {
      case Some(msg) if msg.msgType == Message.Type.OTR_UNVERIFIED || msg.msgType == Message.Type.OTR_DEVICE_ADDED ||  msg.msgType == Message.Type.OTR_MEMBER_ADDED =>
        verbose(l"addOtrVerifiedMessage, removing previous message: $msg")
        storage.remove(msg.id) map { _ => None }
      case _ =>
        updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.OTR_VERIFIED, selfUserId), Status.SENT) map { Some(_) }
    }

  override def addOtrUnverifiedMessage(convId: ConvId, users: Seq[UserId], change: VerificationChange): Future[Option[MessageData]] = {
    val msgType = change match {
      case ClientUnverified => Message.Type.OTR_UNVERIFIED
      case MemberAdded => Message.Type.OTR_MEMBER_ADDED
      case _ => Message.Type.OTR_DEVICE_ADDED
    }
    verbose(l"addOtrUnverifiedMessage($convId, $users, $change), msgType is $msgType")
    updater.addLocalSentMessage(MessageData(MessageId(), convId, msgType, selfUserId, members = users.toSet)) map { Some(_) }
  }

  override def addLegalHoldEnabledMessage(convId: ConvId, time: Option[RemoteInstant]): Future[Option[MessageData]] = {
    val serverTime = time.getOrElse(RemoteInstant(now(clock)))
    val message = MessageData(MessageId(), convId, Message.Type.LEGALHOLD_ENABLED, selfUserId, time = serverTime)
    updater.addLocalSentMessage(message, Some(serverTime)).map(Some(_))
  }

  override def addLegalHoldDisabledMessage(convId: ConvId, time: Option[RemoteInstant]): Future[Option[MessageData]] = {
    val serverTime = time.getOrElse(RemoteInstant(now(clock)))
    val message = MessageData(MessageId(), convId, Message.Type.LEGALHOLD_DISABLED, selfUserId, time = serverTime)
    updater.addLocalSentMessage(message, Some(serverTime)).map(Some(_))
  }

  override def retryMessageSending(conv: ConvId, msgId: MessageId) =
    updater.updateMessage(msgId) { msg =>
      if (msg.state == Status.SENT || msg.state == Status.PENDING) msg
      else msg.copy(state = Status.PENDING)
    } .flatMap {
      case Some(msg) => sync.postMessage(msg.id, conv, msg.editTime) map (Some(_))
      case _ => successful(None)
    }

  def messageSent(convId: ConvId, msgId: MessageId, newTime: RemoteInstant): Future[Option[MessageData]] = {
    import com.waz.utils.RichFiniteDuration
    updater.updateMessage(msgId) { m => m.copy(state = Message.Status.SENT, time = newTime, expiryTime = m.ephemeral.map(_.fromNow())) } andThen {
      case Success(Some(m)) => storage.onMessageSent ! m
    }
  }

  override def addMissedCallMessage(rConvId: RConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]] =
    convs.convByRemoteId(rConvId).flatMap {
      case Some(conv) =>
        addMissedCall(conv, from, time)
      case None =>
        warn(l"No conversation found for remote id: $rConvId")
        Future.successful(None)
    }

  override def addMissedCallMessage(convId: ConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]] = {
    convs.convById(convId).flatMap {
      case Some(conv) =>
        addMissedCall(conv, from, time)
      case None =>
        warn(l"No conversation found for id: $convId")
        Future.successful(None)
    }
  }

  private def addMissedCall(conv: ConversationData, from: UserId, time: RemoteInstant) = {
    missedCall ! MissedCallInfo(selfUserId, conv.id, time, from)
    updater.addMessage(MessageData(MessageId(), conv.id, Message.Type.MISSED_CALL, from, time = time))
  }

  override def addSuccessfulCallMessage(convId: ConvId, from: UserId, time: RemoteInstant, duration: FiniteDuration) =
    updater.addMessage(MessageData(MessageId(), convId, Message.Type.SUCCESSFUL_CALL, from, time = time, duration = Some(duration)))

  override def messageDeliveryFailed(convId: ConvId, msg: MessageData, error: ErrorResponse): Future[Option[MessageData]] =
    updateMessageState(convId, msg.id, Message.Status.FAILED) andThen {
      case Success(Some(m)) => storage.onMessageFailed ! (m, error)
    }

  override def updateMessageState(convId: ConvId, messageId: MessageId, state: Message.Status) =
    updater.updateMessage(messageId) { _.copy(state = state) }

  override def markMessageRead(convId: ConvId, id: MessageId): Future[Option[MessageData]] =
    network.isOnline.head.flatMap {
      case false => Future.successful(None)
      case true =>
        updater.updateMessage(id) { msg =>
          if (msg.state == Status.FAILED) msg.copy(state = Status.FAILED_READ)
          else msg
        }
    }

  override def retentionPolicy2ById(convId: ConvId): Future[AssetClient.Retention] =
    convs.convById(convId).flatMap {
      case Some(c) => retentionPolicy2(c)
      case None =>
        error(l"Failed to find conversation with id: $convId")
        Future.failed(new IllegalArgumentException(s"No conversation with id $convId found"))
    }

  override def retentionPolicy2(convData: ConversationData): Future[AssetClient.Retention] = {
    import AssetClient.Retention

    if (teamId.isDefined || convData.team.isDefined) {
      members
        .activeMembers(convData.id).head
        .flatMap(memberIds => usersStorage.getAll(memberIds))
        .map(_.forall(_.exists(_.teamId.isDefined)))
        .map {
          case true => Retention.EternalInfrequentAccess
          case false => Retention.Expiring
        }
    } else {
      Future.successful(Retention.Expiring)
    }
  }

  override def findMessageIds(convId: ConvId): Future[Set[MessageId]] = storage.findMessageIds(convId)

  override def getAssetIds(messageIds: Set[MessageId]): Future[Set[GeneralAssetId]] = storage.getAssetIds(messageIds)

  override def buttonsForMessage(msgId: MessageId): Signal[Seq[ButtonData]] = RefreshingSignal[Seq[ButtonData]](
    loader        = () => CancellableFuture.lift(buttonsStorage.findByMessage(msgId).map(_.sortBy(_.ordinal))),
    refreshStream = EventStream.zip(buttonsStorage.onChanged.map(_.map(_.id)), buttonsStorage.onDeleted)
  )

  override def clickButton(messageId: MessageId, buttonId: ButtonId): Future[Unit] =
    for {
      Some(msg)      <- storage.get(messageId)
      isSenderActive <- members.isActiveMember(msg.convId, msg.userId)
      _              <- if (isSenderActive)
                          buttonsStorage.update((messageId, buttonId), _.copy(state = ButtonData.ButtonWaiting))
                        else
                          setButtonError(messageId, buttonId)
      _              <- if (isSenderActive) sync.postButtonAction(messageId, buttonId, msg.userId) else Future.successful(())
    } yield ()

  override def setButtonError(messageId: MessageId, buttonId: ButtonId): Future[Unit] =
    buttonsStorage.update((messageId, buttonId), _.copy(state = ButtonData.ButtonError))
      .flatMap(_ => Future.successful(()))

  override def fixErrorMessages(userId: UserId, clientId: ClientId): Future[Unit] =
    for {
      msgs               <- storage.findErrorMessages(userId, clientId)
      onlyFromThisClient =  msgs.filter(_.error.exists(_.clientId == clientId))
      _                  <- Future.sequence(onlyFromThisClient.map(msg =>
                              updater.updateMessage(msg.id)(_.copy(msgType = Message.Type.OTR_ERROR_FIXED))
                            ))
    } yield ()
}
