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

import com.waz.content.UsersStorage
import com.waz.model.{TeamId, UserData}
import com.waz.specs.AndroidFreeSpec
import scala.concurrent.Future

class TeamSizeThresholdSpec extends AndroidFreeSpec {

  val usersStorage    = mock[UsersStorage]
  val teamId          = TeamId("MyTeam")
  var testUsers       = List.empty[UserData]

  val usersThreshold = 400

  (usersStorage.getByTeam _).expects(Set(teamId)).anyNumberOfTimes().onCall { _: Set[TeamId] => Future.successful(testUsers.toSet) }

  override def afterEach() ={
    testUsers = List.empty[UserData]
  }

  def createTestUsers(number: Int, deleted: Boolean = false): Unit = {
    val existingUsers = testUsers.length
    testUsers = testUsers ++ (1 to number).toList
      .map { id => UserData(s"${id + existingUsers}").copy(deleted = deleted) }
  }

  feature("runIfBelowStatusPropagationThreshold") {
    scenario("above propagation threshold") {

      // given
      val service = new TeamSizeThresholdImpl(Some(teamId), usersStorage)
      var executed = false
      createTestUsers(usersThreshold)

      // when
      result(service.runIfBelowStatusPropagationThreshold({ () =>
        executed = true
        Future.successful({})
      }))

      // then
      executed shouldBe false
    }

    scenario("below propagation threshold") {

      // given
      val service = new TeamSizeThresholdImpl(Some(teamId), usersStorage)
      var executed = false

      // when
      result(service.runIfBelowStatusPropagationThreshold({ () =>
        executed = true
        Future.successful({})
      }))

      // then
      executed shouldBe true
    }

    scenario("deleted users don't count towards limit") {

      // given
      val service = new TeamSizeThresholdImpl(Some(teamId), usersStorage)
      var executed = false
      createTestUsers(usersThreshold - 1)
      createTestUsers(1, true)

      // when
      result(service.runIfBelowStatusPropagationThreshold({ () =>
        executed = true
        Future.successful({})
      }))

      // then
      executed shouldBe true
    }

    scenario("no team") {

      // given
      val service = new TeamSizeThresholdImpl(None, usersStorage)
      var executed = false
      createTestUsers(usersThreshold - 1)

      // when
      result(service.runIfBelowStatusPropagationThreshold({ () =>
        executed = true
        Future.successful({})
      }))

      // then
      executed shouldBe true
    }
  }

  feature("isAboveStatusPropagationThreshold") {

    scenario("above propagation threshold") {

      // given
      createTestUsers(usersThreshold)

      // when
      val above = result(TeamSizeThreshold.isAboveStatusPropagationThreshold(Some(teamId), usersStorage))

      // then
      above shouldBe true
    }

    scenario("below propagation threshold") {

      // given
      createTestUsers(usersThreshold - 1)

      // when
      val above = result(TeamSizeThreshold.isAboveStatusPropagationThreshold(Some(teamId), usersStorage))

      // then
      above shouldBe false
    }

    scenario("deleted users don't count towards limit") {

      // given
      createTestUsers(usersThreshold - 1)
      createTestUsers(1, deleted = true)

      // when
      val above = result(TeamSizeThreshold.isAboveStatusPropagationThreshold(Some(teamId), usersStorage))

      // then
      above shouldBe false
    }

    scenario("no team") {

      // given
      createTestUsers(usersThreshold)

      // when
      val above = result(TeamSizeThreshold.isAboveStatusPropagationThreshold(None, usersStorage))

      // then
      above shouldBe false
    }

  }
}
