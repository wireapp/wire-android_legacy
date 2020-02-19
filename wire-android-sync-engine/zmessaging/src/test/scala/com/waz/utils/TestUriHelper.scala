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
package com.waz.utils

import java.io.InputStream
import java.net.URI

import com.waz.model.Mime
import com.waz.service.assets.{AssetFailure, AssetInput, AssetStream, UriHelper}

import scala.util.{Failure, Success, Try}

class TestUriHelper extends UriHelper {
  override def openInputStream(uri: URI): Try[InputStream] = Try { uri.toURL.openStream() }
  override def extractSize(uri: URI): Try[Long] = ???
  override def extractMime(uri: URI): Try[Mime] = ???
  override def extractFileName(uri: URI): Try[String] = ???

  override def assetInput(uri: URI): AssetInput = openInputStream(uri) match {
    case Success(stream)    => AssetStream(stream)
    case Failure(throwable) => AssetFailure(throwable)
  }
}
