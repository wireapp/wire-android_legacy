/*
 * Wire
 * Copyright (C) 2021 Wire Swiss GmbH
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

import com.waz.model.LegalHoldRequest.{Client, Prekey}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

final case class LegalHoldRequest(client: Client, lastPrekey: Prekey)

object LegalHoldRequest {

  final case class Client(id: String)
  final case class Prekey(id: Int, key: String)

  implicit object Decoder extends JsonDecoder[LegalHoldRequest] {

    override def apply(implicit json: JSONObject): LegalHoldRequest =
      LegalHoldRequest(
        client = decodeClient(json.getJSONObject("client")),
        lastPrekey = decodePrekey(json.getJSONObject("last_prekey"))
      )

    private def decodeClient(implicit json: JSONObject): Client =
      Client(json.getString("id"))

    private def decodePrekey(implicit json: JSONObject): Prekey =
      Prekey(json.getInt("id"), json.getString("key"))
  }

  implicit object Encoder extends JsonEncoder[LegalHoldRequest] {

    override def apply(request: LegalHoldRequest): JSONObject = JsonEncoder { json =>
      json.put("client", encodeClient(request.client))
      json.put("last_prekey", encodePrekey(request.lastPrekey))
    }

    private def encodeClient(client: Client): JSONObject = JsonEncoder { json =>
      json.put("id", client.id)
    }

    private def encodePrekey(prekey: Prekey): JSONObject = JsonEncoder { json =>
      json.put("id", prekey.id)
      json.put("key", prekey.key)
    }
  }
}
