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

import com.waz.service.conversation.{FolderDataWithConversations, FoldersService}
import com.waz.sync.SyncResult
import com.waz.sync.client.FoldersClient
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.znet2.http.BodySerializer

import scala.concurrent.Future

class FoldersSyncHandler(foldersClient: FoldersClient, foldersService: FoldersService) {

  private implicit val ec = EventContext.Global

  import Threading.Implicits.Background
  def postFolders(folders: Seq[FolderDataWithConversations])(implicit bs: BodySerializer[Seq[FolderDataWithConversations]]): Future[SyncResult] =
      foldersClient.putFolders(folders).map(SyncResult(_))

}
