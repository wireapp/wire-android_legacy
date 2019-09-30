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
package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.model.ConversationFolderData.ConversationFolderDataDao
import com.waz.model.FolderData.FolderDataDao
import com.waz.model.{ConvId, ConversationFolderData, FolderData, FolderId}
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}
import com.waz.utils.TrimmingLruCache.Fixed

import scala.concurrent.Future

trait FoldersStorage extends CachedStorage[FolderId, FolderData] {
  def getByType(folderType: Int): Future[Seq[FolderData]]
}

class FoldersStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[FolderId, FolderData](new TrimmingLruCache(context, Fixed(1024)), storage)(FolderDataDao, LogTag("FolderStorage_Cached"))
    with FoldersStorage {
  private implicit val dispatcher = new SerialDispatchQueue(name = "FoldersStorage")

  override def getByType(folderType: Int): Future[Seq[FolderData]] = find(_.folderType == folderType, FolderDataDao.findForType(folderType)(_), identity)
}

trait ConversationFoldersStorage extends CachedStorage[(ConvId, FolderId), ConversationFolderData] {
  def findForConv(convId: ConvId): Future[Seq[ConversationFolderData]]
  def findForFolder(folderId: FolderId): Future[Seq[ConversationFolderData]]

  def put(convId: ConvId, folderId: FolderId): Unit
}

class ConversationFoldersStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[(ConvId, FolderId), ConversationFolderData](new TrimmingLruCache(context, Fixed(1024)), storage)(ConversationFolderDataDao, LogTag("ConversationFoldersStorage"))
    with ConversationFoldersStorage {

  override def findForConv(convId: ConvId): Future[Seq[ConversationFolderData]] =
    find(_.convId == convId, ConversationFolderDataDao.findForConv(convId)(_), identity)

  override def findForFolder(folderId: FolderId): Future[Seq[ConversationFolderData]] =
    find(_.folderId == folderId, ConversationFolderDataDao.findForFolder(folderId)(_), identity)

  override def put(convId: ConvId, folderId: FolderId): Unit =
    put((convId, folderId), ConversationFolderData(convId, folderId))
}