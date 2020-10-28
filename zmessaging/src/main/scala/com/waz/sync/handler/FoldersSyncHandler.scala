/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.waz.sync.handler

import com.waz.service.PropertyKey
import com.waz.service.conversation.FoldersService
import com.waz.service.conversation.FoldersService.FoldersProperty
import com.waz.sync.SyncResult
import com.waz.sync.client.PropertiesClient
import com.waz.threading.Threading
import com.wire.signals.EventContext
import io.circe.generic.auto._

import scala.concurrent.Future

class FoldersSyncHandler(prefsClient: PropertiesClient, foldersService: FoldersService) {
  import Threading.Implicits.Background
  import com.waz.znet2.http.HttpClient.AutoDerivation._

  def postFolders(): Future[SyncResult] =
    for {
      folders <- foldersService.foldersToSynchronize()
      res     <- prefsClient.putProperty(PropertyKey.Folders, FoldersProperty.fromRemote(folders))
    } yield SyncResult(res)

  def syncFolders(): Future[SyncResult] =
    prefsClient.getProperty[FoldersProperty](PropertyKey.Folders).future.flatMap {
      case Right(Some(foldersProperty)) =>
        foldersService.processFolders(foldersProperty.toRemote).map(_ => SyncResult.Success)
      case Right(None) => Future.successful(SyncResult.Success)
      case Left(e) => Future.successful(SyncResult(e))
    }
}
