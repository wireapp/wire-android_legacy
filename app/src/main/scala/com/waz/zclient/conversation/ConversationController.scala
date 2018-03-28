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

import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api
import com.waz.api.MessageContent.Asset.ErrorHandler
import com.waz.api.impl.{AssetForUpload, ImageAsset}
import com.waz.api.{EphemeralExpiration, IConversation}
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage}
import com.waz.model._
import com.waz.model.otr.Client
import com.waz.service.UserService
import com.waz.service.conversation.{ConversationsListStateService, ConversationsService, ConversationsUiService}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceStream}
import com.waz.utils.{Serialized, returning, _}
import com.waz.zclient.conversation.ConversationController.ConversationChange
import com.waz.zclient.core.stores.IStoreFactory
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.utils.Callback
import com.waz.zclient.{Injectable, Injector, R}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

class ConversationController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {
  private implicit val dispatcher = new SerialDispatchQueue(name = "ConversationController")

  private lazy val convsStats        = inject[Signal[ConversationsListStateService]]
  private lazy val convsUi           = inject[Signal[ConversationsUiService]]
  private lazy val conversations     = inject[Signal[ConversationsService]]
  private lazy val convsStorage      = inject[Signal[ConversationStorage]]
  private lazy val membersStorage    = inject[Signal[MembersStorage]]
  private lazy val userService       = inject[Signal[UserService]]
  private lazy val otrClientsStorage = inject[Signal[OtrClientsStorage]]

  private var lastConvId = Option.empty[ConvId]

  val currentConvIdOpt: Signal[Option[ConvId]]           = convsStats.flatMap(_.selectedConversationId)
  val currentConvId:    Signal[ConvId]                   = currentConvIdOpt.collect { case Some(convId) => convId }
  val currentConvOpt:   Signal[Option[ConversationData]] = currentConvId.flatMap(conversationData) // updates on every change of the conversation data, not only on switching
  val currentConv:      Signal[ConversationData]         = currentConvOpt.collect { case Some(conv) => conv }

  val convChanged: SourceStream[ConversationChange] = EventStream[ConversationChange]()

  def conversationData(convId: ConvId): Signal[Option[ConversationData]] = convsStorage.flatMap(_.optSignal(convId))

  val currentConvIsGroup: Signal[Boolean] = currentConvId.flatMap(id => Signal.future(isGroup(id)))
  val currentConvIsTeamOnly: Signal[Boolean] = currentConv.map(_.isTeamOnly)

  lazy val currentConvMembers = for {
    membersStorage <- membersStorage
    selfUserId     <- inject[Signal[UserId]]
    conv           <- currentConvId
    members        <- membersStorage.activeMembers(conv)
  } yield members.filter(_ != selfUserId)

  currentConvId { convId =>
    conversations(_.forceNameUpdate(convId))
    if (!lastConvId.contains(convId)) { // to only catch changes coming from SE (we assume it's an account switch)
      verbose(s"a conversation change bypassed selectConv: last = $lastConvId, current = $convId")
      convChanged ! ConversationChange(from = lastConvId, to = Option(convId), requester = ConversationChangeRequester.ACCOUNT_CHANGE)
      lastConvId = Option(convId)
    }
  }

  // this should be the only UI entry point to change conv in SE
  def selectConv(convId: Option[ConvId], requester: ConversationChangeRequester): Future[Unit] = convId match {
    case None => Future.successful({})
    case Some(id) =>
      val oldId  = lastConvId
      lastConvId = convId
      for {
        convsStats <- convsStats.head
        convsUi    <- convsUi.head
        _          <- convsUi.setConversationArchived(id, archived = false)
        _          <- convsStats.selectConversation(convId)
      } yield { // catches changes coming from UI
        verbose(s"changing conversation from $oldId to $convId, requester: $requester")
        convChanged ! ConversationChange(from = oldId, to = convId, requester = requester)
      }
  }

  def selectConv(id: ConvId, requester: ConversationChangeRequester): Future[Unit] = selectConv(Some(id), requester)

  def loadConv(convId: ConvId): Future[Option[ConversationData]] = convsStorage.head.flatMap(_.get(convId))

  def isGroup(id: ConvId): Future[Boolean] =
    conversations.head.flatMap(_.isGroupConversation(id))

  def hasOtherParticipants(conv: ConvId): Future[Boolean] =
    for {
      membersStorage <- membersStorage.head
      ms             <- membersStorage.getActiveUsers(conv)
    } yield ms.size > 1

  def setEphemeralExpiration(expiration: EphemeralExpiration): Future[Unit] = for {
    convsUi <- convsUi.head
    id      <- currentConvId.head
    _       <- convsUi.setEphemeral(id, expiration)
  } yield ()

  def loadMembers(convId: ConvId): Future[Seq[UserData]] = for {
    membersStorage <- membersStorage.head
    userService    <- userService.head
    userIds        <- membersStorage.activeMembers(convId).head // TODO: maybe switch to ConversationsMembersSignal
    users          <- userService.getUsers(userIds.toSeq)
  } yield users

  def loadClients(userId: UserId): Future[Seq[Client]] = otrClientsStorage.head.flatMap(_.getClients(userId)) // TODO: move to SE maybe?

