/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.sync.queue

import com.waz.model.sync.{SyncJob, SyncRequest}
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync.SyncRequest.{PostAssetStatus, PostCustomFoldersAndFavourites, RegisterPushToken}
import com.waz.model.{ConvId, FolderData, FolderId, MessageId, PushToken, SyncId}
import com.waz.service.assets2.UploadAssetStatus
import com.waz.service.conversation.FolderDataWithConversations
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.queue.SyncJobMerger.Merged

import scala.concurrent.duration._

class SyncRequestSpec extends AndroidFreeSpec {

  scenario("Merging RegisterPushToken") {

    val job1 = SyncJob(SyncId(), RegisterPushToken(PushToken("token")), priority = Priority.High)
    val job2 = SyncJob(SyncId(), RegisterPushToken(PushToken("token2")), priority = Priority.High)

    job1.merge(job2) shouldEqual Merged(job1.copy(request = job2.request))
  }

  scenario("PostAssetStatus encoding decoding") {
    val request = PostAssetStatus(ConvId(), MessageId(), Some(10.minutes), UploadAssetStatus.Failed)
    SyncRequest.Decoder.apply(SyncRequest.Encoder(request)) shouldEqual request
  }

  scenario("PostCustomFoldersAndFavourites") {
    //given
    val convId1 = ConvId("cid1")
    val convId2 = ConvId("cid2")
    val convId3 = ConvId("cid3")
    val folderId1 = FolderId("Fid1")
    val folderId2 = FolderId("Fid2")
    val favourites = FolderId("FAVS")
    val mapping = List(
      FolderDataWithConversations(FolderData(folderId1, "F1", FolderData.CustomFolderType), List(convId1)),
      FolderDataWithConversations(FolderData(folderId2, "F2", FolderData.CustomFolderType), List(convId1, convId2)),
      FolderDataWithConversations(FolderData(favourites, "", FolderData.FavouritesFolderType), List(convId2, convId3))
    )

    // when
    val request = PostCustomFoldersAndFavourites(mapping)

    // then
    SyncRequest.Decoder.apply(SyncRequest.Encoder(request)) shouldEqual request

  }


}
