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
package com.waz.mocked.conv

import com.waz.api.ErrorType.CANNOT_ADD_USER_TO_FULL_CONVERSATION
import com.waz.api.ErrorsList.{ErrorDescription, ErrorListener}
import com.waz.api.{MockedClientApiSpec, User}
import com.waz.mocked.MockBackend
import com.waz.model.{RConvId, UserId}
import com.waz.testutils.HasId.{idsOf, idsOfAll}
import com.waz.testutils.Implicits._
import com.waz.testutils.{SpyBase, UpdateSpy}
import com.waz.utils._
import com.waz.utils.events.EventContext.Implicits.global
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class ConversationMemberLimitSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfterAll with BeforeAndAfter with MockBackend with MockedClientApiSpec with ScalaFutures { test =>
  import DefaultPushBehaviour.Implicit

  lazy val convs = api.getConversations
  lazy val errors = api.getErrors
  lazy val ids = (0 to 127) map (n => UserId(s"user-$n"))
  lazy val convId = RConvId()

  lazy val conv = {
    withDelay(convs.size should be > 128)
    convs.find(_.data.remoteId == convId).get
  }

  lazy val userList = conv.getUsers

  lazy val userListSpy = returning(UpdateSpy())(userList.addUpdateListener)
  lazy val errorListSpy = returning(UpdateSpy())(errors.addUpdateListener)
  lazy val errorSpy = returning(OnErrorSpy())(errors.addErrorListener)

  override val clientDelay: Timeout = Duration.Zero // FIXME: why is this test failing with bigger delay ?

  override protected def beforeAll(): Unit = {
    ids foreach (id => addConnection(id))
    addGroupConversation(ids take 127, id = convId)

    super.beforeAll()
  }

  after {
    userListSpy.reset()
    errorListSpy.reset()
    errorSpy.reset()
  }

  scenario("initial sync") {
    val members = zmessaging.membersStorage.activeMembers(conv.id)
    var count = 0
    members { ms =>
      count = ms.size
    }
    withDelay {
      zmessaging.membersStorage.getActiveUsers(conv.id).futureValue should have size 128
      count shouldEqual 128
      withClue("self: " + api.getSelf.getUser.getId + " " + userList.map(_.getId).sorted.mkString(", ")) {
        userList should have size 127 // doesn't include self
      }
      errors shouldBe empty
    }


    userListSpy
    errorListSpy
    errorSpy
  }

  scenario("Adding a member to a group conversation which would bring it over the allowed limit of 128 users.") {
    conv.addMembers(Seq(api.getUser(ids.last.str)).asJava)

    withDelay {
      userListSpy.numberOfTimesCalled should be > 0
      errorListSpy.numberOfTimesCalled should be > 0
      errorSpy.numberOfTimesCalled shouldBe 1
      errorSpy.mostRecentError.value.getType shouldEqual CANNOT_ADD_USER_TO_FULL_CONVERSATION
      idsOfAll((errorSpy.mostRecentError.value.getUsers.asScala.toSeq: Seq[User]):_*) should contain theSameElementsAs Seq(ids.last)
      userList should have size 127
      idsOf(userList) should not contain ids.last
      errors should have size 1
      errors.get(0).getType shouldEqual CANNOT_ADD_USER_TO_FULL_CONVERSATION
      idsOfAll((errors.get(0).getUsers.asScala.toSeq: Seq[User]):_*) should contain theSameElementsAs Seq(ids.last)
    }

    errorListSpy.reset()
    errorSpy.reset()
    errors.get(0).dismiss()

    withDelay {
      errorListSpy.numberOfTimesCalled should be > 0
      errorSpy.numberOfTimesCalled shouldBe 0
      errors shouldBe empty
    }
  }

  scenario("Adding a member to a group conversation of size 128 when the user is already a member of that conversation.") {
    userListSpy.reset()

    conv.addMembers(Seq(api.getUser(ids(100).str)).asJava)

    withDelay {
      userListSpy.numberOfTimesCalled shouldBe 0
      errorListSpy.numberOfTimesCalled shouldBe 0
      errorSpy.numberOfTimesCalled shouldBe 0
      conv.getUsers should have size 127
      idsOf(userList) should contain(ids(100))
      errors shouldBe empty
    }
  }

  scenario("Removing a member from a group conversation of size 128 and then adding another member.") {
    conv.removeMember(api.getUser(ids(100).str))

    withDelay {
      userListSpy.numberOfTimesCalled should be > 0
      errorListSpy.numberOfTimesCalled shouldBe 0
      errorSpy.numberOfTimesCalled shouldBe 0
      conv.getUsers should have size 126
      idsOf(userList) should not(contain(ids(100)))
      errors shouldBe empty
    }

    conv.addMembers(Seq(api.getUser(ids.last.str)).asJava)

    withDelay {
      userListSpy.numberOfTimesCalled should be > 0
      errorListSpy.numberOfTimesCalled shouldBe 0
      errorSpy.numberOfTimesCalled shouldBe 0
      conv.getUsers should have size 127
      idsOf(userList) should contain(ids.last)
      idsOf(userList) should not(contain(ids(100)))
      errors shouldBe empty
    }
  }

  case class OnErrorSpy() extends ErrorListener with SpyBase {
    @volatile var mostRecentError: Option[ErrorDescription] = None

    def onError(error: ErrorDescription): Unit = {
      mostRecentError = Some(error)
      increment()
    }
  }
}
