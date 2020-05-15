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
package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.Handle
import com.waz.service.{SearchQuery, UserSearchService}
import com.waz.sync.SyncResult
import com.waz.sync.client.{UserSearchClient, UsersClient}
import com.waz.threading.Threading

import scala.concurrent.Future

class UserSearchSyncHandler(userSearch:  UserSearchService,
                            client:      UserSearchClient,
                            usersClient: UsersClient
                           )
  extends DerivedLogTag {

  import Threading.Implicits.Background

  def syncSearchQuery(query: SearchQuery): Future[SyncResult] = client.search(query).future.map {
    case Right(results) =>
      debug(l"syncSearchQuery got: ${results.documents.map(_.team)}")
      userSearch.updateSearchResults(query, results)
      SyncResult.Success
    case Left(error) =>
      SyncResult(error)
  }

  def exactMatchHandle(handle: Handle): Future[SyncResult] = usersClient.loadByHandle(handle).future.map {
    case Right(Some(user)) =>
      debug(l"exactMatchHandle, got: ${user.id} for the handle $handle")
      userSearch.updateExactMatch(user)
      SyncResult.Success
    case Right(None) =>
      debug(l"exactMatchHandle, No user id for the handle $handle")
      SyncResult.Success
    case Left(error) =>
      SyncResult(error)
  }
}
