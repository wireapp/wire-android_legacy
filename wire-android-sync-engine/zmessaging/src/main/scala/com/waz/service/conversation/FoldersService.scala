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

import com.waz.content.ConversationStorage
import com.waz.log.LogSE._
import com.waz.model.{ConvId, ConversationFolderData, FolderData, FolderId, FoldersEvent, Name, RemoteFolderData}
import com.waz.service.EventScheduler
import com.waz.service.EventScheduler.Stage
import com.waz.content.{ConversationFoldersStorage, FoldersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.utils.RichFuture
import com.waz.utils.events.{AggregatingSignal, EventContext, EventStream, Signal}

import scala.concurrent.Future

trait FoldersService {
  def eventProcessingStage: EventScheduler.Stage
  def processEvent(event: FoldersEvent): Future[Unit]

  def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFrom(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFromAll(convId: ConvId): Future[Unit]
  def convsInFolder(folderId: FolderId): Future[Set[ConvId]]
  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]

  def favouritesFolderId: Signal[Option[FolderId]]
  def folders: Future[Seq[FolderData]]
  def addFolder(folderName: Name): Future[FolderId]
  def removeFolder(folderId: FolderId): Future[Unit]
  def ensureFavouritesFolder(): Future[FolderId]
  def removeFavouritesFolder(): Future[Unit]
  def update(folderId: FolderId, folderName: Name): Future[Unit]

  def foldersForConv(convId: ConvId): Future[Set[FolderId]]

  def foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]]
  def folder(folderId: FolderId): Signal[Option[FolderData]]
}

class FoldersServiceImpl(foldersStorage: FoldersStorage,
                         conversationFoldersStorage: ConversationFoldersStorage,
                         conversationStorage: ConversationStorage) extends FoldersService with DerivedLogTag  {
  import Threading.Implicits.Background
  private implicit val ev: EventContext = EventContext.Global

  override val eventProcessingStage: Stage.Atomic = EventScheduler.Stage[FoldersEvent] { (_, events) =>
    verbose(l"Handling events: $events")
    RichFuture.traverseSequential(events)(processEvent)
  }

  override def processEvent(event: FoldersEvent): Future[Unit] =
    for {
      newFolders      <- Future.sequence(event.folders.map { case RemoteFolderData(data, rConvIds) =>
                           conversationStorage.getByRemoteIds(rConvIds).map(ids => data.id -> (data, ids.toSet))
                         }).map(_.toMap)
      currentFolders  <- foldersStorage.list().map(_.map(folder => folder.id -> folder).toMap)
      foldersToDelete =  currentFolders.keySet -- newFolders.keySet
      _               <- Future.sequence(foldersToDelete.map(removeFolder))
      foldersToAdd    =  newFolders.filterKeys(id => !currentFolders.contains(id)).values
      _               <- Future.sequence(foldersToAdd.map { case (data, convIds) =>
                           foldersStorage.insert(data).flatMap(_ => addAllConversationsTo(convIds, data.id))
                         })
      foldersToUpdate =  newFolders.collect { case (id, (data, _)) if currentFolders.contains(id) && data.name != currentFolders(id).name => data }
      _               <- Future.sequence(foldersToUpdate.map(folder => update(folder.id, folder.name)))
      // at this point the list of folders in newFolders should be the same as in the storage, so we can use newFolders to get currentConvs
      currentConvs    <- Future.sequence(newFolders.keys.map(id =>
                           conversationFoldersStorage.findForFolder(id).map(convIds => id -> convIds)
                         )).map(_.toMap)
      convsToDelete   =  currentConvs.flatMap { case (folderId, convIds) =>
                           val convsToDelete = convIds -- newFolders(folderId)._2
                           if (convsToDelete.nonEmpty) Some(folderId -> convsToDelete) else None
                         }
      _               <- Future.sequence(convsToDelete.flatMap { case (folderId, convIds) =>
                           convIds.map(removeConversationFrom(_, folderId))
                         })
      convsToAdd      =  currentConvs.flatMap { case (folderId, convIds) =>
                           val convsToAdd = newFolders(folderId)._2 -- convIds
                           if (convsToAdd.nonEmpty) Some(folderId -> convsToAdd) else None
                         }
      _               <- Future.sequence(convsToAdd.flatMap { case (folderId, convIds) =>
                           convIds.map(addConversationTo(_, folderId))
                         })
    } yield ()

  override def favouritesFolderId: Signal[Option[FolderId]] =
    for {
      foldersWithConvs <- foldersWithConvs
      folderDataOpt    <- Signal.sequence(foldersWithConvs.keys.toSeq.map(folder):_*)
      folderData        = folderDataOpt.flatten
      favoriteFolderId  = folderData.find(_.folderType == FolderData.FavouritesFolderType).map(_.id)
    } yield favoriteFolderId


  override def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.put(convId, folderId)

  private def addAllConversationsTo(convIds: Set[ConvId], folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.insertAll(convIds.map(id => ConversationFolderData(id, folderId))).map(_ => ())

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
    addFolder(folderName, FolderData.CustomFolderType)
  }

  private def addFolder(folderName: Name, folderType: Int): Future[FolderId] = {
    val folder = FolderData(name = folderName, folderType = folderType)
    foldersStorage.put(folder.id, folder).map(_.id)
  }

  override def removeFolder(folderId: FolderId): Future[Unit] = for {
    convIds <- convsInFolder(folderId)
    _       <- conversationFoldersStorage.removeAll(convIds.map((_, folderId)))
    _       <- foldersStorage.remove(folderId)
  } yield ()

  override def ensureFavouritesFolder(): Future[FolderId] = favouritesFolderId.head.flatMap {
    case Some(x) => Future.successful(x)
    case None => addFolder("", FolderData.FavouritesFolderType)
  }

  override def removeFavouritesFolder(): Future[Unit] = favouritesFolderId.head.flatMap {
    case Some(id) => removeFolder(id)
    case None     => Future.successful(())
  }

  override def update(folderId: FolderId, folderName: Name): Future[Unit] =
    foldersStorage.update(folderId, _.copy(name = folderName)).map(_ => ())

  override def folders: Future[Seq[FolderData]] = foldersStorage.list()

  override val foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]] = {
    def changesStream: EventStream[(Set[FolderId], Set[FolderId], Map[FolderId, Set[ConvId]], Map[FolderId, Set[ConvId]])] =
      EventStream.union(
        foldersStorage.onDeleted.map            (cs => (cs.toSet,  Set.empty,          Map.empty,                                     Map.empty                                              )),
        foldersStorage.onAdded.map              (cs => (Set.empty, cs.map(_.id).toSet, Map.empty,                                     Map.empty                                              )),
        conversationFoldersStorage.onDeleted.map(cs => (Set.empty, Set.empty,          cs.groupBy(_._2).mapValues(_.map(_._1).toSet), Map.empty                                              )),
        conversationFoldersStorage.onAdded.map  (cs => (Set.empty, Set.empty,          Map.empty,                                     cs.map(_.id).groupBy(_._2).mapValues(_.map(_._1).toSet)))
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
