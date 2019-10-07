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

import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{FolderData, RConvId}
import com.waz.sync.client.PropertiesClient.PropertyPath
import com.waz.utils.JsonDecoder
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request, ResponseCode}
import io.circe.Encoder
import io.circe.syntax._
import org.json.JSONObject

trait FoldersClient {
  def putFolders(folders: Seq[RemoteFolderData]): ErrorOrResponse[Unit]
}

case class RemoteFolderData(folderData: FolderData, conversations: Set[RConvId])

object RemoteFolderData {

  lazy implicit val folderDataConversationsCirceEncoder: Encoder[RemoteFolderData] = Encoder.forProduct4(
      "name", "type", "id", "conversations"
  )(fd => (fd.folderData.name.str, fd.folderData.folderType, fd.folderData.id.str, fd.conversations))


  implicit val remoteFolderDataDecoder: JsonDecoder[RemoteFolderData] = new JsonDecoder[RemoteFolderData] {
    override def apply(implicit js: JSONObject): RemoteFolderData = {
      import JsonDecoder._

      val conversations: Seq[RConvId] = decodeRConvIdSeq('conversations)
      RemoteFolderData(
        FolderData(decodeFolderId('id), decodeString('name), decodeInt('type)),
        conversations.toSet
      )
    }
  }
}

class FoldersClientImpl(implicit
                           urlCreator: UrlCreator,
                           httpClient: HttpClient,
                           authRequestInterceptor: AuthRequestInterceptor) extends FoldersClient with DerivedLogTag {

  import HttpClient.AutoDerivation._
  import HttpClient.dsl._

  private implicit val errorResponseDeserializer: RawBodyDeserializer[ErrorResponse] =
    objectFromCirceJsonRawBodyDeserializer[ErrorResponse]


  override def putFolders(folders: Seq[RemoteFolderData]): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = PropertyPath("labels"), body = folders.asJson)
      .withResultHttpCodes(ResponseCode.SuccessCodes)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

