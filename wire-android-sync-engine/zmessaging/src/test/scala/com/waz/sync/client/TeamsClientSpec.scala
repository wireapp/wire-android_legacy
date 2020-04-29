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

import com.waz.model.{ConversationRole, TeamData, UserId}
import com.waz.model.UserPermissions.Permission._
import com.waz.model.UserPermissions._
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.TeamsClient.{TeamMembers, TeamRoles}
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

      // given
      import io.circe.parser._
      val response =
        """{
          "members": [
            {
              "created_by": "a630278f-5b7e-453b-8e7b-0b4838597312",
              "created_at": "2019-01-18T15:46:00.938Z",
              "user":"7bba67b9-e0c4-43ec-8648-93ee2a567610"
            },
            {
              "created_by": "a630278f-5b7e-453b-8e7b-0b4838597312",
              "created_at": "2019-01-16T13:37:02.222Z",
              "user":"98bc4812-e0a1-426d-9126-441399a1c010",
              "permissions":{"copy":1025,"self":1025}
            },
            {
              "created_by": null,
              "user":"a630278f-5b7e-453b-8e7b-0b4838597312"
            },
            {
              "user":"f3f4f763-ccee-4b3d-b450-582e2c99f8be"
            }
          ],
          "hasMore": false
        }"""

      // when
      val result = decode[TeamMembers](response)

      // then
      val parsed = result.right.get
      parsed.members.length shouldBe 4
      parsed.members(0).user shouldEqual UserId("7bba67b9-e0c4-43ec-8648-93ee2a567610")
      parsed.members(0).permissions shouldEqual None
      parsed.members(0).created_by shouldEqual Some(UserId("a630278f-5b7e-453b-8e7b-0b4838597312"))

      parsed.members(1).user shouldEqual UserId("98bc4812-e0a1-426d-9126-441399a1c010")
      parsed.members(1).permissions shouldEqual Some(TeamsClient.Permissions(1025, 1025))
      parsed.members(1).created_by shouldEqual Some(UserId("a630278f-5b7e-453b-8e7b-0b4838597312"))

      parsed.members(2).user shouldEqual UserId("a630278f-5b7e-453b-8e7b-0b4838597312")
      parsed.members(2).permissions shouldEqual None
      parsed.members(2).created_by shouldEqual None

      parsed.members(3).user shouldEqual UserId("f3f4f763-ccee-4b3d-b450-582e2c99f8be")
      parsed.members(3).permissions shouldEqual None
      parsed.members(3).created_by shouldEqual None

    }

    scenario("Team data response decoding") {
      import io.circe.parser._
      val response = "{\"creator\":\"d22b442c-8a91-4773-8655-4d72b49b8c70\",\"icon\":\"abc\",\"name\":\"Potato\",\"id\":\"ddaad665-9227-4bd3-93d6-a3450bfbbfc7\",\"binding\": true,\"icon_key\": \"abcdefg\"}"
      val result = decode[TeamData](response)

      result shouldEqual Right(TeamData(
        "ddaad665-9227-4bd3-93d6-a3450bfbbfc7",
        "Potato",
        "d22b442c-8a91-4773-8655-4d72b49b8c70",
        "abc"
      ))
    }
  }

  feature("Conversation roles") {
    val rolesJson =
      """
        |{
        |"conversation_roles":[
        |  {"actions":[
        |    "add_conversation_member",
        |    "remove_conversation_member",
        |    "modify_conversation_name",
        |    "modify_conversation_message_timer",
        |    "modify_conversation_receipt_mode",
        |    "modify_conversation_access",
        |    "modify_other_conversation_member",
        |    "leave_conversation",
        |    "delete_conversation"
        |  ],
        |  "conversation_role":"wire_admin"
        |  },
        |  {"actions":[
        |    "leave_conversation"
        |  ],
        |  "conversation_role":"wire_member"}
        |]}
      """.stripMargin

    scenario("should deserialize team conversation roles") {
      import io.circe.parser._


      val result = decode[TeamRoles](rolesJson)
      result.isRight shouldBe true
      val roles = result.right.get.toConversationRoles
      roles shouldEqual Set(ConversationRole.MemberRole, ConversationRole.AdminRole)
    }

  }
}
