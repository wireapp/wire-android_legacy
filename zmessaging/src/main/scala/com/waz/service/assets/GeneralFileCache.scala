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
package com.waz.service.assets

import java.io.File

import com.waz.cache2.{FileCache, SimpleFileCache}

import scala.concurrent.ExecutionContext

trait GeneralFileCache extends FileCache[String]

class GeneralFileCacheImpl(val cacheDirectory: File)
                          (implicit val ec: ExecutionContext) extends SimpleFileCache[String] with GeneralFileCache {

  override protected def createFileName(key: String): String = key

}
