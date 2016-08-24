/**
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
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  */
package com.waz.zclient.messages

import java.util.concurrent.TimeUnit

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams
import com.waz.RobolectricUtils
import com.waz.api.Message
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.testutils.TestUtils.{PrintValues, signalTest}
import com.waz.testutils.{MockZMessaging, TestWireContext, ViewTestActivity}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import junit.framework.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.{Before, Test}
import org.mockito.Mockito._
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.{Robolectric, RobolectricTestRunner}
import org.scalatest.junit.JUnitSuite

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

//integration test
@RunWith(classOf[RobolectricTestRunner])
@Config(manifest = "src/test/AndroidManifest.xml", resourceDir = "../../build/intermediates/res/merged/dev/debug")
class MessagesStreamTest extends JUnitSuite {

    ShadowLog.stream = System.out

  implicit val printSignalVals: PrintValues = true
  val duration = Duration(1000, TimeUnit.MILLISECONDS)
  val durationShort = Duration(200, TimeUnit.MILLISECONDS)

  lazy val context = mock(classOf[TestWireContext])
  implicit lazy val executionContext = ExecutionContext.Implicits.global

  lazy val selfUser = UserData("Self user")
  lazy val user2 = UserData("User 2")
  lazy val user3 = UserData("User 3")

  lazy val conv1 = ConversationData(ConvId(user2.id.str), RConvId(), Some(user2.name), selfUser.id, ConversationType.OneToOne, generatedName = user2.name)
  lazy val conv2 = ConversationData(ConvId(user3.id.str), RConvId(), Some(user3.name), selfUser.id, ConversationType.OneToOne, generatedName = user3.name)

  var zMessaging: MockZMessaging = _

  lazy val injector: Injector = new Module {
    bind[Context] to context
    bind[Signal[ZMessaging]] to Signal.const(zMessaging)
    bind[MessageViewFactory] to new MessageViewFactory()
  }

  val activity = Robolectric.buildActivity(classOf[ViewTestActivity]).create().start().resume().get()
  activity.inj = injector

  @Before
  def setup(): Unit = {
    zMessaging = new MockZMessaging(selfUserId = selfUser.id)

    zMessaging.insertUsers(Seq(selfUser, user2, user3))
    zMessaging.insertConv(conv1)
    zMessaging.insertConv(conv2)
  }

  @Test
  def messageCount(): Unit = {
    addTextMessages(10, conv1.id)
    selectConversation(conv1.id)
    assertEquals(10, attachListViewToActivity().adapter.getItemCount)
  }

  @Test
  def initialLastRead(): Unit = {
    addTextMessages(10, conv1.id)
    selectConversation(conv1.id)
    signalTest(attachListViewToActivity().adapter.initialLastReadIndex) {
      case (_, 0) => true
      case _ => false
    } {}
  }

  @Test
  def messageAtPosition(): Unit = {
    addTextMessages(10, conv1.id)
    val lastMessage = addMessage(conv1.id)
    selectConversation(conv1.id)
    assertEquals(lastMessage, attachListViewToActivity().adapter.message(10))
  }

  @Test
  def scrollToBottom(): Unit = {
    addTextMessages(50, conv1.id)

    selectConversation(conv1.id)
    val listView = attachListViewToActivity()

    scrollToBottom(listView)
    assertEquals(49, listView.layoutManager.findLastVisibleItemPosition())
  }

  @Test
  def changeConversationMaintainsLastRead(): Unit = {
    addTextMessages(50, conv1.id)
    addTextMessages(50, conv2.id)

    selectConversation(conv1.id)
    val listView = attachListViewToActivity()
    scrollToBottom(listView)

    selectConversation(conv2.id, Some(listView))
    assertEquals(0, listView.layoutManager.findLastVisibleItemPosition())

    selectConversation(conv1.id, Some(listView))
    assertEquals(49, listView.layoutManager.findLastVisibleItemPosition())
  }

  def addTextMessages(numMessages: Int, convId: ConvId) = Await.ready(
    Future.sequence((0 until numMessages).map { _ =>
      zMessaging.messagesStorage.addMessage(MessageData(MessageId(), convId, Message.Type.TEXT, UserId(convId.str), content = Seq(MessageContent(Message.Part.Type.TEXT, "Hello world"))))
    }), duration)

  def addMessage(conv: ConvId) = Await.result(zMessaging.messagesStorage.addMessage(MessageData(MessageId(), conv, Message.Type.TEXT, user2.id, content = Seq(MessageContent(Message.Part.Type.TEXT, "Hello world")))), duration)

  def selectConversation(conv: ConvId, view: Option[View] = None): Unit = {
    Await.ready(zMessaging.convsStats.selectConversation(Some(conv)), duration)
    view.foreach { v =>
      RobolectricUtils.awaitUi(durationShort)
      performLayout(v)
    }
  }

  def attachListViewToActivity() = returning(new MessagesListView(activity)) { v =>
    setViewSize(v, 250, 500)
    v.onAttachedToWindow()
    RobolectricUtils.awaitUi(durationShort) //give time for signals to update
    assertEquals(View.VISIBLE, v.getVisibility)
  }

  def setViewSize(v: View, w: Int, h: Int): Unit = {
    v.setLayoutParams(new LayoutParams(w, h))
    v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    v.layout(0, 0, w, h)
    assertEquals(w, v.getWidth)
    assertEquals(h, v.getHeight)
  }

  def scrollToBottom(v: MessagesListView): Unit = {
    v.scrollToBottom()
    performLayout(v)
  }

  def performLayout(v: View) = setViewSize(v, v.getWidth, v.getHeight) //performs the layout for scrolling to take affect

}

