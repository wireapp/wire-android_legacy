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

import java.io.InputStream

import android.media.ExifInterface
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._

import scala.util.control.NonFatal

/**
 * Extracts orientation tag from exif data in jpeg image data.
 * TODO: make it prettier
 */
object ExifOrientation extends DerivedLogTag {
  def apply(in: InputStream): Int = try {

    def readShort(be: Boolean = true) =
      if (be) ((in.read & 0xff) << 8) | (in.read & 0xff)
      else (in.read & 0xff) | ((in.read & 0xff) << 8)

    def readInt(be: Boolean = true) =
      if (be) ((in.read & 0xff) << 24) | ((in.read & 0xff) << 16) | ((in.read & 0xff) << 8) | (in.read & 0xff)
      else (in.read & 0xff) | ((in.read & 0xff) << 8) | ((in.read & 0xff) << 16) | ((in.read & 0xff) << 24)

    def read(n: Int) = Seq.fill(n)(in.read)

    def readSegmentCode() = {
      var code = 0xff
      while (code == 0xff) code = in.read()
      code
    }

    //TODO remove returns
    def readExif(len: Int): Int = {
      if (len < 20) return 0
      /* Read Exif head, check for "Exif" */
      if (read(6) != Seq(0x45, 0x78, 0x69, 0x66, 0, 0)) return 0

      val bigEndian = read(4) match {
        case Seq(0x4d, 0x4d, 0, 0x2a) => true
        case Seq(0x49, 0x49, 0x2a, 0) => false
        case mark =>
          verbose(l"tag mark: ${mark.map(n => showString(n.toHexString))}")
          return 0
      }

      /* Get first IFD offset (offset to IFD0) */
      val offset = readInt(bigEndian)
      if (offset > len - 2) return 0
      in.skip(offset - 8)

      /* Get the number of directory entries contained in this IFD */
      for (_ <- 0 until readShort(bigEndian)) {
        // read tag id
        if (readShort(bigEndian) == 0x0112) { // Orientation tag
          // fetch orientation tag value
          in.skip(6)
          val o = readShort(bigEndian)
          return if (o > 8) 0 else o
        } else
          in.skip(10) // skip tag
      }
      0
    }

    /* Read File head, check for JPEG  */
    if (in.read != 0xFF || in.read != 0xD8) return ExifInterface.ORIENTATION_UNDEFINED

    Iterator.continually(readSegmentCode()) foreach { code =>
      if (code < 0xE0) return ExifInterface.ORIENTATION_UNDEFINED
      else {
        val len = readShort()
        if (code == 0xE1) return readExif(len)
        else in.skip(len - 2)
      }
    }

    0
  } catch {
    case NonFatal(e) =>
      warn(l"Extracting exif orientation failed", e)
      ExifInterface.ORIENTATION_UNDEFINED
  }
}
