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
package com.waz.utils.crypto

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.warn

import scala.util.{Failure, Success, Try}

class RandomBytes {
  RandomBytes.loadLibrary

  /**
    * Generates random byte array using libsodium
    * @param count - number of bytes to generate
    * @return - random bytes array
    */
  def apply(count: Int) : Array[Byte] = {
    val buffer = Array.ofDim[Byte](count)

    RandomBytes.loadLibrary match {
      case Success(_) => randomBytes(buffer, count)
      case _ =>
        warn(s"Libsodium failed to generate $count random bytes. Falling back to SecureRandom")
        ZSecureRandom.nextBytes(buffer)
    }
    buffer
  }

  @native
  protected def randomBytes(buffer: Array[Byte], count: Int) : Unit
}

object RandomBytes {

  private lazy val loadLibrary: Try[Unit] = {
    try {
      System.loadLibrary("sodium")
      System.loadLibrary("randombytes")
      Success(())
    } catch {
      case error: Throwable => Failure(error)
    }
  }

}
