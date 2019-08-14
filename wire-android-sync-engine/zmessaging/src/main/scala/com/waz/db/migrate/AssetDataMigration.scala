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
package com.waz.db.migrate

import com.waz.db._
import com.waz.utils.wrappers.DB
import org.json.JSONObject

object AssetDataMigration {
  lazy val v70: DB => Unit = { implicit db =>
    // migrate AnyAssetData preview field
    withStatement("UPDATE Assets SET data = ? WHERE _id = ?") { stmt =>
      forEachRow(db.query("Assets", Array("_id", "data"), "asset_type = 'Any'", null, null, null, null)) { c =>
        val asset = new JSONObject(c.getString(1))
        if (asset.has("preview")) {
          val preview = new JSONObject()
          preview.put("type", "image")
          preview.put("img", asset.getJSONObject("preview"))
          asset.put("preview", preview)

          stmt.clearBindings()
          stmt.bindString(1, preview.toString)
          stmt.bindString(2, c.getString(0)) // id
          stmt.execute()
        }
      }
    }
  }
}
