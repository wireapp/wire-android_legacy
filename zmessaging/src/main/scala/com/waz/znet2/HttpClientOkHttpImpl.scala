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

import java.io.IOException

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.threading.CancellableFuture
import com.waz.utils.ExecutorServiceWrapper
import com.waz.znet2.OkHttpConverters._
import com.waz.znet2.http._
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.{Level, Logger}
import okhttp3.{Call, Callback, Dispatcher, OkHttpClient}

import scala.concurrent.{ExecutionContext, Promise}

class HttpClientOkHttpImpl(implicit protected val ec: ExecutionContext) extends HttpClient {

  import HttpClient._

  private val client = {
    val logging = new HttpLoggingInterceptor(new Logger() {
      override def log(message: String): Unit = verbose(message)
    })
    logging.setLevel(Level.HEADERS)

    new OkHttpClient.Builder()
      .dispatcher(new Dispatcher(new ExecutorServiceWrapper()(ec)))
      .addInterceptor(logging)
      .build()
  }

  protected def execute(
      request: Request[Body],
      uploadCallback: Option[ProgressCallback] = None,
      downloadCallback: Option[ProgressCallback] = None
  ): CancellableFuture[Response[Body]] =
    request.interceptor.intercept(request)
      .flatMap { authRequest =>
        val promise = Promise[Response[Body]]
        new CancellableFuture(promise) {
          verbose(s"start executing request: $request")
          private val okCall = client.newCall(convertHttpRequest(authRequest, uploadCallback))
          okCall.enqueue(new Callback {
            override def onResponse(call: Call, response: okhttp3.Response): Unit =
              promise.trySuccess(convertOkHttpResponse(response, downloadCallback))
            override def onFailure(call: Call, e: IOException): Unit = {
              error("failure while getting okHttp response.", e)
              promise.tryFailure(ConnectionError(e))
            }
          })

          override def cancel()(implicit tag: LogTag): Boolean = {
            okCall.cancel()
            super.cancel()(tag)
          }
        }
      }

}
