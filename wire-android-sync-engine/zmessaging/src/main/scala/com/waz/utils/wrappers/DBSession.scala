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
package com.waz.utils.wrappers

import android.database.sqlite.{SQLiteConnectionPool, SQLiteSession}

import scala.language.implicitConversions

trait DBSession {
  def beginTransaction(): Unit

}

class SQLiteSessionWrapper(val session: SQLiteSession) extends DBSession {
  override def beginTransaction(): Unit = session.beginTransaction(SQLiteSession.TRANSACTION_MODE_DEFERRED, null, SQLiteConnectionPool.CONNECTION_FLAG_READ_ONLY, null)
}

object DBSession {
  def apply(session: SQLiteSession): DBSession = new SQLiteSessionWrapper(session)

  implicit def fromAndroid(session: SQLiteSession): DBSession = apply(session)

}
