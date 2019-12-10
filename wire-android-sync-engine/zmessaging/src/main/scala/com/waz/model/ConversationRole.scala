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

import com.waz.db.Col.{id, opt, text}
import com.waz.db.Dao3
import com.waz.utils.JsonDecoder.decodeSeq
import com.waz.utils.{Identifiable, JsonDecoder}
import com.waz.utils.wrappers.{DB, DBCursor}
import org.json.JSONObject

case class ConversationRole(label: String, actions: Set[ConversationAction]) {
  def toRoleActions(convId: Option[ConvId]): List[ConversationRoleAction] =
    actions.map(action => ConversationRoleAction(label, action.name, convId)).toList

  override def toString: String = label

  import ConversationAction._

  lazy val canAddGroupMember: Boolean = actions.contains(AddMember)
  lazy val canRemoveGroupMember: Boolean = actions.contains(RemoveMember)
  lazy val canDeleteGroup: Boolean = actions.contains(DeleteConversation)
  lazy val canModifyGroupName: Boolean = actions.contains(ModifyName)
  lazy val canModifyMessageTimer: Boolean = actions.contains(ModifyMessageTimer)
  lazy val canModifyReceiptMode: Boolean = actions.contains(ModifyReceiptMode)
  lazy val canModifyAccess: Boolean = actions.contains(ModifyAccess)
  lazy val canModifyOtherMember: Boolean = actions.contains(ModifyOtherMember)
  lazy val canLeaveConversation: Boolean = actions.contains(LeaveConversation)
}

object ConversationRole {
  import ConversationAction._

  val AdminRole  = ConversationRole("wire_admin", allActions)
  val MemberRole = ConversationRole("wire_member", Set(LeaveConversation))
  val BotRole    = ConversationRole("wire_bot", Set(LeaveConversation))

  val defaultRoles = Set(AdminRole, MemberRole, BotRole)

  def getRole(label: String, defaultRole: ConversationRole = ConversationRole.MemberRole): ConversationRole =
    defaultRoles.find(_.label == label).getOrElse(defaultRole)

  implicit val Decoder: JsonDecoder[(UserId, String)] = new JsonDecoder[(UserId, String)] {
    import com.waz.utils.JsonDecoder._
    override def apply(implicit js: JSONObject): (UserId, String) = (UserId('id), 'conversation_role)
  }

  def decodeUserIdsWithRoles(s: Symbol)(implicit js: JSONObject): Seq[(UserId, String)] = decodeSeq[(UserId, String)](s)

  // usually ids should be exactly the same set as members, but if not, we add surplus ids as members with the Member role
  def membersWithRoles(userIds: Seq[UserId], users: Seq[(UserId, String)]) =
    users.map { case (id, label) => id -> ConversationRole.getRole(label) }.toMap ++ userIds.map(_ -> ConversationRole.MemberRole).toMap
}

case class ConversationAction(name: String) extends Identifiable[String] {
  override def id: String = name
}

object ConversationAction {

  val AddMember          = ConversationAction("add_conversation_member")
  val RemoveMember       = ConversationAction("remove_conversation_member")
  val DeleteConversation = ConversationAction("delete_conversation")
  val ModifyName         = ConversationAction("modify_conversation_name")
  val ModifyMessageTimer = ConversationAction("modify_conversation_message_timer")
  val ModifyReceiptMode  = ConversationAction("modify_conversation_receipt_mode")
  val ModifyAccess       = ConversationAction("modify_conversation_access")
  val ModifyOtherMember  = ConversationAction("modify_other_conversation_member")
  val LeaveConversation  = ConversationAction("leave_conversation")

  val allActions = Set(AddMember, RemoveMember, DeleteConversation, ModifyName, ModifyMessageTimer, ModifyReceiptMode, ModifyAccess, ModifyOtherMember, LeaveConversation)
}

case class ConversationRoleAction(label: String, action: String, convId: Option[ConvId]) extends Identifiable[(String, String, Option[ConvId])] {
  override def id: (String, String, Option[ConvId]) = (label, action, convId)
}

object ConversationRoleAction {
  implicit object ConversationRoleActionDao extends Dao3[ConversationRoleAction, String, String, Option[ConvId]] {
    val Label  = text('label).apply(_.label)
    val Action = text('action).apply(_.action)
    val ConvId = opt(id[ConvId]('conv_id)).apply(_.convId)

    override val idCol = (Label, Action, ConvId)
    override val table = Table("ConversationRoleAction", Label, Action, ConvId)
    override def apply(implicit cursor: DBCursor): ConversationRoleAction = ConversationRoleAction(Label, Action, ConvId)

    def findForConv(convId: Option[ConvId])(implicit db: DB) =
      iterating(if (convId.isDefined) find(ConvId, convId) else findWhereNull(ConvId))

      //iterating(convId.fold(findWhereNull(ConvId))(id => find(ConvId, id)))
  }

}

