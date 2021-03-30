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
import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class LegalHoldRequest(client: Client, lastPrekey: Prekey)

object LegalHoldRequest {

  final case class Client(id: String)
  final case class Prekey(id: Int, key: String)

  implicit object Decoder extends JsonDecoder[LegalHoldRequest] {

    override def apply(implicit js: JSONObject): LegalHoldRequest =
      LegalHoldRequest(
        client = decodeClient(js.getJSONObject("client")),
        lastPrekey = decodePrekey(js.getJSONObject("last_prekey"))
      )

    private def decodeClient(implicit js: JSONObject): Client =
      Client(js.getString("id"))

    private def decodePrekey(implicit js: JSONObject): Prekey =
      Prekey(js.getInt("id"), js.getString("key"))
  }

}
