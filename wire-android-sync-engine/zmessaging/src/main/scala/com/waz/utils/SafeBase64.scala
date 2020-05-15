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

import android.annotation.TargetApi

import scala.util.Try

object SafeBase64 {

  @volatile
  private var delegate: Delegate = new JavaBase64Delegate

  def setDelegate(delegate: Delegate): Unit = {
    this.delegate = delegate
  }

  def encode(bytes: Array[Byte]): String = delegate.encode(bytes)
  def decode(base64: String): Try[Array[Byte]] = delegate.decode(base64)

  trait Delegate {
    def encode(bytes: Array[Byte]): String
    def decode(base64: String): Try[Array[Byte]]
  }

  class JavaBase64Delegate extends Delegate {
    import java.util.{Base64 => JBase64}
    @TargetApi(26)
    def encode(bytes: Array[Byte]): String = JBase64.getEncoder.encodeToString(bytes)
    @TargetApi(26)
    def decode(base64: String): Try[Array[Byte]] = Try { JBase64.getDecoder.decode(base64) }
  }

}
