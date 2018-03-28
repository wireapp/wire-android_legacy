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
package com.waz.zclient.messages.parts.footer

import java.util.concurrent.TimeUnit

import android.content.Context
import com.waz.api.Message
import com.waz.content.GlobalPreferences
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.testutils.TestUtils.{PrintValues, signalTest}
import com.waz.testutils.{MockZMessaging, TestWireContext}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.global.{AccentColorController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.{LikesController, UsersController}
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.RobolectricTestRunner
import org.scalatest.junit.JUnitSuite

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

@RunWith(classOf[RobolectricTestRunner])
@Config(manifest = "src/test/AndroidManifest.xml", resourceDir = "../../build/intermediates/res/merged/dev/debug")
class FooterViewControllerTest extends JUnitSuite {

  ShadowLog.stream = System.out

  implicit val logTag = com.waz.ZLog.ImplicitTag.implicitLogTag

  implicit val printSignalVals: PrintValues = true
  val duration = Duration(1000, TimeUnit.MILLISECONDS)
  val durationShort = Duration(200, TimeUnit.MILLISECONDS)
  implicit lazy val context = mock(classOf[TestWireContext])
  implicit lazy val executionContext = ExecutionContext.Implicits.global
  implicit lazy val eventContext = EventContext.Implicits.global

  lazy val selfUser = UserData("Self user")
  lazy val user2 = UserData("User 2")
  lazy val user3 = UserData("User 3")

  lazy val conv = ConversationData(ConvId(user2.id.str), RConvId(), Some(user2.name), selfUser.id, ConversationType.OneToOne, generatedName = user2.name)

  lazy val likedMsg = MessageData(MessageId(), conv.id, Message.Type.TEXT, user2.id)

  lazy val zMessaging: MockZMessaging = returning(new MockZMessaging(selfUserId = selfUser.id)) { zms =>
    zms.insertUsers(Seq(selfUser, user2, user3))
    zms.insertConv(conv)
    zms.addMessage(likedMsg)
/*    Await.result(zms.reactionsStorage.insert(Seq(
      Liking(likedMsg.id, user3.id, Instant.now, Liking.Action.Like)
    )), duration)*/
  }

  lazy val injector: Injector = new Module {
    bind[Context] to context
    bind[Signal[ZMessaging]] to Signal.const(zMessaging)
    bind[Signal[Option[ZMessaging]]] to Signal.const(Some(zMessaging))
    bind[GlobalPreferences] to zMessaging.prefs
    bind[AccentColorController] to new AccentColorController
    bind[ConversationController] to new ConversationController
    bind[UsersController] to new UsersController
    bind[LikesController] to new LikesController()
  }

  val activity = mock(classOf[TestWireContext])
  //val activity = Robolectric.buildActivity(classOf[ViewTestActivity]).create().start().resume().get()
  //activity.inj = injector

  @Test
  def statusVisibilityLikedMsgIsClicked(): Unit = {
    val controller = new FooterViewController()(injector, context, EventContext.Global)

//    controller.opts ! MsgBindOptions(1, isSelf = false, isLast = false, isLastSelf = false, isFirstUnread = false, Dim2(100, 100), ConversationType.Group)
    controller.messageAndLikes ! MessageAndLikes(likedMsg, IndexedSeq(user3.id), likedBySelf = false)

    assertEquals(false, Await.result(controller.showTimestamp.head, durationShort))
    assertEquals(true, Await.result(controller.isLiked.head, durationShort))

//    assertEquals(true, Await.result(injector.binding[ConversationController].get().zms.map(_ != null).head, durationShort))

    controller.selection.toggleFocused(likedMsg.id)
    signalTest(controller.showTimestamp)(_ == true){} // show timestamp for focused message

    controller.selection.toggleFocused(likedMsg.id)
    signalTest(controller.showTimestamp)(_ == false){} // hide timestamp on second click

    controller.selection.toggleFocused(likedMsg.id)
    signalTest(controller.showTimestamp)(_ == true){} // show timestamp when focused again

    signalTest(controller.showTimestamp)(_ == false){}(printSignalVals, 5000) // hide timestamp after delay

    controller.selection.toggleFocused(likedMsg.id)
    signalTest(controller.showTimestamp)(_ == true){} // show timestamp on next click
  }

  @Test
  def openMessageAndLike(): Unit = {
    val controller = new FooterViewController()(injector, context, EventContext.Global)

//    controller.opts ! MsgBindOptions(1, isSelf = false, isLast = false, isLastSelf = false, isFirstUnread = false, Dim2(100, 100), ConversationType.Group)
    controller.messageAndLikes ! MessageAndLikes(likedMsg, IndexedSeq(), likedBySelf = false)

    assertEquals(false, Await.result(controller.showTimestamp.head, durationShort))
    assertEquals(false, Await.result(controller.isLiked.head, durationShort))
//    assertEquals(true, Await.result(injector.binding[ConversationController].get().zms.map(_ != null).head, durationShort))

    controller.selection.toggleFocused(likedMsg.id)
    signalTest(controller.showTimestamp)(_ == true){} //show timestamp

    controller.messageAndLikes ! MessageAndLikes(likedMsg, IndexedSeq(selfUser.id), likedBySelf = true)
    signalTest(controller.showTimestamp)(_ == false){} //timestamp should disappear

    /**
      * TODO - there is a slight timing edge case here.
      *
      * Because we want to be able to un-set the 'activity' of a msg (or footer), we set back the lastActivity time to
      * Instant.now - ActivityTimeout. This allows the timestamp to be dismissed if the timeout is already active, essentially
      * by replacing it with a timeout that expires in the past.
      *
      * If, however, a message is liked within [lastActivityTime - (Instant.now - ActivityTimeout)] BEFORE lastActivityTime,
      * then the likes rule will take precedence and prevent the timestamp from showing when it should.
      *
      * This can be reproduced manually by clicking on an unliked message, liking it and then trying to display the timestamp
      * again, all in relatively quick succession. The "You" remains in place when one would expect the timestamp to appear.
      *
      * It can be reproduced in the test by commenting out this sleep.
      *
      * Be careful - trying to fix this issue will likely break other things. If the user likes something, and ActivityTimeout
      * goes by (currently 3 seconds) and then they click the message again, the timestamp appears as expected. A pretty small
      * edge case, but nice to fix one day
      */
    Thread.sleep(3000)

    controller.selection.toggleFocused(likedMsg.id)
    signalTest(controller.showTimestamp)(_ == true){} //show timestamp again

  }
}
