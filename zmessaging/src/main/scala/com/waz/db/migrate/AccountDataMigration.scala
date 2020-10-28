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

import androidx.sqlite.db.SupportSQLiteDatabase

object AccountDataMigration {
  lazy val v14 = { implicit db: SupportSQLiteDatabase =>
    db.execSQL("ALTER TABLE Accounts ADD COLUMN handle TEXT DEFAULT ''")
    db.execSQL("ALTER TABLE Accounts ADD COLUMN private_mode BOOL DEFAULT false")
  }

  lazy val v20 = { implicit db: SupportSQLiteDatabase =>
    db.execSQL("ALTER TABLE Accounts ADD COLUMN pending_email TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE Accounts ADD COLUMN pending_phone TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE Accounts ADD COLUMN reg_waiting INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE Accounts ADD COLUMN code TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE Accounts ADD COLUMN invitation_token TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE Accounts ADD COLUMN name TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE Accounts ADD COLUMN first_login INTEGER DEFAULT 0")
  }
}
