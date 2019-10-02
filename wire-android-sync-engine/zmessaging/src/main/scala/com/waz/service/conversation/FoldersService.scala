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

  /**
    * Returns list of all custom folders. Does not include the favourites special folder.
    * @return
    */
  def folders: Future[Seq[FolderData]]
  def convsInFolder(folderId: FolderId): Future[Seq[ConvId]]

  /**
    * Returns the list of folders that contains the conversation. Does not include
    * the favourite special folder
    * @param convId
    * @return
    */
  def foldersForConv(convId: ConvId): Future[Seq[FolderId]]

  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]
  def addToFolder(convId: ConvId, folderId: FolderId): Unit
  def removeFromFolder(convId: ConvId, folderId: FolderId): Future[Unit]

  /**
    * Removes a conversation from all folders (excluding the favourites)
    * @param convId
    * @return
    */
  def removeFromAllFolders(convId: ConvId): Future[Unit]

  /**
    * Removes a conversation from all folders (excluding the favourites) and adds it to another one
    * @param convId
    * @param folderId
    * @return
    */
  def moveToFolder(convId: ConvId, folderId: FolderId): Future[Unit]

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

  override def addToFolder(convId: ConvId, folderId: FolderId): Unit =
    conversationFoldersStorage.put(convId, folderId)

  override def removeFromFolder(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.remove((convId, folderId))

  override def removeFromAllFolders(convId: ConvId): Future[Unit] = for {
    allFolders <- foldersForConv(convId)
    _ <- conversationFoldersStorage.removeAll(allFolders.map((convId, _)))
  } yield ()

  override def foldersForConv(convId: ConvId): Future[scala.Seq[FolderId]] = for {
    favourite <- favouritesFolder
    allFolders <- conversationFoldersStorage.findForConv(convId).map(_.map(_.folderId))
      .map(_.filter(_ != favourite.id))
  } yield allFolders

  override def moveToFolder(convId: ConvId, folderId: FolderId): Future[Unit] = for {
    _ <- removeFromAllFolders(convId)
  } yield addToFolder(convId, folderId)

  override def folders: Future[Seq[FolderData]] = foldersStorage.list()
    .map(_.filter(_.folderType == FolderData.CustomFolderType))

  override def convsInFolder(folderId: FolderId): Future[Seq[ConvId]] =
    conversationFoldersStorage.findForFolder(folderId).map(_.map(_.convId))

  override def addToFavourite(convId: ConvId): Future[Unit] = for {
    favourite <- favouritesFolder
    _ = addToFolder(convId, favourite.id)
  } yield()

  override def removeFromFavourite(convId: ConvId): Future[Unit] = for {
    favourite <- favouritesFolder
    _ = removeFromFolder(convId, favourite.id)
  } yield()

  override def favouriteConversations: Future[Seq[ConvId]] = for {
    favourite <- favouritesFolder
    convsData <- conversationFoldersStorage.findForFolder(favourite.id)
  } yield convsData.map(_.convId)

}
