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

import com.waz.content.{ConversationFoldersStorage, ConversationStorage, FoldersStorage, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.EventScheduler
import com.waz.service.EventScheduler.Stage
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.waz.utils.{JsonDecoder, RichFuture}
import com.waz.utils.events.{AggregatingSignal, EventContext, EventStream, Signal}
import io.circe.{Decoder, Encoder}
import org.json.JSONObject

import scala.concurrent.Future

trait FoldersService {
  def eventProcessingStage: EventScheduler.Stage
  def processFolders(folders: Seq[RemoteFolderData]): Future[Unit]

  def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFrom(convId: ConvId, folderId: FolderId): Future[Unit]
  def removeConversationFromAll(convId: ConvId): Future[Unit]
  def convsInFolder(folderId: FolderId): Future[Set[ConvId]]
  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]

  def favouritesFolderId: Future[Option[FolderId]]
  def folders: Future[Seq[FolderData]]
  def addFolder(folderName: Name): Future[FolderId]
  def removeFolder(folderId: FolderId): Future[Unit]
  def ensureFavouritesFolder(): Future[FolderId]
  def removeFavouritesFolder(): Future[Unit]
  def update(folderId: FolderId, folderName: Name): Future[Unit]

  def foldersForConv(convId: ConvId): Future[Set[FolderId]]

  def foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]]
  def folder(folderId: FolderId): Signal[Option[FolderData]]

  def foldersToSynchronize(): Future[Seq[RemoteFolderData]]
}

class FoldersServiceImpl(foldersStorage: FoldersStorage,
                         conversationFoldersStorage: ConversationFoldersStorage,
                         conversationStorage: ConversationStorage,
                         userPrefs: UserPreferences,
                         sync: SyncServiceHandle
                        ) extends FoldersService with DerivedLogTag  {
  import Threading.Implicits.Background
  private implicit val ev: EventContext = EventContext.Global

  private val shouldSyncFolders = userPrefs.preference(UserPreferences.ShouldSyncFolders)

  shouldSyncFolders().foreach {
    case false =>
    case true => sync.syncFolders().flatMap(_ => shouldSyncFolders := false)
  }

  override val eventProcessingStage: Stage.Atomic = EventScheduler.Stage[FoldersEvent] { (_, events) =>
    verbose(l"Handling events: $events")
    RichFuture.traverseSequential(events)(ev => processFolders(ev.folders))
  }

  override def processFolders(folders: Seq[RemoteFolderData]): Future[Unit] =
    for {
      newFolders      <- Future.sequence(folders.map { case RemoteFolderData(data, rConvIds) =>
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

  override def favouritesFolderId: Future[Option[FolderId]] =
    foldersStorage.getByType(FolderData.FavouritesFolderType).map {
      case head :: _  => Some(head.id)
      case Nil        => None
    }

  override def addConversationTo(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.put(convId, folderId)

  private def addAllConversationsTo(convIds: Set[ConvId], folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.insertAll(convIds.map(id => ConversationFolderData(id, folderId))).map(_ => ())

  override def removeConversationFrom(convId: ConvId, folderId: FolderId): Future[Unit] =
    conversationFoldersStorage.remove((convId, folderId))

  override def removeConversationFromAll(convId: ConvId): Future[Unit] = for {
    allFolders <- foldersForConv(convId)
    _ <- conversationFoldersStorage.removeAll(allFolders.map((convId, _)))
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
    _ <- conversationFoldersStorage.removeAll(convIds.map((_, folderId)))
    _ <- foldersStorage.remove(folderId)
  } yield ()

  override def ensureFavouritesFolder(): Future[FolderId] = favouritesFolderId.map {
    case None =>
      val folderData = FolderData(FolderId(), "", FolderData.FavouritesFolderType)
      foldersStorage.put(folderData.id, folderData).map(_ => folderData.id)
      folderData.id
    case Some(id) => id
  }

  override def removeFavouritesFolder(): Future[Unit] = favouritesFolderId.flatMap {
    case Some(id) => removeFolder(id)
    case None => Future.successful(())
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
      folders <- foldersStorage.list()
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

  override def foldersToSynchronize(): Future[Seq[RemoteFolderData]] = for {
    allFolders <- folders
    data <- Future.sequence(allFolders.map(conversationDataForFolder))
  } yield data


  private def conversationDataForFolder(folder: FolderData): Future[RemoteFolderData] = for {
    convsInFolder <- convsInFolder(folder.id)
    convData      <- Future.sequence(convsInFolder.map(id => conversationStorage.get(id)))
    convRIds      =  convData.flatten.map(_.remoteId)
  } yield RemoteFolderData(folder, convRIds)

}

case class RemoteFolderData(folderData: FolderData, conversations: Set[RConvId])

object RemoteFolderData {

  case class IntermediateFolderData(id: String, name: Option[String], `type`: Int, conversations: List[String]) {
    def toRemoteFolderData: RemoteFolderData =
      RemoteFolderData(
        FolderData(id = FolderId(id), name = Name(name.getOrElse("")), folderType = `type`),
        conversations.map(RConvId(_)).toSet
      )
  }

  lazy implicit val folderDataConversationsCirceEncoder: Encoder[RemoteFolderData] = Encoder.forProduct4(
    "id", "name", "type", "conversations"
  )(fd => (fd.folderData.id.str, fd.folderData.name.str, fd.folderData.folderType, fd.conversations.map(_.str)))

  lazy implicit val folderDataConversationsCirceDecoder: Decoder[IntermediateFolderData] =
    Decoder.forProduct4("id", "name", "type", "conversations")(IntermediateFolderData.apply)

  // TODO: the old JSON decoder is still needed for FoldersEvent. Remove after migrating to circe.
  implicit val remoteFolderDataDecoder: JsonDecoder[RemoteFolderData] = new JsonDecoder[RemoteFolderData] {
    override def apply(implicit js: JSONObject): RemoteFolderData = {
      import JsonDecoder._

      val conversations: Seq[RConvId] = decodeRConvIdSeq('conversations)
      val name: Option[String] = decodeOptString('name)
      RemoteFolderData(
        FolderData(decodeFolderId('id), Name(name.getOrElse("")), decodeInt('type)),
        conversations.toSet
      )
    }
  }
}
