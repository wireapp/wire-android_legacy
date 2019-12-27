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
import com.waz.model.{Name, TeamData, UserData}

case class ConnectionViewItem(data: ConnectionViewModel) extends SearchViewItem {

  import SearchViewItem._
  import SectionViewItem._

  override def section: Int = if (data.isConnected) ContactsSection else DirectorySection

  override def index: Int = data.indexVal

  override def itemType: Int = if (data.isConnected) ConnectedUser else UnconnectedUser

  override def id: Long = data.idVal

  override def name: model.Name = if (data.isConnected) data.name else super.name
}

case class ConnectionViewModel(indexVal:              Int,
                               idVal:                 Long,
                               isConnected:           Boolean,
                               shouldHideUserStatus:  Boolean,
                               results:               Seq[UserData],
                               name:                  Name = Name.Empty,
                               team:                  Option[TeamData])
