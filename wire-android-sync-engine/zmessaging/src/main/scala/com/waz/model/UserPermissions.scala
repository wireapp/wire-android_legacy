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
package com.waz.model

import scala.collection.mutable

object UserPermissions {
  type PermissionsMasks = (Long, Long) //self and copy permissions

  type Permission = Permission.Value
  object Permission extends Enumeration {
    val
    CreateConversation,         // 0x001
    DeleteConversation,         // 0x002
    AddTeamMember,              // 0x004
    RemoveTeamMember,           // 0x008
    AddConversationMember,      // 0x010
    RemoveConversationMember,   // 0x020
    GetBilling,                 // 0x040
    SetBilling,                 // 0x080
    SetTeamData,                // 0x100
    GetMemberPermissions,       // 0x200
    GetTeamConversations,       // 0x400
    DeleteTeam,                 // 0x800
    SetMemberPermissions        // 0x1000
    = Value
  }

  import Permission._
  val AdminPermissions: Set[Permission] = Permission.values -- Set(GetBilling, SetBilling, DeleteTeam)
  val ExternalPermissions: Set[Permission] = Set(CreateConversation, GetTeamConversations)

  def decodeBitmask(mask: Long): Set[Permission] = {
    val builder = new mutable.SetBuilder[Permission, Set[Permission]](Set.empty)
    (0 until Permission.values.size).map(math.pow(2, _).toInt).zipWithIndex.foreach {
      case (one, pos) => if ((mask & one) != 0) builder += Permission(pos)
    }
    builder.result()
  }

  def encodeBitmask(ps: Set[Permission]): Long = {
    var mask = 0L
    (0 until Permission.values.size).map(math.pow(2, _).toLong).zipWithIndex.foreach {
      case (m, i) => if (ps.contains(Permission(i))) mask = mask | m
    }
    mask
  }
}
