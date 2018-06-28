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
package com.waz.model.otr

import javax.crypto.spec.SecretKeySpec

import com.waz.model.AESKey
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

case class MsgAuthCode(str: String) {
  lazy val bytes = AESUtils.base64(str)
}

object MsgAuthCode {
  def apply(bytes: Array[Byte]) = new MsgAuthCode(AESUtils.base64(bytes))
}

/*
 * The MAC uses HMAC-SHA256.
 *
 * TODO remove - not used anymore... (https://github.com/wireapp/android-project/issues/50)
 */
case class SignalingKey(encKey: AESKey, macKey: String) {

  lazy val encKeyBytes = encKey.bytes
  lazy val macKeyBytes = AESUtils.base64(macKey)
  lazy val mac = new SecretKeySpec(macKeyBytes, "HmacSHA256")
}

object SignalingKey {

  def apply(): SignalingKey = new SignalingKey(AESKey(), AESKey().str)

  def apply(enc: Array[Byte], mac: Array[Byte]): SignalingKey = new SignalingKey(AESKey(enc), AESUtils.base64(mac))

  implicit lazy val Decoder: JsonDecoder[SignalingKey] = new JsonDecoder[SignalingKey] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): SignalingKey = SignalingKey(AESKey('enckey: String), 'mackey)
  }

  implicit lazy val Encoder: JsonEncoder[SignalingKey] = new JsonEncoder[SignalingKey] {
    override def apply(v: SignalingKey): JSONObject = JsonEncoder { o =>
      o.put("enckey", v.encKey.str)
      o.put("mackey", v.macKey)
    }
  }
}
