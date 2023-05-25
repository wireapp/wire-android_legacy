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
import com.waz.db.{BaseDaoDB, RoomDaoDB, ZGlobalDB}
import com.waz.service.tracking.TrackingService
import com.waz.zclient.storage.db.{GlobalDatabase => RoomGlobalDatabase}
import com.waz.zclient.storage.di.StorageModule
import java.util.concurrent.Executors


class GlobalDatabase(context: Context, dbNameSuffix: String = "", tracking: TrackingService) extends Database {

  override implicit val dispatcher = Executors.newFixedThreadPool(8)

  override          val dbHelper  : BaseDaoDB           =
    new RoomDaoDB(StorageModule.getGlobalDatabase(
      context,
      ZGlobalDB.migrations.map(_.toRoomMigration).toArray ++ RoomGlobalDatabase.getMigrations)
    )
}
