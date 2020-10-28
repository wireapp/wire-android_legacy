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

import java.io.{FilterInputStream, IOException, InputStream}

import com.waz.znet2.ProgressInputStream.Listener

object ProgressInputStream {
  trait Listener {
    def progressUpdated(bytesRead: Long, bytesReadTotal: Long): Unit
  }
}

class ProgressInputStream(val input: InputStream, val listener: Listener) extends FilterInputStream(input) {
  private var totalNumBytesRead = 0L
  listener.progressUpdated(0, 0)

  @throws[IOException]
  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    updateProgress(super.read(b, off, len)).toInt
  }

  @throws[IOException]
  override def skip(n: Long): Long = {
    updateProgress(super.skip(n))
  }

  override def mark(readlimit: Int): Unit = {
    throw new UnsupportedOperationException
  }

  @throws[IOException]
  override def reset(): Unit = {
    throw new UnsupportedOperationException
  }

  override def markSupported = false

  private def updateProgress(numBytesRead: Long) = {
    if (numBytesRead > 0) {
      this.totalNumBytesRead += numBytesRead
      listener.progressUpdated(numBytesRead, totalNumBytesRead)
    }
    numBytesRead
  }
}
