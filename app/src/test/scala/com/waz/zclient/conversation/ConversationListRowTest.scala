/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
import com.waz.api.{Message, VoiceChannelState}
import com.waz.model._
import com.waz.service.call.CallInfo
import com.waz.testutils.TestUtils._
import com.waz.testutils.ViewTestActivity
import com.waz.utils.events.EventContext
import com.waz.zclient.views.ConversationBadge
import com.waz.zclient.views.conversationlist.ConversationListRow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.robolectric.annotation.Config
import org.robolectric.{Robolectric, RobolectricTestRunner}
import org.scalatest.junit.JUnitSuite

@RunWith(classOf[RobolectricTestRunner])
@Config(manifest = "app/src/test/AndroidManifest.xml", resourceDir = "../../build/intermediates/res/merged/dev/debug")
class ConversationListRowTest extends JUnitSuite {

  implicit val printSignalVals: PrintValues = false
  implicit val eventContext = EventContext.Implicits.global

  //implicit lazy val context = mock(classOf[TestWireContext])
  implicit val activity: Context = Robolectric.buildActivity(classOf[ViewTestActivity]).create().start().resume().get()

  val selfId = "0"
  val convId = "0"

  /* TODO: The config seems wrong to get the resources on Jenkins...
  @Test
  def subtitleMuted(): Unit ={
    assert(NewConversationListRow.subtitleStringForLastMessages(createMessagesWithPing(convId)) == "3 new messages, 1 ping")
  }
  */
  @Test
  def badgeMuted(): Unit = {
    assert(ConversationListRow.badgeStatusForConversation(createMutedConversationData(), 5, typing = false, Map()) == ConversationBadge.Muted)
  }
  @Test
  def badgeCount(): Unit = {
    assert(ConversationListRow.badgeStatusForConversation(createGroupConversationData(), 5, typing = false, Map()) == ConversationBadge.Count(5))
    assert(ConversationListRow.badgeStatusForConversation(createGroupConversationData(), 10, typing = false, Map()) == ConversationBadge.Count(10))
  }
  @Test
  def badgeMissedCall(): Unit = {
    assert(ConversationListRow.badgeStatusForConversation(createMissedCallConversationData(), 10, typing = false, Map()) == ConversationBadge.MissedCall)
  }
  @Test
  def badgeOngoingCall(): Unit = {
    assert(ConversationListRow.badgeStatusForConversation(createGroupConversationData(), 5, typing = false, Map(ConvId(convId) -> createGenericCallInfo())) == ConversationBadge.IncomingCall)
  }
  @Test
  def badgePing(): Unit = {
    assert(ConversationListRow.badgeStatusForConversation(createKnockedConversationData(), 5, typing = false, Map()) == ConversationBadge.Ping)
  }
  @Test
  def badgeTyping(): Unit = {
    assert(ConversationListRow.badgeStatusForConversation(createGroupConversationData(), 5, typing = true, Map()) == ConversationBadge.Typing)
  }

  def createKnockedConversationData() : ConversationData = {
    val conversationData = mock(classOf[ConversationData])
    when(conversationData.id).thenReturn(ConvId(convId))
    when(conversationData.muted).thenReturn(false)
    when(conversationData.incomingKnockMessage).thenReturn(Some(MessageId()))
    when(conversationData.missedCallMessage).thenReturn(None)
    when(conversationData.convType).thenReturn(ConversationData.ConversationType.Group)
    conversationData
  }

  def createMutedConversationData() : ConversationData = {
    val conversationData = mock(classOf[ConversationData])
    when(conversationData.id).thenReturn(ConvId(convId))
    when(conversationData.muted).thenReturn(true)
    when(conversationData.incomingKnockMessage).thenReturn(None)
    when(conversationData.missedCallMessage).thenReturn(None)
    when(conversationData.convType).thenReturn(ConversationData.ConversationType.Group)
    conversationData
  }

  def createMissedCallConversationData() : ConversationData = {
    val conversationData = mock(classOf[ConversationData])
    when(conversationData.id).thenReturn(ConvId(convId))
    when(conversationData.muted).thenReturn(false)
    when(conversationData.incomingKnockMessage).thenReturn(None)
    when(conversationData.missedCallMessage).thenReturn(Some(MessageId()))
    when(conversationData.convType).thenReturn(ConversationData.ConversationType.Group)
    conversationData
  }

  def createGroupConversationData() : ConversationData = {
    val conversationData = mock(classOf[ConversationData])
    when(conversationData.id).thenReturn(ConvId(convId))
    when(conversationData.muted).thenReturn(false)
    when(conversationData.incomingKnockMessage).thenReturn(None)
    when(conversationData.missedCallMessage).thenReturn(None)
    when(conversationData.convType).thenReturn(ConversationData.ConversationType.Group)
    conversationData
  }

  def createMessagesWithPing(cId: String): Seq[MessageData] = {
    Seq(
      createMessage("0", cId, "0", "a", Message.Type.TEXT),
      createMessage("1", cId, "1", "b", Message.Type.KNOCK),
      createMessage("2", cId, "2", "c", Message.Type.ASSET),
      createMessage("3", cId, "2", "d", Message.Type.RICH_MEDIA)
    )
  }

  def createGenericMessage(): MessageData ={
    createMessage("0", convId, "0", "a", Message.Type.TEXT)
  }

  def createGenericCallInfo(): CallInfo ={
    CallInfo(ConvId(convId), UserId(), VoiceChannelState.OTHER_CALLING)
  }

  def createMessage(mId: String, cId: String, uId:String, content: String, tpe: Message.Type): MessageData = {
    val message = mock(classOf[MessageData])
    when(message.id).thenReturn(MessageId(mId))
    when(message.convId).thenReturn(ConvId(cId))
    when(message.msgType).thenReturn(tpe)
    when(message.userId).thenReturn(UserId(uId))
    when(message.contentString).thenReturn(content)
    message
  }

}
