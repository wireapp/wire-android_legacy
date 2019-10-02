/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.waz.sync.client

import com.waz.model.{ConvId, FolderData, FolderId}
import com.waz.service.conversation.FolderDataWithConversations
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.CirceJSONSupport
import io.circe.syntax._

class FoldersClientSpec extends AndroidFreeSpec with CirceJSONSupport {

  feature("encoding request") {
    scenario("with favourites") {

      // given
      val convId1 = ConvId("c1")
      val convId2 = ConvId("c2")
      val folderId1 = FolderId("f1")
      val favouritesId = FolderId("fav")
      val folder1 = FolderData(folderId1, "F1", FolderData.CustomFolderType)
      val folderFavorites = FolderData(favouritesId, "FAV", FolderData.FavouritesFolderType)
      val payload = List(
        FolderDataWithConversations(folder1, List(convId1, convId2)),
        FolderDataWithConversations(folderFavorites, List(convId2))
      )

      // when
      val json = payload.asJson

      // then
      json.toString().replaceAll("\\s","") shouldEqual """[
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
                                    |  }]
                                  """.stripMargin.replaceAll("\\s","")
    }
  }
}
