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
package com.waz.service.conversation

import com.waz.content.{ConversationFoldersStorage, FoldersStorage}
import com.waz.model.{ConvId, FolderData, FolderId}
import com.waz.threading.Threading

import scala.concurrent.Future

trait FoldersService {
  def ensureFavouritesFolderExists(): Future[Unit]

  def favouritesFolder: Future[FolderData]
  def folders: Future[Seq[FolderData]]
  def convsInFolder(folderId: FolderId): Future[Seq[ConvId]]

  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]
  def move(convId: ConvId, folderId: FolderId): Future[Unit]
  def remove(convId: ConvId): Future[Unit]
}

class FoldersServiceImpl(foldersStorage: FoldersStorage,
                         conversationFoldersStorage: ConversationFoldersStorage) extends FoldersService {
  import Threading.Implicits.Background

  override def ensureFavouritesFolderExists(): Future[Unit] =
    foldersStorage.getByType(FolderData.FavouritesFolderType).map {
      case Nil => foldersStorage.put(FolderData.FavouritesFolder.id, FolderData.FavouritesFolder)
      case _   =>
    }

  override def favouritesFolder: Future[FolderData] =
    foldersStorage.getByType(FolderData.FavouritesFolderType).flatMap {
      case head :: _ => Future.successful(head)
      case Nil       => foldersStorage.put(FolderData.FavouritesFolder.id, FolderData.FavouritesFolder)
    }

  override def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean] =
    conversationFoldersStorage.get((convId, folderId)).map(_.nonEmpty)

  override def move(convId: ConvId, folderId: FolderId): Future[Unit] =
    remove(convId).map(_ => conversationFoldersStorage.put(convId, folderId))

  override def remove(convId: ConvId): Future[Unit] = for {
    convFolders <- conversationFoldersStorage.findForConv(convId)
    _           <- conversationFoldersStorage.removeAll(convFolders.map(_.id))
  } yield ()

  override def folders: Future[Seq[FolderData]] = foldersStorage.list()

  override def convsInFolder(folderId: FolderId): Future[Seq[ConvId]] =
    conversationFoldersStorage.findForFolder(folderId).map(_.map(_.convId))
}
