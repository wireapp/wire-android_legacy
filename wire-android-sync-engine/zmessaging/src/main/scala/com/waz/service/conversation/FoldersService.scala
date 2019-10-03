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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, FolderData, FolderId, Name}
import com.waz.threading.Threading
import com.waz.utils.events.{AggregatingSignal, EventContext, EventStream, Signal}
import com.waz.log.LogSE._

import scala.concurrent.Future

trait FoldersService {
  def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFrom(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFromAll(convId: ConvId): Future[Unit]
  def convsInFolder(folderId: FolderId): Future[Set[ConvId]]
  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]

  def favouritesFolderId: Future[Option[FolderId]]
  def folders: Future[Seq[FolderData]]
  def addFolder(folderName: Name): Future[FolderId]
  def removeFolder(folderId: FolderId): Future[Unit]
  def addFavouritesFolder(): Future[FolderId]
  def removeFavouritesFolder(): Future[Unit]

  def foldersForConv(convId: ConvId): Future[Set[FolderId]]

  def foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]]
  def folder(folderId: FolderId): Signal[Option[FolderData]]
}

class FoldersServiceImpl(foldersStorage: FoldersStorage,
                         conversationFoldersStorage: ConversationFoldersStorage) extends FoldersService with DerivedLogTag {
  import Threading.Implicits.Background
  private implicit val ev = EventContext.Global

  override def favouritesFolderId: Future[Option[FolderId]] =
    foldersStorage.getByType(FolderData.FavouritesFolderType).map {
      case head :: _ => Some(head.id)
      case Nil       => None
    }

  override def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.put(convId, folderId)

  override def removeConversationFrom(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.remove((convId, folderId))

  override def removeConversationFromAll(convId: ConvId): Future[Unit] = for {
    allFolders <- foldersForConv(convId)
    _          <- conversationFoldersStorage.removeAll(allFolders.map((convId, _)))
  } yield ()

  override def foldersForConv(convId: ConvId): Future[Set[FolderId]] =
    conversationFoldersStorage.findForConv(convId)
  
  override def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean] =
    conversationFoldersStorage.get((convId, folderId)).map(_.nonEmpty)

  override def convsInFolder(folderId: FolderId): Future[Set[ConvId]] =
    conversationFoldersStorage.findForFolder(folderId)

  override def folder(folderId: FolderId): Signal[Option[FolderData]] =
    foldersStorage.optSignal(folderId)

  override def addFolder(folderName: Name): Future[FolderId] = {
    val folder = FolderData(name = folderName)
    foldersStorage.put(folder.id, folder).map(_.id)
  }

  override def removeFolder(folderId: FolderId): Future[Unit] = for {
    convIds <- convsInFolder(folderId)
    _       <- conversationFoldersStorage.removeAll(convIds.map((_, folderId)))
    _       <- foldersStorage.remove(folderId)
  } yield ()

  override def addFavouritesFolder(): Future[FolderId] = favouritesFolderId.map {
    case None =>
      val folderData = FolderData(FolderId(), "", FolderData.FavouritesFolderType)
      foldersStorage.put(folderData.id, folderData).map(_ => folderData.id)
      folderData.id
    case Some(id) => id
  }

  override def removeFavouritesFolder(): Future[Unit] = favouritesFolderId.flatMap {
    case Some(id) => removeFolder(id)
    case None     => Future.successful(())
  }

  override def folders: Future[Seq[FolderData]] = foldersStorage.list()

  override val foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]] = {
    def changesStream: EventStream[(Set[FolderId], Set[FolderId], Map[FolderId, Set[ConvId]], Map[FolderId, Set[ConvId]])] = EventStream.union(
      foldersStorage.onDeleted.map(cs => (cs.toSet, Set.empty, Map.empty, Map.empty)),
      foldersStorage.onAdded.map(cs => (Set.empty, cs.map(_.id).toSet, Map.empty, Map.empty)),
      conversationFoldersStorage.onDeleted.map(cs => (Set.empty, Set.empty, cs.groupBy(_._2).mapValues(_.map(_._1).toSet), Map.empty)),
      conversationFoldersStorage.onAdded.map(cs => (Set.empty, Set.empty, Map.empty, cs.map(_.id).groupBy(_._2).mapValues(_.map(_._1).toSet)))
    )

    def loadAll = for {
      folders     <- foldersStorage.list()
      folderConvs <- Future.sequence(folders.map(folder => conversationFoldersStorage.findForFolder(folder.id).map(convIds => folder.id -> convIds)))
    } yield folderConvs.toMap

    new AggregatingSignal[
      (Set[FolderId], Set[FolderId], Map[FolderId, Set[ConvId]], Map[FolderId, Set[ConvId]]),
      Map[FolderId, Set[ConvId]]
    ](changesStream, loadAll, { case (current, (deletedFolderIds, addedFolderIds, removedConvIds, addedConvIds)) =>

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
    }).disableAutowiring()
  }
}
