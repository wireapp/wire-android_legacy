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
package com.waz.zclient.usersearch.listitems

import com.waz.model.Name

trait SearchViewItem {
  def section: Int
  def index: Int
  def itemType: Int

  def id: Long = itemType + section + index
  def name: Name = Name.Empty
}

object SearchViewItem {

  //Item types
  val TopUsers: Int = 0
  val ConnectedUser: Int = 1
  val UnconnectedUser: Int = 2
  val GroupConversation: Int = 3
  val SectionHeader: Int = 4
  val Expand: Int = 5
  val Integration: Int = 6
  val NewConversation: Int = 7
  val NewGuestRoom: Int = 8
  val ManageServices: Int = 9

  //Constants
  val CollapsedContacts = 5
  val CollapsedGroups = 5
}
