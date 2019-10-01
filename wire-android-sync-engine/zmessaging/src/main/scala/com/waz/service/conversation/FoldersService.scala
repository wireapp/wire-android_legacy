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
import com.waz.utils.events.{AggregatingSignal, EventContext, Signal}

import scala.concurrent.Future

trait FoldersService {
  def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFrom(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFromAll(convId: ConvId): Future[Unit]
  def moveConversationToCustomFolder(convId: ConvId, folderId: FolderId): Future[Unit]
  def convsInFolder(folderId: FolderId): Future[Set[ConvId]]
  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]

  def favouritesFolderId: Future[FolderId]
  def folders: Future[Seq[FolderData]]
  def addFolder(folder: FolderData): Future[Unit]
  def removeFolder(folderId: FolderId): Future[Unit]

  def foldersForConv(convId: ConvId): Future[Set[FolderId]]

  def foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]]
  def folder(folderId: FolderId): Signal[Option[FolderData]]
}

class FoldersServiceImpl(foldersStorage: FoldersStorage,
                         conversationFoldersStorage: ConversationFoldersStorage) extends FoldersService {
  import Threading.Implicits.Background
  private implicit val ev = EventContext.Global

  override def favouritesFolderId: Future[FolderId] =
    foldersStorage.getByType(FolderData.FavouritesFolderType).flatMap {
      case head :: _ => Future.successful(head.id)
      case Nil       =>
        val folderData = FolderData(FolderId(), "", FolderData.FavouritesFolderType)
        foldersStorage.put(folderData.id, folderData).map(_ => folderData.id)
    }

  override def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.put(convId, folderId)

  override def removeConversationFrom(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.remove((convId, folderId))

  override def removeConversationFromAll(convId: ConvId): Future[Unit] = for {
    allFolders <- foldersForConv(convId)
    _          <- conversationFoldersStorage.removeAll(allFolders.map((convId, _)))
  } yield ()

  override def moveConversationToCustomFolder(convId: ConvId, folderId: FolderId): Future[Unit] = for {
    favId         <- favouritesFolderId
    allFolders    <- foldersForConv(convId)
    customFolders =  allFolders.filterNot(_ == favId)
    _             <- conversationFoldersStorage.removeAll(customFolders.map((convId, _)))
    _             =  addConversationTo(convId, folderId)
  } yield ()

  override def foldersForConv(convId: ConvId): Future[Set[FolderId]] = for {
    favId      <- favouritesFolderId
    allFolders <- conversationFoldersStorage.findForConv(convId)
  } yield allFolders
  
  override def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean] =
    conversationFoldersStorage.get((convId, folderId)).map(_.nonEmpty)

  override def convsInFolder(folderId: FolderId): Future[Set[ConvId]] =
    conversationFoldersStorage.findForFolder(folderId)

  override def folder(folderId: FolderId): Signal[Option[FolderData]] =
    foldersStorage.optSignal(folderId)

  override def addFolder(folder: FolderData): Future[Unit] =
    foldersStorage.put(folder.id, folder).map(_ => ())

  override def removeFolder(folderId: FolderId): Future[Unit] =
    foldersStorage.remove(folderId)

  override def folders: Future[Seq[FolderData]] = foldersStorage.list()

  override val foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]] = {
    def changesStream = for {
      deletedFolderIds <- foldersStorage.onDeleted.map(_.toSet)
      addedFolderIds   <- foldersStorage.onAdded.map(_.map(_.id).toSet)
      removedConvIds   <- conversationFoldersStorage.onDeleted.map(_.groupBy(_._2).mapValues(_.map(_._1).toSet))
      addedConvIds     <- conversationFoldersStorage.onAdded.map(_.map(_.id).groupBy(_._2).mapValues(_.map(_._1).toSet))
    } yield (deletedFolderIds, addedFolderIds, removedConvIds, addedConvIds)

    def loadAll = for {
      folders     <- foldersStorage.list()
      folderConvs <- Future.sequence(folders.map(folder => conversationFoldersStorage.findForFolder(folder.id).map(convIds => folder.id -> convIds)))
    } yield folderConvs.toMap

    new AggregatingSignal[
      (Set[FolderId], Set[FolderId], Map[FolderId, Set[ConvId]], Map[FolderId, Set[ConvId]]),
      Map[FolderId, Set[ConvId]]
    ](
      changesStream,
      loadAll,
      { case (current, (deletedFolderIds, addedFolderIds, removedConvIds, addedConvIds)) =>

        // Step 1: remove deleted folders and add new ones
        val step1 = current -- deletedFolderIds ++ addedFolderIds.map(_ -> Set.empty[ConvId]).toMap

        // Step 2: remove conversations from folders
        val step2 = step1.map {
          case (folderId, convIds) if removedConvIds.contains(folderId) =>
           (folderId, convIds -- removedConvIds(folderId))
          case other => other
        }

        // Step 3: add conversations to folders
        step2.map {
          case (folderId, convIds) if addedConvIds.contains(folderId) =>
            (folderId, convIds ++ addedConvIds(folderId))
          case other => other
        }
      })
  }
}
