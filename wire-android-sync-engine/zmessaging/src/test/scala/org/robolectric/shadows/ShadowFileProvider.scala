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
package org.robolectric.shadows

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import android.content.Context
import android.net.Uri
import android.support.v4.content.FileProvider
import org.robolectric.annotation.{Implementation, Implements}

import scala.annotation.tailrec

@Implements(classOf[FileProvider]) object ShadowFileProvider {
  private val registeredUris = new AtomicReference(Map.empty[(String, File), Uri])

  @tailrec def register(authority: String, file: File): Unit = {
    val uri = new Uri.Builder().scheme("content").authority(authority).path(file.getPath).build()
    val prev = registeredUris.get
    if (! registeredUris.compareAndSet(prev, prev + ((authority, file) -> uri))) register(authority, file)
  }

  def reset(): Unit = registeredUris.set(Map.empty)

  @Implementation def getUriForFile(context: Context, authority: String, file: File): Uri =
    registeredUris.get.getOrElse((authority, file), throw new IllegalArgumentException(s"no URI registered for authority '$authority' and file '$file'"))
}

@Implements(classOf[FileProvider]) class ShadowFileProvider
