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

//import java.util.Date

//import android.database.sqlite.SQLiteDatabase
//import com.waz.RobolectricUtils
//import com.waz.api.Message
import com.waz.content.{ConversationStorage, MembersStorage}
import com.waz.sync.client.ConversationsClient
//import com.waz.content.GlobalDatabase
//import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.messages.MessagesService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
//import com.waz.sync.client.ConversationsClient.ConversationResponse
//import com.waz.testutils.{EmptySyncService, MockZMessaging}
//import com.waz.threading.Threading
//import org.robolectric.Robolectric
//import org.robolectric.shadows.ShadowLog
//import org.scalatest._
//import org.threeten.bp.Instant
//
//import scala.concurrent.Await
//import scala.concurrent.duration._

class PrivateConversationSpec extends AndroidFreeSpec {

  val self         = UserId()
  val members      = mock[MembersStorage]
  val convsContent = mock[ConversationsContentUpdater]
  val convsStorage = mock[ConversationStorage]
  val sync         = mock[SyncServiceHandle]
  val messages     = mock[MessagesService]


//  implicit lazy val dispatcher = Threading.Background
//  lazy val globalStorage = new GlobalDatabase(Robolectric.application)
//
//  lazy val selfUser = UserData("self user")
//  lazy val user1 = UserData("user 1")
//  lazy val user2 = UserData("user 1")
//  lazy val user3 = UserData("user 3")
//
//  implicit def db: SQLiteDatabase = service.db.dbHelper.getWritableDatabase
//
//  var service: MockZMessaging = _
//  var convSync = None: Option[ConvId]
//
//  def storage = service.db
//
//  before {
//    convSync = None
//
//    service = new MockZMessaging(selfUserId = selfUser.id) {
//      override lazy val sync = new EmptySyncService {
//        override def postConversation(id: ConvId, us: Seq[UserId], n: Option[String], t: Option[TeamId]) = {
//          convSync = Some(id)
//          super.postConversation(id, us, n, t)
//        }
//      }
//    }
//
//    service.insertUsers(Seq(selfUser, user1, user2, user3))
//  }
//
//  after {
//    ShadowLog.stream = null
//    awaitStorage()
//    Await.result(storage.close(), 10.seconds)
//    Robolectric.application.getDatabasePath(storage.dbHelper.getDatabaseName).delete()
//  }
//
//  implicit val timeout = 5.seconds
//
//  def awaitStorage() = Await.result(storage { _ => }, 25.seconds) // wait for storage operations to complete
//
//  def listMessages(conv: ConvId) = service.listMessages(conv)
//  def lastMessage(conv: ConvId) = service.lastMessage(conv)
//  def lastLocalMessage(conv: ConvId, tpe: Message.Type) = Await.result(service.messagesStorage.lastLocalMessage(conv, tpe), timeout)
//
//  def conversationResponse(convId: ConvId = ConvId(), remoteId: RConvId = RConvId()) = {
//    ConversationResponse(ConversationData(convId, remoteId, None, selfUser.id, ConversationType.Group), Seq(ConversationMemberData(user1.id, convId), ConversationMemberData(user2.id, convId)))
//  }
//
//  def createConversationEvent(remoteId: RConvId = RConvId()) = {
//    val response = conversationResponse(ConvId(), remoteId)
//    CreateConversationEvent(remoteId, new Date, selfUser.id, response)
//  }
//
//  def getConv(id: ConvId) = Await.result(service.convsContent.convById(id), timeout)
//  def getConv(id: RConvId) = Await.result(service.convsContent.convByRemoteId(id), timeout)
//  def listConvs = Await.result(service.convsStorage.list, timeout)
//
//  def listActiveMembers(conv: ConvId) = Await.result(service.membersStorage.getActiveUsers(conv), timeout).toList

  scenario("create new conversation for users with name") {
//    val convId = ConvId()
//    val conv = Await.result(service.convsUi.createGroupConversation(convId, Seq(user1.id, user2.id)), timeout)
//
//    conv.id shouldEqual convId
//    conv.creator shouldEqual selfUser.id
//
//    getConv(convId).map(_.copy(searchKey = None)) shouldEqual Some(conv)
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//
//    convSync shouldEqual Some(conv.id)
//
//    lastMessage(conv.id) should be('defined)
//    val msg = lastLocalMessage(conv.id, Message.Type.MEMBER_JOIN)
//    msg should be('defined)
//
//    msg.map(_.userId) shouldEqual Some(selfUser.id)
//    msg.map(_.members.toSet) shouldEqual Some(Set(user1.id, user2.id))
//
//    listConvs should have size 1
  }

  scenario("create new conversation for event") {
//    val event = createConversationEvent()
//    service.dispatchEvent(event)
//    Thread.sleep(250)
//
//    val convOpt = getConv(event.convId)
//    convOpt should be('defined)
//    val conv = convOpt.get
//
//    conv.remoteId shouldEqual event.convId
//    conv.creator shouldEqual selfUser.id
//
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//
//    lastMessage(conv.id).value.msgType shouldEqual Message.Type.MEMBER_JOIN
//
//    listConvs should have size 1
  }

