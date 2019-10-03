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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationFolderData, FolderData, FolderId}
import com.waz.service.conversation.{FoldersService, FoldersServiceImpl}
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.Threading
import com.waz.utils.events.EventStream

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FoldersServiceSpec extends AndroidFreeSpec with DerivedLogTag {
  import Threading.Implicits.Background

  val foldersStorage = mock[FoldersStorage]
  val conversationFoldersStorage = mock[ConversationFoldersStorage]

  private val folders = mutable.ListBuffer[FolderData]()
  private val convFolders = mutable.HashMap[(ConvId, FolderId), ConversationFolderData]()

  (foldersStorage.getByType _).expects(*).anyNumberOfTimes().onCall { folderType: Int =>
    Future(folders.filter(_.folderType == folderType).toList )
  }

  (foldersStorage.put _).expects(*, *).anyNumberOfTimes().onCall { (id: FolderId, folder: FolderData) =>
      Future {
        folders += folder
        onFoldersAdded ! Seq(folder)
      }.map(_ => folder)
  }

  (foldersStorage.list _).expects().anyNumberOfTimes().onCall { _ =>
    Future(folders.toList)
  }

  (foldersStorage.remove _).expects(*).anyNumberOfTimes().onCall { folderId: FolderId =>
    Future {
      folders.find(_.id == folderId).foreach(folders -= _)
      onFoldersDeleted ! Seq(folderId)
    }
  }

  (conversationFoldersStorage.put _).expects(*, *).anyNumberOfTimes().onCall { (convId: ConvId, folderId: FolderId) =>
    Future {
      val convFolder = ConversationFolderData(convId, folderId)
      convFolders += ((convId, folderId) -> convFolder)
      onConvsAdded ! Seq(convFolder)
    }
  }

  (conversationFoldersStorage.get _).expects(*).anyNumberOfTimes().onCall { convFolder: (ConvId, FolderId) =>
    Future(convFolders.get(convFolder))
  }

  (conversationFoldersStorage.remove _).expects(*).anyNumberOfTimes().onCall { convFolder: (ConvId, FolderId) =>
    Future {
      convFolders.remove(convFolder)
      onConvsDeleted ! Seq(convFolder)
    }
  }

  (conversationFoldersStorage.removeAll _).expects(*).anyNumberOfTimes().onCall { cfs: Iterable[(ConvId, FolderId)] =>
    Future {
      convFolders --= cfs
      onConvsDeleted ! cfs.toSeq
    }
  }

  (conversationFoldersStorage.findForConv _).expects(*).anyNumberOfTimes().onCall { convId: ConvId =>
    Future(convFolders.values.filter(_.convId == convId).map(_.folderId).toSet)
  }

  (conversationFoldersStorage.findForFolder _).expects(*).anyNumberOfTimes().onCall { folderId: FolderId =>
    Future.successful(convFolders.values.filter(_.folderId == folderId).map(_.convId).toSet)
  }

  (conversationFoldersStorage.removeAll _).expects(*).anyNumberOfTimes().onCall { cfs: Iterable[(ConvId, FolderId)] =>
    Future.successful(convFolders --= cfs.toSet).map(_ => ())
  }

  val onFoldersAdded = EventStream[Seq[FolderData]]()
  val onConvsAdded = EventStream[Seq[ConversationFolderData]]()
  val onFoldersDeleted = EventStream[Seq[FolderId]]()
  val onConvsDeleted = EventStream[Seq[(ConvId, FolderId)]]()

  (foldersStorage.onAdded _).expects().anyNumberOfTimes().returning(onFoldersAdded)
  (foldersStorage.onDeleted _).expects().anyNumberOfTimes().returning(onFoldersDeleted)
  (conversationFoldersStorage.onAdded _).expects().anyNumberOfTimes().returning(onConvsAdded)
  (conversationFoldersStorage.onDeleted _).expects().anyNumberOfTimes().returning(onConvsDeleted)

  private def getService: FoldersService = {
    new FoldersServiceImpl(foldersStorage, conversationFoldersStorage)
  }

  feature("Favourites") {
    scenario("adding to favourites") {

      // given
      val convId = ConvId("conv_id1")
      val service = getService

      // when
      val favConvs = for {
        favId <- service.addFavouritesFolder()
        _     <- service.addConversationTo(convId, favId)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId)

    }

    scenario("adding and removing from favourites") {

      // given
      val convId = ConvId("conv_id1")
      val service = getService

      // when
      val favConvs = for {
        favId <- service.addFavouritesFolder()
        _     <- service.addConversationTo(convId, favId)
        _     <- service.removeConversationFrom(convId, favId)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis).isEmpty shouldBe true
    }

    scenario("Keep in favourites after adding to folder") {
      // given
      val convId = ConvId("conv_id1")
      val service = getService

      // when
      val favConvs = for {
        favId     <- service.addFavouritesFolder()
        folderId  <- service.addFolder("custom folder")
        _         <- service.addConversationTo(convId, favId)
        _         <- service.addConversationTo(convId, folderId)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId)
    }

    scenario("Conversations stays in favourites after removing from another folder") {
      // given
      val convId = ConvId("conv_id1")
      val service = getService

      // when
      val favConvs = for {
        favId     <- service.addFavouritesFolder()
        folderId  <- service.addFolder("custom folder")
        _         <- service.addConversationTo(convId, favId)
        _         <- service.addConversationTo(convId, folderId)
        _         <- service.removeConversationFrom(convId, folderId)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId)
    }

    scenario("Favourites stays empty after adding to folder") {
      // given
      val convId = ConvId("conv_id1")
      val folderId = FolderId("folder_id1")
      val service = getService

      // when
      val favConvs = for {
        favId     <- service.addFavouritesFolder()
        folderId  <- service.addFolder("custom folder")
        _         <- service.addConversationTo(convId, folderId)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis).isEmpty shouldBe true
    }

    scenario("Multiple conversations in Favourites") {
      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val service = getService

      // when
      val favConvs = for {
        favId <- service.addFavouritesFolder()
        _     <- service.addConversationTo(convId1, favId)
        _     <- service.addConversationTo(convId2, favId)
        _     <- service.addConversationTo(convId3, favId)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId1, convId2, convId3)
    }

    scenario("Adding and removing multiple conversations in Favourites") {
      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val service = getService

      // when
      val favConvs = for {
        favId <- service.addFavouritesFolder()
        _     <- service.addConversationTo(convId1, favId)
        _     <- service.addConversationTo(convId2, favId)
        _     <- service.removeConversationFrom(convId1, favId)
        _     <- service.addConversationTo(convId3, favId)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId2, convId3)
    }

    scenario("Adding and removing conversations from custom folders does not change favourites") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val service = getService

      val favConvs = for {
        favId     <- service.addFavouritesFolder()
        _         <- service.addConversationTo(convId1, favId)
        _         <- service.addConversationTo(convId2, favId)
        folderId1 <- service.addFolder("custom folder 1")
        folderId2 <- service.addFolder("custom folder 2")
        _         <- service.addConversationTo(convId1, folderId1)
        _         <- service.addConversationTo(convId2, folderId2)
        _         <- service.addConversationTo(convId3, folderId1)
        _         <- service.removeConversationFrom(convId1, folderId1)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId1, convId2)
    }
  }

  feature("Custom folders") {
    scenario("Add conversation to a folder") {

      // given
      val convId1 = ConvId("conv_id1")
      val folderId1 = FolderId("folder_id1")
      val service = getService

      // when
      val res = for {
        folderId1  <- service.addFolder("custom folder")
        _          <- service.addConversationTo(convId1, folderId1)
        convs      <- service.convsInFolder(folderId1)
        isInFolder <- service.isInFolder(convId1, folderId1)
      } yield (convs, isInFolder)

      // then
      Await.result(res, 500.millis) shouldBe (Set(convId1), true)
    }

    scenario("Add conversations to various folders") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val service = getService

      // when
      val res = for {
        folderId1 <- service.addFolder("custom folder 1")
        folderId2 <- service.addFolder("custom folder 2")
        _         <- service.addConversationTo(convId1, folderId1)
        _         <- service.addConversationTo(convId2, folderId2)
        _         <- service.addConversationTo(convId3, folderId1)
      } yield (folderId1, folderId2)
     val (folderId1, folderId2) = Await.result(res, 500.millis)

      // then
      val conversationsInFolder1 = Await.result(service.convsInFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInFolder(folderId2), 500.millis)
      conversationsInFolder1 shouldEqual Set(convId1, convId3)
      conversationsInFolder2 shouldEqual Set(convId2)
      Await.result(service.isInFolder(convId1, folderId1), 500.millis) shouldBe true
      Await.result(service.isInFolder(convId1, folderId2), 500.millis) shouldBe false
    }

    scenario("Add a single conversation to multiple folders") {

      // given
      val convId1 = ConvId("conv_id1")
      val service = getService

      // when
      val res = for {
        folderId1 <- service.addFolder("custom folder 1")
        folderId2 <- service.addFolder("custom folder 2")
        _         <- service.addConversationTo(convId1, folderId1)
        _         <- service.addConversationTo(convId1, folderId2)
      } yield (folderId1, folderId2)
      val (folderId1, folderId2) = Await.result(res, 500.millis)

      // then
      val conversationsInFolder1 = Await.result(service.convsInFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInFolder(folderId1), 500.millis)
      conversationsInFolder1 shouldEqual Set(convId1)
      conversationsInFolder2 shouldEqual Set(convId1)
      Await.result(service.isInFolder(convId1, folderId1), 500.millis) shouldBe true
      Await.result(service.isInFolder(convId1, folderId2), 500.millis) shouldBe true
    }

    scenario("Remove conversations from folders") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val convId3 = ConvId("conv_id3")
      val service = getService
      val res = for {
        folderId1 <- service.addFolder("custom folder 1")
        folderId2 <- service.addFolder("custom folder 2")
        _         <- service.addConversationTo(convId1, folderId1)
        _         <- service.addConversationTo(convId2, folderId2)
        _         <- service.addConversationTo(convId3, folderId1)
        _         <- service.removeConversationFrom(convId1, folderId1)
      } yield (folderId1, folderId2)
      val (folderId1, folderId2) = Await.result(res, 500.millis)

      // then
      val conversationsInFolder1 = Await.result(service.convsInFolder(folderId1), 500.millis)
      val conversationsInFolder2 = Await.result(service.convsInFolder(folderId2), 500.millis)
      conversationsInFolder1 shouldEqual Set(convId3)
      conversationsInFolder2 shouldEqual Set(convId2)
      Await.result(service.isInFolder(convId1, folderId1), 500.millis) shouldBe false
      Await.result(service.isInFolder(convId2, folderId2), 500.millis) shouldBe true
      Await.result(service.isInFolder(convId3, folderId1), 500.millis) shouldBe true
    }

    scenario("Remove all conversations from a folder") {
      // given
      val convId1 = ConvId("conv_id1")
      val folderId1 = FolderId("folder_id1")
      val service = getService

      // when
      val convs = for {
        folderId1 <- service.addFolder("custom folder 1")
        _         <- service.addConversationTo(convId1, folderId1)
        _         <- service.removeConversationFrom(convId1, folderId1)
        convs     <- service.convsInFolder(folderId1)
      } yield convs

      // then
      Await.result(convs, 500.millis).isEmpty shouldBe true
    }

    scenario("Remove conversations from all folders") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val service = getService
      val convs = for {
        folderId1 <- service.addFolder("custom folder 1")
        folderId2 <- service.addFolder("custom folder 2")
        _         <- service.addConversationTo(convId2, folderId1)
        _         <- service.addConversationTo(convId1, folderId2)
        _         <- service.removeConversationFromAll(convId1)
        convs1    <- service.convsInFolder(folderId1)
        convs2    <- service.convsInFolder(folderId2)
      } yield (convs1, convs2)
      val (conversationsInFolder1, conversationsInFolder2) = Await.result(convs, 500.millis)

      // then

      conversationsInFolder1 shouldEqual Set(convId2)
      conversationsInFolder2 shouldEqual Set()
    }

    scenario("Get list of folders for conversation includes favourites") {
      // given
      val convId1 = ConvId("conv_id1")
      val service = getService
      val fs = for {
        favId     <- service.addFavouritesFolder()
        folderId1 <- service.addFolder("custom folder 1")
        folderId2 <- service.addFolder("custom folder 2")
        _         <- service.addConversationTo(convId1, folderId1)
        _         <- service.addConversationTo(convId1, folderId2)
        _         <- service.addConversationTo(convId1, favId)
        folders   <- service.foldersForConv(convId1)
      } yield ((favId, folderId1, folderId2), folders)
      val ((favId, folderId1, folderId2), folders) = Await.result(fs, 500.millis)

      // then
      folders shouldEqual Set(folderId1, folderId2, favId)
    }

    scenario("remove a conversation from a folder") {
      val convId = ConvId("conv_id1")

      val service = getService
      val convInFavsAfterAdding = for {
        favFolder <- service.addFavouritesFolder()
        _         <- service.addConversationTo(convId, favFolder)
        res       <- service.isInFolder(convId, favFolder)
      } yield res

      assert(Await.result(convInFavsAfterAdding, 500.millis) == true)

      val convInFavsAfterRemoval = for {
        favFolder <- service.addFavouritesFolder()
        _         <- service.removeConversationFrom(convId, favFolder)
        res       <- service.isInFolder(convId, favFolder)
      } yield res

      assert(Await.result(convInFavsAfterRemoval, 500.millis) == false)
    }

    scenario("Retrieve changes to the Favourites through a signal") {
      val convId = ConvId("conv_id1")

      val service = getService

      val states = mutable.ListBuffer[Map[FolderId, Set[ConvId]]]()
      service.foldersWithConvs { state =>
        println(s"${states.size} > $state")
        states += state
      }

      Await.result(service.foldersWithConvs.head, 5.seconds) //  wait for the signal to initialize, otherwise we'll skip the initial state

      val res = for {
        favId     <- service.addFavouritesFolder()
        _         <- service.addConversationTo(convId, favId)
        folderId1 <- service.addFolder("custom folder 1")
        _         <- service.addConversationTo(convId, folderId1)
        folderId2 <- service.addFolder("custom folder 2")
        _         <- service.removeConversationFrom(convId, folderId1)
        _         <- service.addConversationTo(convId, folderId2)
        _         <- service.removeFolder(folderId1)
        _         <- service.removeFavouritesFolder()
      } yield (favId, folderId1, folderId2)

      val (favId, folderId1, folderId2) = Await.result(res, 5.seconds)

      // initial empty state
      states(0) shouldEqual Map.empty

      // after adding the favourites folder, but before putting a convId in it
      states(1).size shouldBe 1
      states(1)(favId) shouldBe Set.empty

      // after adding convId to to favourites
      states(2).size shouldBe 1
      states(2)(favId) shouldBe Set(convId)

      // after creating the first custom folder
      states(3).size shouldBe 2
      states(3)(favId) shouldBe Set(convId)
      states(3)(folderId1) shouldBe Set.empty

      // after adding convId to the folder
      states(4).size shouldBe 2
      states(4)(favId) shouldBe Set(convId)
      states(4)(folderId1) shouldBe Set(convId)

      // after creating the second custom folder
      states(5).size shouldBe 3
      states(5)(favId) shouldBe Set(convId)
      states(5)(folderId1) shouldBe Set(convId)
      states(5)(folderId2) shouldBe Set.empty

      // after removing convId from the first folder
      states(6).size shouldBe 3
      states(6)(favId) shouldBe Set(convId)
      states(6)(folderId1) shouldBe Set.empty
      states(6)(folderId2) shouldBe Set.empty

      // after adding convId to the second folder
      states(7).size shouldBe 3
      states(7)(favId) shouldBe Set(convId)
      states(7)(folderId1) shouldBe Set.empty
      states(7)(folderId2) shouldBe Set(convId)

      // after removing the first folder
      states(8).size shouldBe 2
      states(8)(favId) shouldBe Set(convId)
      states(8)(folderId2) shouldBe Set(convId)

      // after removing the favourites folder
      // Here's a trick: it consists of two operations: removing convId from the fav folder,
      // and then deleting the fav folder itself. The signal might skip that intermediate step
      // and update immediately to the state after the removal of the fav folder. But it might not.
      // So we can't rely on `states(9)` - instead we use `states.last`
      states.last.size shouldBe 1
      states.last.apply(folderId2) shouldBe Set(convId)
    }
  }
}
