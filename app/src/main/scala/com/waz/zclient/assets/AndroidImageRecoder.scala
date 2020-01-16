/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.assets

import java.io.{InputStream, OutputStream}

import android.graphics.{Bitmap, BitmapFactory}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.errors.FailedExpectationsError
import com.waz.model.{Dim2, Mime}
import com.waz.service.assets.ImageRecoder
import com.waz.utils.IoUtils
import com.waz.zclient.log.LogUI._

class AndroidImageRecoder extends ImageRecoder with DerivedLogTag {

  override def recode(dim: Dim2, targetMime: Mime, scaleTo: Int, source: () => InputStream, target: () => OutputStream): Unit = {
    lazy val compressFormat = targetMime match {
      case Mime.Image.Jpg => Bitmap.CompressFormat.JPEG
      case Mime.Image.Png => Bitmap.CompressFormat.PNG
      case mime => throw FailedExpectationsError(s"We do not expect $mime as target mime.")
    }

    // Determine how much to scale down the image
    val scaleFactor = Math.max(dim.width / scaleTo, dim.height / scaleTo)
    verbose(l"scaleFactor is $scaleFactor")
    if (scaleFactor > 1) {
      val opts = new BitmapFactory.Options()
      opts.inJustDecodeBounds = false
      opts.inSampleSize = scaleFactor
      opts.inPreferredConfig = com.waz.zclient.utils.BitmapOptions.inPreferredConfig

      val resized = IoUtils.withResource(source()) { in => BitmapFactory.decodeStream(in, null, opts) }
      IoUtils.withResource(target()) { out => resized.compress(compressFormat, 75, out) }
    } else {
      // If we don't want to downscale the image, we still need to copy it to the target.
      // It needs to be done in two steps because sometimes the source and the target point to the same data,
      // so we have to first read it fully and only then write it.
      // TODO: Fix it. If no change is needed we should be able to simply use the source as the target.
      val array = IoUtils.withResource(source())(IoUtils.toByteArray)
      IoUtils.withResource(target()) (_.write(array))
    }
  }
}
