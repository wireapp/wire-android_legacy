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

  (foldersStorage.list _).expects().anyNumberOfTimes().onCall { _ =>
    Future.successful(folders.toList)
  }

  (conversationFoldersStorage.put _).expects(*, *).anyNumberOfTimes().onCall { (convId: ConvId, folderId: FolderId) =>
    convFolders += ((convId, folderId) -> ConversationFolderData(convId, folderId))
  }

  (conversationFoldersStorage.get _).expects(*).anyNumberOfTimes().onCall { convFolder: (ConvId, FolderId) =>
    Future.successful(convFolders.get(convFolder))
  }

  (conversationFoldersStorage.remove _).expects(*).anyNumberOfTimes().onCall { convFolder: (ConvId, FolderId) =>
    convFolders.remove(convFolder)
    Future.successful(())
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

  feature("Favourites") {
    scenario("empty favourites") {

      // given
      val service = getService

      // when
      val favourites = Await.result(service.favouriteConversations, 500.millis)

      // then
      favourites.isEmpty shouldBe true
    }

    scenario("adding to favourites") {

      // given
      val convId = ConvId("conv_id1")
      val service = getService

      // when
      Await.result(service.addToFavourite(convId), 500.millis)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites shouldEqual List(convId)

    }

    scenario("adding and removing from favourites") {

      // given
      val convId = ConvId("conv_id1")
      val service = getService

      // when
      Await.result(service.addToFavourite(convId), 500.millis)
      Await.result(service.removeFromFavourite(convId), 500.millis)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites.isEmpty shouldBe true
    }

    scenario("Keep in favourites after adding to folder") {

      // given
      val convId = ConvId("conv_id1")
      val folderId = FolderId("folder_id1")
      val service = getService

      // when
      Await.result(service.addToFavourite(convId), 500.millis)
      service.addToCustomFolder(convId, folderId)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites shouldEqual List(convId)
    }

    scenario("Conversations stays in favourites after removing from folder") {

      // given
      val convId = ConvId("conv_id1")
      val folderId = FolderId("folder_id1")
      val service = getService

      // when
      Await.result(service.addToFavourite(convId), 500.millis)
      service.addToCustomFolder(convId, folderId)
      service.removeFromCustomFolder(convId, folderId)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites shouldEqual List(convId)
    }

    scenario("Favourites stays empty after adding to folder") {

      // given
      val convId = ConvId("conv_id1")
      val folderId = FolderId("folder_id1")
      val service = getService

      // when
      service.addToCustomFolder(convId, folderId)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites.length shouldBe 0
    }

    scenario("Multiple conversations in Favourites") {
      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val service = getService

      // when
      Await.result(service.addToFavourite(convId1), 500.millis)
      Await.result(service.addToFavourite(convId2), 500.millis)
      Await.result(service.addToFavourite(convId3), 500.millis)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites.toSet shouldEqual Set(convId1, convId2, convId3)
    }

    scenario("Adding and removing multiple conversations in Favourites") {
      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val service = getService

      // when
      Await.result(service.addToFavourite(convId1), 500.millis)
      Await.result(service.addToFavourite(convId2), 500.millis)
      Await.result(service.removeFromFavourite(convId1), 500.millis)
      Await.result(service.addToFavourite(convId3), 500.millis)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites.toSet shouldEqual Set(convId2, convId3)
    }

    scenario("Adding and removing conversations from custom folders does not change favourites") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val service = getService
      Await.result(service.addToFavourite(convId1), 500.millis)
      Await.result(service.addToFavourite(convId2), 500.millis)

      // when
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId2, folderId2)
      service.addToCustomFolder(convId3, folderId1)
      Await.result(service.removeFromCustomFolder(convId1, folderId1), 500.millis)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites.toSet shouldEqual Set(convId1, convId2)
    }
  }

  feature("Custom folders") {
    scenario("Add conversation to custom folder") {

      // given
      val convId1 = ConvId("conv_id1")
      val folderId1 = FolderId("folder_id1")
      val service = getService

      // when
      service.addToCustomFolder(convId1, folderId1)

      // then
      val conversationsInFolder = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      conversationsInFolder shouldEqual List(convId1)
      Await.result(service.isInCustomFolder(convId1, folderId1), 500.millis) shouldBe true
    }

    scenario("Add conversations to various custom folder") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val service = getService

      // when
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId2, folderId2)
      service.addToCustomFolder(convId3, folderId1)

      // then
      val conversationsInFolder1 = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInCustomFolder(folderId2), 500.millis)
      conversationsInFolder1.toSet shouldEqual Set(convId1, convId3)
      conversationsInFolder2.toSet shouldEqual Set(convId2)
      Await.result(service.isInCustomFolder(convId1, folderId1), 500.millis) shouldBe true
      Await.result(service.isInCustomFolder(convId1, folderId2), 500.millis) shouldBe false
    }

    scenario("Add a single conversation to multiple custom folder") {

      // given
      val convId1 = ConvId("conv_id1")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val service = getService

      // when
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId1, folderId2)

      // then
      val conversationsInFolder1 = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      conversationsInFolder1 shouldEqual List(convId1)
      conversationsInFolder2 shouldEqual List(convId1)
      Await.result(service.isInCustomFolder(convId1, folderId1), 500.millis) shouldBe true
      Await.result(service.isInCustomFolder(convId1, folderId2), 500.millis) shouldBe true
    }

    scenario("Remove conversations from folders") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val service = getService
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId2, folderId2)
      service.addToCustomFolder(convId3, folderId1)

      // when
      Await.result(service.removeFromCustomFolder(convId1, folderId1), 500.millis)

      // then
      val conversationsInFolder1 = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInCustomFolder(folderId2), 500.millis)
      conversationsInFolder1 shouldEqual List(convId3)
      conversationsInFolder2 shouldEqual List(convId2)
      Await.result(service.isInCustomFolder(convId1, folderId1), 500.millis) shouldBe false
      Await.result(service.isInCustomFolder(convId2, folderId2), 500.millis) shouldBe true
      Await.result(service.isInCustomFolder(convId3, folderId1), 500.millis) shouldBe true
    }

    scenario("Remove all conversations from a folders") {

      // given
      val convId1 = ConvId("conv_id1")
      val folderId1 = FolderId("folder_id1")
      val service = getService
      service.addToCustomFolder(convId1, folderId1)

      // when
      Await.result(service.removeFromCustomFolder(convId1, folderId1), 500.millis)

      // then
      val conversationsInFolder1 = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      conversationsInFolder1.isEmpty shouldBe true
    }

    scenario("Remove conversations from all folders") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val service = getService
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId2, folderId1)
      service.addToCustomFolder(convId1, folderId2)

      // when
      Await.result(service.removeFromAllCustomFolders(convId1), 500.millis)

      // then
      val conversationsInFolder1 = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInCustomFolder(folderId2), 500.millis)
      conversationsInFolder1 shouldEqual List(convId2)
      conversationsInFolder2 shouldEqual List()
    }

    scenario("Move conversation to folder") {
      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val folderId3 = FolderId("folder_id3")
      val service = getService
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId2, folderId1)
      service.addToCustomFolder(convId1, folderId2)

      // when
      Await.result(service.moveToCustomFolder(convId1, folderId3), 500.millis)

      // then
      val conversationsInFolder1 = Await.result(service.convsInCustomFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInCustomFolder(folderId2), 500.millis)
      val conversationsInFolder3 = Await.result(service.convsInCustomFolder(folderId3), 500.millis)
      conversationsInFolder1 shouldEqual List(convId2)
      conversationsInFolder2 shouldEqual List()
      conversationsInFolder3 shouldEqual List(convId1)
    }

    scenario("Move conversation to folder does not remove from favourite") {
      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val folderId3 = FolderId("folder_id3")
      val favouriteId = FolderId("folder_fav")
      val service = getService
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId2, folderId1)
      service.addToCustomFolder(convId1, folderId2)
      this.folders += FolderData(favouriteId, "", FolderData.FavouritesFolderType)
      Await.result(service.addToFavourite(convId1), 500.millis)

      // when
      Await.result(service.moveToCustomFolder(convId1, folderId3), 500.millis)

      // then
      val favourites = Await.result(service.favouriteConversations, 500.millis)
      favourites shouldEqual List(convId1)
    }

    scenario("List of folders does not include favourite") {
      // given
      val folderId1 = FolderId("folder_id1")
      val favouriteId = FolderId("folder_fav")
      val service = getService
      this.folders += FolderData(folderId1, "", FolderData.CustomFolderType)
      this.folders += FolderData(favouriteId, "", FolderData.FavouritesFolderType)

      // when
      val folders = Await.result(service.customFolders, 500.millis)

      // then
      folders.map(_.id).toSet shouldEqual Set(folderId1)
    }

    scenario("Get list of folders for conversation does not include favourite") {
      // given
      val convId1 = ConvId("conv_id1")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val favouriteId = FolderId("folder_fav")
      val service = getService
      service.addToCustomFolder(convId1, folderId1)
      service.addToCustomFolder(convId1, folderId2)
      this.folders += FolderData(favouriteId, "", FolderData.FavouritesFolderType)
      Await.result(service.addToFavourite(convId1), 500.millis)

      // when
      val foldersForConv = Await.result(service.customFoldersForConv(convId1), 500.millis)

      // then
      foldersForConv.toSet shouldEqual Set(folderId1, folderId2)
    }
  }
}
