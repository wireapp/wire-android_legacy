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
package com.waz.utils

import com.waz.utils.crypto.ZSecureRandom

import scala.concurrent.duration._
import scala.math.abs


trait Backoff {
  val maxRetries:  Int
  def delay(retry: Int, minDelay: FiniteDuration = Duration.Zero): FiniteDuration
}

/**
 * Calculates retry delay using randomized exponential backoff strategy.
  */
class ExponentialBackoff(initialDelay: FiniteDuration, maxDelay: FiniteDuration) extends Backoff {

  override val maxRetries: Int = ExponentialBackoff.bitsCount(maxDelay.toMillis / math.max(initialDelay.toMillis, 1L))

  override def delay(retry: Int, minDelay: FiniteDuration = Duration.Zero): FiniteDuration = {
    if (retry <= 0) initialDelay
    else if (retry >= maxRetries) randomized(maxDelay)
    else {
      val expDelay = initialDelay * (1L << retry)
      randomized(maxDelay min expDelay max minDelay)
    }
  }

  private def randomized(delay: Duration) = {
    val ms = delay.toMillis / 2d
    (ms + abs(ZSecureRandom.nextDouble()) * ms).millis
  }
}

object ExponentialBackoff {
  def bitsCount(v: Long): Int = if (v >= 2) 1 + bitsCount(v >> 1) else if (v >= 0) 1 else 0

  def zeroBackoff(max: Int): ExponentialBackoff = new ExponentialBackoff(Duration.Zero, Duration.Zero){
    override val maxRetries: Int = max
    override def delay(retry: Int, minDelay: FiniteDuration = Duration.Zero): FiniteDuration = Duration.Zero
  }

  def constantBackoff(duration: FiniteDuration): ExponentialBackoff = new ExponentialBackoff(duration, duration) {
    override val maxRetries: Int = Int.MaxValue
    override def delay(retry: Int, minDelay: FiniteDuration = Duration.Zero): FiniteDuration = duration
  }

  lazy val minimalBackoff = new ExponentialBackoff(1.millis, 1.millis)
  
  lazy val standardBackoff = new ExponentialBackoff(250.millis, 5.minutes)

}
