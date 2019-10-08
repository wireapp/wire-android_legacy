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
import com.waz.service.conversation.RemoteFolderData.IntermediateFolderData
import com.waz.sync.SyncResult
import com.waz.sync.client.PropertiesClient
import com.waz.threading.Threading
import com.waz.utils.events.EventContext

import scala.concurrent.Future

class FoldersSyncHandler(prefsClient: PropertiesClient, foldersService: FoldersService) {
  private implicit val ec = EventContext.Global

  import Threading.Implicits.Background
  import com.waz.znet2.http.HttpClient.AutoDerivation._

  def postFolders(): Future[SyncResult] =
    foldersService.foldersToSynchronize().flatMap(folders => prefsClient.putProperty(PropertyKey.Folders, folders)).map(SyncResult(_))

  def syncFolders(): Future[SyncResult] = {
    prefsClient.getProperty[Seq[IntermediateFolderData]](PropertyKey.Folders).future.flatMap {
      case Right(Some(folders)) =>
        foldersService.processFolders(folders.map(_.toRemoteFolderData)).map(_ => SyncResult.Success)
      case Right(None) => Future.successful(SyncResult.Success)
      case Left(e) => Future.successful(SyncResult(e))
    }
  }
}
