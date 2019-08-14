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
package com.waz.model

import java.math.BigInteger
import java.io.InputStream
import java.security.MessageDigest

import com.waz.utils.IoUtils
import com.waz.utils.crypto.AESUtils
import javax.crypto.KeyGenerator

import scala.util.Try

//TODO Do we have any reasons to store key as String? Why not just an Array[Byte]?
case class AESKey(str: String) {
  lazy val bytes = AESUtils.base64(str)

  def symmetricCipher(mode: Int, iv: Array[Byte]) = AESUtils.cipher(this, iv, mode)
}

object AESKey extends (String => AESKey) {
  val Empty = AESKey("")

  def apply(): AESKey = AESUtils.randomKey()
  def random: AESKey = {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(256)
    val secretKey = keyGen.generateKey
    AESKey(secretKey.getEncoded)
  }
  def apply(bytes: Array[Byte]): AESKey = new AESKey(AESUtils.base64(bytes))
}

case class Sha256(str: String) {
  def bytes = AESUtils.base64(str)

  def matches(bytes: Array[Byte]) = str == com.waz.utils.sha2(bytes)

  def hexString = String.format("%02X", new BigInteger(1, bytes)).toLowerCase
}

object Sha256 {
  val Empty = Sha256("")

  def apply(bytes: Array[Byte]) = new Sha256(AESUtils.base64(bytes))

  def calculate(bytes: Array[Byte]): Sha256 = Sha256(MessageDigest.getInstance("SHA-256").digest(bytes))

  def calculate(is: InputStream): Try[Sha256] = Try { Sha256(IoUtils.sha256(is)) }

}

class MD5(val bytes: Array[Byte]) extends AnyVal

object MD5 {
  def apply(bytes: Array[Byte]): MD5 = new MD5(bytes)
}
