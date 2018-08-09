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
import com.waz.model._
import com.waz.service.BackendConfig
import com.waz.utils.wrappers.URI
import com.waz.znet2.http.{HttpClient, Method, Request}

trait VersionBlacklistClient {
  def loadVersionBlacklist(): ErrorOrResponse[VersionBlacklist]
}

class VersionBlacklistClientImpl(backendConfig: BackendConfig)
                                (implicit httpClient: HttpClient) extends VersionBlacklistClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivation._
  import VersionBlacklistClientImpl._

  def loadVersionBlacklist(): ErrorOrResponse[VersionBlacklist] = {
    Request.create(method = Method.Get, url = blacklistsUrl(backendConfig.environment))
      .withResultType[VersionBlacklist]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object VersionBlacklistClientImpl {
  def blacklistsUrl(env: String): URL = new URL(
    URI.parse(s"https://clientblacklist.wire.com/${Option(env) filterNot (_.isEmpty) getOrElse "prod"}/android").toString
  )
}
