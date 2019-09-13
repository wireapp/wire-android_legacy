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
package com.waz.bitmap

import java.io.{File, InputStream}

import android.graphics.BitmapFactory.Options
import android.graphics.BitmapFactory
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils._
import com.waz.utils.wrappers.Bitmap
import com.waz.utils.wrappers

class BitmapDecoder extends DerivedLogTag {

  private implicit lazy val dispatcher = Threading.ImageDispatcher

  def factoryOptions(sampleSize: Int) = returning(new Options) { opts =>
    opts.inSampleSize = sampleSize
    opts.inScaled = false
  }

  private def nextPowerOfTwo(i : Int) = 1 << (32 - Integer.numberOfLeadingZeros(i))

  def withFixedOrientation(bitmap: Bitmap, orientation: Int): Bitmap =
    if (bitmap == null || bitmap.isEmpty) wrappers.EmptyBitmap
    else BitmapUtils.fixOrientation(bitmap, orientation)

  def retryOnError(sampleSize: Int, maxSampleSize: Int = 8)(loader: Int => Bitmap): Bitmap = {
    try {
      loader(sampleSize)
    } catch {
      case e: OutOfMemoryError if sampleSize < maxSampleSize =>
        warn(l"decoding failed for sampleSize: $sampleSize", e)
        retryOnError(nextPowerOfTwo(sampleSize), maxSampleSize)(loader)
    }
  }

  def apply(file: File, inSampleSize: Int, orientation: Int): CancellableFuture[Bitmap] = dispatcher {
    retryOnError(inSampleSize, inSampleSize * 3) { sampleSize =>
      withFixedOrientation(BitmapFactory.decodeFile(file.getAbsolutePath, factoryOptions(sampleSize)), orientation)
    }
  }

  def apply(data: Array[Byte], inSampleSize: Int, orientation: Int): CancellableFuture[Bitmap] = dispatcher {
    retryOnError(inSampleSize, inSampleSize * 3) { sampleSize =>
      withFixedOrientation(BitmapFactory.decodeByteArray(data, 0, data.length, factoryOptions(sampleSize)), orientation)
    }
  }

  def apply(stream: () => InputStream, inSampleSize: Int, orientation: Int): CancellableFuture[Bitmap] = dispatcher {
    retryOnError(inSampleSize, inSampleSize * 3) { sampleSize =>
      IoUtils.withResource(stream()) { in =>
        withFixedOrientation(BitmapFactory.decodeStream(in, null, factoryOptions(sampleSize)), orientation)
      }
    }
  }

}
