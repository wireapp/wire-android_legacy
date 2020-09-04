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
package com.waz.service

import com.waz.api.IConversation
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.ConnectionService._
import com.waz.service.EventScheduler.Stage
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.wire.signals.{EventContext, Serialized}
import com.waz.utils.RichWireInstant

import scala.collection.breakOut
import scala.concurrent.Future

trait ConnectionService {
  def connectionEventsStage: Stage.Atomic

  def connectToUser(userId: UserId, message: String, name: Name): Future[Option[ConversationData]]
  def handleUserConnectionEvents(events: Seq[UserConnectionEvent]): Future[Unit]
  def acceptConnection(userId: UserId): Future[ConversationData]
  def ignoreConnection(userId: UserId): Future[Option[UserData]]
  def blockConnection(userId: UserId): Future[Option[UserData]]
  def unblockConnection(userId: UserId): Future[ConversationData]
  def cancelConnection(userId: UserId): Future[Option[UserData]]
}

class ConnectionServiceImpl(selfUserId:      UserId,
                            teamId:          Option[TeamId],
                            push:            PushService,
                            convsContent:    ConversationsContentUpdater,
                            convsStorage:    ConversationStorage,
                            members:         MembersStorage,
                            messages:        MessagesService,
                            messagesStorage: MessagesStorage,
                            users:           UserService,
                            usersStorage:    UsersStorage,
                            sync:            SyncServiceHandle) extends ConnectionService with DerivedLogTag {

  import Threading.Implicits.Background
  private implicit val ec = EventContext.Global

  override val connectionEventsStage = EventScheduler.Stage[UserConnectionEvent]((c, e) => handleUserConnectionEvents(e))

  override def handleUserConnectionEvents(events: Seq[UserConnectionEvent]) = {
    def updateOrCreate(event: UserConnectionEvent)(user: Option[UserData]): UserData =
      user.fold {
        UserData(event.to, None, event.fromUserName.getOrElse(Name.Empty), None, None, connection = event.status, conversation = Some(event.convId), connectionMessage = event.message, searchKey = SearchKey.Empty, connectionLastUpdated = event.lastUpdated,
          handle = None)
      } {
        _.copy(conversation = Some(event.convId)).updateConnectionStatus(event.status, Some(event.lastUpdated), event.message)
      }

    val lastEvents = events.groupBy(_.to).map { case (to, es) => to -> es.maxBy(_.lastUpdated) }
    val fromSync: Set[UserId] = lastEvents.filter(_._2.localTime == LocalInstant.Epoch).map(_._2.to)(breakOut)

    verbose(l"lastEvents: $lastEvents, fromSync: $fromSync")

    usersStorage.updateOrCreateAll2(lastEvents.map(_._2.to), { case (uId, user) => updateOrCreate(lastEvents(uId))(user) })
      .map { users => (users.map(u => (u, lastEvents(u.id).lastUpdated)), fromSync) }
  }.flatMap { case (users, fromSync) =>
    verbose(l"syncing $users and fromSync: $fromSync")
    val toSync = users filter { case (user, _) => user.connection == ConnectionStatus.Accepted || user.connection == ConnectionStatus.PendingFromOther || user.connection == ConnectionStatus.PendingFromUser }
    sync.syncUsers(toSync.map(_._1.id)(breakOut)) flatMap { _ =>
      updateConversationsForConnections(users.map(u => ConnectionEventInfo(u._1, fromSync(u._1.id), u._2))).map(_ => ())
    }
  }

  private def updateConversationsForConnections(eventInfos: Set[ConnectionEventInfo]): Future[Seq[ConversationData]] = {
    verbose(l"updateConversationForConnections: ${eventInfos.size}")

    def getConvTypeForUser(user: UserData): IConversation.Type = user.connection match {
      case ConnectionStatus.PendingFromUser | ConnectionStatus.Cancelled => ConversationType.WaitForConnection
      case ConnectionStatus.PendingFromOther | ConnectionStatus.Ignored => ConversationType.Incoming
      case _ => ConversationType.OneToOne
    }

    val oneToOneConvData = eventInfos.map { case ConnectionEventInfo(user , _ , _) =>
      OneToOneConvData(user.id, user.conversation, getConvTypeForUser(user))
    }

    val eventMap = eventInfos.map(eventInfo => eventInfo.user.id -> eventInfo).toMap
    val userIds = eventInfos.map(_.user.id)

    for {
      otoConvs     <- getOrCreateOneToOneConversations(oneToOneConvData.toSeq)
      convToUser   = userIds.flatMap(e => otoConvs.get(e).map(c => c.id -> e)).toMap
      _            <- members.addAll(convToUser.map { case (convId, userId) => convId -> Map(userId -> ConversationRole.AdminRole, selfUserId -> ConversationRole.AdminRole) })
      updatedConvs <- convsStorage.updateAll2(convToUser.keys, { conv =>

        val userId = convToUser(conv.id)
        val user   = eventMap(userId).user
        val hidden = user.connection == ConnectionStatus.Ignored || user.connection == ConnectionStatus.Blocked || user.connection == ConnectionStatus.Cancelled

        //TODO For some reasons we are loosing convType after getOrCreateOneToOneConversations. Flow is very messy and should be refactored.
        val convType = getConvTypeForUser(user)

        conv.copy(convType = convType, hidden = hidden, lastEventTime = conv.lastEventTime max eventMap(userId).lastEventTime)
      })

      result <- Future.sequence(updatedConvs.map { case (_, conv) =>
        messagesStorage.getLastMessage(conv.id) flatMap {
          case None if conv.convType == ConversationType.Incoming =>
            val userId = convToUser(conv.id)
            val user = eventMap(userId).user
            messages.addConnectRequestMessage(conv.id, user.id, selfUserId, user.connectionMessage.getOrElse(""), user.name, fromSync = eventMap(userId).fromSync)
          case None if conv.convType == ConversationType.OneToOne =>
            messages.addDeviceStartMessages(Seq(conv), selfUserId)
          case _ =>
            Future.successful(())
        } map { _ =>
          val userId = convToUser(conv.id)
          val user = eventMap(userId).user
          val hidden = user.connection == ConnectionStatus.Ignored || user.connection == ConnectionStatus.Blocked || user.connection == ConnectionStatus.Cancelled
          if (conv.hidden && !hidden) sync.syncConversations(Set(conv.id))
          conv
        }
      })
    } yield result
  }

  /**
   * Connects to user and creates one-to-one conversation if needed. Returns existing conversation if user is already connected.
   */
  override def connectToUser(userId: UserId, message: String, name: Name): Future[Option[ConversationData]] = {

    def sanitizedName = if (name.isEmpty) Name("_") else if (name.length >= 256) name.substring(0, 256) else name

    def connectIfUnconnected() = users.getOrCreateUser(userId).flatMap { user =>
      if (user.isConnected) {
        verbose(l"User already connected: $user")
        Future.successful(None)
      } else {
        users.updateConnectionStatus(user.id, ConnectionStatus.PendingFromUser).flatMap {
          case Some(u) => sync.postConnection(userId, sanitizedName, message).map(_ => Some(u))
          case _       => Future.successful(None)
        }
      }
    }

    connectIfUnconnected().flatMap {
      case Some(_) =>
        for {
          conv <- getOrCreateOneToOneConversation(userId, convType = ConversationType.WaitForConnection)
          _ = verbose(l"connectToUser, conv: $conv")
          _ <- messages.addConnectRequestMessage(conv.id, selfUserId, userId, message, name)
        } yield Some(conv)
      case None => //already connected
        convsContent.convById(ConvId(userId.str))
    }
  }

  override def acceptConnection(userId: UserId): Future[ConversationData] =
    users.updateConnectionStatus(userId, ConnectionStatus.Accepted).map {
      case Some(_) =>
        sync.postConnectionStatus(userId, ConnectionStatus.Accepted).map { syncId =>
          sync.syncConversations(Set(ConvId(userId.str)), Some(syncId))
        }
      case _ =>
    } flatMap { _ =>
      getOrCreateOneToOneConversation(userId, convType = ConversationType.OneToOne).flatMap { conv =>
        convsContent.updateConversation(conv.id, Some(ConversationType.OneToOne), hidden = Some(false)).flatMap { updated =>
          messages.addMemberJoinMessage(conv.id, selfUserId, Set(selfUserId), firstMessage = true) map { _ =>
            updated.fold(conv)(_._2)
          }
        }
      }
    }

  override def ignoreConnection(userId: UserId): Future[Option[UserData]] =
    for {
      user <- users.updateConnectionStatus(userId, ConnectionStatus.Ignored)
      _    <- user.fold(Future.successful({}))(_ => sync.postConnectionStatus(userId, ConnectionStatus.Ignored).map(_ => {}))
      _    <- convsContent.hideIncomingConversation(userId)
    } yield user

  override def blockConnection(userId: UserId): Future[Option[UserData]] = {
    for {
      _    <- convsContent.setConversationHidden(ConvId(userId.str), hidden = true)
      user <- users.updateConnectionStatus(userId, ConnectionStatus.Blocked)
      _    <- user.fold(Future.successful({}))(_ => sync.postConnectionStatus(userId, ConnectionStatus.Blocked).map(_ => {}))
    } yield user
  }

  override def unblockConnection(userId: UserId): Future[ConversationData] =
    for {
      user <- users.updateConnectionStatus(userId, ConnectionStatus.Accepted)
      _    <- user.fold(Future.successful({})) { _ =>
        for {
          syncId <- sync.postConnectionStatus(userId, ConnectionStatus.Accepted)
          _      <- sync.syncConversations(Set(ConvId(userId.str)), Some(syncId)) // sync conversation after syncing connection state (conv is locked on backend while connection is blocked) TODO: we could use some better api for that
        } yield {}
      }
      conv    <- getOrCreateOneToOneConversation(userId, convType = ConversationType.OneToOne)
      updated <- convsContent.updateConversation(conv.id, Some(ConversationType.OneToOne), hidden = Some(false)) map { _.fold(conv)(_._2) } // TODO: what about messages
    } yield updated


  override def cancelConnection(userId: UserId): Future[Option[UserData]] =
    users.updateUserData(userId, { user =>
      if (user.connection == ConnectionStatus.PendingFromUser) user.copy(connection = ConnectionStatus.Cancelled)
      else {
        warn(l"can't cancel connection for user in wrong state: ${user.connection}")
        user
      }
    }).flatMap {
      case Some((prev, user)) if prev != user =>
        sync.postConnectionStatus(userId, ConnectionStatus.Cancelled)
        convsContent.setConversationHidden(ConvId(user.id.str), hidden = true).map { _ => Some(user) }
      case None =>
        Future.successful(None)
    }

  /**
    * Finds or creates local one-to-one conversation with given user and/or remoteId.
    * Updates remoteId if different and merges conversations if two are found for given local and remote id.
    *
    * Will not post created conversation to backend.
    * TODO: improve - it looks too complicated and duplicates some code
    */
  private def getOrCreateOneToOneConversation(toUser: UserId, remoteId: Option[RConvId] = None, convType: ConversationType = ConversationType.OneToOne): Future[ConversationData] =
    getOrCreateOneToOneConversations(Seq(OneToOneConvData(toUser, remoteId, convType))).map(_.values.head)

  private def getOrCreateOneToOneConversations(convsInfo: Seq[OneToOneConvData]): Future[Map[UserId, ConversationData]] =
    Serialized.future("getOrCreateOneToOneConversations") {
      verbose(l"getOrCreateOneToOneConversations(self: $selfUserId, convs:${convsInfo.size})")

      def convIdForUser(userId: UserId) = ConvId(userId.str)
      def userIdForConv(convId: ConvId) = UserId(convId.str)

      for {
        remotes   <- convsStorage.getByRemoteIds2(convsInfo.flatMap(_.remoteId).toSet)
        _         <- convsStorage.updateLocalIds(convsInfo.collect {
                       case OneToOneConvData(toUser, Some(remoteId), _) if remotes.contains(remoteId) =>
                         remotes(remoteId).id -> convIdForUser(toUser)
                     }.toMap)
                     // remotes need to be refreshed after updating local ids
        remotes   <- convsStorage.getByRemoteIds2(convsInfo.flatMap(_.remoteId).toSet)
        remoteIds =  convsInfo.map(i => convIdForUser(i.toUser) -> i.remoteId).toMap
        newConvs  =  convsInfo.map { case OneToOneConvData(toUser, remoteId, convType) =>
                       val convId = convIdForUser(toUser)
                       convId -> ConversationData(
                         convId,
                         remoteId.getOrElse(RConvId(toUser.str)),
                         name          = None,
                         creator       = selfUserId,
                         convType      = convType,
                         team          = teamId,
                         access        = Set(Access.PRIVATE),
                         accessRole    = Some(AccessRole.PRIVATE)
                       )
                     }.toMap
        result    <- convsStorage.updateOrCreateAll2(newConvs.keys, {
                       case (cId, Some(conv)) =>
                         remoteIds(cId).fold(conv)(rId => remotes.getOrElse(rId, conv.copy(remoteId = rId)))
                        case (cId, _)          =>
                          newConvs(cId)
                     })
      } yield result.map(conv => userIdForConv(conv.id) -> conv).toMap
    }

}

object ConnectionService {
  case class ConnectionEventInfo(user: UserData, fromSync: Boolean, lastEventTime: RemoteInstant)

  case class OneToOneConvData(toUser:   UserId, remoteId: Option[RConvId] = None, convType: ConversationType = ConversationType.OneToOne)
}