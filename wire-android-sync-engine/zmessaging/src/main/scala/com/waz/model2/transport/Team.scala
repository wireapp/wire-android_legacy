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
package com.waz.model2.transport

import com.waz.model._
import com.waz.utils.JsonDecoder
import org.json.JSONObject

case class Team(
    id: TeamId,
    name: Name,
    creator: UserId,
    icon: Option[RAssetId] = None,
    iconKey: Option[AESKey] = None,
    binding: Boolean
)

object Team {

  implicit val TeamBDecoder: JsonDecoder[Team] = new JsonDecoder[Team] {
    override def apply(implicit js: JSONObject): Team = {
      import JsonDecoder._
      Team(
        'id,
        'name,
        'creator,
        'icon,
        decodeOptString('icon_key).map(AESKey),
        decodeBool('binding)
      )
    }
  }

}
