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

  // returns all folders. The "favourites folder" is not included.
  def folders: Future[Seq[FolderData]]
  def convsInFolder(folderId: FolderId): Future[Seq[ConvId]]

  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]
  def add(convId: ConvId, folderId: FolderId): Unit
  def remove(convId: ConvId, folderId: FolderId): Future[Unit]

  def favouriteConversations: Future[Seq[ConvId]]
  def addToFavourite(convId: ConvId): Future[Unit]
  def removeFromFavourite(convId: ConvId): Future[Unit]

}

class FoldersServiceImpl(foldersStorage: FoldersStorage,
                         conversationFoldersStorage: ConversationFoldersStorage) extends FoldersService {
  import Threading.Implicits.Background

  private def favouritesFolder: Future[FolderData] =
    foldersStorage.getByType(FolderData.FavouritesFolderType).flatMap {
      case head :: _ => Future.successful(head)
      case Nil       => {
        val folderData = FolderData(FolderId(), "", FolderData.FavouritesFolderType)
        foldersStorage.put(folderData.id, folderData)
      }
    }

  override def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean] =
    conversationFoldersStorage.get((convId, folderId)).map(_.nonEmpty)

  override def add(convId: ConvId, folderId: FolderId): Unit =
    conversationFoldersStorage.put(convId, folderId)

  override def remove(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.remove((convId, folderId))

  override def folders: Future[Seq[FolderData]] = foldersStorage.list()
    .map(_.filter(_.folderType == FolderData.CustomFolderType))

  override def convsInFolder(folderId: FolderId): Future[Seq[ConvId]] =
    conversationFoldersStorage.findForFolder(folderId).map(_.map(_.convId))

  override def addToFavourite(convId: ConvId): Future[Unit] = for {
    favourite <- favouritesFolder
    _ = add(convId, favourite.id)
  } yield()

  override def removeFromFavourite(convId: ConvId): Future[Unit] = for {
    favourite <- favouritesFolder
    _ = remove(convId, favourite.id)
  } yield()

  override def favouriteConversations: Future[Seq[ConvId]] = for {
    favourite <- favouritesFolder
    convsData <- conversationFoldersStorage.findForFolder(favourite.id)
  } yield convsData.map(_.convId)

}
