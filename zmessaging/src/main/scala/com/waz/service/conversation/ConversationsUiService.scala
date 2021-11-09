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

import com.waz.api
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.Message
import com.waz.api.NetworkMode.{OFFLINE, WIFI}
import com.waz.api.impl._
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.{ConversationType, getAccessAndRoleForGroupConv}
import com.waz.model.GenericContent.{Location, MsgEdit}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.model.sync.ReceiptType
import com.waz.service.AccountsService.InForeground
import com.waz.service.ZMessaging.currentBeDrift
import com.waz.service._
import com.waz.service.assets.{AES_CBC_Encryption, AssetService, ContentForUpload, UploadAsset, UriHelper}
import com.waz.service.assets.Asset.Video
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.tracking.{ContributionEvent, TrackingService}
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.sync.client.{ConversationsClient, ErrorOr}
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.zms.BuildConfig
import com.wire.signals.EventStream

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.NonFatal

trait ConversationsUiService {
  import ConversationsUiService._

  def sendTextMessage(convId: ConvId, text: String, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Some[MessageData]]
  def sendTextMessages(convs: Seq[ConvId], text: String, mentions: Seq[Mention] = Nil, exp: Option[FiniteDuration]): Future[Unit]

  def sendReplyMessage(replyTo: MessageId, text: String, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Option[MessageData]]

  def sendAssetMessage(convId: ConvId,
                       content: ContentForUpload,
                       confirmation: WifiWarningConfirmation = DefaultConfirmation,
                       exp: Option[Option[FiniteDuration]] = None): Future[Some[MessageData]]
  def sendAssetMessages(convId: ConvId,
                        contents: Seq[ContentForUpload],
                        confirmation: WifiWarningConfirmation = DefaultConfirmation,
                        exp: Option[Option[FiniteDuration]] = None): Future[Unit]

  def sendLocationMessage(convId: ConvId, l: api.MessageContent.Location): Future[Some[MessageData]] //TODO remove use of MessageContent.Location

  def updateMessage(convId: ConvId, id: MessageId, text: String, mentions: Seq[Mention] = Nil): Future[Option[MessageData]]

  def deleteMessage(convId: ConvId, id: MessageId): Future[Unit]
  def recallMessage(convId: ConvId, id: MessageId): Future[Option[MessageData]]
  def setConversationArchived(id: ConvId, archived: Boolean): Future[Option[ConversationData]]
  def setConversationMuted(id: ConvId, muted: MuteSet): Future[Option[ConversationData]]
  def setConversationName(id: ConvId, name: Name): Future[Option[ConversationData]]

  def addRestrictedFileMessage(convId: ConvId, from: Option[UserId] = None, extension: Option[String] = None): Future[Option[MessageData]]

  def addConversationMembers(conv: ConvId, members: Set[UserId], defaultRole: ConversationRole, enforceModifyMembers: Boolean = false): Future[Option[SyncId]]
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
  def createGroupConversation(name:        Option[Name],
                              members:     Set[UserId] = Set.empty,
                              teamOnly:    Boolean = false,
                              receiptMode: Int = 0,
                              defaultRole: ConversationRole = ConversationRole.MemberRole
                             ): Future[(ConversationData, SyncId)]
  def createConvWithFederatedUser(qId:         QualifiedId,
                                  teamOnly:    Boolean = false,
                                  receiptMode: Int = 0,
                                  defaultRole: ConversationRole = ConversationRole.MemberRole
                                 ): Future[(ConversationData, SyncId)]

  def assetUploadCancelled : EventStream[Mime]
  def assetUploadFailed    : EventStream[ErrorResponse]
}


object ConversationsUiService {
  type WifiWarningConfirmation = Long => Future[Boolean]
  val DefaultConfirmation = (_: Long) => Future.successful(true)

  val LargeAssetWarningThresholdInBytes = 3145728L // 3MiB
}

