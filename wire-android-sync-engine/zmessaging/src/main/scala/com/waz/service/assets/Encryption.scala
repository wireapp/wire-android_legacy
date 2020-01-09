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
package com.waz.service.assets

import java.io.{File, FileInputStream, InputStream}

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.utils.crypto.AESUtils
import javax.crypto.KeyGenerator

trait Encryption {
  def decrypt(is: InputStream, salt: Option[Salt] = None): InputStream
  def encrypt(os: InputStream, salt: Option[Salt] = None): InputStream
  def sizeAfterEncryption(sizeBeforeEncryption: Long, salt: Option[Salt] = None): Long
  def randomSalt: Option[Salt]
}

case object NoEncryption extends Encryption {
  override def decrypt(is: InputStream, salt: Option[Salt] = None): InputStream = is
  override def encrypt(is: InputStream, salt: Option[Salt] = None): InputStream = is
  override def sizeAfterEncryption(sizeBeforeEncryption: Long, salt: Option[Salt] = None): Long = sizeBeforeEncryption
  override def randomSalt: Option[Salt] = None
}

case class AES_CBC_Encryption(key: AESKey2) extends Encryption with DerivedLogTag {
  override def decrypt(is: InputStream, salt: Option[Salt] = None): InputStream =
    AESUtils.decryptInputStream(key.bytes, is)
  override def encrypt(is: InputStream, salt: Option[Salt] = None): InputStream = {
    val s = salt orElse randomSalt
    debug(l"encrypt input stream: key: $key salt: $s")
    AESUtils.encryptInputStream(key.bytes, s.get.bytes, is)
  }
  override def sizeAfterEncryption(sizeBeforeEncryption: Long, salt: Option[Salt] = None): Long =
    AESUtils.sizeAfterEncryption(key.bytes, (salt orElse randomSalt).get.bytes, sizeBeforeEncryption)
  override def randomSalt: Option[Salt] =
    Some(Salt(AESUtils.generateIV))
}

object AES_CBC_Encryption {
  def random: AES_CBC_Encryption = AES_CBC_Encryption(AESKey2.random)
}

case class EncryptedFile(file: File, encryption: Encryption) {
  def decryptedStream: InputStream = encryption.decrypt(new FileInputStream(file))
}

//TODO Can be removed when we will remove android Base64 from project
case class AESKey2(bytes: Array[Byte]) extends AnyVal

case class Salt(bytes: Array[Byte]) extends AnyVal

object AESKey2 {

  def random: AESKey2 = {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(256)
    val secretKey = keyGen.generateKey
    AESKey2(secretKey.getEncoded)
  }

}