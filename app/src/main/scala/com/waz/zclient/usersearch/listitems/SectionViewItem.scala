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

import com.waz.model.{Domain, Name}

final case class SectionViewItem(override val section: Int,
                                 override val index:   Int,
                                 override val name:    Name = Name.Empty,
                                 federatedDomain:      Domain = Domain.Empty
                                )
  extends SearchViewItem {
  import SearchViewItem._

  override val itemType: Int = SectionHeader
}

object SectionViewItem {
  val TopUsersSection           = 0
  val GroupConversationsSection = 1
  val ContactsSection           = 2
  val DirectorySection          = 3
  val IntegrationsSection       = 4
}
