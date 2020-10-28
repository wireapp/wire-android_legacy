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
import org.json.JSONObject

case class Dim2(width: Int, height: Int) extends SafeToLog {
  def swap: Dim2 = Dim2(width = height, height = width)
}

object Dim2 extends ((Int, Int) => Dim2) {
  import JsonDecoder._

  val Empty = Dim2(0, 0)

  implicit lazy val Dim2Encoder: JsonEncoder[Dim2] = new JsonEncoder[Dim2] {
    override def apply(data: Dim2): JSONObject = JsonEncoder { o =>
      o.put("width", data.width)
      o.put("height", data.height)
    }
  }

  implicit lazy val Dim2Decoder: JsonDecoder[Dim2] = new JsonDecoder[Dim2] {
    override def apply(implicit js: JSONObject): Dim2 = Dim2('width, 'height)
  }
}
