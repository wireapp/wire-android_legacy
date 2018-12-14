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
package org.robolectric.shadows

import android.database.sqlite.SQLiteConnection
import org.robolectric.annotation.{Implementation, Implements}

@Implements(classOf[SQLiteConnection]) object ShadowSQLiteConnection2 {
  private val collatePattern = "(?i)\\s+COLLATE\\s+(LOCALIZED|UNICODE)".r.pattern

  @Implementation
  def nativePrepareStatement(connectionPtr: Int, sql: String): Int =
    ShadowSQLiteConnection.nativePrepareStatement(connectionPtr, collatePattern.matcher(sql).replaceAll(" COLLATE NOCASE"))
}

@Implements(classOf[SQLiteConnection]) class ShadowSQLiteConnection2 extends ShadowSQLiteConnection
