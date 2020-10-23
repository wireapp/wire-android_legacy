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

import com.waz.db.{BaseDaoDB, inReadTransaction, inTransaction}
import com.waz.log.BasicLogging.LogTag
import com.waz.threading.Threading
import com.wire.signals.{CancellableFuture, DispatchQueue, SerialDispatchQueue, UnlimitedDispatchQueue}
import com.waz.utils.wrappers.DB

import scala.concurrent.Future

trait Database {
  protected implicit val dispatcher: DispatchQueue

  val dbHelper: BaseDaoDB

  protected lazy val readExecutionContext: DispatchQueue =
    DispatchQueue(DispatchQueue.UNLIMITED, Threading.IO, name = Some("Database_readQueue_" + hashCode().toHexString))

  def apply[A](f: DB => A)(implicit logTag: LogTag = LogTag("")): CancellableFuture[A] = dispatcher {
    implicit val db:DB = dbHelper.getWritableDatabase
    inTransaction(f(db))
  }

  def withTransaction[A](f: DB => A)(implicit logTag: LogTag = LogTag("")): CancellableFuture[A] = apply(f)

  def read[A](f: DB => A): Future[A] = Future {
    implicit val db:DB = dbHelper.getWritableDatabase
    inReadTransaction(f(db))
  } (readExecutionContext)

  def close(): CancellableFuture[Unit] = dispatcher {
    dbHelper.close()
  }

  def flushWALToDatabase(): Future[Unit] =
    dispatcher(dbHelper.flushWALFile())
}
