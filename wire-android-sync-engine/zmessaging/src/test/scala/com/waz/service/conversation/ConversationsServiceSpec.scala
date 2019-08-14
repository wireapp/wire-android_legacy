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
import com.waz.api.Message
import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.testutils.TestGlobalPreferences
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{BgEventSource, EventStream, Signal, SourceSignal}
import org.threeten.bp.Instant

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ConversationsServiceSpec extends AndroidFreeSpec {

  val selfUserId = UserId()
  val push =        mock[PushService]
  val users =           mock[UserService]
  val usersStorage =    mock[UsersStorage]
  val membersStorage =  mock[MembersStorage]
  val convsStorage =    mock[ConversationStorage]
  val content =         mock[ConversationsContentUpdater]
  val sync =            mock[SyncServiceHandle]
  val errors =          mock[ErrorsService]
  val messages =        mock[MessagesService]
  val userPrefs =       mock[UserPreferences]
  val requests =        mock[SyncRequestService]
  val client =          mock[ConversationsClient]
  val selectedConv =    mock[SelectedConversationService]
  val messagesStorage = mock[MessagesStorage]
  val deletions =       mock[MsgDeletionStorage]

  val prefs = new TestGlobalPreferences()

  private def getService(teamId: Option[TeamId] = None): ConversationsServiceImpl = {
    val msgContent = new MessagesContentUpdater(messagesStorage, convsStorage, deletions, prefs)
    new ConversationsServiceImpl(teamId, selfUserId, push, users, usersStorage, membersStorage, convsStorage, content, sync, errors, messages, msgContent, userPrefs, null, tracking, client, selectedConv, requests)
  }

  scenario("updateConversationsWithDeviceStartMessage happy path") {

    val rConvId = RConvId("conv")
    val from = UserId("User1")
    val convId = ConvId(rConvId.str)
    val response = ConversationResponse(
      rConvId, Some(Name("conv")), from, ConversationType.Group, None, MuteSet.AllAllowed, RemoteInstant.Epoch, archived = false, RemoteInstant.Epoch, Set.empty, None, None, None, Set(account1Id, from), None
    )
    val userStorageOnAdded    = EventStream[Seq[UserData]]()
    val userStorageOnUpdated  = EventStream[Seq[(UserData, UserData)]]()
    val conversationStorageOnAdded = EventStream[Seq[ConversationData]]()
    val conversationStorageOnUpdated = EventStream[Seq[(ConversationData, ConversationData)]]()
    val membersStorageOnAdded = EventStream[Seq[ConversationMemberData]]()
    val membersStorageOnDeleted = EventStream[Seq[(UserId, ConvId)]]()

    (usersStorage.onAdded _).expects().once().returning(userStorageOnAdded)
    (usersStorage.onUpdated _).expects().once().returning(userStorageOnUpdated)
    (convsStorage.onAdded _).expects().once().returning(conversationStorageOnAdded)
    (convsStorage.onUpdated _).expects().once().returning(conversationStorageOnUpdated)
    (membersStorage.onAdded _).expects().once().returning(membersStorageOnAdded)
    (membersStorage.onDeleted _).expects().once().returning(membersStorageOnDeleted)

    (selectedConv.selectedConversationId _).expects().returning(Signal.const(Option.empty[ConvId]))
    (push.onHistoryLost _).expects().returning(new SourceSignal[Instant] with BgEventSource)
    (errors.onErrorDismissed _).expects(*).returning(CancellableFuture.successful(()))


    (convsStorage.apply[Seq[(ConvId, ConversationResponse)]] _).expects(*).onCall { x: (Map[ConvId, ConversationData] => Seq[(ConvId, ConversationResponse)]) =>
      Future.successful(x(Map[ConvId, ConversationData]()))
    }

    (convsStorage.updateOrCreateAll _).expects(*).onCall { x: Map[ConvId, Option[ConversationData] => ConversationData ] =>
      Future.successful(x.values.map(_(None)).toSet)
    }

    (content.convsByRemoteId _).expects(*).returning(Future.successful(Map()))

    (membersStorage.setAll _).expects(*).returning(Future.successful(()))

    (users.syncIfNeeded _).expects(*, *).returning(Future.successful(Option(SyncId())))

    (messages.addDeviceStartMessages _).expects(*, *).onCall{ (convs: Seq[ConversationData], selfUserId: UserId) =>
      convs.headOption.flatMap(_.name) should be (Some(Name("conv")))
      convs.headOption.map(_.muted) should be (Some(MuteSet.AllAllowed))
      convs.headOption.map(_.creator) should be (Some(from))
      convs.headOption.map(_.remoteId) should be (Some(rConvId))
      convs.headOption.map(_.id) should be (Some(convId))
      Future.successful(Set(MessageData(MessageId(), convId, Message.Type.STARTED_USING_DEVICE, selfUserId, time = RemoteInstant.Epoch)))
    }

    Await.result(getService().updateConversationsWithDeviceStartMessage(Seq(response)), 1.second)
  }
}
