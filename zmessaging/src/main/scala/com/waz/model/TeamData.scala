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

import com.waz.db.Dao
import com.waz.model
import com.waz.utils.{Identifiable, JsonDecoder}
import com.waz.utils.wrappers.DBCursor
import org.json.JSONObject

case class TeamData(override val id: TeamId,
                    name:            Name,
                    creator:         UserId,
                    icon:            Option[RAssetId] = None,
                    iconKey:         Option[AESKey]  = None) extends Identifiable[TeamId]

object TeamData {

  implicit lazy val Decoder: JsonDecoder[TeamData] = new JsonDecoder[TeamData] {
    override def apply(implicit js: JSONObject): TeamData = {
      import JsonDecoder._
      TeamData('id, 'name, 'creator, 'icon, decodeOptString('icon_key).map(AESKey))
    }
  }

  implicit lazy val TeamBindingDecoder: JsonDecoder[(TeamData, Boolean)] = new JsonDecoder[(TeamData, Boolean)] {
    override def apply(implicit js: JSONObject): (TeamData, Boolean) = {
      import JsonDecoder._
      (TeamData('id, 'name, 'creator, 'icon, decodeOptString('icon_key).map(AESKey)), decodeOptBoolean('binding).getOrElse(false))
    }
  }

  import com.waz.db.Col._
  implicit object TeamDataDoa extends Dao[TeamData, TeamId] {
    val Id      = id[TeamId]      ('_id, "PRIMARY KEY").apply(_.id)
    val Name    = text[model.Name]('name, _.str, model.Name)(_.name)
    val Creator = id[UserId]      ('creator).apply(_.creator)
    val Icon    = opt(id[RAssetId] ('icon))(_.icon)
    val IconKey = opt(text[AESKey]('icon_key, _.str, AESKey))(_.iconKey)

    override val idCol = Id
    override val table = Table("Teams", Id, Name, Creator, Icon, IconKey)

    override def apply(implicit cursor: DBCursor): TeamData = new TeamData(Id, Name, Creator, Icon, IconKey)
  }
}