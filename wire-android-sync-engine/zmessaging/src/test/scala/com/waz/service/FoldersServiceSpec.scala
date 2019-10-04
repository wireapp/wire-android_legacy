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

import com.waz.content.{ConversationFoldersStorage, ConversationStorage, FoldersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationFolderData, FolderData, FolderId, FoldersEvent, Name, RConvId, RemoteFolderData}
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
  val conversationStorage = mock[ConversationStorage]

  private val folders = mutable.ListBuffer[FolderData]()
  private val convFolders = mutable.HashMap[(ConvId, FolderId), ConversationFolderData]()

  (foldersStorage.getByType _).expects(*).anyNumberOfTimes().onCall { folderType: Int =>
    Future(folders.filter(_.folderType == folderType).toList )
  }

  (foldersStorage.put _).expects(*, *).anyNumberOfTimes().onCall { (_: FolderId, folder: FolderData) =>
      Future {
        folders += folder
        onFoldersAdded ! Seq(folder)
      }.map(_ => folder)
  }

  (foldersStorage.insert _).expects(*).anyNumberOfTimes().onCall { folder: FolderData =>
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

  (foldersStorage.update _).expects(*, *).anyNumberOfTimes().onCall { (folderId: FolderId, updater: FolderData => FolderData) =>
    Future {
      folders.find(_.id == folderId).map { oldFolder =>
        folders -= oldFolder
        val newFolder = updater(oldFolder)
        folders += newFolder
        (oldFolder, newFolder)
      }
    }
  }

  (conversationFoldersStorage.put _).expects(*, *).anyNumberOfTimes().onCall { (convId: ConvId, folderId: FolderId)  =>
    Future {
      val convFolder = ConversationFolderData(convId, folderId)
      convFolders += ((convId, folderId) -> convFolder)
      onConvsAdded ! Seq(convFolder)
    }.map(_ => ())
  }

  (conversationFoldersStorage.insertAll(_: Set[ConversationFolderData])).expects(*).anyNumberOfTimes().onCall { cfs: Set[ConversationFolderData] =>
    Future {
      convFolders ++= cfs.map(cf => (cf.convId, cf.folderId) -> cf).toMap
      onConvsAdded ! cfs.toSeq
    }.map(_ => Set.empty[ConversationFolderData])
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
    Future(convFolders.values.filter(_.folderId == folderId).map(_.convId).toSet)
  }

  (conversationFoldersStorage.removeAll _).expects(*).anyNumberOfTimes().onCall { cfs: Iterable[(ConvId, FolderId)] =>
    Future(convFolders --= cfs.toSet).map(_ => ())
  }

  (conversationStorage.getByRemoteIds _).expects(*).anyNumberOfTimes().onCall { ids: Traversable[RConvId] =>
    Future(ids.map(id => ConvId(id.str)).toSeq)
  }

  val onFoldersAdded = EventStream[Seq[FolderData]]()
  val onConvsAdded = EventStream[Seq[ConversationFolderData]]()
  val onFoldersDeleted = EventStream[Seq[FolderId]]()
  val onConvsDeleted = EventStream[Seq[(ConvId, FolderId)]]()

  (foldersStorage.onAdded _).expects().anyNumberOfTimes().returning(onFoldersAdded)
  (foldersStorage.onDeleted _).expects().anyNumberOfTimes().returning(onFoldersDeleted)
  (conversationFoldersStorage.onAdded _).expects().anyNumberOfTimes().returning(onConvsAdded)
  (conversationFoldersStorage.onDeleted _).expects().anyNumberOfTimes().returning(onConvsDeleted)

  private var _service = Option.empty[FoldersService]

  private def getService: FoldersService = _service match {
    case Some(service) => service
    case None =>
      val service = new FoldersServiceImpl(foldersStorage, conversationFoldersStorage, conversationStorage)
      _service = Some(service)
      service
  }

  feature("Favourites") {
    scenario("adding to favourites") {

      // given
      val convId = ConvId("conv_id1")
      val service = getService

      // when
      val favConvs = for {
        favId <- service.ensureFavouritesFolder()
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
        favId <- service.ensureFavouritesFolder()
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
        favId     <- service.ensureFavouritesFolder()
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
        favId     <- service.ensureFavouritesFolder()
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
        favId     <- service.ensureFavouritesFolder()
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
        favId <- service.ensureFavouritesFolder()
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
        favId <- service.ensureFavouritesFolder()
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
        favId     <- service.ensureFavouritesFolder()
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
        favId     <- service.ensureFavouritesFolder()
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
        favFolder <- service.ensureFavouritesFolder()
        _         <- service.addConversationTo(convId, favFolder)
        res       <- service.isInFolder(convId, favFolder)
      } yield res

      assert(Await.result(convInFavsAfterAdding, 500.millis) == true)

      val convInFavsAfterRemoval = for {
        favFolder <- service.ensureFavouritesFolder()
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
        favId     <- service.ensureFavouritesFolder()
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

    scenario("Get mapping from folders to conversations") {

      // given
      val convId1 = ConvId("conv_id1")
      val convId2 = ConvId("conv_id2")
      val folderId1 = FolderId("folder_id1")
      val folderId2 = FolderId("folder_id2")
      val favouriteId = FolderId("folder_fav")
      val service = getService
      service.addConversationTo(convId1, folderId1)
      service.addConversationTo(convId1, folderId2)
      service.addConversationTo(convId2, folderId1)
      this.folders += FolderData(favouriteId, "", FolderData.FavouritesFolderType)
      this.folders += FolderData(folderId1, "F1", FolderData.CustomFolderType)
      this.folders += FolderData(folderId2, "F2", FolderData.CustomFolderType)
      Await.result(service.addConversationTo(convId1, favouriteId), 500.millis)

      // when
      val folders = Await.result(service.foldersToSynchronize(), 500.millis)

      // then
      folders.length shouldBe 3
      val folder1 = folders.find(_._1.id == folderId1).get
      folder1._1.name.toString shouldEqual "F1"
      folder1._1.folderType shouldEqual FolderData.CustomFolderType
      folder1._2 shouldEqual List(convId1, convId2)

      val folder2 = folders.find(_._1.id == folderId2).get
      folder2._1.name.toString shouldEqual "F2"
      folder2._1.folderType shouldEqual FolderData.CustomFolderType
      folder2._2 shouldEqual List(convId1)

      val favourite = folders.find(_._1.id == favouriteId).get
      favourite._1.name.toString shouldEqual ""
      favourite._1.folderType shouldEqual FolderData.FavouritesFolderType
      favourite._2 shouldEqual List(convId1)

    }
  }

  feature("Events handling") {
    scenario("Create the Favourites folder") {
      // given
      val convId = ConvId()
      val (folder, event) = generateEventOneFolder(convIds = Set(convId))

      // when
      val (before, after) = sendEvent(event)

      // then
      before.isEmpty shouldBe true
      after.size shouldBe 1
      after.head shouldBe (folder.id, (folder, Set(convId)))
    }

    scenario("Add a conversation to the Favourites") {
      // given
      val favId = FolderId()
      val convId1 = ConvId()
      val (folder, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

      val convId2 = ConvId()
      val (_, event2) = generateEventOneFolder(folderId = favId, convIds = Set(convId1, convId2))

      // when
      val (before, after) = sendEvent(event2)

      // then
      before.size shouldBe 1
      before.head shouldBe (favId, (folder, Set(convId1)))
      after.size shouldBe 1
      after.head shouldBe (favId, (folder, Set(convId1, convId2)))
    }

    scenario("Remove a conversation from the Favourites") {
      // given
      val favId = FolderId()
      val convId1 = ConvId()
      val (folder, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

      val convId2 = ConvId()
      val (_, event2) = generateEventOneFolder(folderId = favId, convIds = Set(convId1, convId2))
      sendEvent(event2)

      val (_, event3) = generateEventOneFolder(folderId = favId, convIds = Set(convId2))

      // when
      val (before, after) = sendEvent(event3)

      // then
      println(s"before: $before, after: $after")
      before.size shouldBe 1
      before.head shouldBe (favId, (folder, Set(convId1, convId2)))
      after.size shouldBe 1
      after.head shouldBe (favId, (folder, Set(convId2)))
    }

    scenario("Add a custom folder") {
      // given
      val favId = FolderId()
      val customId = FolderId()
      val convId1 = ConvId()
      val (folder1, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

      val convId2 = ConvId()
      val (folder2, event2) = generateEventAddFolder(event1, folderId = customId, name = "Custom", convIds = Set(convId2), folderType =  FolderData.CustomFolderType)

      // when
      val (before, after) = sendEvent(event2)

      // then
      before.size shouldBe 1
      before.head shouldBe (favId, (folder1, Set(convId1)))
      after.size shouldBe 2
      after(favId) shouldBe (folder1, Set(convId1))
      after(customId) shouldBe (folder2, Set(convId2))
    }

    scenario("Change the folder's name") {
      // given
      val favId = FolderId()
      val customId = FolderId()
      val convId1 = ConvId()
      val (folder1, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

      val convId2 = ConvId()
      val (folder2, event2) = generateEventAddFolder(event1, folderId = customId, name = "Custom", convIds = Set(convId2), folderType = FolderData.CustomFolderType)
      sendEvent(event2)

      val (folder3, event3) = generateEventAddFolder(event1, folderId = customId, name = "Custom 2", convIds = Set(convId2), folderType = FolderData.CustomFolderType)

      // when
      val (before, after) = sendEvent(event3)

      // then
      before.size shouldBe 2
      before(favId) shouldBe (folder1, Set(convId1))
      before(customId) shouldBe (folder2, Set(convId2))
      after.size shouldBe 2
      after(favId) shouldBe (folder1, Set(convId1))
      after(customId) shouldBe (folder3, Set(convId2))
    }

    scenario("Remove the Favourites folder") {
      // given
      val favId = FolderId()
      val customId = FolderId()
      val convId1 = ConvId()
      val (folder1, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

      val convId2 = ConvId()
      val (folder2, event2) = generateEventAddFolder(event1, folderId = customId, name = "Custom", convIds = Set(convId2), folderType = FolderData.CustomFolderType)
      sendEvent(event2)

      val (folder3, event3) = generateEventOneFolder(folderId = folder2.id, name = folder2.name, convIds = Set(convId2), folderType = FolderData.CustomFolderType)
      assert(folder2 == folder3)

      // when
      val (before, after) = sendEvent(event3)

      // then
      before.size shouldBe 2
      before(favId) shouldBe (folder1, Set(convId1))
      before(customId) shouldBe (folder2, Set(convId2))
      after.size shouldBe 1
      after.head shouldBe (customId, (folder3, Set(convId2)))
    }
  }

  private def generateEventOneFolder(folderId: FolderId   = FolderId(),
                                     name: String         = "Favourites",
                                     convIds: Set[ConvId] = Set.empty,
                                     folderType: Int      = FolderData.FavouritesFolderType) = {
    val folder = FolderData(folderId, name, folderType)
    (folder, FoldersEvent(Seq(RemoteFolderData(folder, convIds.map(id => RConvId(id.str)).toSeq))))
  }

  private def generateEventAddFolder(oldEvent: FoldersEvent,
                                     folderId: FolderId   = FolderId(),
                                     name: String         = "Favourites",
                                     convIds: Set[ConvId] = Set.empty,
                                     folderType: Int      = FolderData.FavouritesFolderType) = {
    val folder = FolderData(folderId, name, folderType)
    (folder, FoldersEvent(oldEvent.folders ++ Seq(RemoteFolderData(folder, convIds.map(id => RConvId(id.str)).toSeq))))
  }

  private def sendEvent(event: FoldersEvent) = {
    val service = getService

    def getState = for {
      map  <- service.foldersWithConvs.head
      data <- service.folders
      res  =  map.map { case (id, convs) => id -> (data.find(_.id == id).get, convs) }
    } yield res

    val test = for {
      before <- getState
      _      <- service.processEvent(event)
      after  <- getState
    } yield (before, after)

    Await.result(test, 500.millis)
  }
}
