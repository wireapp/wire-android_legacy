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

import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

case class UserField(key: String, value: String)

object UserField {
  import com.waz.utils.JsonDecoder._

  private val key: Symbol = 'type
  private val value: Symbol = 'value

  def decodeOptUserFields(s: Symbol)(implicit js: JSONObject): Option[Seq[UserField]] =
    opt(s, js => userFieldsDecoder(js))

  def decodeUserFields(s: Symbol)(implicit js: JSONObject): Seq[UserField] = userFieldsDecoder(js)

  implicit val userFieldsDecoder: JsonDecoder[Seq[UserField]] = new JsonDecoder[Seq[UserField]] {
    override def apply(implicit js: JSONObject): Seq[UserField] =
      decodeSeq('fields)(js, userFieldDecoder)
  }

  implicit val userFieldDecoder: JsonDecoder[UserField] = new JsonDecoder[UserField] {
    override def apply(implicit js: JSONObject): UserField =
      UserField(decodeString(key), decodeString(value))
  }

  implicit val userFieldsEncoder: JsonEncoder[Seq[UserField]] = new JsonEncoder[Seq[UserField]] {
    override def apply(fields: Seq[UserField]): JSONObject = JsonEncoder { o =>
      o.put("fields", JsonEncoder.arr(fields))
    }
  }

  implicit val userFieldEncoder: JsonEncoder[UserField] = new JsonEncoder[UserField] {
    override def apply(field: UserField): JSONObject = JsonEncoder { o =>
      o.put(key.name, field.key)
      o.put(value.name, field.value)
    }
  }
}
