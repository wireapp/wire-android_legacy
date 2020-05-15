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
package com.waz.utils.wrappers

import android.graphics.{Bitmap => ABitmap}
import com.waz.bitmap

import scala.language.implicitConversions

trait Bitmap {
  def getByteCount: Int
  def getWidth: Int
  def getHeight: Int
  def isEmpty: Boolean
}

case class AndroidBitmap(bm: ABitmap) extends Bitmap {
  override def getByteCount = bm.getByteCount()
  override def getWidth = bm.getWidth()
  override def getHeight = bm.getHeight()
  override def isEmpty = (bm == bitmap.EmptyBitmap)
}

case class FakeBitmap(getByteCount: Int = 1, getWidth: Int = 1, getHeight: Int = 1, isEmpty: Boolean = false) extends Bitmap

object EmptyBitmap extends Bitmap {
  override def getByteCount: Int = 0
  override def getWidth: Int = 1
  override def getHeight: Int = 1
  override def isEmpty: Boolean = true
}

object Bitmap {
  def apply(bitmap: ABitmap): Bitmap = AndroidBitmap(bitmap)

  implicit def fromAndroid(bitmap: ABitmap): Bitmap = apply(bitmap)
  implicit def toAndroid(bmp: Bitmap): ABitmap = bmp match {
    case AndroidBitmap(bitmap) => bitmap
    case _ => throw new IllegalArgumentException(s"Expected Android Bitmap, but tried to unwrap: ${bmp.getClass.getName}")
  }
}
