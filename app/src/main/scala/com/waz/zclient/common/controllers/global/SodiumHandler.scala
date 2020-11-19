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
package com.waz.zclient.common.controllers.global

import com.waz.utils.crypto.{AESUtils, LibSodium}
import com.waz.zclient.{Injectable, Injector}
import org.libsodium.jni.Sodium

class SodiumHandler(implicit inj: Injector) extends Injectable {

  def hash(input: String, salt: String): String = {
    LibSodium.loadLibrary
    val outputSize = Sodium.crypto_aead_chacha20poly1305_keybytes()
    val output = Array.fill[Byte](outputSize)(0)
    val passBytes = input.getBytes("utf8")
    val saltBytes = salt.getBytes("utf8").take(16)
    Sodium.crypto_pwhash(
      output,
      output.length,
      passBytes,
      passBytes.length,
      saltBytes,
      Sodium.crypto_pwhash_opslimit_interactive(),
      Sodium.crypto_pwhash_memlimit_interactive(),
      Sodium.crypto_pwhash_alg_default()
    )
    AESUtils.base64(output)
  }

}

