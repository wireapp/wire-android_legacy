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

import com.waz.log.LogShow.SafeToLog
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.utils.JsonDecoder.{decodeOptObject, decodeString}
import org.json.JSONObject

case class SSOId(subject: String, tenant: String) {
  def encode(implicit js: JSONObject): Unit = {
    js.put("subject", subject)
    js.put("tenant", tenant)
  }
}

object SSOId {
  private def decodeSSOId(implicit js: JSONObject): SSOId = SSOId(decodeString('subject), decodeString('tenant))

  def decodeOptSSOId(s: Symbol)(implicit js: JSONObject): Option[SSOId] = decodeOptObject(s) match {
    case Some(ssoId) => Option(decodeSSOId(ssoId))
    case _ => None
  }

  implicit object Decoder extends JsonDecoder[SSOId] {
    override def apply(implicit js: JSONObject): SSOId = decodeSSOId(js)
  }

  implicit lazy val Encoder: JsonEncoder[SSOId] = new JsonEncoder[SSOId] {
    override def apply(ssoId: SSOId): JSONObject = JsonEncoder { o =>
      o.put("subject", ssoId.subject)
      o.put("tenant", ssoId.tenant)
    }
  }
}
