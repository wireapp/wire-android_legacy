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

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.{Handler, Looper}
import com.waz.log.BasicLogging.LogTag
import com.waz.threading.CancellableFuture

import scala.concurrent.Promise

/**
 * Creates a future waiting for first change on given uri.
 */
object ContentObserverFuture {
  lazy val handler = new Handler(Looper.getMainLooper)

  def apply(uri: Uri, cr: ContentResolver, notifyForDescendants: Boolean = false): CancellableFuture[Unit] = {
    val p = Promise[Unit]()
    val observer = new ContentObserver(handler) {
      override def onChange(selfChange: Boolean): Unit = {

        cr.unregisterContentObserver(this)
        p.trySuccess({})
      }
    }
    cr.registerContentObserver(uri, notifyForDescendants, observer)
    new CancellableFuture[Unit](p) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        cr.unregisterContentObserver(observer)
        super.cancel()(tag)
      }
    }
  }
}
