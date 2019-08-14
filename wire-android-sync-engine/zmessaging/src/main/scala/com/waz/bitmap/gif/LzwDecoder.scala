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
package com.waz.bitmap.gif

import java.nio.{ByteBuffer, ByteOrder, IntBuffer}

import android.graphics.{Bitmap, Color}
import com.waz.bitmap.gif.Gif.{Disposal, Frame}

class LzwDecoder(gif: Gif) {
  import LzwDecoder._

  ensureLibraryLoaded()

  val imageWidth = gif.width
  val imageHeight = gif.height
  val maxImageDataSize = gif.frames.map(_.imageDataSize).max

  var bgIndex = gif.bgIndex
  var bgColor = if (gif.gct.isEmpty) Color.TRANSPARENT else gif.gctRGBA(bgIndex)

  val inputData = ByteBuffer.allocateDirect(maxImageDataSize)
  val pixels = ByteBuffer.allocateDirect(imageWidth * imageHeight * 4).order(ByteOrder.BIG_ENDIAN).asIntBuffer()
  val colors = ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.BIG_ENDIAN).asIntBuffer()

  val currentImage = Bitmap.createBitmap(gif.width, gif.height, Bitmap.Config.ARGB_8888)

  val usesDisposePrevious = gif.frames.exists(_.dispose == Gif.Disposal.Previous)

  // temp, used only when frame disposal is set to PREVIOUS
  lazy val pixelsSwap = ByteBuffer.allocateDirect(imageWidth * imageHeight * 4).order(ByteOrder.BIG_ENDIAN).asIntBuffer()

  val failed = ByteBuffer.allocateDirect(4).asIntBuffer()

  val decoder = init(inputData, pixels, colors, imageWidth, imageHeight, failed)

  if(failed.get(0) == -1) {
    throw new IllegalArgumentException("Invalid parameters given to decoder")
  }

  private var destroyed = false

  def destroy(): Unit = {
    destroy(decoder)
    destroyed = true
  }

  override def finalize(): Unit = {
    if (!destroyed) destroy(decoder)
    super.finalize()
  }

  def clear(): Unit = clear(decoder, 0, 0, imageWidth, imageHeight, bgColor)

  /**
    * Decode next frame pixels, but does not modify current frame image yet.
    */
  def decode(frame: Frame): Unit = {
    colors.put(if (frame.lct.isEmpty) gif.gctRGBA else frame.lctRGBA)
    colors.rewind()

    if (frame.dispose == Disposal.Previous) {
      pixelsSwap.put(pixels)
      pixels.rewind()
      pixelsSwap.rewind()
    }

    gif.data(frame).readFully(inputData, frame.imageDataSize)
    inputData.rewind()

    val b = frame.bounds
    decode(decoder, b.x, b.y, b.w, b.h, frame.imageDataSize, frame.transIndex, bgColor, frame.interlace, frame.transparency)
  }

  /**
    * Updates frame image from current pixel data (and clears pixel buffer according to disposition codes).
    */
  def updateImage(frame: Gif.Frame): Bitmap = {
    pixels.rewind()
    currentImage.copyPixelsFromBuffer(pixels)

    pixels.rewind()

    frame.dispose match {
      case Disposal.Previous =>
        pixels.put(pixelsSwap)
        pixels.rewind()
        pixelsSwap.rewind()
      case Disposal.Background =>
        val b = frame.bounds
        clear(decoder, b.x, b.y, b.w, b.h, if (frame.transparency) Color.TRANSPARENT else bgColor)
      case _ => // ignore
    }
    currentImage
  }

  @native protected def clear(decoder: Long, x: Int, y: Int, w: Int, h: Int, color: Int): Unit
  @native protected def decode(decoder: Long, x: Int, y: Int, w: Int, h: Int, inputSize: Int, transIndex: Int, bgColor: Int, interlace: Boolean, transparency: Boolean): Unit
  @native protected def init(image: ByteBuffer, pixels: IntBuffer, colors: IntBuffer, width: Int, height: Int, failed: IntBuffer): Long
  @native protected def destroy(decoder: Long): Unit
}

object LzwDecoder {
  private val loadLibrary = { System.loadLibrary("lzw-decoder"); true }

  private def ensureLibraryLoaded() = loadLibrary
}
