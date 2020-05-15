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

import com.waz.api.impl.ErrorResponse
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}

trait CallingClient {
  def getConfig: ErrorOrResponse[String]
}

class CallingClientImpl(implicit
                        urlCreator: UrlCreator,
                        client: HttpClient,
                        authRequestInterceptor: AuthRequestInterceptor) extends CallingClient {

  import CallingClientImpl._
  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._

  override def getConfig: ErrorOrResponse[String] = {
    Request.Get(relativePath = CallConfigPath)
      .withResultType[String]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

}

object CallingClientImpl {
  val CallConfigPath = "/calls/config/v2"
}
