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

import com.waz.api.Message
import com.waz.api.Message.Status
import com.waz.api.Message.Type._
import com.waz.content._
import com.waz.model._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.{MessagesContentUpdater, MessagesServiceImpl}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.TestGlobalPreferences

import scala.concurrent.Future
import scala.concurrent.duration._

class MessagesServiceSpec extends AndroidFreeSpec {

  val selfUserId =    UserId("self")
  val storage =       mock[MessagesStorage]
  val convsStorage =  mock[ConversationStorage]
  val edits =         mock[EditHistoryStorage]
  val convs =         mock[ConversationsContentUpdater]
  val network =       mock[NetworkModeService]
  val sync =          mock[SyncServiceHandle]
  val deletions =     mock[MsgDeletionStorage]
  val members =       mock[MembersStorage]
  val users =         mock[UsersStorage]
  val prefs          = new TestGlobalPreferences()

  scenario("Add local memberJoinEvent with no previous member change events") {

    val service = getService

    val convId = ConvId("conv")
    val instigator = UserId("instigator")

    val usersAdded = Set(
      UserId("user1"),
      UserId("user2")
    )

    //not the first message in the conversation
    val lastMsg = MessageData(MessageId(), convId, TEXT, instigator, time = RemoteInstant(clock.instant()))

    clock.advance(5.seconds)
    val newMsg = MessageData(MessageId(), convId, Message.Type.MEMBER_JOIN, instigator, members = usersAdded, state = Status.PENDING, time = RemoteInstant(clock.instant()), localTime = LocalInstant(clock.instant()))

    //no previous member join events
    (storage.lastLocalMessage _).expects(convId, MEMBER_LEAVE).once().returning(Future.successful(None))
    (storage.lastLocalMessage _).expects(convId, MEMBER_JOIN).once().returning(Future.successful(None))

    (storage.getLastMessage _).expects(convId).once().returning(Future.successful(Some(lastMsg)))

    (storage.addMessage _).expects(*).once().onCall { msg: MessageData => Future.successful(msg) }

    result(service.addMemberJoinMessage(convId, instigator, usersAdded)).map(_.copy(id = newMsg.id)) shouldEqual Some(newMsg)
  }

  def getService = {
    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, prefs)
    new MessagesServiceImpl(selfUserId, None, storage, updater, edits, convs, network, members, users, sync)
  }
}
