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

import io.circe.generic._
import com.waz.api.impl.ErrorResponse
import com.waz.model.{ConvId, FolderData, FolderId, Name}
import com.waz.service.conversation.FolderDataWithConversations
import com.waz.sync.client.PropertiesClient.PropertyPath
import com.waz.utils.CirceJSONSupport
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.{HttpClient, Request, ResponseCode}
import com.waz.znet2.http.Request.UrlCreator
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

trait FoldersClient {
  def putFolders(folders: Seq[FolderDataWithConversations]): ErrorOrResponse[Unit]
}

class FoldersClientImpl(implicit
                           urlCreator: UrlCreator,
                           httpClient: HttpClient,
                           authRequestInterceptor: AuthRequestInterceptor) extends FoldersClient with CirceJSONSupport {

  import HttpClient.dsl._
  import HttpClient.AutoDerivation._


  override def putFolders(folders: Seq[FolderDataWithConversations]): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = PropertyPath("labels"), body = folders.asJson)
      .withResultHttpCodes(ResponseCode.SuccessCodes)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

