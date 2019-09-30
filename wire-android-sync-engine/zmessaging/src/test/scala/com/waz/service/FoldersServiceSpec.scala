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
package com.waz.service

import com.waz.content.{ConversationFoldersStorage, FoldersStorage}
import com.waz.model.{ConvId, ConversationFolderData, FolderData, FolderId}
import com.waz.service.conversation.{FoldersService, FoldersServiceImpl}
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.Threading

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FoldersServiceSpec extends AndroidFreeSpec {
  import Threading.Implicits.Background

  val foldersStorage = mock[FoldersStorage]
  val conversationFoldersStorage = mock[ConversationFoldersStorage]

  private val folders = mutable.ListBuffer[FolderData]()
  private val convFolders = mutable.HashMap[(ConvId, FolderId), ConversationFolderData]()

  (foldersStorage.getByType _).expects(*).anyNumberOfTimes().onCall { folderType: Int =>
    Future.successful(folders.filter(_.folderType == folderType).toList )
  }

  (foldersStorage.put _).expects(*, *).anyNumberOfTimes().onCall { (id: FolderId, folder: FolderData) =>
      Future.successful(folders += folder).map(_ => folder)
  }

  (conversationFoldersStorage.put _).expects(*, *).anyNumberOfTimes().onCall { (convId: ConvId, folderId: FolderId) =>
    convFolders += ((convId, folderId) -> ConversationFolderData(convId, folderId))
  }

  (conversationFoldersStorage.get _).expects(*).anyNumberOfTimes().onCall { convFolder: (ConvId, FolderId) =>
    Future.successful(convFolders.get(convFolder))
  }

  (conversationFoldersStorage.findForConv _).expects(*).anyNumberOfTimes().onCall { convId: ConvId =>
    Future.successful(convFolders.values.filter(_.convId == convId).toSeq)
  }

  (conversationFoldersStorage.findForFolder _).expects(*).anyNumberOfTimes().onCall { folderId: FolderId =>
    Future.successful(convFolders.values.filter(_.folderId == folderId).toSeq)
  }

  (conversationFoldersStorage.removeAll _).expects(*).anyNumberOfTimes().onCall { cfs: Iterable[(ConvId, FolderId)] =>
    Future.successful(convFolders --= cfs.toSet).map(_ => ())
  }

  private def getService: FoldersService = {
    new FoldersServiceImpl(foldersStorage, conversationFoldersStorage)
  }

  feature("the Favourites folder") {
    scenario("ensure Favourites is present on start") {
      val service = getService
      val favouritesFolder = Await.result(service.favouritesFolder, 500.millis)
      assert(favouritesFolder.id == FolderData.FavouritesFolder.id)
      assert(favouritesFolder.folderType == FolderData.FavouritesFolderType)
    }

    scenario("check if a conversation is in Favourites") {
      val convId = ConvId("conv_id1")

      val service = getService
      val res1 = Await.result(service.isInFolder(convId, FolderData.FavouritesFolder.id), 500.millis)
      assert(res1 == false)

      val res2 = Await.result(service.move(convId, FolderData.FavouritesFolder.id).flatMap(_ => service.isInFolder(convId, FolderData.FavouritesFolder.id)), 500.millis)
      assert(res2 == true)

      val convsInFavourites = Await.result(service.convsInFolder(FolderData.FavouritesFolder.id), 500.millis)
      assert(convsInFavourites.contains(convId))
    }
  }
}
