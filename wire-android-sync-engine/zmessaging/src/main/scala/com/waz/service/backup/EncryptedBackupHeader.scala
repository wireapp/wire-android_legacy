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
package com.waz.service.backup

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.ByteBuffer

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.IoUtils
import com.waz.utils.IoUtils.withResource

object EncryptedBackupHeader extends DerivedLogTag {
  val androidMagicNumber: String = "WBUA"
  val currentVersion: Short = 2
  val saltLength = 16
  val uuidHashLength = 32

  private val androidMagicNumberLength = 4
  val totalHeaderLength = androidMagicNumberLength + 1 + 2 + saltLength + uuidHashLength + 4 + 4

  import com.waz.log.LogSE._

  def parse(bytes: Array[Byte]): Option[EncryptedBackupHeader] = {
    val buffer = ByteBuffer.wrap(bytes)
    if(bytes.length == totalHeaderLength) {
      val magicNumber = Array.ofDim[Byte](androidMagicNumberLength)
      buffer.get(magicNumber)
      if(magicNumber.map(_.toChar).mkString.equals(androidMagicNumber)) {
        buffer.get() //skip null byte
        val version = buffer.getShort()
        if(version == currentVersion) {
          val salt = Array.ofDim[Byte](saltLength)
          val uuidHash = Array.ofDim[Byte](uuidHashLength)
          buffer.get(salt)
          buffer.get(uuidHash)
          val opslimit = buffer.getInt
          val memlimit = buffer.getInt
          Some(EncryptedBackupHeader(currentVersion, salt, uuidHash, opslimit, memlimit))
        } else {
          error(l"Unsupported backup version")
          None
        }
      } else {
        error(l"archive has incorrect magic number")
        None
      }
    } else {
      error(l"Invalid header length")
      None
    }
  }

  def serializeHeader(header: EncryptedBackupHeader): Array[Byte] = {
    val buffer = ByteBuffer.allocate(totalHeaderLength)

    buffer.put(androidMagicNumber.getBytes())
    buffer.put(0.toByte)
    buffer.putShort(header.version)
    buffer.put(header.salt)
    buffer.put(header.uuidHash)
    buffer.putInt(header.opslimit)
    buffer.putInt(header.memlimit)

    buffer.array()
  }

  def readEncryptedMetadata(encryptedBackup: File): Option[EncryptedBackupHeader] =
    if(encryptedBackup.length() > totalHeaderLength) {
      val encryptedMetadataBytes = IoUtils.readFileBytes(encryptedBackup, byteCount = Some(totalHeaderLength))
      parse(encryptedMetadataBytes)
    } else {
      error(l"Backup file header corrupted or invalid")
      None
    }

}

case class EncryptedBackupHeader(version: Short = EncryptedBackupHeader.currentVersion,
                                 salt: Array[Byte],
                                 uuidHash: Array[Byte],
                                 opslimit: Int,
                                 memlimit: Int)

