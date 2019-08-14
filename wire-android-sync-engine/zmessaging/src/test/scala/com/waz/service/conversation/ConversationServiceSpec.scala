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

import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConversationData, _}
import com.waz.service._
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.PushService
import com.waz.service.tracking.TrackingService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{BgEventSource, EventStream, Signal, SourceSignal}
import org.threeten.bp.Instant

import scala.concurrent.Future

class ConversationServiceSpec extends AndroidFreeSpec {

  lazy val convosUpdaterMock  = mock[ConversationsContentUpdater]
  lazy val storageMock        = mock[ConversationStorage]
  lazy val messagesMock       = mock[MessagesService]
  lazy val msgStorageMock     = mock[MessagesStorage]
  lazy val membersMock        = mock[MembersStorage]
  lazy val usersMock          = mock[UserService]
  lazy val syncMock           = mock[SyncServiceHandle]
  lazy val pushMock           = mock[PushService]
  lazy val usersStorageMock   = mock[UsersStorage]
  lazy val convoStorageMock   = mock[ConversationStorage]
  lazy val convoContentMock   = mock[ConversationsContentUpdater]
  lazy val syncHandleMock     = mock[SyncServiceHandle]
  lazy val errorMock          = mock[ErrorsService]
  lazy val messageUpdaterMock = mock[MessagesContentUpdater]
  lazy val userPrefsMock      = mock[UserPreferences]
  lazy val syncRequestMock    = mock[SyncRequestService]
  lazy val eventSchedulerMock = mock[EventScheduler]
  lazy val trackingMock       = mock[TrackingService]
  lazy val convosClientMock   = mock[ConversationsClient]
  lazy val selectedConvoMock  = mock[SelectedConversationService]

  val selfUserId = UserId("user1")
  val convId = ConvId("conv_id1")
  val rConvId = RConvId("r_conv_id1")
  val convsInStorage = Signal[Map[ConvId, ConversationData]]()

  lazy val service = new ConversationsServiceImpl(
    None,
    selfUserId,
    pushMock,
    usersMock,
    usersStorageMock,
    membersMock,
    convoStorageMock,
    convoContentMock,
    syncHandleMock,
    errorMock,
    messagesMock,
    messageUpdaterMock,
    userPrefsMock,
    eventSchedulerMock,
    trackingMock,
    convosClientMock,
    selectedConvoMock,
    syncRequestMock
  )

  // mock mapping from remote to local conversation ID
  (convoStorageMock.getByRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq(convId)))

  // EXPECTS
  (usersStorageMock.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (usersStorageMock.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (convoStorageMock.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (convoStorageMock.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (membersMock.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (membersMock.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (membersMock.onDeleted _).expects().anyNumberOfTimes().returning(EventStream())
  (selectedConvoMock.selectedConversationId _).expects().anyNumberOfTimes().returning(Signal.empty)
  (pushMock.onHistoryLost _).expects().anyNumberOfTimes().returning(new SourceSignal[Instant] with BgEventSource)
  (errorMock.onErrorDismissed _).expects(*).anyNumberOfTimes().returning(CancellableFuture.successful(()))


  scenario("Archive conversation when the user leaves it remotely") {

    // GIVEN
    val convData = ConversationData(
      convId,
      rConvId,
      Some(Name("name")),
      UserId(),
      ConversationType.Group,
      lastEventTime = RemoteInstant.Epoch,
      archived = false,
      muted = MuteSet.AllMuted
    )

    val events = Seq(
      MemberLeaveEvent(rConvId, RemoteInstant.ofEpochSec(10000), selfUserId, Seq(selfUserId))
    )

    (convoContentMock.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
      Future.successful(Some(convData))
    }
    (membersMock.remove (_: ConvId, _: Iterable[UserId])).expects(*, *)
      .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
    (convoContentMock.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

    // EXPECT
    (convoContentMock.updateConversationState _).expects(where { (id, state) =>
      id.equals(convId) && state.archived.getOrElse(false)
    }).once()

    // WHEN
    result(service.convStateEventProcessingStage.apply(rConvId, events))
  }

  scenario("Does not archive conversation when the user is removed by someone else") {

    // GIVEN
    val convData = ConversationData(
      convId,
      rConvId,
      Some(Name("name")),
      UserId(),
      ConversationType.Group,
      lastEventTime = RemoteInstant.Epoch,
      archived = false,
      muted = MuteSet.AllMuted
    )

    val events = Seq(
      MemberLeaveEvent(rConvId, RemoteInstant.ofEpochSec(10000), UserId(), Seq(selfUserId))
    )

    (convoContentMock.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
      Future.successful(Some(convData))
    }
    (membersMock.remove (_: ConvId, _: Iterable[UserId])).expects(*, *)
      .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
    (convoContentMock.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

    // EXPECT
    (convoContentMock.updateConversationState _).expects(*, *).never()

    // WHEN
    result(service.convStateEventProcessingStage.apply(rConvId, events))
  }

  scenario("Does not archive conversation when the user is not the one being removed") {

    // GIVEN
    val convData = ConversationData(
      convId,
      rConvId,
      Some(Name("name")),
      UserId(),
      ConversationType.Group,
      lastEventTime = RemoteInstant.Epoch,
      archived = false,
      muted = MuteSet.AllMuted
    )

    val events = Seq(
      MemberLeaveEvent(rConvId, RemoteInstant.ofEpochSec(10000), selfUserId, Seq(UserId()))
    )

    (convoContentMock.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
      Future.successful(Some(convData))
    }
    (membersMock.remove (_: ConvId, _: Iterable[UserId])).expects(*, *)
      .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
    (convoContentMock.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

    // EXPECT
    (convoContentMock.updateConversationState _).expects(*, *).never()

    // WHEN
    result(service.convStateEventProcessingStage.apply(rConvId, events))
  }
}
