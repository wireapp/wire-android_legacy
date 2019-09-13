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

import android.graphics.Bitmap
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._

import scala.concurrent.duration.Duration

/**
 * Decodes gif animation frames.
 *
 * TODO: consider keeping pixels data in native buffer (especially for swap pixels)
 * TODO: maybe image decoder could save pixels directly to current image (line bye line), would not need pixels buffer
 * @param gif
 */
class AnimGifDecoder(gif: Gif) extends DerivedLogTag {
  val framesCount = gif.frames.length
  var frameIndex = -1
  var loopCounter = 0
  var frameDirty = false

  var currentImage: Bitmap = _

  val decoder = new LzwDecoder(gif)

  /**
   * Returns a delay to wait before displaying next frame.
   * @return finite duration if there is next frame to show (or looping) or Duration.Inf if this is the last frame and loopCount is finished
   */
  def getFrameDelay: Duration = {
    if (frameIndex < 0) Duration.Zero
    else if (gif.loop.shouldAnimate(loopCounter)) gif.frames(frameIndex).delay
    else Duration.Inf
  }


  private def advance(): Boolean = {
    if (frameIndex == framesCount - 1) loopCounter += 1
    if (gif.loop.shouldAnimate(loopCounter)) {
      frameIndex = if (frameIndex == framesCount - 1) 0 else frameIndex + 1
      true
    } else false
  }

  /**
   * Advances animation.
   * Will decode next frame pixels, but not modify current frame image yet.
   */
  def advanceNextFrame(): Unit = {
    if (frameDirty) warn(l"should call getCurrentFrame before advancing to next frame")
    if (!frameDirty && advance()) {
      val frame = gif.frames(frameIndex)
      if (frameIndex == 0) decoder.clear()
      decoder.decode(frame)
      frameDirty = true
    }
  }

  /**
   * Returns current frame image.
   * @return Bitmap representation of frame
   */
  def getCurrentFrame: Bitmap = {
    if (frameDirty) {
      currentImage = decoder.updateImage(gif.frames(frameIndex))
      frameDirty = false
    }
    currentImage
  }

  def destroy() = decoder.destroy()
}
