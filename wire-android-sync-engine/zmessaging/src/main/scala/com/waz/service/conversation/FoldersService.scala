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
import com.waz.utils.events.{AggregatingSignal, EventContext, EventStream, Signal}
import com.waz.utils.{JsonDecoder, RichFuture}
import org.json.JSONObject

import scala.concurrent.Future

trait FoldersService {
  def eventProcessingStage: EventScheduler.Stage
  def processFolders(folders: Seq[RemoteFolderData]): Future[Unit]

  def addConversationTo(convId: ConvId, folderId: FolderId, uploadAllChanges: Boolean): Future[Unit]
  def removeConversationFrom(convId: ConvId, folderId: FolderId, uploadAllChanges: Boolean): Future[Unit]
  def removeConversationFromAll(convId: ConvId, uploadAllChanges: Boolean): Future[Unit]
  def convsInFolder(folderId: FolderId): Future[Set[ConvId]]
  def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean]

  def favoritesFolderId: Signal[Option[FolderId]]
  def folders: Future[Seq[FolderData]]
  def addFolder(folderName: Name, uploadAllChanges: Boolean): Future[FolderId]
  def removeFolder(folderId: FolderId, uploadAllChanges: Boolean): Future[Unit]
  def ensureFavoritesFolder(): Future[FolderId]
  def removeFavoritesFolder(): Future[Unit]
  def update(folderId: FolderId, folderName: Name, uploadAllChanges: Boolean): Future[Unit]

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
      currentFolders  <- foldersStorage.list().map(_.toIdMap)
      foldersToDelete =  currentFolders.keySet -- newFolders.keySet
      _               <- Future.sequence(foldersToDelete.map(removeFolder(_, false)))
      foldersToAdd    =  newFolders.filterKeys(id => !currentFolders.contains(id)).values
      _               <- Future.sequence(foldersToAdd.map { case (data, convIds) =>
        foldersStorage.insert(data).flatMap(_ => addAllConversationsTo(convIds, data.id, false))
      })
      foldersToUpdate =  newFolders.collect { case (id, (data, _)) if currentFolders.contains(id) && data.name != currentFolders(id).name => data }
      _               <- Future.sequence(foldersToUpdate.map(folder => update(folder.id, folder.name, false)))
      // at this point the list of folders in newFolders should be the same as in the storage, so we can use newFolders to get currentConvs
      currentConvs    <- Future.sequence(newFolders.keys.map(id =>
        conversationFoldersStorage.findForFolder(id).map(convIds => id -> convIds)
      )).map(_.toMap)
      convsToDelete   =  currentConvs.flatMap { case (folderId, convIds) =>
        val convsToDelete = convIds -- newFolders(folderId)._2
        if (convsToDelete.nonEmpty) Some(folderId -> convsToDelete) else None
      }
      _               <- Future.sequence(convsToDelete.flatMap { case (folderId, convIds) =>
        convIds.map(removeConversationFrom(_, folderId, false))
      })
      convsToAdd      =  currentConvs.flatMap { case (folderId, convIds) =>
        val convsToAdd = newFolders(folderId)._2 -- convIds
        if (convsToAdd.nonEmpty) Some(folderId -> convsToAdd) else None
      }
      _               <- Future.sequence(convsToAdd.flatMap { case (folderId, convIds) =>
        convIds.map(addConversationTo(_, folderId, false))
      })
    } yield ()

  override def favoritesFolderId: Signal[Option[FolderId]] =
    for {
      foldersWithConvs <- foldersWithConvs
      folderDataOpt    <- Signal.sequence(foldersWithConvs.keys.toSeq.map(folder):_*)
      folderData        = folderDataOpt.flatten
      favoriteFolderId  = folderData.find(_.folderType == FolderData.FavoritesFolderType).map(_.id)
    } yield favoriteFolderId


  override def addConversationTo(convId: ConvId, folderId: FolderId, uploadAllChanges: Boolean): Future[Unit] = for {
    _ <- conversationFoldersStorage.put(convId, folderId)
    _ <- postFoldersIfNeeded(uploadAllChanges)
  } yield ()


  private def addAllConversationsTo(convIds: Set[ConvId], folderId: FolderId, uploadAllChanges: Boolean): Future[Unit] = for {
    _ <- conversationFoldersStorage.insertAll(convIds.map(id => ConversationFolderData(id, folderId)))
    _ <- postFoldersIfNeeded(uploadAllChanges)
  } yield ()

  override def removeConversationFrom(convId: ConvId, folderId: FolderId, uploadAllChanges: Boolean): Future[Unit] = for {
    _ <- conversationFoldersStorage.remove((convId, folderId))
    _ <- postFoldersIfNeeded(uploadAllChanges)
  } yield ()

  override def removeConversationFromAll(convId: ConvId, uploadAllChanges: Boolean): Future[Unit] = for {
    allFolders <- foldersForConv(convId)
    _ <- conversationFoldersStorage.removeAll(allFolders.map((convId, _)))
    _ <- postFoldersIfNeeded(uploadAllChanges)
  } yield ()

  override def foldersForConv(convId: ConvId): Future[Set[FolderId]] =
    conversationFoldersStorage.findForConv(convId)

  override def isInFolder(convId: ConvId, folderId: FolderId): Future[Boolean] =
    conversationFoldersStorage.get((convId, folderId)).map(_.nonEmpty)

  override def convsInFolder(folderId: FolderId): Future[Set[ConvId]] =
    conversationFoldersStorage.findForFolder(folderId)

  override def folder(folderId: FolderId): Signal[Option[FolderData]] =
    foldersStorage.optSignal(folderId)

  override def addFolder(folderName: Name, uploadAllChanges: Boolean): Future[FolderId] = addFolder(folderName, FolderData.CustomFolderType, uploadAllChanges)

  private def addFolder(folderName: Name, folderType: Int, uploadAllChanges: Boolean): Future[FolderId] = for {
    folder    <- Future.successful(FolderData(name = folderName, folderType = folderType))
    folderId  <- foldersStorage.put(folder.id, folder).map(_.id)
    _         <- postFoldersIfNeeded(uploadAllChanges)
  } yield folderId

  override def removeFolder(folderId: FolderId, uploadAllChanges: Boolean): Future[Unit] = for {
    convIds <- convsInFolder(folderId)
    _       <- conversationFoldersStorage.removeAll(convIds.map((_, folderId)))
    _       <- foldersStorage.remove(folderId)
    _       <- postFoldersIfNeeded(uploadAllChanges)
  } yield ()

  override def ensureFavoritesFolder(): Future[FolderId] = favoritesFolderId.head.flatMap {
    case Some(x) => Future.successful(x)
    case None => addFolder("", FolderData.FavoritesFolderType, true)
  }

  override def removeFavoritesFolder(): Future[Unit] = favoritesFolderId.head.flatMap {
    case Some(id) => removeFolder(id, true)
    case None => Future.successful(())
  }

  override def update(folderId: FolderId, folderName: Name, uploadAllChanges: Boolean): Future[Unit] = for {
    _ <- foldersStorage.update(folderId, _.copy(name = folderName))
    _ <- postFoldersIfNeeded(uploadAllChanges)
  } yield ()

  private def postFoldersIfNeeded(shouldUpload: Boolean) =
    if (shouldUpload) sync.postFolders() else Future.successful(())

  override def folders: Future[Seq[FolderData]] = foldersStorage.list()

  override val foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]] = {
    val changesStream: EventStream[(Set[FolderId], Set[FolderId], Map[FolderId, Set[ConvId]], Map[FolderId, Set[ConvId]])] =
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

object FoldersService {

  // This type is matching the JSON payload on the backend
  // e. g. Do NOT rename "labels" or it will not serialize/deserialize properly
  case class FoldersProperty(labels: Seq[IntermediateFolderData]) {
    def toRemote: Seq[RemoteFolderData] = labels.map(label =>
      RemoteFolderData(
        FolderData(id = FolderId(label.id), name = Name(label.name.getOrElse("")), folderType = label.`type`),
        label.conversations.map(id => RConvId(id.toLowerCase())).toSet
      )
    )
  }

  object FoldersProperty {
    def fromRemote(folders: Seq[RemoteFolderData]): FoldersProperty = FoldersProperty(folders.map(_.toIntermediate))
  }

  case class IntermediateFolderData(id: String, name: Option[String], `type`: Int, conversations: Vector[String])

  // TODO: the old JSON decoder is still needed for FoldersEvent. Remove after migrating to circe.
  implicit val remoteFolderPropertyDecoder: JsonDecoder[FoldersProperty] = new JsonDecoder[FoldersProperty] {
    override def apply(implicit js: JSONObject): FoldersProperty = {
      import JsonDecoder._
      val folderData = decodeSeq[IntermediateFolderData]('labels)
      FoldersProperty(folderData)
    }
  }

  implicit val remoteFolderDataDecoder: JsonDecoder[IntermediateFolderData] = new JsonDecoder[IntermediateFolderData] {
    override def apply(implicit js: JSONObject): IntermediateFolderData = {
      import JsonDecoder._

      val conversations: Seq[String] = decodeStringSeq('conversations)
      val name: Option[String] = decodeOptString('name)
      IntermediateFolderData(
        decodeString('id),
        name,
        decodeInt('type),
        conversations.toVector
      )
    }
  }
}

case class RemoteFolderData(folderData: FolderData, conversations: Set[RConvId]) {
  import FoldersService.IntermediateFolderData

  def toIntermediate: IntermediateFolderData = IntermediateFolderData(
    folderData.id.str,
    Some(folderData.name.str),
    folderData.folderType,
    conversations.map(_.str).toVector
  )
}
