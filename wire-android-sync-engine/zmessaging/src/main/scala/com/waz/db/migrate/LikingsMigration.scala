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

import com.waz.utils.wrappers.DB

object LikingsMigration {
  lazy val v67: DB => Unit = { db =>
    // likes were not really used so far (not implemented on UI), so we can just drop the empty table, no need to copy data
    db.execSQL("DROP TABLE IF EXISTS Likings")
    db.execSQL("CREATE TABLE Likings (message_id TEXT, user_id TEXT, action INTEGER, timestamp INTEGER, PRIMARY KEY (message_id, user_id))")
  }
}
