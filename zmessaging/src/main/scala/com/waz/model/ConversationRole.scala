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

import com.waz.db.Col.{id, text}
import com.waz.db.Dao3
import com.waz.utils.JsonDecoder.decodeSeq
import com.waz.utils.{Identifiable, JsonDecoder}
import com.waz.utils.wrappers.{DB, DBCursor}
import org.json.JSONObject

case class ConversationRole(label: String, actions: Set[ConversationAction]) {
  def toRoleActions(convId: ConvId): List[ConversationRoleAction] =
    actions.map(action => ConversationRoleAction(label, action.name, convId)).toList

  override def toString: String = label

  import ConversationAction._

  lazy val canAddGroupMember: Boolean     = actions.contains(AddMember)
  lazy val canRemoveGroupMember: Boolean  = actions.contains(RemoveMember)
  lazy val canDeleteGroup: Boolean        = actions.contains(DeleteConversation)
  lazy val canModifyGroupName: Boolean    = actions.contains(ModifyName)
  lazy val canModifyMessageTimer: Boolean = actions.contains(ModifyMessageTimer)
  lazy val canModifyReceiptMode: Boolean  = actions.contains(ModifyReceiptMode)
  lazy val canModifyAccess: Boolean       = actions.contains(ModifyAccess)
  lazy val canModifyOtherMember: Boolean  = actions.contains(ModifyOtherMember)
  lazy val canLeaveConversation: Boolean  = actions.contains(LeaveConversation)
}

object ConversationRole {
  import ConversationAction._

  val AdminRole  = ConversationRole("wire_admin", allActions)
  val MemberRole = ConversationRole("wire_member", Set(LeaveConversation))
  val BotRole    = ConversationRole("wire_bot", Set(LeaveConversation))

  val defaultRoles = Set(AdminRole, MemberRole, BotRole)

  def getRole(label: String, defaultRole: ConversationRole = ConversationRole.MemberRole): ConversationRole =
    defaultRoles.find(_.label == label).getOrElse(defaultRole)

  def decodeUserIdsWithRoles(s: Symbol)(implicit js: JSONObject): Map[UserId, ConversationRole] = {
    implicit val decoder: JsonDecoder[(UserId, String)] = new JsonDecoder[(UserId, String)] {
      import com.waz.utils.JsonDecoder._
      override def apply(implicit js: JSONObject): (UserId, String) = (UserId('id), 'conversation_role)
    }

    decodeSeq[(UserId, String)](s).map { case (id, label) => id -> getRole(label) }.toMap
  }

  def decodeQualifiedIdsWithRoles(s: Symbol)(implicit js: JSONObject): Map[QualifiedId, ConversationRole] = {
    implicit val decoder: JsonDecoder[(QualifiedId, String)] = new JsonDecoder[(QualifiedId, String)] {
      import com.waz.utils.JsonDecoder._
      override def apply(implicit js: JSONObject): (QualifiedId, String) =
        (QualifiedId.decodeOpt('qualified_id).getOrElse(QualifiedId(UserId('id))), 'conversation_role)
    }

    decodeSeq[(QualifiedId, String)](s).map { case (id, label) => id -> getRole(label) }.toMap
  }

  def fromRoleActions(roleActions: Iterable[ConversationRoleAction]): Map[ConvId, Set[ConversationRole]] =
    roleActions.groupBy(_.convId).mapValues {
      _.groupBy(_.label).map {
          case (label, actions) =>
            val actionNames = actions.map(_.action).toSet
            ConversationRole(label, ConversationAction.allActions.filter(ca => actionNames.contains(ca.name)))
        }.toSet
    }
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

case class ConversationRoleAction(label: String, action: String, convId: ConvId) extends Identifiable[(String, String, ConvId)] {
  override def id: (String, String, ConvId) = (label, action, convId)
}

object ConversationRoleAction {

  implicit object ConversationRoleActionDao extends Dao3[ConversationRoleAction, String, String, ConvId] {
    val Label  = text('label).apply(_.label)
    val Action = text('action).apply(_.action)
    val ConvId = id[ConvId]('conv_id).apply(_.convId)

    override val idCol = (Label, Action, ConvId)
    override val table = Table("ConversationRoleAction", Label, Action, ConvId)
    override def apply(implicit cursor: DBCursor): ConversationRoleAction = ConversationRoleAction(Label, Action, ConvId)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationRoleAction_convid on ConversationRoleAction (${ConvId.name})")
    }

    def findForConv(convId: ConvId)(implicit db: DB) = iterating(find(ConvId, convId))
  }
}