  def sendMessage(audioAsset: AssetForUpload, errorHandler: ErrorHandler): Future[Unit] = currentConvId.head.map { convId => sendMessage(convId, audioAsset, errorHandler) }
  def sendMessage(convId: ConvId, audioAsset: AssetForUpload, errorHandler: ErrorHandler): Future[Unit] = convsUi.head.map { _.sendMessage(convId, audioAsset, errorHandler) }
  def sendMessage(text: String): Future[Unit] = for {
    convsUi <- convsUi.head
    convId  <- currentConvId.head
  } yield convsUi.sendMessage(convId, text)
  def sendMessage(imageAsset: com.waz.api.ImageAsset): Future[Unit] = imageAsset match { // TODO: remove when not used anymore
    case a: com.waz.api.impl.ImageAsset => currentConvId.head.map { convId => sendMessage(convId, a) }
    case _                              => Future.successful({})
  }
  def sendMessage(imageAsset: ImageAsset): Future[Unit] = currentConvId.head.map { convId => sendMessage(convId, imageAsset) }
  def sendMessage(convId: ConvId, imageAsset: ImageAsset): Future[Unit] = convsUi.head.map { _.sendMessage(convId, imageAsset) }
  def sendMessage(location: api.MessageContent.Location): Future[Unit] = for {
    convsUi <- convsUi.head
    convId  <- currentConvId.head
  } yield convsUi.sendMessage(convId, location)

  def setCurrentConvName(name: String): Future[Unit] = for {
    convsUi <- convsUi.head
    convId  <- currentConvId.head
  } yield convsUi.setConversationName(convId, name)

  def addMembers(id: ConvId, users: Set[UserId]): Future[Unit] = convsUi.head.map { _.addConversationMembers(id, users) }

  def removeMember(user: UserId): Future[Unit] = for {
    convsUi <- convsUi.head
    id      <- currentConvId.head
  } yield convsUi.removeConversationMember(id, user)

  def leave(convId: ConvId): CancellableFuture[Option[ConversationData]] =
    returning (Serialized("Conversations", convId)(CancellableFuture.lift(convsUi.head.flatMap(_.leaveConversation(convId))))) { _ =>
      currentConvId.head.map { id => if (id == convId) setCurrentConversationToNext(ConversationChangeRequester.LEAVE_CONVERSATION) }
    }

  def setCurrentConversationToNext(requester: ConversationChangeRequester): Future[Unit] =
    currentConvId.head
      .map { id => inject[IStoreFactory].conversationStore.nextConversation(id) }
      .map { convId => selectConv(convId, requester) }

  def archive(convId: ConvId, archive: Boolean): Unit = {
    convsUi.head.map { _.setConversationArchived(convId, archive) }
    currentConvId.head.map { id => if (id == convId) CancellableFuture.delayed(ConversationController.ARCHIVE_DELAY){
      if (!archive) selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST_UNARCHIVED_CONVERSATION)
      else setCurrentConversationToNext(ConversationChangeRequester.ARCHIVED_RESULT)
    }}
  }

  def setMuted(id: ConvId, muted: Boolean): Future[Unit] = convsUi.head.map { _.setConversationMuted(id, muted) }

  def delete(id: ConvId, alsoLeave: Boolean): CancellableFuture[Option[ConversationData]] = {
    def clear(id: ConvId) = Serialized("Conversations", id) { CancellableFuture.lift( convsUi.head.flatMap { _.clearConversation(id) } ) }

    if (alsoLeave) leave(id).flatMap(_ => clear(id)) else clear(id)
  }

  def createGuestRoom(): Future[ConversationData] = createGroupConversation(Some(context.getString(R.string.guest_room_name)), Set(), false)

  def createGroupConversation(name: Option[String], users: Set[UserId], teamOnly: Boolean): Future[ConversationData] =
    convsUi.head.flatMap { _.createGroupConversation(name, users, teamOnly).map(_._1) }

  // TODO: remove when not used anymore
  def iConv(id: ConvId): IConversation = inject[IStoreFactory].conversationStore.getConversation(id.str)

  def withCurrentConvName(callback: Callback[String]): Unit =
    currentConv.map(_.displayName).head.foreach(callback.callback)(Threading.Ui)

  def getCurrentConvId: ConvId = currentConvId.currentValue.orNull
  def withConvLoaded(convId: ConvId, callback: Callback[ConversationData]): Unit = loadConv(convId).foreach {
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
      verbose(s"toggleFocused($id)")
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

object ConversationController {
  val ARCHIVE_DELAY = 500.millis

  case class ConversationChange(from: Option[ConvId], to: Option[ConvId], requester: ConversationChangeRequester) {
    def toConvId: ConvId = to.orNull // TODO: remove when not used anymore
    lazy val noChange: Boolean = from == to
  }

  val emptyImageAsset: com.waz.api.ImageAsset = ImageAsset.Empty.asInstanceOf[com.waz.api.ImageAsset]

  def getOtherParticipantForOneToOneConv(conv: ConversationData): UserId = {
    if (conv != ConversationData.Empty &&
        conv.convType != IConversation.Type.ONE_TO_ONE &&
        conv.convType != IConversation.Type.WAIT_FOR_CONNECTION &&
        conv.convType != IConversation.Type.INCOMING_CONNECTION)
      error(s"unexpected call, most likely UI error", new UnsupportedOperationException(s"Can't get other participant for: ${conv.convType} conversation"))
    UserId(conv.id.str) // one-to-one conversation has the same id as the other user, so we can access it directly
  }

}
