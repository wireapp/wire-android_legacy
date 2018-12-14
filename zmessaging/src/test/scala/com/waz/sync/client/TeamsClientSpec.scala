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
package com.waz.sync.client

import com.waz.model.AccountDataOld._
import com.waz.model.AccountDataOld.Permission._
import com.waz.specs.AndroidFreeSpec

class TeamsClientSpec extends AndroidFreeSpec {

  feature("permissions bitmask") {

    scenario("Some permissions") {
      val permissions = 41 //101001
      decodeBitmask(permissions) shouldEqual Set(CreateConversation, RemoveTeamMember, RemoveConversationMember)
    }

    scenario("No permissions") {
      val permissions = 0
      decodeBitmask(permissions) shouldEqual Set.empty
    }

    scenario("All permissions") {
      val permissions = ~(Permission.values.size & 0)
      decodeBitmask(permissions) shouldEqual Permission.values
    }

    scenario("Encode/decode permissions") {
      val ps = Set(CreateConversation, DeleteConversation, SetMemberPermissions)
      val mask = encodeBitmask(ps)
      val psOut = decodeBitmask(mask)
      psOut shouldEqual ps
    }

  }


}
