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
package com.waz.sync

import com.waz.api.impl.ErrorResponse
import com.waz.cache.CacheEntry
import com.waz.threading.CancellableFuture
import org.json.{JSONArray, JSONObject}

import scala.concurrent.Future

package object client {

  def userAgent(appVersion: String = "*", zmsVersion: String = "*"): String = {
    import android.os.Build._
    s"Wire/$appVersion (zms $zmsVersion; Android ${VERSION.RELEASE}; $MANUFACTURER $MODEL)"
  }

  //TODO Use only one from this two.
  type ErrorOr[T] = Future[Either[ErrorResponse, T]]
  type ErrorOrResponse[T] = CancellableFuture[Either[ErrorResponse, T]]

  //TODO Have to be removed while resolving https://github.com/wireapp/wire-android-sync-engine/issues/376
  sealed trait ResponseContent
  sealed trait JsonResponse extends ResponseContent
  case object EmptyResponse extends ResponseContent
  case class StringResponse(value: String) extends ResponseContent
  case class JsonObjectResponse(value: JSONObject) extends JsonResponse
  case class JsonArrayResponse(value: JSONArray) extends JsonResponse
  case class BinaryResponse(value: Array[Byte], mime: String) extends ResponseContent {
    override def toString: String = s"BinaryResponse(${new String(value.take(1024))}, $mime)"
  }
  case class FileResponse(value: CacheEntry, mime: String) extends ResponseContent

}
