/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient.ConvRoles
import com.waz.utils.CirceJSONSupport
import org.json.JSONObject
import io.circe.parser.decode

class ConversationRoleSpec extends AndroidFreeSpec with CirceJSONSupport {
  feature("JSON parsing") {
    scenario("Deserialize conversation roles for members") {
      val member1 = UserId()
      val member2 = UserId()
      val bot = UserId()
      val admin = UserId()

      val jsonStr = s"""
          |{
          |  "others": [
          |    { "id": "${member1.str}", "conversation_role": "wire_member" },
          |    { "id": "${member2.str}", "conversation_role": "wire_member" },
          |    { "id": "${bot.str}", "conversation_role": "wire_bot" },
          |    { "id": "${admin.str}", "conversation_role": "wire_admin" }
          |  ]
          |}
        """.stripMargin

      println(s"json: $jsonStr")
      val jsonObject = new JSONObject(jsonStr)

      val roles: Map[UserId, ConversationRole] = ConversationRole.decodeUserIdsWithRoles('others)(jsonObject)
      println(s"roles: $roles")
      roles.size shouldEqual 4
      roles(member1) shouldEqual ConversationRole.MemberRole
      roles(member2) shouldEqual ConversationRole.MemberRole
      roles(bot) shouldEqual ConversationRole.BotRole
      roles(admin) shouldEqual ConversationRole.AdminRole
    }

    scenario("Deserialize conversation roles and their actions") {
      val jsonStr =
        s"""
           |{
           |  "conversation_roles": [
           |    {
           |      "actions":[
           |        "add_conversation_member",
           |        "remove_conversation_member",
           |        "modify_conversation_name",
           |        "modify_conversation_message_timer",
           |        "modify_conversation_receipt_mode",
           |        "modify_conversation_access",
           |        "modify_other_conversation_member",
           |        "leave_conversation",
           |        "delete_conversation"
           |      ],
           |      "conversation_role": "wire_admin"
           |    },
           |    {
           |      "actions": ["leave_conversation"],
           |      "conversation_role": "wire_member"
           |    }
           |  ]
           |}
         """.stripMargin

      println(s"json: $jsonStr")

      val roles = decode[ConvRoles](jsonStr) match {
        case Right(rs)   => rs.toConversationRoles
        case Left(error) => fail(error)
      }

      roles.size shouldEqual 2
      roles.contains(ConversationRole.MemberRole) shouldBe true
      roles.contains(ConversationRole.AdminRole) shouldBe true
    }
  }
}
