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

import com.waz.model.UserPermissions._
import com.waz.model.UserPermissions.Permission._
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.TeamsClient.TeamMembers
import com.waz.utils.CirceJSONSupport

//TODO Replace with integration test when AuthRequestInterceptor2 is introduced
class TeamsClientSpec extends AndroidFreeSpec with CirceJSONSupport {

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

    scenario("Team members response decoding") {
      import io.circe.parser._
      val response = "{\"members\":[{\"invited\":{\"at\":\"2019-01-18T15:46:00.938Z\",\"by\":\"a630278f-5b7e-453b-8e7b-0b4838597312\"},\"user\":\"7bba67b9-e0c4-43ec-8648-93ee2a567610\"},{\"invited\":{\"at\":\"2019-01-16T13:37:02.222Z\",\"by\":\"a630278f-5b7e-453b-8e7b-0b4838597312\"},\"user\":\"98bc4812-e0a1-426d-9126-441399a1c010\",\"permissions\":{\"copy\":1025,\"self\":1025}},{\"invited\":null,\"user\":\"a630278f-5b7e-453b-8e7b-0b4838597312\"},{\"invited\":{\"at\":\"2019-01-18T15:17:45.127Z\",\"by\":\"a630278f-5b7e-453b-8e7b-0b4838597312\"},\"user\":\"f3f4f763-ccee-4b3d-b450-582e2c99f8be\"}]}"
      val result = decode[TeamMembers](response)

      println(result)
    }

  }


}
