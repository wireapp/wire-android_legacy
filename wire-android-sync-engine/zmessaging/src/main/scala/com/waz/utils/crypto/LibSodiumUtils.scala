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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import org.libsodium.jni.Sodium
import org.libsodium.jni.NaCl

trait LibSodiumUtils {
  def encrypt(msg: Array[Byte], password: Password, salt: Array[Byte], opslimit: Int = getOpsLimit, memlimit: Int = getMemLimit): Option[Array[Byte]]
  def decrypt(ciphertext: Array[Byte], password: Password, salt: Array[Byte], opslimit: Int = getOpsLimit, memlimit: Int = getMemLimit): Option[Array[Byte]]
  def hash(input: String, salt: Array[Byte], opslimit: Int = getOpsLimit, memlimit: Int = getMemLimit): Option[Array[Byte]]
  def generateSalt(): Array[Byte]
  def getOpsLimit: Int
  def getMemLimit: Int
}

class LibSodiumUtilsImpl() extends LibSodiumUtils with DerivedLogTag {

  import com.waz.log.LogSE._

  private val _ = NaCl.sodium() // dynamically load the libsodium library

  private val streamHeaderLength = Sodium.crypto_secretstream_xchacha20poly1305_headerbytes

  override def encrypt(msg: Array[Byte], password: Password, salt: Array[Byte], opslimit: Int, memlimit: Int): Option[Array[Byte]] = {
    val cipherText = Array.ofDim[Byte](msg.length + Sodium.crypto_secretstream_xchacha20poly1305_abytes)
    val header = Array.ofDim[Byte](streamHeaderLength)

    hash(password.str, salt, opslimit, memlimit) match {
      case Some(key) =>
        val expectedKeySize = Sodium.crypto_aead_chacha20poly1305_keybytes
        if (key.length != expectedKeySize) {
          verbose(l"Key length invalid: ${key.length} did not match $expectedKeySize")
        }
        initPush(key, header) match {
          case Some(s) =>
            val tag = Sodium.crypto_secretstream_xchacha20poly1305_tag_final().toShort
            val ret = Sodium.crypto_secretstream_xchacha20poly1305_push(s, cipherText, Array.emptyIntArray,
              msg, msg.length, Array.emptyByteArray, 0, tag)

            if (ret == 0) Some(header ++ cipherText)
            else {
              error(l"Failed to hash backup")
              None
            }
          case _ =>
            error(l"Failed to init encrypt ")
            None
        }
      case _ =>
        error(l"Couldn't derive key from password")
        None
    }
  }

  override def decrypt(input: Array[Byte], password: Password, salt: Array[Byte], opslimit: Int, memlimit: Int): Option[Array[Byte]] =
    hash(password.str, salt, opslimit, memlimit) match {
      case Some(key) =>
        val expectedKeyBytes = Sodium.crypto_secretstream_xchacha20poly1305_keybytes
        if (key.length != expectedKeyBytes) {
          verbose(l"Key length invalid: ${key.length} did not match $expectedKeyBytes")
        }
        val (header, cipherText) = input.splitAt(streamHeaderLength)
        val decrypted = Array.ofDim[Byte](cipherText.length + Sodium.crypto_secretstream_xchacha20poly1305_abytes)
        val tag = Array.ofDim[Byte](1)

        initPull(key, header) match {
          case Some(s) =>
            val ret: Int = Sodium.crypto_secretstream_xchacha20poly1305_pull(
              s, decrypted, Array.emptyIntArray, tag, cipherText, cipherText.length,
              Array.emptyByteArray, 0)

            if (ret == 0) Some(decrypted)
            else {
              error(l"Failed to decrypt backup, got code $ret")
              None
            }
          case _ =>
            error(l"Failed to init decrypt ")
            None
        }
      case _ =>
        error(l"Couldn't derive key from password")
        None
    }

  override def hash(input: String, salt: Array[Byte], opslimit: Int, memlimit: Int): Option[Array[Byte]] = {
    val outputLength = Sodium.crypto_secretstream_xchacha20poly1305_keybytes
    val output: Array[Byte] = Array.ofDim[Byte](outputLength)
    val passBytes: Array[Byte] = input.getBytes
    val ret: Int = Sodium.crypto_pwhash(output, output.length, passBytes, passBytes.length, salt,
      opslimit,
      memlimit,
      Sodium.crypto_pwhash_alg_default())

    if (ret == 0) Some(output) else None
  }

  override def generateSalt(): Array[Byte] = new RandomBytes().apply(Sodium.crypto_pwhash_saltbytes)

  override def getOpsLimit: Int = Sodium.crypto_pwhash_opslimit_interactive

  override def getMemLimit: Int = Sodium.crypto_pwhash_memlimit_interactive

  private def initPull(key: Array[Byte], header: Array[Byte]): Option[Array[Byte]] =
    initializeState(key, header, Sodium.crypto_secretstream_xchacha20poly1305_init_pull)

  private def initPush(key: Array[Byte], header: Array[Byte]): Option[Array[Byte]] =
    initializeState(key, header, Sodium.crypto_secretstream_xchacha20poly1305_init_push)

  private def initializeState(key: Array[Byte], header: Array[Byte], init: (Array[Byte], Array[Byte], Array[Byte]) => Int): Option[Array[Byte]] = {
    //Got this magic number from https://github.com/joshjdevl/libsodium-jni/blob/master/src/test/java/org/libsodium/jni/crypto/SecretStreamTest.java#L48
    val state = Array.ofDim[Byte](52)
    if(header.length != Sodium.crypto_secretstream_xchacha20poly1305_headerbytes) {
      error(l"Invalid header length")
      None
    } else {
      if (key.length != Sodium.crypto_secretstream_xchacha20poly1305_keybytes) {
        error(l"Invalid key length")
        None
      } else {
        if (init(state, header, key) == 0) {
          Some(state)
        } else {
          error(l"error whilst initializing push")
          None
        }
      }
    }
  }

}
