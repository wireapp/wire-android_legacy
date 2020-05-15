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
import com.waz.model.sync.SyncJob.SyncJobDao
import com.waz.utils.wrappers.DB
import org.json.JSONObject

object SyncJobMigration {
  lazy val v75: DB => Unit = { implicit db =>
    val cmdsToDrop = Set("sync-search", "post-exclude-pymk")

    inTransaction {
      withStatement(s"DELETE FROM ${SyncJobDao.table.name} WHERE ${SyncJobDao.Id.name} = ?") { stmt =>
        forEachRow(db.query(SyncJobDao.table.name, Array(SyncJobDao.Id.name, SyncJobDao.Data.name), null, null, null, null, null)) { c =>
          stmt.clearBindings()
          val id = c.getString(0)
          val cmd =
            for {
              root <- Option(c.getString(1)).map(new JSONObject(_))
              request <- Option(root.optJSONObject("request"))
              command <- Option(request.optString("cmd"))
            } yield command

          if (cmd exists cmdsToDrop) {
            stmt.bindString(1, id)
            stmt.execute()
          }
        }
      }
    }
  }
}
