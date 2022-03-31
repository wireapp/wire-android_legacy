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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.BackendConfig
import com.waz.znet2.http.{HttpClient, Method, Request}
import com.waz.log.LogSE._
import com.wire.signals.CancellableFuture

trait VersionBlacklistClient {
  def loadVersionBlacklist(): ErrorOrResponse[VersionBlacklist]
}

class VersionBlacklistClientImpl(backendConfig: BackendConfig)
                                (implicit httpClient: HttpClient) extends VersionBlacklistClient with DerivedLogTag {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._

  def loadVersionBlacklist(): ErrorOrResponse[VersionBlacklist] =
    blacklistsUrl match {
      case Some(url) =>
      verbose(l"Loading blacklist from: $url")
      Request.create(method = Method.Get, url)
        .withResultType[VersionBlacklist]
        .withErrorType[ErrorResponse]
        .executeSafe
      case _ =>
        //if url wasn't given, then just accept any version
        verbose(l"There is no blacklist URL to download")
        CancellableFuture.successful(Right(VersionBlacklist()))
    }

  def blacklistsUrl: Option[URL] = {
    backendConfig.blacklistHost.map { uri =>
      new URL(if (uri.getPath.endsWith("/android")) uri.toString
      else uri.buildUpon.appendPath("android").build.toString)
    }
  }
}
