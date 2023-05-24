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
package com.waz.content

import android.content.Context
import com.waz.db.{BaseDaoDB, RoomDaoDB, ZMessagingDB}
import com.waz.model.UserId
import com.waz.threading.Threading
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.di.StorageModule
import com.wire.signals.DispatchQueue

/**
  * Single user storage. Keeps data specific to used user account.
  */
class ZmsDatabase(user: UserId, context: Context) extends Database {
  override implicit val dispatcher: DispatchQueue =
    DispatchQueue(DispatchQueue.Serial, Threading.IOThreadPool, name = "ZmsDatabase_" + user.str.substring(24))

  override val dbHelper: BaseDaoDB =
    new RoomDaoDB(StorageModule.getUserDatabase(
      context, user.str,
      ZMessagingDB.migrations.map(_.toRoomMigration).toArray ++ UserDatabase.getMigrations)
    )
}
