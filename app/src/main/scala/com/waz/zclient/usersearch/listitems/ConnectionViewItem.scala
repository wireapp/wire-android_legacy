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

import com.waz.model
import com.waz.model.{TeamId, UserData}

case class ConnectionViewItem(override val index: Int,
                              user:               UserData,
                              selfTeamId:         Option[TeamId],
                              connected:          Boolean
                             ) extends SearchViewItem {
  import SearchViewItem._
  import SectionViewItem._

  override val id: Long         = user.id.str.hashCode
  override val section: Int     = if (connected) ContactsSection else DirectorySection
  override val itemType: Int    = if (connected) ConnectedUser else UnconnectedUser
  override val name: model.Name = if (connected) user.name else super.name
}
