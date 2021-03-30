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

import com.waz.model.LegalHoldRequest.Client
import com.waz.model.otr.PreKeyEncoder
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.wire.cryptobox.PreKey
import org.json.JSONObject

final case class LegalHoldRequest(client: Client, lastPreKey: PreKey)

object LegalHoldRequest {

  final case class Client(id: String)

  implicit object Decoder extends JsonDecoder[LegalHoldRequest] {

    override def apply(implicit json: JSONObject): LegalHoldRequest =
      LegalHoldRequest(
        client = decodeClient(json.getJSONObject("client")),
        lastPreKey = otr.PreKeyDecoder(json.getJSONObject("last_prekey"))
      )

    private def decodeClient(implicit json: JSONObject): Client =
      Client(json.getString("id"))

  }


  implicit object Encoder extends JsonEncoder[LegalHoldRequest] {

    override def apply(request: LegalHoldRequest): JSONObject = JsonEncoder { json =>
      json.put("client", encodeClient(request.client))
      json.put("last_prekey", JsonEncoder.encode(request.lastPreKey)(PreKeyEncoder))
    }

    private def encodeClient(client: Client): JSONObject = JsonEncoder { json =>
      json.put("id", client.id)
    }
  }
}
