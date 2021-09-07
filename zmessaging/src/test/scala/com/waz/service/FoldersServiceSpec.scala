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
import com.waz.model.{ConvId, ConversationData, ConversationFolderData, FolderData, FolderId, FoldersEvent, Name, RConvId, SyncId}
import com.waz.service.conversation.FoldersService.{FoldersProperty, IntermediateFolderData}
import com.waz.service.conversation.{FoldersService, FoldersServiceImpl, RemoteFolderData}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.TestUserPreferences
import com.waz.threading.Threading
import com.wire.signals.{EventStream, Signal}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import com.waz.utils.CirceJSONSupport
import io.circe.syntax._
import io.circe.parser.decode

class FoldersServiceSpec extends AndroidFreeSpec with DerivedLogTag with CirceJSONSupport {
  import Threading.Implicits.Background

  val foldersStorage             = mock[FoldersStorage]
  val conversationFoldersStorage = mock[ConversationFoldersStorage]
  val conversationStorage        = mock[ConversationStorage]
  val userPrefs                  = new TestUserPreferences
  val sync                       = mock[SyncServiceHandle]

  (sync.syncFolders _).expects().anyNumberOfTimes().returning(Future.successful(SyncId()))

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

  (foldersStorage.values _).expects().anyNumberOfTimes().onCall { _ =>
    Future(folders.toVector)
  }

  (foldersStorage.keySet _).expects().anyNumberOfTimes().onCall { _ =>
    Future.successful(folders.map(_.id).toSet)
  }

  (foldersStorage.optSignal _).expects(*).anyNumberOfTimes().onCall { folderId: FolderId =>
    Signal.const(folders.find(_.id === folderId))
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

  val convId1 = ConvId("conv_id1")
  val convId2 = ConvId("conv_id2")
  val convId3 = ConvId("conv_id3")
  val conversations = Set(convId1, convId2).map(id => id -> ConversationData(id, remoteId = RConvId(id.str))).toMap

  (conversationStorage.get _).expects(*).anyNumberOfTimes().onCall { convId: ConvId => Future.successful(conversations.get(convId)) }

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
      val service = new FoldersServiceImpl(foldersStorage, conversationFoldersStorage, conversationStorage, userPrefs, sync)
      _service = Some(service)
      service
  }

  feature("Favorites") {
    scenario("adding to favorites") {

      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val favConvs = for {
        favId <- service.ensureFavoritesFolder()
        _     <- service.addConversationTo(convId1, favId, false)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId1)

    }

    scenario("adding and removing from favorites") {

      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val favConvs = for {
        favId <- service.ensureFavoritesFolder()
        _     <- service.addConversationTo(convId1, favId, false)
        _     <- service.removeConversationFrom(convId1, favId, false)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis).isEmpty shouldBe true
    }

    scenario("Keep in favorites after adding to folder") {
      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val favConvs = for {
        favId     <- service.ensureFavoritesFolder()
        folderId  <- service.addFolder("custom folder", false)
        _         <- service.addConversationTo(convId1, favId, false)
        _         <- service.addConversationTo(convId1, folderId, false)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId1)
    }

    scenario("Conversations stays in favorites after removing from another folder") {
      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val favConvs = for {
        favId     <- service.ensureFavoritesFolder()
        folderId  <- service.addFolder("custom folder", false)
        _         <- service.addConversationTo(convId1, favId, false)
        _         <- service.addConversationTo(convId1, folderId, false)
        _         <- service.removeConversationFrom(convId1, folderId, false)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId1)
    }

    scenario("favorites stays empty after adding to folder") {
      // given
      val folderId = FolderId("folder_id1")
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val favConvs = for {
        favId     <- service.ensureFavoritesFolder()
        folderId  <- service.addFolder("custom folder", false)
        _         <- service.addConversationTo(convId1, folderId, false)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis).isEmpty shouldBe true
    }

    scenario("Multiple conversations in favorites") {
      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val favConvs = for {
        favId <- service.ensureFavoritesFolder()
        _     <- service.addConversationTo(convId1, favId, false)
        _     <- service.addConversationTo(convId2, favId, false)
        _     <- service.addConversationTo(convId3, favId, false)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId1, convId2, convId3)
    }

    scenario("Adding and removing multiple conversations in favorites") {
      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val favConvs = for {
        favId <- service.ensureFavoritesFolder()
        _     <- service.addConversationTo(convId1, favId, false)
        _     <- service.addConversationTo(convId2, favId, false)
        _     <- service.removeConversationFrom(convId1, favId, false)
        _     <- service.addConversationTo(convId3, favId, false)
        favs  <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId2, convId3)
    }

    scenario("Adding and removing conversations from custom folders does not change favorites") {
      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      val favConvs = for {
        favId     <- service.ensureFavoritesFolder()
        _         <- service.addConversationTo(convId1, favId, false)
        _         <- service.addConversationTo(convId2, favId, false)
        folderId1 <- service.addFolder("custom folder 1", false)
        folderId2 <- service.addFolder("custom folder 2", false)
        _         <- service.addConversationTo(convId1, folderId1, false)
        _         <- service.addConversationTo(convId2, folderId2, false)
        _         <- service.addConversationTo(convId3, folderId1, false)
        _         <- service.removeConversationFrom(convId1, folderId1, false)
        favs      <- service.convsInFolder(favId)
      } yield favs

      // then
      Await.result(favConvs, 500.millis) shouldEqual Set(convId1, convId2)
    }
  }

