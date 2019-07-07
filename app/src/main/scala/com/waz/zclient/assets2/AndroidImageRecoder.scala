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
package com.waz.zclient.assets2

import java.io.{InputStream, OutputStream}

import android.graphics.{Bitmap, BitmapFactory}
import com.waz.model.errors.FailedExpectationsError
import com.waz.model.{Dim2, Mime}
import com.waz.service.assets2.ImageRecoder
import com.waz.utils.IoUtils

class AndroidImageRecoder extends ImageRecoder {

  override def recode(dim: Dim2, targetMime: Mime, scaleTo: Int, source: () => InputStream, target: () => OutputStream): Unit = {
    val compressFormat = targetMime match {
      case Mime.Image.Jpg => Bitmap.CompressFormat.JPEG
      case Mime.Image.Png => Bitmap.CompressFormat.PNG
      case mime => throw FailedExpectationsError(s"We do not expect $mime as target mime.")
    }

    // Determine how much to scale down the image
    val scaleFactor = Math.max(dim.width / scaleTo, dim.height / scaleTo)

    val opts = new BitmapFactory.Options()
    opts.inJustDecodeBounds = false
    opts.inSampleSize = scaleFactor

    if (scaleFactor <= 1) {
      val resizingSuccessful = IoUtils.withResource(source()) { in =>
        val resized = BitmapFactory.decodeStream(in, null, opts)
        val success = resized != null
        if (success) IoUtils.withResource(target())(resized.compress(compressFormat, 75, _))
        success
      }
      //TODO What should we do in this case?
//      if (!resizingSuccessful) {
//        IoUtils.withResources(source(), target())(IoUtils.copy)
//      }
    } else {
      IoUtils.withResources(source(), target()) { (in, out) =>
        val resized = BitmapFactory.decodeStream(in, null, opts)
        resized.compress(compressFormat, 75, out)
      }
    }

  }

}
