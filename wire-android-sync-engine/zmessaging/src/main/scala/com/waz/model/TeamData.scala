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
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.DBCursor

case class TeamData(override val id: TeamId,
                    name:            Name,
                    creator:         UserId,
                    icon:            AssetId) extends Identifiable[TeamId] {

  def picture: Option[Picture] = {
    if (hasValidIconId) Some(PictureUploaded(icon))
    else None
  }

  // Team icon is optional, but on backend it's not. A dummy value (less than 10 chars)
  // signifies no team icon.
  private def hasValidIconId: Boolean = icon.str.length >= 10
}

object TeamData {

  def apply(id: String, name: String, creator: String, icon: String): TeamData =
    TeamData(TeamId(id), Name(name), UserId(creator), AssetId(icon))

  import com.waz.db.Col._
  implicit object TeamDataDao extends Dao[TeamData, TeamId] {
    val Id      = id[TeamId]      ('_id, "PRIMARY KEY").apply(_.id)
    val Name    = text[model.Name]('name, _.str, model.Name)(_.name)
    val Creator = id[UserId]      ('creator).apply(_.creator)
    val Icon    = id[AssetId]     ('icon).apply(_.icon)

    override val idCol = Id
    override val table = Table("Teams", Id, Name, Creator, Icon)

    override def apply(implicit cursor: DBCursor): TeamData = new TeamData(Id, Name, Creator, Icon)
  }
}