  feature("Custom folders") {
    scenario("Add conversation to a folder") {

      // given
      val folderId1 = FolderId("folder_id1")
      val service = getService

      // when
      val res = for {
        folderId1  <- service.addFolder("custom folder", false)
        _          <- service.addConversationTo(convId1, folderId1, false)
        convs      <- service.convsInFolder(folderId1)
        isInFolder <- service.isInFolder(convId1, folderId1)
      } yield (convs, isInFolder)

      // then
      Await.result(res, 500.millis) shouldBe (Set(convId1), true)
    }

    scenario("Add conversations to various folders") {
      // given
      val service = getService

      // when
      val res = for {
        folderId1 <- service.addFolder("custom folder 1", false)
        folderId2 <- service.addFolder("custom folder 2", false)
        _         <- service.addConversationTo(convId1, folderId1, false)
        _         <- service.addConversationTo(convId2, folderId2, false)
        _         <- service.addConversationTo(convId3, folderId1, false)
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
      val service = getService
      val res = for {
        folderId1 <- service.addFolder("custom folder 1", false)
        folderId2 <- service.addFolder("custom folder 2", false)
        _         <- service.addConversationTo(convId1, folderId1, false)
        _         <- service.addConversationTo(convId2, folderId2, false)
        _         <- service.addConversationTo(convId3, folderId1, false)
        _         <- service.removeConversationFrom(convId1, folderId1, false)
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
      val folderId1 = FolderId("folder_id1")
      val service = getService

      // when
      val convs = for {
        folderId1 <- service.addFolder("custom folder 1", false)
        _         <- service.addConversationTo(convId1, folderId1, false)
        _         <- service.removeConversationFrom(convId1, folderId1, false)
        convs     <- service.convsInFolder(folderId1)
      } yield convs

      // then
      Await.result(convs, 500.millis).isEmpty shouldBe true
    }

    scenario("Remove conversations from all folders") {
      // given
      val service = getService

      // when
      val convs = for {
        folderId1 <- service.addFolder("custom folder 1", false)
        folderId2 <- service.addFolder("custom folder 2", false)
        _         <- service.addConversationTo(convId2, folderId1, false)
        _         <- service.addConversationTo(convId1, folderId2, false)
        _         <- service.removeConversationFromAll(convId1, false)
        convs1    <- service.convsInFolder(folderId1)
        convs2    <- service.convsInFolder(folderId2)
      } yield (convs1, convs2)
      val (conversationsInFolder1, conversationsInFolder2) = Await.result(convs, 500.millis)

      // then
      conversationsInFolder1 shouldEqual Set(convId2)
      conversationsInFolder2 shouldEqual Set()
    }

    scenario("Get list of folders for conversation includes favorites") {
      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val fs = for {
        favId     <- service.ensureFavoritesFolder()
        folderId1 <- service.addFolder("custom folder 1", false)
        folderId2 <- service.addFolder("custom folder 2", false)
        _         <- service.addConversationTo(convId1, folderId1, false)
        _         <- service.addConversationTo(convId1, folderId2, false)
        _         <- service.addConversationTo(convId1, favId, false)
        folders   <- service.foldersForConv(convId1)
      } yield ((favId, folderId1, folderId2), folders)
      val ((favId, folderId1, folderId2), folders) = Await.result(fs, 500.millis)

      // then
      folders shouldEqual Set(folderId1, folderId2, favId)
    }

    scenario("remove a conversation from a folder") {
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      val convInFavsAfterAdding = for {
        favFolder <- service.ensureFavoritesFolder()
        _         <- service.addConversationTo(convId1, favFolder, false)
        res       <- service.isInFolder(convId1, favFolder)
      } yield res
      assert(Await.result(convInFavsAfterAdding, 500.millis) == true)

      val convInFavsAfterRemoval = for {
        favFolder <- service.ensureFavoritesFolder()
        _         <- service.removeConversationFrom(convId1, favFolder, false)
        res       <- service.isInFolder(convId1, favFolder)
      } yield res
      assert(Await.result(convInFavsAfterRemoval, 500.millis) == false)
    }

    scenario("Get mapping from folders to conversations") {
      // given
      val service = getService
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val foldersFuture = for {
        favouriteId <- service.ensureFavoritesFolder()
        folderId1   <- service.addFolder("F1", false)
        _           <- service.addConversationTo(convId1, folderId1, false)
        _           <- service.addConversationTo(convId2, folderId1, false)
        folderId2   <- service.addFolder("F2", false)
        _           <- service.addConversationTo(convId1, folderId2, false)
        _           <- service.addConversationTo(convId1, favouriteId, false)
        folders     <- service.foldersToSynchronize()
      } yield (favouriteId, folderId1, folderId2, folders)
      val (favouriteId, folderId1, folderId2, folders) = Await.result(foldersFuture, 500.millis)

      // then
      folders.length shouldBe 3
      val folder1 = folders.find(_.folderData.id == folderId1).get
      folder1.folderData.name.toString shouldEqual "F1"
      folder1.folderData.folderType shouldEqual FolderData.CustomFolderType
      folder1.conversations shouldEqual Set(RConvId(convId1.str), RConvId(convId2.str))

      val folder2 = folders.find(_.folderData.id == folderId2).get
      folder2.folderData.name.toString shouldEqual "F2"
      folder2.folderData.folderType shouldEqual FolderData.CustomFolderType
      folder2.conversations shouldEqual Set(RConvId(convId1.str))

      val favourite = folders.find(_.folderData.id == favouriteId).get
      favourite.folderData.name.toString shouldEqual ""
      favourite.folderData.folderType shouldEqual FolderData.FavoritesFolderType
      favourite.conversations shouldEqual Set(RConvId(convId1.str))
    }
  }

  feature("Events handling") {
    scenario("Create the favorites folder") {
      // given
      val (folder, event) = generateEventOneFolder(convIds = Set(convId1))

      // when
      val (before, after) = sendEvent(event)

      // then
      before.isEmpty shouldBe true
      after.size shouldBe 1
      after.head shouldBe (folder.id, (folder, Set(convId1)))
    }

    scenario("Add a conversation to the favorites") {
      // given
      val favId = FolderId()
      val (folder, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

      val (_, event2) = generateEventOneFolder(folderId = favId, convIds = Set(convId1, convId2))

      // when
      val (before, after) = sendEvent(event2)

      // then
      before.size shouldBe 1
      before.head shouldBe (favId, (folder, Set(convId1)))
      after.size shouldBe 1
      after.head shouldBe (favId, (folder, Set(convId1, convId2)))
    }

    scenario("Remove a conversation from the favorites") {
      // given
      val favId = FolderId()
      val (folder, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

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
      val (folder1, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

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
      val (folder1, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

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

    scenario("Remove the favorites folder") {
      // given
      val favId = FolderId()
      val customId = FolderId()
      val (folder1, event1) = generateEventOneFolder(folderId = favId, convIds = Set(convId1))
      sendEvent(event1)

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

  feature("Upload to backend") {
    scenario("Add a folder will upload to backend") {
      // given
      val service = getService

      // expect
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      val fid = Await.result(service.addFolder("custom folder", false), 500.millis)
      Await.result(service.addConversationTo(convId1, fid, true), 500.millis)
    }

    scenario("Add to an existing folder will upload to backend") {
      // given
      val service = getService
      val folderId = Await.result(service.addFolder("custom folder", false), 500.millis)

      // expect
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      Await.result(service.addConversationTo(ConvId("foo"), folderId, true), 500.millis)
    }

    scenario("Remove from folder will upload to backend") {
      // given
      val service = getService
      val convId = ConvId("cid1")
      val folderId = Await.result(service.addFolder("custom folder", false), 500.millis)
      Await.result(service.addConversationTo(convId, folderId, false), 500.millis)

      // expect
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      Await.result(service.removeConversationFrom(convId, folderId, true), 500.millis)
    }

    scenario("Remove from all folders will upload to backend") {
      // given
      val service = getService
      val convId = ConvId("cid1")
      val folderId = Await.result(service.addFolder("custom folder", false), 500.millis)
      Await.result(service.addConversationTo(convId, folderId, false), 500.millis)

      // expect
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      Await.result(service.removeConversationFromAll(convId, true), 500.millis)
    }

    scenario("Renaming folder will upload to backend") {
      // given
      val service = getService
      val folderId = Await.result(service.addFolder("Foo", false), 500.millis)

      // expect
      (sync.postFolders _).expects().once().returning{ Future.successful(SyncId()) }

      // when
      Await.result(service.update(folderId, "Bam!", true), 500.millis)
    }
  }

  private def generateEventOneFolder(folderId: FolderId   = FolderId(),
                                     name: String         = "favorites",
                                     convIds: Set[ConvId] = Set.empty,
                                     folderType: Int      = FolderData.FavoritesFolderType) = {
    val folder = FolderData(folderId, name, folderType)
    (folder, FoldersEvent(Seq(RemoteFolderData(folder, convIds.map(id => RConvId(id.str))))))
  }

  private def generateEventAddFolder(oldEvent: FoldersEvent,
                                     folderId: FolderId   = FolderId(),
                                     name: String         = "favorites",
                                     convIds: Set[ConvId] = Set.empty,
                                     folderType: Int      = FolderData.FavoritesFolderType) = {
    val folder = FolderData(folderId, name, folderType)
    (folder, FoldersEvent(oldEvent.folders ++ Seq(RemoteFolderData(folder, convIds.map(id => RConvId(id.str))))))
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
      _      <- service.processFolders(event.folders)
      after  <- getState
    } yield (before, after)

    Await.result(test, 500.millis)
  }

  feature("encoding request") {
    scenario("with favorites") {

      // given
      val convId1 = RConvId("c1")
      val convId2 = RConvId("c2")
      val folderId1 = FolderId("f1")
      val favoritesId = FolderId("fav")
      val folder1 = FolderData(folderId1, "F1", FolderData.CustomFolderType)
      val folderFavorites = FolderData(favoritesId, "FAV", FolderData.FavoritesFolderType)
      val payload = FoldersProperty.fromRemote(Seq(
        RemoteFolderData(folder1, Set(convId1, convId2)),
        RemoteFolderData(folderFavorites, Set(convId2))
      ))

      // when
      val json = payload.asJson.noSpaces

      // then
      json shouldEqual """{
                         | "labels":
                         |  [
                         |    {
                         |      "id" : "f1",
                         |      "name" : "F1",
                         |      "type" : 0,
                         |      "conversations" : [
                         |        "c1",
                         |        "c2"
                         |      ]
                         |    },
                         |    {
                         |      "id" : "fav",
                         |      "name" : "FAV",
                         |      "type" : 1,
                         |      "conversations" : [
                         |        "c2"
                         |      ]
                         |  }]
                         |}
                       """.stripMargin.replaceAll("\\s","")
    }
  }

  feature("decoding payload") {
    scenario ("with favorites") {

      // given
      val payload = """{
                 | "labels" : [
                 |  {
                 |    "name" : "F1",
                 |    "type" : 0,
                 |    "id" : "f1",
                 |    "conversations" : [
                 |      "c1",
                 |      "c2"
                 |    ]
                 |  },
                 |  {
                 |    "name" : "FAV",
                 |    "type" : 1,
                 |    "id" : "fav",
                 |    "conversations" : [
                 |      "c2"
                 |    ]
                 |  }
                 |]}""".stripMargin

      // when
      val seq = decode[FoldersProperty](payload) match {
        case Right(fp)   => fp.toRemote
        case Left(error) => fail(error.getMessage)
      }

      // then
      seq(0).folderData.name shouldEqual Name("F1")
      seq(0).folderData.id shouldEqual FolderId("f1")
      seq(0).folderData.folderType shouldEqual FolderData.CustomFolderType
      seq(0).conversations shouldEqual Set(RConvId("c1"), RConvId("c2"))

      seq(1).folderData.name shouldEqual Name("FAV")
      seq(1).folderData.id shouldEqual FolderId("fav")
      seq(1).folderData.folderType shouldEqual FolderData.FavoritesFolderType
      seq(1).conversations shouldEqual Set(RConvId("c2"))
    }

    scenario ("favorites with no name") {

      // given
      val payload = """{
                       |  "labels": [
                       |    {
                       |      "name" : "F1",
                       |      "type" : 0,
                       |      "id" : "f1",
                       |      "conversations" : [
                       |        "c1",
                       |        "c2"
                       |      ]
                       |    },
                       |    {
                       |      "type" : 1,
                       |      "id" : "fav",
                       |      "conversations" : [
                       |        "c2"
                       |      ]
                       |    }
                       |  ]
                       |}""".stripMargin

      // when
      val seq = decode[FoldersProperty](payload) match {
        case Right(fp)   => fp.toRemote
        case Left(error) => fail(error.getMessage)
      }

      // then
      seq(0).folderData.name shouldEqual Name("F1")
      seq(0).folderData.id shouldEqual FolderId("f1")
      seq(0).folderData.folderType shouldEqual FolderData.CustomFolderType
      seq(0).conversations shouldEqual Set(RConvId("c1"), RConvId("c2"))

      seq(1).folderData.name shouldEqual Name("")
      seq(1).folderData.id shouldEqual FolderId("fav")
      seq(1).folderData.folderType shouldEqual FolderData.FavoritesFolderType
      seq(1).conversations shouldEqual Set(RConvId("c2"))
    }

    scenario ("uppercase conv IDs (thanks, iOS!) are lowercased") {

      // given
      val payload = """{
                      |  "labels": [
                      |    {
                      |      "name" : "F1",
                      |      "type" : 0,
                      |      "id" : "f1",
                      |      "conversations" : [
                      |        "UPPERCASE",
                      |        "c2"
                      |      ]
                      |    }
                      |  ]
                      |}""".stripMargin

      // when
      val seq = decode[FoldersProperty](payload) match {
        case Right(fp)   => fp.toRemote
        case Left(error) => fail(error.getMessage)
      }

      // then
      seq(0).folderData.name shouldEqual Name("F1")
      seq(0).folderData.id shouldEqual FolderId("f1")
      seq(0).folderData.folderType shouldEqual FolderData.CustomFolderType
      seq(0).conversations shouldEqual Set(RConvId("uppercase"), RConvId("c2"))
    }
  }
}
