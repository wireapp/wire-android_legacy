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
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.{MessagesContentUpdater, MessagesServiceImpl}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.TestGlobalPreferences
import com.waz.threading.Threading

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

  scenario("Create a reply text MessageData") {
    import Threading.Implicits.Background

    val service = getService

    val messageId = MessageId()
    val convId = ConvId()

    val msg = MessageData(messageId, convId, TEXT, selfUserId)
    val conv = ConversationData(convId, RConvId(), Some("conv"))

    (storage.getLastMessage _).expects(convId).once().returning(Future.successful(None))
    (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(conv)))
    (storage.addMessage _).expects(*).anyNumberOfTimes().onCall { msg: MessageData => Future.successful(msg) }

    var originalMsgId = MessageId()

    val reply = service.addTextMessage(convId, "aaa").flatMap { msg1 =>
      originalMsgId = msg1.id
      (storage.getMessage _).expects(msg1.id).once().returning(Future.successful(Some(msg1)))
      (storage.getLastMessage _).expects(convId).once().returning(Future.successful(Some(msg1)))

      service.addReplyMessage(msg1.id, "bbb").collect { case Some(msg2) => (msg2.contentString, msg2.replyTo) }
    }

    result(reply) shouldEqual ("bbb", Some(originalMsgId))
  }

  def getService = {
    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, prefs)
    new MessagesServiceImpl(selfUserId, None, storage, updater, edits, convs, network, members, users, sync)
  }
}
