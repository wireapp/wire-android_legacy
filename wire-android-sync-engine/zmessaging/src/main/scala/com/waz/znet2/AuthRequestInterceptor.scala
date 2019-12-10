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
package com.waz.znet2

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.sync.client.AuthenticationManager
import com.waz.threading.CancellableFuture
import com.waz.znet2.http.HttpClient.ProgressCallback
import com.waz.znet2.http._

trait AuthRequestInterceptor extends RequestInterceptor

class AuthRequestInterceptorImpl(authManager: AuthenticationManager, httpClient: HttpClient, attеmptsIfAuthFailed: Int = 1)
    extends AuthRequestInterceptor with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def intercept(request: Request[Body]): CancellableFuture[Request[Body]] =
    CancellableFuture.lift(authManager.currentToken()).map {
      case Right(token) =>
        request.copy(headers = Headers(request.headers.headers ++ token.headers))
      case Left(err) =>
        throw new IllegalArgumentException(err.toString)
    }

  override def intercept(
      request: Request[Body],
      uploadCallback: Option[ProgressCallback],
      downloadCallback: Option[ProgressCallback],
      response: Response[Body]
  ): CancellableFuture[Response[Body]] =
    if (response.code == ResponseCode.Unauthorized && attеmptsIfAuthFailed > 0) {
      verbose(l"Got 'Unauthorized' error. Retrying... Attempts left: ${attеmptsIfAuthFailed - 1}")
      CancellableFuture.lift(authManager.invalidateToken()).flatMap { _ =>
        httpClient.execute(
          request.copy(interceptor = new AuthRequestInterceptorImpl(authManager, httpClient, attеmptsIfAuthFailed - 1)),
          uploadCallback,
          downloadCallback
        )
      }
    } else CancellableFuture.successful(response)

}
