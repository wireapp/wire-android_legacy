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

import com.waz._
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.{StorageModule, Timeouts, UiLifeCycle, UiLifeCycleImpl}
import com.waz.testutils.Matchers._
import com.waz.testutils.{EmptySyncService, TestGlobalPreferences}
import com.waz.utils._
import com.waz.utils.events.EventContext
import org.scalatest._
import org.scalatest.matchers.Matcher
import org.threeten.bp.Instant

import scala.concurrent.duration._

@Ignore
class TypingSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils { test =>

  implicit val timeout: FiniteDuration = 1.seconds

  @volatile var typingSync: Option[ConvId] = None
  @volatile var syncWasTyping = false

  lazy val selfUser = UserData("self user")
  lazy val users = 0 to 1 map { i => UserData(s"user $i") }

  lazy val conv = ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group)

  lazy val storage = new StorageModule(context, UserId(), new TestGlobalPreferences) {
    convsStorage.insert(conv)
  }

  lazy val lifecycle: UiLifeCycle = new UiLifeCycleImpl {
    acquireUi()
  }

  lazy val timeouts = new Timeouts {
    override val typing: Typing = new Typing {
      override val stopTimeout: Timeout = 2.seconds
      override val refreshDelay: Timeout = 500.millis
      override val receiverTimeout: Timeout = 2.seconds
    }
  }

  lazy val service = new TypingService(UserId(), storage.convsStorage, timeouts, null, new EmptySyncService {
    override def postTypingState(id: ConvId, typing: Boolean) = {
      test.syncWasTyping = typing
      test.typingSync = Some(id)
      super.postTypingState(id, typing)
    }
  }, null)

  before {
    typingSync = None
  }

  feature("Send typing events") {
    scenario("Typing and then clearing the input") {
      service.isSelfTyping(conv.id) should eventually(be(false))
      service.selfChangedInput(conv.id)
      service.isSelfTyping(conv.id) should eventually(be(true))
      withDelay {
        typingSync shouldEqual Some(conv.id)
      }

      typingSync = None
      1 to 3 foreach { _ => service.selfChangedInput(conv.id)}

      service.isSelfTyping(conv.id) should eventually(be(true))
      withDelay {
        typingSync shouldEqual None
      }

      service.selfClearedInput(conv.id)
      withDelay {
        service.isSelfTyping(conv.id) should eventually(be(false))
        typingSync shouldEqual Some(conv.id)
      }

      typingSync = None
      service.selfClearedInput(conv.id)
      withDelay {
        service.isSelfTyping(conv.id) should eventually(be(false))
        typingSync shouldEqual None
      }
    }

    scenario("Resend typing event after refresh delay") {
      service.selfChangedInput(conv.id)
      withDelay(typingSync shouldEqual Some(conv.id))
      syncWasTyping shouldEqual true
      typingSync = None
      withDelay(typingSync shouldEqual Some(conv.id))(1.second)
      syncWasTyping shouldEqual true
      typingSync = None
      withDelay(typingSync shouldEqual Some(conv.id))(1.second)
      syncWasTyping shouldEqual true
    }
    
    scenario("Stop sending after stop timeout") {
      service.selfChangedInput(conv.id)
      withDelay(typingSync shouldEqual Some(conv.id))
      awaitUi(timeouts.typing.stopTimeout + 2.seconds)
      typingSync = None
      awaitUi(1.second)
      typingSync shouldEqual None
      service.isSelfTyping(conv.id) should eventually(be(false))
    }
    
    scenario("Send stopped typing event after timeout") {
      service.selfChangedInput(conv.id)
      withDelay(typingSync shouldEqual Some(conv.id))
      awaitUi(timeouts.typing.stopTimeout + 2.seconds)
      syncWasTyping shouldEqual false
    }
  }

  feature("Receive typing events") {
    scenario("The users list contains the typing users in the correct order") {
      startTyping(selfUser)
      service should haveTypingUsers(selfUser)

      startTyping(users(0))
      startTyping(users(1))
      withDelay { service should haveTypingUsers (selfUser, users(0), users(1)) }

      startTyping(users(0), notify = false)
      startTyping(selfUser, notify = false)
      withDelay { service should haveTypingUsers (selfUser, users(0), users(1)) }

      stopTyping(users(0))
      withDelay { service should haveTypingUsers (selfUser, users(1)) }

      stopTyping(selfUser)
      withDelay { service should haveTypingUsers (users(1)) }

      stopTyping(selfUser, notify = false)
      withDelay { service should haveTypingUsers (users(1)) }

      stopTyping(users(1), notify = false)
      withDelay { service should haveNoTypingUsers }
    }

    scenario("Removing users from an empty list doesn't do anything") {
      stopTyping(selfUser, notify = false)
      withDelay { service should haveNoTypingUsers }
    }

    scenario("Clear typing users list after receiver timeout") {
      startTyping(users(0))
      startTyping(users(1))
      withDelay { service should haveTypingUsers (users(0), users(1)) }
      withDelay { service should haveNoTypingUsers } (timeouts.typing.receiverTimeout + 2.seconds)
    }
  }

  def startTyping(user: UserData, time: Instant = Instant.now, notify: Boolean = true): Unit = setTypingState(user, time, isTyping = true, notify)
  def stopTyping(user: UserData, time: Instant = Instant.now, notify: Boolean = true): Unit = setTypingState(user, time, isTyping = false, notify)

  def setTypingState(user: UserData, time: Instant, isTyping: Boolean, notify: Boolean) = {
    val event = TypingEvent(conv.remoteId, RemoteInstant(time), user.id, isTyping = isTyping)
    event.localTime = LocalInstant(time)
    var notified = false
    val sub = service.onTypingChanged { _ => notified = true } (EventContext.Global)
    service.typingEventStage.apply(conv.remoteId, Seq(event))
    if (notify) withDelay { notified shouldEqual true }
    sub.destroy()
  }

  def haveTypingUsers(expected: UserData*): Matcher[TypingService] = eventually(be(IndexedSeq(expected: _*) map (_.id))) compose { _.getTypingUsers(conv.id) }
  def haveNoTypingUsers: Matcher[TypingService] = eventually(be('empty)) compose { _.getTypingUsers(conv.id) }
}
