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
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.{DB, DBCursor}

case class ConversationRole(label: String, actions: Set[ConversationAction]) {
  def toRoleActions(convId: Option[ConvId]): List[ConversationRoleAction] =
    actions.map(action => ConversationRoleAction(label, action.name, convId)).toList

  import ConversationAction._

  lazy val canAddGroupMember: Boolean = actions.contains(AddMember)
  lazy val canRemoveGroupMember: Boolean = actions.contains(RemoveMember)
  lazy val canDeleteGroup: Boolean = actions.contains(RemoveMember)
  lazy val canModifyGroupName: Boolean = actions.contains(ModifyName)
  lazy val canModifyMessageTimer: Boolean = actions.contains(ModifyMessageTimer)
  lazy val canModifyReceiptMode: Boolean = actions.contains(ModifyReceiptMode)
  lazy val canModifyAccess: Boolean = actions.contains(ModifyAccess)
}

object ConversationRole {
  import ConversationAction._

  val MemberRole = ConversationRole("member_role", Set(AddMember, RemoveMember))
  val AdminRole  = ConversationRole("admin_role", allActions)

  val defaultRoles = Set(MemberRole, AdminRole)
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

  val allActions = Set(AddMember, RemoveMember, DeleteConversation, ModifyName, ModifyMessageTimer, ModifyReceiptMode, ModifyAccess)
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

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)

      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_userid on ConversationMembers (${Label.name})")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_conv on ConversationMembers (${ConvId.name})")
    }

    def findForConv(convId: Option[ConvId])(implicit db: DB) = iterating(find(ConvId, convId))
    def findForConvs(convs: Set[ConvId])(implicit db: DB) = iteratingMultiple(findInSet(ConvId, convs.map(Option(_))))
    def findForRole(role: String)(implicit db: DB) = iterating(find(Label, role))
    def findForRoles(roles: Set[String])(implicit db: DB) = iteratingMultiple(findInSet(Label, roles))

    def findForRoleAndConv(role: String, convId: Option[ConvId])(implicit db: DB) = iterating(
      db.query(table.name, null, s"${Label.name} = $role AND ${ConvId.name} = ${convId.getOrElse("")}", Array(), null, null, null)
    )
  }

}

