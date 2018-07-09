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

import java.util.Date

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.waz.utils.{RichFuture, RichWireInstant}
import com.waz.utils.events.EventContext

import scala.collection.breakOut
import scala.concurrent.Future

trait ConnectionService {
  def connectToUser(userId: UserId, message: String, name: String): Future[Option[ConversationData]]
  def handleUserConnectionEvents(events: Seq[UserConnectionEvent]): Future[Unit]
  def syncConversationInitiallyAfterCreation(convId: RConvId, selfUserId: UserId, userId: UserId): Future[SyncId]
}

class ConnectionServiceImpl(selfUserId:      UserId,
                            push:            PushService,
                            convs:           ConversationsContentUpdater,
                            members:         MembersStorage,
                            messages:        MessagesService,
                            messagesStorage: MessagesStorage,
                            users:           UserService,
                            usersStorage:    UsersStorage,
                            sync:            SyncServiceHandle) extends ConnectionService {

  import Threading.Implicits.Background
  private implicit val ec = EventContext.Global
  import messages._
  import users._

  val convStorage = convs.storage

  val connectionEventsStage = EventScheduler.Stage[UserConnectionEvent]((c, e) => handleUserConnectionEvents(e))

  val contactJoinEventsStage = EventScheduler.Stage[ContactJoinEvent] { (c, es) =>
    RichFuture.processSequential(es) { e =>
      getOrCreateUser(e.user) flatMap { _ =>
        // update user name if it was just created (has empty name)
        updateUserData(e.user, u => u.copy(name = if (u.name == "") e.name else u.name))
      }
    }
  }

  def syncConversationInitiallyAfterCreation(convId: RConvId, selfUserId: UserId, userId: UserId) =
    convs.getOneToOneConversation(userId, selfUserId, Some(convId), ConversationType.WaitForConnection) flatMap { conv =>
      sync.syncConversations(Set(conv.id))
    }

  def handleUserConnectionEvents(events: Seq[UserConnectionEvent]) = {
    verbose(s"handleUserConnectionEvents: $events")
    def updateOrCreate(event: UserConnectionEvent)(user: Option[UserData]): UserData =
      user.fold {
        UserData(event.to, None, UserService.DefaultUserName, None, None, connection = event.status, conversation = Some(event.convId), connectionMessage = event.message, searchKey = SearchKey(UserService.DefaultUserName), connectionLastUpdated = event.lastUpdated,
          handle = None)
      } {
        _.copy(conversation = Some(event.convId)).updateConnectionStatus(event.status, Some(event.lastUpdated), event.message)
      }

    val lastEvents = events.groupBy(_.to).map { case (_, es) => es.maxBy(_.lastUpdated) }
    val fromSync: Set[UserId] = lastEvents.filter(_.localTime == Event.UnknownDateTime).map(_.to)(breakOut)

    verbose(s"lastEvents: $lastEvents, fromSync: $fromSync")

    Future.sequence(lastEvents.map { ev =>
      verbose(s"Updating users based on last events: $ev")
      usersStorage.updateOrCreate(ev.to, prev => updateOrCreate(ev)(Some(prev)), updateOrCreate(ev)(None)).map((_, ev.lastUpdated))
    }).map(users => (users.toSet, fromSync))
  }.flatMap { case (users, fromSync) =>
    verbose(s"syncing $users and fromSync: $fromSync")
    val toSync = users filter { case (user, _) => user.connection == ConnectionStatus.Accepted || user.connection == ConnectionStatus.PendingFromOther || user.connection == ConnectionStatus.PendingFromUser }
    sync.syncUsers(toSync.map(_._1.id)(breakOut)) flatMap { _ =>
      RichFuture.processSequential(users.grouped(16).toSeq) { us =>
        Future.traverse(us){ case (user, time) => updateConversationForConnection(user, selfUserId, fromSync = fromSync(user.id), time) }
      }
    }
  }

  def updateConversationForConnection(user: UserData, selfUserId: UserId, fromSync: Boolean, lastEventTime: RemoteInstant) = {
    verbose(s"updateConversationForConnection: $user")
    val convType = user.connection match {
      case ConnectionStatus.PendingFromUser | ConnectionStatus.Cancelled => ConversationType.WaitForConnection
      case ConnectionStatus.PendingFromOther | ConnectionStatus.Ignored => ConversationType.Incoming
      case _ => ConversationType.OneToOne
    }
    val hidden = user.connection == ConnectionStatus.Ignored || user.connection == ConnectionStatus.Blocked || user.connection == ConnectionStatus.Cancelled
    convs.getOneToOneConversation(user.id, selfUserId, user.conversation, convType) flatMap { conv =>
      members.add(conv.id, Set(selfUserId, user.id)).flatMap { _ =>
        convStorage.update(conv.id, c => c.copy(convType = convType, hidden = hidden, lastEventTime = c.lastEventTime max lastEventTime)) flatMap { updated =>
          messagesStorage.getLastMessage(conv.id) flatMap {
            case None if convType == ConversationType.Incoming =>
              addConnectRequestMessage(conv.id, user.id, selfUserId, user.connectionMessage.getOrElse(""), user.getDisplayName, fromSync = fromSync)
            case None if convType == ConversationType.OneToOne =>
              messages.addDeviceStartMessages(Seq(conv), selfUserId)
            case _ =>
              Future.successful(())
          } map { _ =>
            if (conv.hidden && !hidden) sync.syncConversations(Set(conv.id))
            updated.fold(conv)(_._2)
          }
        }
      }
    }
  }

  /**
   * Connects to user and creates one-to-one conversation if needed. Returns existing conversation if user is already connected.
   */
  def connectToUser(userId: UserId, message: String, name: String): Future[Option[ConversationData]] = {

    def sanitizedName = if (name.isEmpty) "_" else if (name.length >= 256) name.substring(0, 256) else name

    def connectIfUnconnected() = getOrCreateUser(userId) flatMap { user =>
      if (user.isConnected) {
        info(s"User already connected: $user")
        Future successful None
      } else {
        updateConnectionStatus(user.id, ConnectionStatus.PendingFromUser) flatMap {
          case Some(u) => sync.postConnection(userId, sanitizedName, message) map (_ => Some(u))
          case _ => Future.successful(None)
        }
      }
    }

    connectIfUnconnected().flatMap {
      case Some(_) =>
        convs.getOneToOneConversation(userId, selfUserId, convType = ConversationType.WaitForConnection) flatMap { conv =>
          verbose(s"connectToUser, conv: $conv")
          convStorage.update(conv.id, _.copy(convType = ConversationType.WaitForConnection, hidden = false)) flatMap { _ =>
            addConnectRequestMessage(conv.id, selfUserId, userId, message, name) map { _ => Some(conv) }
          }
        }
      case None => //already connected
        convs.convById(ConvId(userId.str))
    }
  }

  def acceptConnection(userId: UserId): Future[ConversationData] =
    updateConnectionStatus(userId, ConnectionStatus.Accepted) map {
      case Some(_) =>
        sync.postConnectionStatus(userId, ConnectionStatus.Accepted) map { syncId =>
          sync.syncConversations(Set(ConvId(userId.str)), Some(syncId))
        }
      case _ =>
    } flatMap { _ =>
      convs.getOneToOneConversation(userId, selfUserId, convType = ConversationType.OneToOne) flatMap { conv =>
        convs.updateConversation(conv.id, Some(ConversationType.OneToOne), hidden = Some(false)) flatMap { updated =>
          addMemberJoinMessage(conv.id, selfUserId, Set(selfUserId), firstMessage = true) map { _ =>
            updated.fold(conv)(_._2)
          }
        }
      }
    }

  def ignoreConnection(userId: UserId): Future[Option[UserData]] =
    for {
      user <- updateConnectionStatus(userId, ConnectionStatus.Ignored)
      _    <- user.fold(Future.successful({}))(_ => sync.postConnectionStatus(userId, ConnectionStatus.Ignored).map(_ => {}))
      _    <- convs.hideIncomingConversation(userId)
    } yield user

  def blockConnection(userId: UserId): Future[Option[UserData]] = {
    for {
      _    <- convs.setConversationHidden(ConvId(userId.str), hidden = true)
      user <- updateConnectionStatus(userId, ConnectionStatus.Blocked)
      _    <- user.fold(Future.successful({}))(_ => sync.postConnectionStatus(userId, ConnectionStatus.Blocked).map(_ => {}))
    } yield user
  }

  def unblockConnection(userId: UserId): Future[ConversationData] =
    for {
      user <- updateConnectionStatus(userId, ConnectionStatus.Accepted)
      _    <- user.fold(Future.successful({})) { _ =>
        for {
          syncId <- sync.postConnectionStatus(userId, ConnectionStatus.Accepted)
          _      <- sync.syncConversations(Set(ConvId(userId.str)), Some(syncId)) // sync conversation after syncing connection state (conv is locked on backend while connection is blocked) TODO: we could use some better api for that
        } yield {}
      }
      conv    <- convs.getOneToOneConversation(userId, selfUserId, convType = ConversationType.OneToOne)
      updated <- convs.updateConversation(conv.id, Some(ConversationType.OneToOne), hidden = Some(false)) map { _.fold(conv)(_._2) } // TODO: what about messages
    } yield updated


  def cancelConnection(userId: UserId): Future[Option[UserData]] = {
    updateUserData(userId, { user =>
      if (user.connection == ConnectionStatus.PendingFromUser) user.copy(connection = ConnectionStatus.Cancelled)
      else {
        warn(s"can't cancel connection for user in wrong state: $user")
        user
      }
    }) flatMap {
      case Some((prev, user)) if prev != user =>
        sync.postConnectionStatus(userId, ConnectionStatus.Cancelled)
        convs.setConversationHidden(ConvId(user.id.str), hidden = true) map { _ => Some(user) }
      case None => Future successful None
    }
  }
}
