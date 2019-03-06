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
package com.waz.zclient.utils

import com.evernote.android.job.Job
import com.waz.log.ZLog2._
import com.waz.zclient.messages.UsersController.DisplayName
import com.waz.zclient.search.SearchController.SearchUserListState

/**
  * A collection of implicit `LogShow` instances for UI types.
  */
object UILogShow {

  implicit val JobLogShow: LogShow[Job] = LogShow.logShowWithHash

  implicit val SearchUserListStateLogShow: LogShow[SearchUserListState] =
    LogShow.createFrom {
      case SearchUserListState.NoUsers => l"NoUsers"
      case SearchUserListState.NoUsersFound => l"NoUsersFound"
      case SearchUserListState.Users(us) => l"Users(us: $us)"
      case SearchUserListState.NoServices => l"NoServices"
      case SearchUserListState.NoServicesFound => l"NoServicesFound"
      case SearchUserListState.LoadingServices => l"LoadingServices"
      case SearchUserListState.Services(ss) => l"Services(ss: $ss)"
      case SearchUserListState.Error(err) => l"Error(err: $err)"
    }

  implicit val DisplayNameLogShow: LogShow[DisplayName] =
    LogShow.createFrom {
      case DisplayName.Me => l"Me"
      case DisplayName.Other(name) => l"Other(name: ${redactedString(name)})"
    }
}
