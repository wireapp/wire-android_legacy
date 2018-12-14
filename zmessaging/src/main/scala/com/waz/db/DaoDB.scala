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
package com.waz.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import com.waz.ZLog.ImplicitTag._
import com.waz.log.ZLog2._
import com.waz.service.tracking.TrackingService

import scala.util.Try

class DaoDB(context:    Context,
            name:       String,
            factory:    CursorFactory,
            version:    Int,
            daos:       Seq[BaseDao[_]],
            migrations: Seq[Migration],
            tracking:   TrackingService)
  extends SQLiteOpenHelper(context, name, factory, version) {

  override def onConfigure(db: SQLiteDatabase): Unit = {
    super.onConfigure(db)
    db.enableWriteAheadLogging()
    val c = db.rawQuery("PRAGMA secure_delete = true", null)
    Try {
      c.moveToNext()
      verbose(l"PRAGMA secure_delete set to: ${c.getString(0).toInt == 1}")
    }
  }

  override def onOpen(db: SQLiteDatabase) = {
    super.onOpen(db)
    flushWALFile(Some(db))
  }


  def flushWALFile(db: Option[SQLiteDatabase] = None): Unit = {
    val c = db.getOrElse(getWritableDatabase).rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
    Try {
      c.moveToNext()
      verbose(l"PRAGMA wal_checkpoint performed. Busy?: ${c.getInt(0) == 1}. WAL pages modified: ${c.getInt(1)}. WAL pages moved back: ${c.getInt(2)}")
    }
  }

  override def onCreate(db: SQLiteDatabase): Unit = {
    daos.foreach { dao =>
      dao.onCreate(db)
    }
  }

  override def onUpgrade(db: SQLiteDatabase, from: Int, to: Int): Unit =
    new Migrations(migrations: _*)(tracking).migrate(this, from, to)(db)

  def dropAllTables(db: SQLiteDatabase): Unit =
    daos.foreach { dao =>
      db.execSQL(s"DROP TABLE IF EXISTS ${dao.table.name};")
    }
}

