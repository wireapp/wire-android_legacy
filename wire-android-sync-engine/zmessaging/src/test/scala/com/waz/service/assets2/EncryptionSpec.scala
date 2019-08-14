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
package com.waz.service.assets2

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}

import com.waz.TestData
import com.waz.specs.ZSpec
import com.waz.utils.IoUtils

abstract class EncryptionSpec(encryption: Encryption) extends ZSpec {

  private val unencrypted = TestData.bytes(1024 * 1024)

  private def copy(bytes: Array[Byte], transformation: InputStream => InputStream): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    IoUtils.copy(
      in = transformation(new ByteArrayInputStream(bytes)),
      out = outputStream
    )

    outputStream.toByteArray
  }

  private def encryptContent(bytes: Array[Byte], salt: Option[Salt] = None): Array[Byte] =
    copy(bytes, encryption.encrypt(_, salt))

  private def decryptContent(bytes: Array[Byte], salt: Option[Salt] = None): Array[Byte] =
    copy(bytes, encryption.decrypt(_, salt))

  feature(s"Basic encryption") {

    scenario("should encrypt and decrypt input stream properly") {
      val encrypted = encryptContent(unencrypted)
      encrypted shouldNot contain(unencrypted)
      val decrypted = decryptContent(encrypted)
      decrypted shouldBe unencrypted
    }

  }

  feature("Additional contracts") {

    scenario("should calculate after encryption size properly") {
      val encrypted = encryptContent(unencrypted)
      encryption.sizeAfterEncryption(unencrypted.length) shouldBe encrypted.length
    }

    scenario("encryption should be stateless if salt is the same") {
      val salt = encryption.randomSalt
      if (salt.nonEmpty) {
        val encrypted1 = encryptContent(unencrypted, salt)
        val encrypted2 = encryptContent(unencrypted, salt)
        val encrypted3 = encryptContent(unencrypted, salt)
        val encrypted4 = encryptContent(unencrypted, salt)

        encrypted1 shouldBe encrypted2
        encrypted2 shouldBe encrypted3
        encrypted3 shouldBe encrypted4
      }
    }

  }

}

class NoEncryptionSpec extends EncryptionSpec(NoEncryption)
class AES_CBC_EncryptionSpec extends EncryptionSpec(AES_CBC_Encryption.random)