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

import com.waz.model.UserData

case class TopUserViewItem(data: TopUserViewModel) extends SearchViewItem {

  import SearchViewItem._
  import SectionViewItem._

  override def section: Int = TopUsersSection

  override def index: Int = data.indexVal

  override def itemType: Int = TopUsers
}

case class TopUserViewModel(indexVal: Int,
                            topUsers: Seq[UserData])

case class TopUserButtonViewItem(data: TopUserButtonViewModel) extends SearchViewItem {

  override def section: Int = data.section

  override def index: Int = data.indexVal

  override def itemType: Int = data.itemType
}

case class TopUserButtonViewModel(itemType: Int,
                                  section: Int,
                                  indexVal: Int)