class ConversationsUiServiceImpl(selfUserId:        UserId,
                                 teamId:            Option[TeamId],
                                 domain:            Domain,
                                 assets:            AssetService,
                                 userService:       UserService,
                                 messages:          MessagesService,
                                 messagesStorage:   MessagesStorage,
                                 messagesContent:   MessagesContentUpdater,
                                 members:           MembersStorage,
                                 convsContent:      ConversationsContentUpdater,
                                 convStorage:       ConversationStorage,
                                 network:           NetworkModeService,
                                 convs:             ConversationsService,
                                 sync:              SyncServiceHandle,
                                 syncRequests:      SyncRequestService,
                                 client:            ConversationsClient,
                                 accounts:          AccountsService,
                                 tracking:          TrackingService,
                                 errors:            ErrorsService,
                                 uriHelper:         UriHelper,
                                 propertiesService: PropertiesService) extends ConversationsUiService with DerivedLogTag {
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

  override def sendAssetMessage(convId: ConvId,
                                content: ContentForUpload,
                                confirmation: WifiWarningConfirmation = DefaultConfirmation,
                                exp: Option[Option[FiniteDuration]] = None): Future[Some[MessageData]] = {

    def trackAsset(mime: Option[Mime]): Future[Unit] =
      mime.fold(Future.successful(()))(m => tracking.contribution(ContributionEvent.fromMime(m), Some(selfUserId)))

    val messageId = MessageId()
    for {
      retention  <- messages.retentionPolicy2ById(convId)
      rr         <- readReceiptSettings(convId)
      rawAsset   <- assets.createAndSaveUploadAsset(content, AES_CBC_Encryption.random, public = false, retention, Some(messageId))
      message    <- messages.addAssetMessage(convId, messageId, rawAsset, rr, exp)
      _          <- updateLastRead(message)
      assetMime  =  content.content.getMime(uriHelper).toOption
      _          <- trackAsset(assetMime)
      shouldSend <- checkSize(convId, rawAsset, message, confirmation)
      _          <- if (shouldSend) sync.postMessage(message.id, convId, message.editTime) else Future.successful(())
    } yield Some(message)
  }

  override def sendAssetMessages(convId:       ConvId,
                                 contents:     Seq[ContentForUpload],
                                 confirmation: WifiWarningConfirmation = DefaultConfirmation,
                                 exp:          Option[Option[FiniteDuration]] = None): Future[Unit] = {
    val contentMap = contents.map { c => MessageId() -> c }.toMap
    for {
      retention <- messages.retentionPolicy2ById(convId)
      rr        <- readReceiptSettings(convId)
      rawAssets <- Future.traverse(contentMap) { case (messageId, content) =>
                     assets.createAndSaveUploadAsset(content, AES_CBC_Encryption.random, public = false, retention, Some(messageId))
                           .map(asset => messageId -> asset)
                   }
      assetMap  =  rawAssets.toMap
      msgs      <- Future.traverse(assetMap) { case (messageId, rawAsset) =>
                     messages.addAssetMessage(convId, messageId, rawAsset, rr, exp)
                   }
      _         <- updateLastRead(msgs.toList.maxBy(_.time))
      msgMap    =  msgs.toIdMap
      _         <- Future.traverse(assetMap) { case (messageId, rawAsset) =>
                     val message = msgMap(messageId)
                     checkSize(convId, rawAsset, message, confirmation).flatMap {
                       case true  => sync.postMessage(message.id, convId, message.editTime)
                       case false => Future.successful(())
                     }
                   }
    } yield ()
  }

  override def sendLocationMessage(convId: ConvId, l: api.MessageContent.Location): Future[Some[MessageData]] = {
    for {
      rr              <- readReceiptSettings(convId)
      legalHoldStatus <- convStorage.getLegalHoldHint(convId)
      msg             <- messages.addLocationMessage(convId, Location(l.getLongitude, l.getLatitude, l.getName, l.getZoom, rr.selfSettings, legalHoldStatus))
      _               <- updateLastRead(msg)
      _               <- sync.postMessage(msg.id, convId, msg.editTime)
    } yield Some(msg)
  }

  override def updateMessage(convId: ConvId, id: MessageId, text: String, mentions: Seq[Mention] = Nil): Future[Option[MessageData]] =
    convStorage.getLegalHoldHint(convId).flatMap { legalHoldStatus =>
      messagesStorage.update(id, {
        case m if m.convId == convId && m.userId == selfUserId =>
          val (tpe, ct) = MessageData.messageContent(text, mentions, weblinkEnabled = true)
          verbose(l"updated content: ${(tpe, ct)}")
          m.copy(
            msgType = tpe,
            content = ct,
            genericMsgs = Seq(GenericMessage(Uid(), MsgEdit(id, GenericContent.Text(text, ct.flatMap(_.mentions), Nil, m.protoQuote, m.protoReadReceipts.getOrElse(false), legalHoldStatus)))),
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
    convsContent.updateLastEvent(id, LocalInstant.Now.toRemote(currentBeDrift)).flatMap { _ =>
      convsContent.updateConversationMuted(id, muted) map {
        case Some((_, conv)) =>
          sync.postConversationState(
            id,
            ConversationState(muted = Some(conv.muted.oldMutedFlag), muteTime = Some(conv.muteTime), mutedStatus = Some(conv.muted.toInt))
          )
          Some(conv)
        case None => None
      }
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

  override def addRestrictedFileMessage(convId: ConvId, from: Option[UserId] = None, extension: Option[String] = None): Future[Option[MessageData]]
    = messages.addRestrictedFileMessage(convId, from, extension)

  private def partitionForQualified(userIds: Set[UserId]) =
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      for {
        users        <- userService.findUsers(userIds.toSeq)
        qualified    =  users.collect { case Some(u) if u.qualifiedId.nonEmpty => u.id -> u.qualifiedId }.toMap
        nonQualified =  userIds -- qualified.keySet
      } yield (qualified.values.flatten.toSet, nonQualified)
    } else {
      Future.successful((Set.empty[QualifiedId], userIds))
    }

  override def addConversationMembers(conv:                 ConvId,
                                      userIds:              Set[UserId],
                                      defaultRole:          ConversationRole,
                                      enforceModifyMembers: Boolean = false): Future[Option[SyncId]] =
    (for {
      true                  <- if (enforceModifyMembers) Future.successful(true) else canModifyMembers(conv)
      contacted             <- members.getByUsers(userIds)
      toSync                =  userIds -- contacted.map(_.userId).toSet
      _                     <- if (toSync.nonEmpty) userService.syncUsers(toSync) else Future.successful(())
      _                     <- if (userIds.nonEmpty) members.updateOrCreateAll(conv, userIds.map(_ -> defaultRole).toMap) else Future.successful(())
      _                     <- messages.addMemberJoinMessage(conv, selfUserId, userIds)
      (qIds, nonQIds)       <- partitionForQualified(userIds)
      syncId1               <- if (qIds.nonEmpty)
                                 sync.postQualifiedConversationMemberJoin(conv, qIds, defaultRole).map(Option(_))
                               else
                                 Future.successful(None)
      syncId2               <- if (nonQIds.nonEmpty)
                                 sync.postConversationMemberJoin(conv, nonQIds, defaultRole).map(Option(_))
                               else
                                 Future.successful(None)
    } yield syncId2.orElse(syncId1))
      .recover {
        case NonFatal(e) =>
          warn(l"Failed to add members: $userIds to conv: $conv", e)
          Option.empty[SyncId]
      }

  override def removeConversationMember(conv: ConvId, user: UserId): Future[Option[SyncId]] = {
    (for {
      true     <- canModifyMembers(conv)
      Some(_)  <- members.remove(conv, user)
      toDelete <- if (user != selfUserId) members.getByUsers(Set(user)).map(_.isEmpty)
                  else Future.successful(false)
      _        <- if (toDelete) userService.deleteUsers(Set(user), sendLeaveMessage = false) else Future.successful(())
      _        <- messages.addMemberLeaveMessage(conv, selfUserId, Set(user), reason = None)
      qId      <- if (BuildConfig.FEDERATION_USER_DISCOVERY) userService.qualifiedId(user).map(Some(_)) else Future.successful(None)
      syncId   <- qId.fold(sync.postConversationMemberLeave(conv, user))(sync.postConversationMemberLeave(conv, _))
    } yield Option(syncId))
      .recover {
        case NonFatal(e) =>
          warn(l"Failed to remove member: $user from conv: $conv", e)
          Option.empty[SyncId]
      }
  }

  private def canModifyMembers(convId: ConvId) =
    members.isActiveMember(convId, selfUserId).flatMap {
      case false => Future.successful(false)
      case true  =>
        convs.isGroupConversation(convId).flatMap {
          case true  => Future.successful(true)
          case false => convs.isWithService(convId)
        }
    }

  override def leaveConversation(conv: ConvId): Future[Unit] = {
    verbose(l"leaveConversation($conv)")
    for {
      _ <- convsContent.setConvActive(conv, active = false)
      _ <- removeConversationMember(conv, selfUserId)
      _ <- convsContent.updateConversationArchived(conv, archived = true)
    } yield {}
  }

  override def clearConversation(id: ConvId): Future[Option[ConversationData]] = convsContent.convById(id).flatMap {
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

  override def getOrCreateOneToOneConversation(otherUserId: UserId): Future[ConversationData] = {

    def createReal1to1() =
      convsContent.convById(ConvId(otherUserId.str)) flatMap {
        case Some(conv) => Future.successful(conv)
        case _ => userService.findUser(otherUserId).flatMap {
          case Some(u) if u.connection == ConnectionStatus.Ignored =>
            for {
              conv <- convsContent.createConversation(
                        convId      = ConvId(otherUserId.str),
                        remoteId    = u.conversation.map(_.id).getOrElse(RConvId()),
                        convType    = ConversationType.Incoming,
                        creator     = otherUserId,
                        name        = None,
                        hidden      = true,
                        defaultRole = ConversationRole.AdminRole
                      )
              _    <- messages.addMemberJoinMessage(conv.id, otherUserId, Set(selfUserId), firstMessage = true)
              _    <- u.connectionMessage.fold(Future.successful(conv))(messages.addConnectRequestMessage(conv.id, otherUserId, selfUserId, _, u.name).map(_ => conv))
            } yield conv
          case _ =>
            for {
              _    <-
                if (BuildConfig.FEDERATION_USER_DISCOVERY) {
                  userService.qualifiedId(otherUserId).flatMap { qId =>
                    sync.postConversation(ConvId(otherUserId.str), qId, None, Set(Access.PRIVATE), AccessRole.PRIVATE, None, ConversationRole.AdminRole)
                  }
                } else {
                  sync.postConversation(ConvId(otherUserId.str), Some(otherUserId), None, None, Set(Access.PRIVATE), AccessRole.PRIVATE, None, ConversationRole.AdminRole)
                }
              conv <- convsContent.createConversation(
                        convId      = ConvId(otherUserId.str),
                        remoteId    = RConvId(),
                        convType    = ConversationType.OneToOne,
                        creator     = selfUserId,
                        name        = None,
                        defaultRole = ConversationRole.AdminRole
                      )
              _    <- messages.addMemberJoinMessage(conv.id, selfUserId, Set(otherUserId), firstMessage = true)
            } yield conv
        }
      }

    def createFake1To1(tId: TeamId, otherUser: UserData) = {
      verbose(l"Checking for 1:1 conversation with user: ${otherUser.id}")
      (for {
        allConvs    <- this.members.getByUsers(Set(otherUser.id)).map(_.map(_.convId))
        allMembers  <- this.members.getByConvs(allConvs.toSet).map(_.map(m => m.convId -> m.userId))
        onlyUs      =  allMembers.groupBy { case (c, _) => c }
                                 .map     { case (cid, us) => cid -> us.map(_._2).toSet }
                                 .collect { case (c, us) if us == Set(otherUser.id, selfUserId) => c }
        convs       <- convStorage.getAll(onlyUs).map(_.flatten)
      } yield {
        if (convs.size > 1)
          warn(l"Found ${convs.size} available team conversations with user: $otherUserId, returning first conversation found")
        convs.find(_.name.isEmpty) // if we already have a conv with that person but it's named, we prefer to create a new one
      }).flatMap {
        case Some(conv) =>
          Future.successful(conv)
        case _ =>
          createGroupConversation(
            name        = None,
            members     = Set(otherUser.id),
            defaultRole = ConversationRole.AdminRole
          ).map(_._1)
      }
    }

    teamId match {
      case Some(tId) =>
        for {
          Some(otherUser) <- userService.findUser(otherUserId)
          isGuest         =  otherUser.isGuest(tId, domain)
          conv            <- if (isGuest) createReal1to1() else createFake1To1(tId, otherUser)
        } yield conv
      case None =>
        createReal1to1()
    }
  }

  override def createGroupConversation(name:        Option[Name],
                                       members:     Set[UserId]      = Set.empty,
                                       teamOnly:    Boolean          = false,
                                       receiptMode: Int              = 0,
                                       defaultRole: ConversationRole = ConversationRole.MemberRole
                                      ): Future[(ConversationData, SyncId)] = {
    val tempRConvId =  convs.generateTempConversationId(members)
    for {
      (conv, syncId) <- createAndPostGroupConversation(ConvId(), name, teamOnly, receiptMode, defaultRole, tempRConvId)
      _              <- syncRequests.await(syncId)
      _              <- addConversationMembers(conv.id, members, defaultRole, enforceModifyMembers = true)
    } yield (conv, syncId)
  }

  override def createConvWithFederatedUser(qId:         QualifiedId,
                                           teamOnly:    Boolean = false,
                                           receiptMode: Int = 0,
                                           defaultRole: ConversationRole = ConversationRole.MemberRole
                                          ): Future[(ConversationData, SyncId)] =
    createGroupConversation(None, Set(qId.id), teamOnly, receiptMode, defaultRole)

  private def createLocalGroupConversation(id:          ConvId,
                                           name:        Option[Name],
                                           access:      Set[Access],
                                           accessRole:  AccessRole,
                                           receiptMode: Int,
                                           defaultRole: ConversationRole,
                                           tempRConvId: RConvId
                                          ): Future[ConversationData] =
    for {
      conv <- convsContent.createConversation(
                convId      = id,
                remoteId    = tempRConvId,
                convType    = ConversationType.Group,
                creator     = selfUserId,
                name        = name,
                access      = access,
                accessRole  = accessRole,
                receiptMode = receiptMode,
                defaultRole = defaultRole
              )
      _    =  verbose(l"created: $conv")
      _    <- messages.addConversationStartMessage(conv.id, selfUserId, Set(selfUserId), name, conv.readReceiptsAllowed)
    } yield conv

  private def createAndPostGroupConversation(id:          ConvId,
                                             name:        Option[Name],
                                             teamOnly:    Boolean = false,
                                             receiptMode: Int = 0,
                                             defaultRole: ConversationRole,
                                             tempRConvId: RConvId
                                            ): Future[(ConversationData, SyncId)] = {
    val (ac, ar) = getAccessAndRoleForGroupConv(teamOnly, teamId)
    for {
      conv   <- createLocalGroupConversation(id, name, ac, ar, receiptMode, defaultRole, tempRConvId)
      syncId <- sync.postConversation(id, None, conv.name, teamId, ac, ar, Some(receiptMode), defaultRole)
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

  def shouldSendReadReceipts(convId: ConvId, readReceiptSettings: ReadReceiptSettings): Future[Boolean] =
    convs.isGroupConversation(convId).map {
      case true  => readReceiptSettings.convSetting.contains(1)
      case false => readReceiptSettings.selfSettings
    }

  override def setLastRead(convId: ConvId, msg: MessageData): Future[Option[ConversationData]] = {
    def sendReadReceipts(from: RemoteInstant, to: RemoteInstant, readReceiptSettings: ReadReceiptSettings): Future[Seq[SyncId]] = {
      shouldSendReadReceipts(convId, readReceiptSettings).flatMap {
        case true =>
          messagesStorage.findMessagesBetween(convId, from, to).flatMap { messages =>
            val msgs = messages.filter { m =>
              m.userId != selfUserId && m.expectsRead.contains(true)
            }
            RichFuture.traverseSequential(msgs.groupBy(_.userId).toSeq)({ case (u, ms) if ms.nonEmpty =>
              sync.postReceipt(convId, ms.map(_.id), u, ReceiptType.Read)
            })
          }
        case false => Future.successful(Seq())
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

  //TODO Refactor this. Maybe move some part of this method into UI project
  private def checkSize(convId: ConvId, rawAsset: UploadAsset, message: MessageData, confirmation: WifiWarningConfirmation) = {
    val isAssetLarge = rawAsset.size > LargeAssetWarningThresholdInBytes
    val isAssetTooLarge: Boolean = rawAsset.details match {
      case _: Video => false
      case _ => rawAsset.size > AssetData.maxAssetSizeInBytes(teamId.isDefined)
    }

    if (isAssetTooLarge) {
      for {
        _ <- messages.updateMessageState(convId, message.id, Message.Status.FAILED)
        _ <- errors.addAssetTooLargeError(convId, message.id)
        _ <- Future.successful(assetUploadFailed ! ErrorResponse.internalError("asset too large"))
      } yield false
    } else if (isAssetLarge) {
      for {
        mode         <- network.networkMode.head
        inForeground <- accounts.accountState(selfUserId).map(_ == InForeground).head
        res <- if (!Set(OFFLINE, WIFI).contains(mode) && inForeground)
        // will mark message as failed and ask user if it should really be sent
        // marking as failed ensures that user has a way to retry even if he doesn't respond to this warning
        // this is possible if app is paused or killed in meantime, we don't want to be left with message in state PENDING without a sync request
          messages.updateMessageState(convId, message.id, Message.Status.FAILED).map { _ =>
            confirmation(rawAsset.size).foreach {
              case true  => messages.retryMessageSending(convId, message.id)
              case false => messagesContent.deleteMessage(message).map(_ => assetUploadCancelled ! rawAsset.mime)
            }
            false
          }(Threading.Ui)
        else Future.successful(true)
      } yield res
    } else {
      Future.successful(true)
    }

  }
}
