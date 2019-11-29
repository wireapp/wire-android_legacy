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
import com.waz.sync.client.UserSearchClient
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.concurrent.Future.successful

class UserSearchSyncHandler(userSearch: UserSearchService,
                            client: UserSearchClient,
                            usersSyncHandler: UsersSyncHandler) extends DerivedLogTag {

  import Threading.Implicits.Background

  def syncSearchQuery(query: SearchQuery): Future[SyncResult] = {
    debug(l"starting sync for: $query")
    client.getContacts(query).future flatMap {
      case Right(results) =>
        userSearch.updateSearchResults(query, results)
          .map(_ => SyncResult.Success)
      case Left(error) =>
        successful(SyncResult(error))
    }
  }

  def exactMatchHandle(handle: Handle): Future[SyncResult] = client.exactMatchHandle(handle).future.flatMap {
    case Right(Some(userId)) =>
      debug(l"exactMatchHandle, got: $userId for the handle $handle")
      for {
        _ <- usersSyncHandler.syncUsers(userId)
        _ <- userSearch.updateExactMatch(userId)
      } yield SyncResult.Success
    case Right(None)         => successful(SyncResult.Success)
    case Left(error)         => successful(SyncResult(error))
  }
}