  scenario("update temp conversation on post response") {
    val convId = ConvId()
//    Await.result(service.convsUi.createGroupConversation(convId, Seq(user1.id, user2.id)), timeout)
//    convSync shouldEqual Some(convId)
//
//    val response = conversationResponse(convId)
//    Await.result(service.conversations.updateConversations(Seq(response)), 10.seconds)
//
//    val conv = getConv(convId).get
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//    conv.remoteId shouldEqual response.conversation.remoteId
//
//    val event = MemberJoinEvent(conv.remoteId, new Date, selfUser.id, Seq(user1.id, user2.id), firstEvent = true)
//    service.dispatchEvent(event)
//    awaitUi(250.millis)
//
//    lastLocalMessage(conv.id, Message.Type.MEMBER_JOIN) should be('empty)
//    val msg = lastMessage(conv.id)
//    msg should be('defined)
//
//    msg.map(_.userId) shouldEqual Some(selfUser.id)
//    msg.map(_.members.toSet) shouldEqual Some(Set(user1.id, user2.id))
//
//    listConvs should have size 1
  }

  scenario("update temp conversation on event") {
//    val convId = ConvId()
//    Await.result(service.convsUi.createGroupConversation(convId, Seq(user1.id, user2.id)), timeout)
//    convSync shouldEqual Some(convId)
//
//    val event = createConversationEvent()
//    service.dispatchEvent(event)
//    Thread.sleep(250)
//
//    val conv = getConv(convId).get
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//    conv.remoteId shouldEqual event.convId
//
//    listConvs should have size 1
  }

  scenario("receive new group conversation with same members as recently created one") {
    val convId = ConvId()
//    Await.result(service.convsUi.createGroupConversation(convId, Seq(user1.id, user2.id)), timeout)
//    convSync shouldEqual Some(convId)
//
//    val event = createConversationEvent()
//    service.dispatchEvent(event)
//    Thread.sleep(250)
//
//    val conv = getConv(convId).get
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//    conv.remoteId shouldEqual event.convId
//
//    service.dispatchEvent(createConversationEvent())
//    withDelay {
//      getConv(convId) shouldEqual Some(conv)
//      listConvs should have size 2
//    }
  }

  scenario("update conversation on event") {
//    val convId = ConvId()
//    Await.result(service.convsUi.createGroupConversation(convId, Seq(user1.id, user2.id)), timeout)
//    val response = conversationResponse(convId)
//    Await.result(service.conversations.updateConversations(Seq(response)), 10.seconds)
//
//    val event = createConversationEvent(response.conversation.remoteId)
//    service.dispatchEvent(event)
//    Thread.sleep(250)
//
//    val conv = getConv(convId).get
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//    conv.remoteId shouldEqual event.convId
//
//    val msg = lastMessage(conv.id).value
//    msg.msgType shouldEqual Message.Type.MEMBER_JOIN
//    msg.userId shouldEqual selfUser.id
//    msg.members shouldEqual Set(user1.id, user2.id)
//
//    listConvs should have size 1
  }

  scenario("update temp conversation on event 1") {
//    val convId = ConvId()
//    Await.result(service.convsUi.createGroupConversation(convId, Seq(user1.id, user2.id)), timeout)
//    convSync shouldEqual Some(convId)
//
//    val remoteId = RConvId()
//    val event = CreateConversationEvent(remoteId, new Date, selfUser.id, ConversationResponse(ConversationData(ConvId(remoteId.str), remoteId, Some(""), selfUser.id, ConversationType.Group, None, None, Instant.now, isActive = true),
//      Seq(ConversationMemberData(user1.id,ConvId(remoteId.str)), ConversationMemberData(user2.id, ConvId(remoteId.str)))))
//
//    info(s"tempId: ${ConversationsService.generateTempConversationId(event.data.members.map(_.userId): _*)}")
//
//    service.dispatchEvent(event)
//    Thread.sleep(250)
//
//    val conv = getConv(convId).get
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//    conv.remoteId shouldEqual event.convId
//
//    listConvs should have size 1
  }

  scenario("update conversation on event and post response") {

//    val convId = ConvId()
//    Await.result(service.convsUi.createGroupConversation(convId, Seq(user1.id, user2.id)), timeout)
//
//    val remoteId = RConvId()
//    val event = CreateConversationEvent(remoteId, new Date, selfUser.id, ConversationResponse(ConversationData(ConvId(remoteId.str), remoteId, Some(""), selfUser.id, ConversationType.Group, None, None, Instant.now, isActive = true),
//    Seq(ConversationMemberData(user1.id,ConvId(remoteId.str)), ConversationMemberData(user2.id, ConvId(remoteId.str)))))
//
//    service.dispatchEvent(event)
//    Thread.sleep(250)
//
//    val response = event.data.copy(conversation = event.data.conversation.copy(id = convId))
//    Await.result(service.conversations.updateConversations(Seq(response)), 10.seconds)
//
//    val conv = getConv(convId).get
//    listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//    conv.remoteId shouldEqual remoteId
//
//    val msg = lastMessage(conv.id).value
//    msg.msgType shouldEqual Message.Type.MEMBER_JOIN
//    msg.userId shouldEqual selfUser.id
//    msg.members shouldEqual Set(user1.id, user2.id)
//
//    listConvs should have size 1
  }

  def initService: ConversationsUiService =
    new ConversationsUiServiceImpl(self, None, null, null, messages, null, null, members, null, convsContent, convsStorage, null, null, sync, null, null, null, null)
}
