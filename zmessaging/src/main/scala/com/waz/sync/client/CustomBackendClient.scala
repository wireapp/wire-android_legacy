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
package com.waz.sync.client

import java.net.URL
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.CanBeShownImpl
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE.{toLogHelper, verbose}
import com.waz.sync.client.CustomBackendClient.BackendConfigResponse
import com.waz.utils.CirceJSONSupport
import com.waz.znet2.http.{HttpClient, Method, RawBodyDeserializer, Request}

trait CustomBackendClient {
  def loadBackendConfig(url: URL): ErrorOrResponse[BackendConfigResponse]
}

class CustomBackendClientImpl(implicit httpClient: HttpClient)
  extends CustomBackendClient
    with CirceJSONSupport
    with DerivedLogTag
{

  import HttpClient.AutoDerivation._
  import HttpClient.dsl._

  private implicit val errorResponseDeserializer: RawBodyDeserializer[ErrorResponse] =
    objectFromCirceJsonRawBodyDeserializer[ErrorResponse]

  def loadBackendConfig(url: URL): ErrorOrResponse[BackendConfigResponse] = {
    verbose(l"Loading custom backend configuration from ${new CanBeShownImpl(url.toString)}")
    Request.create(Method.Get, url)
      .withResultType[BackendConfigResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object CustomBackendClient {
  case class BackendConfigResponse(endpoints: EndPoints, title: String)

  case class EndPoints(backendURL: URL,
                       backendWSURL: URL,
                       blackListURL: Option[URL],
                       teamsURL: URL,
                       accountsURL: URL,
                       websiteURL: URL)
}
