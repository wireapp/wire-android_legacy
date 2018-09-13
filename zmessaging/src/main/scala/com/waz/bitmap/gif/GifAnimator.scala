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
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.bitmap
import com.waz.threading.{CancellableFuture, Threading}

import scala.concurrent.Promise
import scala.concurrent.duration.{FiniteDuration, _}

class GifAnimator(gif: Gif, reserveFrameMemory: () => Unit, frameCallback: Bitmap => Unit) {
  private implicit val dispatcher = GifAnimator.dispatcher

  def run(): CancellableFuture[Unit] = {
    val p = Promise[Unit]()
    var frameFuture = CancellableFuture.successful({})

    dispatcher {
      nextFrame(new AnimGifDecoder(gif))
    }

    def done(decoder: AnimGifDecoder) = {
      decoder.destroy()
      gif.data.close()
      p.trySuccess(())
    }

    def nextFrame(decoder: AnimGifDecoder, lastFrameTime: Long = System.currentTimeMillis()): Unit = {
      reserveFrameMemory()

      val delay = decoder.getFrameDelay
      def remainingDelay = (delay - (System.currentTimeMillis() - lastFrameTime).millis).asInstanceOf[FiniteDuration]

      if (delay.isFinite()) {
        decoder.advanceNextFrame()
        // always wait before accessing next frame, getCurrentFrame will modify image previously passed to UI, we need to give it some time to display it
        // leaving 3 millis for getCurrentFrame - should be enough since it only applies pixels to image
        frameFuture = CancellableFuture.delayed(remainingDelay - 3.millis) {
          val frame = decoder.getCurrentFrame
          if (frame != null && frame != bitmap.EmptyBitmap) frameCallback(frame)
        }
        frameFuture.onComplete { _ =>
          if (!p.isCompleted) nextFrame(decoder, math.max(lastFrameTime + delay.toMillis, System.currentTimeMillis() - 100))
          else done(decoder)
        }
      } else done(decoder)
    }

    new CancellableFuture[Unit](p) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        frameFuture.cancel()(tag)
        super.cancel()(tag)
      }
    }
  }
}


object GifAnimator {
  val dispatcher = Threading.ImageDispatcher
}
