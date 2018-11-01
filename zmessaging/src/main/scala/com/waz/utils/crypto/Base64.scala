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

import java.math.BigInteger
import java.security.MessageDigest

import com.waz.model.Sha256
import javax.xml.bind.DatatypeConverter

trait Base64 {
  def encode(key: Array[Byte]): String
  def decode(key: String): Array[Byte]
}

class JVMBase64 extends Base64 {
  override def encode(key: Array[Byte]): String = DatatypeConverter.printBase64Binary(key)
  override def decode(key: String): Array[Byte] = DatatypeConverter.parseBase64Binary(key)
}

//TODO: create trait for AESUtils so both implementations of Sha256 can be merged
case class Sha256Inj(str: String)(implicit base64: Base64) {
  def bytes = base64.decode(str)

  def hexString = String.format("%02X", new BigInteger(1, AESUtils.base64(str))).toLowerCase

  def sha256(): Sha256 = Sha256(str)
}
object Sha256Inj{
  def apply(bytes: Array[Byte])(implicit base64: Base64): Sha256Inj = Sha256Inj(base64.encode(bytes))
  def calculate(bytes: Array[Byte])(implicit base64: Base64): Sha256Inj = {
    val digest = MessageDigest.getInstance("SHA-256")
    Sha256Inj(digest.digest(bytes))
  }
}
