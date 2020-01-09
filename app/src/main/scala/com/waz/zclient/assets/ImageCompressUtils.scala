/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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

import java.io.ByteArrayOutputStream

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.waz.model.Mime
import com.waz.service.assets.Content

object ImageCompressUtils {

  private def defaultCompressionQuality(format: CompressFormat): Int = format match {
    case CompressFormat.JPEG => 75
    case _ => 50
  }

  private def compress(in: Bitmap, toFormat: CompressFormat, quality: Option[Int] = None): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    in.compress(toFormat, quality.getOrElse(defaultCompressionQuality(toFormat)), out)
    out.toByteArray
  }

  def toJpg(bitmap: Bitmap): Content.Bytes =
    Content.Bytes(Mime.Image.Jpg, compress(bitmap, CompressFormat.JPEG))
}
