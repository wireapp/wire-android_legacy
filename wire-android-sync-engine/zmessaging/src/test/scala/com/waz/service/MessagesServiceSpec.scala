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
import com.waz.log.BasicLogging
import com.waz.model.ButtonData.{ButtonError, ButtonNotClicked, ButtonWaiting}
import com.waz.model.GenericContent.{MsgEdit, Text}
import com.waz.model._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.{MessagesContentUpdater, MessagesServiceImpl}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.TestGlobalPreferences
import com.waz.threading.Threading
import com.waz.utils.crypto.ReplyHashing
import com.waz.utils.events.{EventStream, Signal}

import scala.concurrent.{Await, Future}
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
  val buttons =       mock[ButtonsStorage]
  val users =         mock[UsersStorage]
  val replyHashing =  mock[ReplyHashing]
  lazy val prefs =         new TestGlobalPreferences()

  def getService = {
    val updater = new MessagesContentUpdater(storage, convsStorage, deletions, buttons, prefs)
    new MessagesServiceImpl(selfUserId, None, replyHashing, storage, updater, edits, convs, network, members, users, buttons, sync)
  }

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

  scenario("Create a quote") {
    import Threading.Implicits.Background

    val service = getService

    val messageId = MessageId()
    val convId = ConvId()

    val msg = MessageData(messageId, convId, TEXT, selfUserId)
    val conv = ConversationData(convId, RConvId(), Some("conv"))

    (storage.getLastMessage _).expects(convId).once().returning(Future.successful(None))
    (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(conv)))
    (storage.addMessage _).expects(*).anyNumberOfTimes().onCall { msg: MessageData => Future.successful(msg) }
    (replyHashing.hashMessage _).expects(*).once().onCall { _: MessageData => Future.successful(Sha256("sbc")) }

    var originalMsgId = MessageId()

    val quote = service.addTextMessage(convId, "aaa", expectsReadReceipt = AllDisabled).flatMap { msg1 =>
      originalMsgId = msg1.id
      (storage.getMessage _).expects(msg1.id).once().returning(Future.successful(Some(msg1)))
      (storage.getLastMessage _).expects(convId).once().returning(Future.successful(Some(msg1)))

      service.addReplyMessage(msg1.id, "bbb", expectsReadReceipt = AllDisabled).collect { case Some(msg2) => (msg2.contentString, msg2.quote.map(_.message)) }
    }

    result(quote) shouldEqual ("bbb", Some(originalMsgId))
  }

  scenario("Recall a message") {
    val service = getService

    val now = RemoteInstant(clock.instant())
    val convId = ConvId()
    val messageId = MessageId()

    val msg = MessageData(messageId, convId, TEXT, selfUserId)
    val deletion = MsgDeletion(messageId, now.instant)

    (storage.getMessage _).expects(messageId).returning(Future.successful(Some(msg)))
    (deletions.insertAll _).expects(Seq(deletion)).returning(Future.successful(Set(deletion)))
    (storage.removeAll _).expects(Seq(messageId)).returning(Future.successful(()))
    (buttons.deleteAllForMessage _).expects(messageId).atLeastOnce().returning(Future.successful(()))

    val recalledMessage = Await.result(service.recallMessage(convId, messageId, selfUserId, time = now), 1.second)

    recalledMessage.map(_.msgType) should be (Some(Message.Type.RECALLED))
  }

  scenario("Recall a message from the wrong conversations") {
    val service = getService

    val now = RemoteInstant(clock.instant())
    val convId = ConvId("123")
    val convId2 = ConvId("456")
    val messageId = MessageId()

    val msg = MessageData(messageId, convId, TEXT, selfUserId)

    (storage.getMessage _).expects(messageId).returning(Future.successful(Some(msg)))

    val recalledMessage = Await.result(service.recallMessage(convId2, messageId, selfUserId, time = now), 1.second)

    recalledMessage should be (None)
  }

  scenario("Recall ephemeral message") {
    val service = getService

    val now = RemoteInstant(clock.instant())
    val convId = ConvId()
    val messageId = MessageId()

    val msg = MessageData(messageId, convId, TEXT, selfUserId, ephemeral = Some(5.seconds))
    val deletion = MsgDeletion(messageId, now.instant)

    (storage.getMessage _).expects(messageId).returning(Future.successful(Some(msg)))
    (deletions.insertAll _).expects(Seq(deletion)).returning(Future.successful(Set(deletion)))
    (storage.removeAll _).expects(Seq(messageId)).returning(Future.successful(()))
    (buttons.deleteAllForMessage _).expects(messageId).returning(Future.successful(()))

    val recalledMessage = Await.result(service.recallMessage(convId, messageId, selfUserId, time = now), 1.second)

    recalledMessage.map(_.msgType) should be (Some(Message.Type.RECALLED))
  }

  scenario("Recall another user's message should fail") {
    val service = getService

    val now = RemoteInstant(clock.instant())
    val convId = ConvId()
    val messageId = MessageId()
    val userId = UserId()

    val msg = MessageData(messageId, convId, TEXT, selfUserId)

    (storage.getMessage _).expects(messageId).returning(Future.successful(Some(msg)))

    val recalledMessage = Await.result(service.recallMessage(convId, messageId, userId, time = now), 1.second)

    recalledMessage should be (None)
  }

  scenario("Edit a message") {
    val service = getService

    val now = RemoteInstant(clock.instant())
    val convId = ConvId()
    val messageId = MessageId()
    val messageId2 = MessageId()

    val msg = MessageData(messageId, convId, TEXT, selfUserId)
    val edit = GenericMessage(Uid(messageId2.str), MsgEdit(messageId, Text("stuff")))
    val editHistory = EditHistory(messageId, messageId2, now)

    (storage.getMessage _).expects(messageId).returning(Future.successful(Some(msg)))
    (buttons.deleteAllForMessage _).expects(messageId).returning(Future.successful(Nil))
    (edits.insert _).expects(editHistory).returning(Future.successful(editHistory))
    (deletions.getAll _).expects(Seq(messageId2)).returning(Future.successful(Seq(None)))
    (storage.updateOrCreate _).expects(*, *, *).onCall { (_, updater, _) => Future.successful(updater(msg)) }
    (storage.findQuotesOf _).expects(*).returning(Future.successful(Seq()))
    (storage.updateAll2 _).expects(*, *).onCall { (keys, updater) =>
      Future.successful(Seq((msg, updater(msg))))
    }
    (deletions.insertAll _).expects(*).onCall { all: Traversable[MsgDeletion] =>
      Future.successful(all.toSet)
    }
    (storage.removeAll _).expects(*).returning(Future.successful(()))

    val editedMessage = Await.result(service.applyMessageEdit(convId, selfUserId, time = now, edit), 1.second)

    editedMessage.map(_.contentString) should be (Some("stuff"))
  }

  scenario("Add new rename conversation message") {
    // Given
    val service = getService
    val convId = ConvId()
    val fromUserId = UserId()
    val (oldName, newName) = (Name("Bleep!"), Name("Quack!"))

    // There is a conversation
    val conv = ConversationData(convId, RConvId(), Some(oldName))
    (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(conv)))

    // There is a normal message but not a rename message
    val lastMsg = MessageData(MessageId(), convId, TEXT, fromUserId, time = RemoteInstant(clock.instant()))
    (storage.getLastMessage _).expects(convId).once().returning(Future.successful(Some(lastMsg)))
    (storage.lastLocalMessage _).expects(convId, RENAME).once().returning(Future.successful(None))
    (storage.addMessage _).expects(*).once().onCall { msg: MessageData => Future.successful(msg)}

    // When
    val actual = result(service.addRenameConversationMessage(convId, fromUserId, newName)) map { msg =>
      (msg.convId, msg.msgType, msg.name)
    }

    // Then
    actual shouldBe Some((convId, RENAME, Some(newName)))
  }

  scenario("Update rename conversation message") {
    // Given
    val service = getService
    val convId = ConvId()
    val fromUserId = UserId()
    val (oldName, newName) = (Name("Bleep!"), Name("Quack!"))
    val msgId = MessageId()

    // There is a conversation
    val conv = ConversationData(convId, RConvId(), Some(oldName))
    (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(conv)))

    // There is already a rename message
    val lastMsg = MessageData(msgId, convId, RENAME, fromUserId, name = Some(oldName), time = RemoteInstant(clock.instant()))
    (storage.lastLocalMessage _).expects(convId, RENAME).once().returning(Future.successful(Some(lastMsg)))

    // Update this message with the new name
    (storage.update _).expects(msgId, *).once().returning(Future.successful(Some((lastMsg, lastMsg.copy(name = Some(newName))))))

    // When
    val actual = result(service.addRenameConversationMessage(convId, fromUserId, newName)) map { msg =>
      (msg.id, msg.convId, msg.msgType, msg.name)
    }

    // Then
    actual shouldBe Some((msgId, convId, RENAME, Some(newName)))
  }

  scenario("Get sorted buttons") {
    implicit val logTag = BasicLogging.LogTag("MessagesServiceSpec")

    val msgId = MessageId()
    val button1Id = ButtonId()
    val title1 = "Title 1"
    val button0Id = ButtonId()
    val title0 = "Title 0"

    val expectedButtons = Seq(
      ButtonData(msgId, button1Id, title1, 1),
      ButtonData(msgId, button0Id, title0, 0)
    )

    val onChanged = EventStream[Seq[ButtonData]]()

    (buttons.onChanged _).expects().anyNumberOfTimes().returning(onChanged)
    (buttons.onDeleted _).expects().anyNumberOfTimes().returning(EventStream())

    (buttons.findByMessage _).expects(msgId).anyNumberOfTimes().onCall { _: MessageId => Future.successful(expectedButtons) }

    val service = getService
    val res = result(service.buttonsForMessage(msgId).head)

    res.size shouldEqual 2

    res.head shouldEqual ButtonData(msgId, button0Id, title0, 0, ButtonNotClicked)
    res(1)   shouldEqual ButtonData(msgId, button1Id, title1, 1, ButtonNotClicked)
  }

  scenario("Include the sender id in the button action") {
    import Threading.Implicits.Background
    implicit val logTag = BasicLogging.LogTag("MessagesServiceSpec")

    val messageId = MessageId()
    val buttonId = ButtonId()
    val convId = ConvId()
    val senderId = UserId()

    val message = MessageData(messageId, msgType = Message.Type.COMPOSITE, convId = convId, userId = senderId)
    val button = Signal(ButtonData(messageId, buttonId, "button", 0, ButtonNotClicked))

    (storage.get _).expects(messageId).atLeastOnce().returning(Future.successful(Some(message)))
    (members.isActiveMember _).expects(convId, senderId).anyNumberOfTimes().returning(Future.successful(true))
    (buttons.update _).expects((messageId, buttonId), *).anyNumberOfTimes().onCall { (_, updater) =>
      button.head.map { b =>
        val newB = updater(b)
        button ! newB
        Option((b, newB))
      }
    }
    (sync.postButtonAction _).expects(messageId, buttonId, senderId).atLeastOnce().returning(Future.successful(SyncId()))

    val service = getService

    result(service.clickButton(messageId, buttonId))
    val res = result(button.filter(_.state == ButtonWaiting).head)

    res.messageId shouldEqual messageId
    res.buttonId shouldEqual buttonId
  }

  scenario("Set button error if the poll's sender is not in the conversation") {
    import Threading.Implicits.Background
    implicit val logTag = BasicLogging.LogTag("MessagesServiceSpec")

    val messageId = MessageId()
    val buttonId = ButtonId()
    val convId = ConvId()
    val senderId = UserId()

    val message = MessageData(messageId, msgType = Message.Type.COMPOSITE, convId = convId, userId = senderId)
    val button = Signal(ButtonData(messageId, buttonId, "button", 0, ButtonNotClicked))

    (storage.get _).expects(messageId).atLeastOnce().returning(Future.successful(Some(message)))
    (members.isActiveMember _).expects(convId, senderId).anyNumberOfTimes().returning(Future.successful(false))
    (buttons.update _).expects((messageId, buttonId), *).anyNumberOfTimes().onCall { (_, updater) =>
      button.head.map { b =>
        val newB = updater(b)
        button ! newB
        Option((b, newB))
      }
    }

    val service = getService

    result(service.clickButton(messageId, buttonId))
    val res = result(button.filter(_.state == ButtonError).head)

    res.messageId shouldEqual messageId
    res.buttonId shouldEqual buttonId
  }
}
