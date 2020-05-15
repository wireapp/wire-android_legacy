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

import com.waz.bitmap.gif.Gif.{Frame, FrameDataSource, Loop}

import scala.concurrent.duration.{Duration, FiniteDuration}

case class Gif(
                width: Int,
                height: Int,
                frames: Array[Frame],
                data: FrameDataSource,
                loop: Loop = Loop.None,
                gct: Array[Int] = Array.empty,
                bgIndex: Int = 0,
                pixelAspect: Int = 1 // pixel aspect ratio
              ) {

  require(frames.length > 0)

  lazy val gctRGBA = gct map { c => (c << 8) | 0xff }

  override def toString: String = {
    s"Gif(w: $width, h: $height, {${frames.length}}${frames.toSeq.take(5)}, lop: $loop, gct: ${gct.nonEmpty}, bg: $bgIndex, pa: $pixelAspect)"
  }
}

object Gif {

  sealed trait Disposal
  object Disposal {
    /**
     * GIF Disposal Method meaning take no action
     */
    case object Unspecified extends Disposal
    /**
     * GIF Disposal Method meaning leave canvas from previous frame
     */
    case object None extends Disposal
    /**
     * GIF Disposal Method meaning clear canvas to background color
     */
    case object Background extends Disposal
    /**
     * GIF Disposal Method meaning clear canvas to frame before last
     */
    case object Previous extends Disposal

    def apply(code: Int) = code match {
      case 2 => Background
      case 3 => Previous
      case _ => None
    }
  }

  case class Bounds(x: Int, y: Int, w: Int, h: Int) {

    /**
     * Moves and/or shrinks to fit image with given dimensions.
     */
    def fit(width: Int, height: Int) = {
      if (x >= 0 && y >= 0 && x + w <= width && y + h <= height) this
      else {
        def clamp(v: Int, min: Int, max: Int) = if (v < min) min else if (v > max) max else v
        val w1 = math.min(w, width)
        val h1 = math.min(h, height)
        Bounds(clamp(x, 0, width - w1), clamp(y, 0, height - h1), w1, h1)
      }
    }
  }

  case class Frame(bounds: Bounds,
                   interlace: Boolean = false,
                   transparency: Boolean = false,
                   dispose: Disposal = Disposal.Unspecified,
                   transIndex: Int = 0, // Transparency Index
                   delay: FiniteDuration = Duration.Zero, // Delay to next frame
                   bufferFrameStart: Int = 0, // file/data buffer position
                   imageDataSize: Int = 0, // file/data buffer size
                   lct: Array[Int] = Array.empty) {

    lazy val lctRGBA = lct map { c => (c << 8) | 0xff }
  }

  trait FrameDataSource extends (Frame => DataSource) {
    def close(): Unit = {}
  }

  sealed trait Loop {
    def shouldAnimate(loopCount: Int): Boolean
  }
  object Loop {
    case object Unknown extends Loop {
      override def shouldAnimate(loopCount: Int) = loopCount < 1
    }
    case object Forever extends Loop {
      override def shouldAnimate(loopCount: Int) = true
    }
    case object None extends Loop {
      override def shouldAnimate(loopCount: Int) = loopCount < 1
    }
    case class Count(repeats: Int = 1) extends Loop {
      override def shouldAnimate(loopCount: Int) = loopCount < repeats
    }

    def apply(count: Int): Loop = count match {
      case 0 => Forever
      case 1 => None
      case _ => Count(count)
    }
  }
}
