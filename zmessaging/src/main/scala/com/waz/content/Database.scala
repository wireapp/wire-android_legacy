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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.threading.Threading
import com.waz.utils.wrappers.DB
import com.wire.signals.CancellableFuture

import java.util.concurrent.ExecutorService
import scala.concurrent.{ExecutionContext, Future}

object DuleExec extends ExecutionContext {
       implicit val logTag = LogTag("DuleExec")
    override def execute(runnable: Runnable): Unit = {
    	     verbose(l"DuleExec - start")
    	     runnable.run()
    	     verbose(l"DuleExec - end")
    }
    override def reportFailure(cause: Throwable): Unit = {
    	     verbose(l"DuleExec has failure")
    	     cause.printStackTrace()
    }
  }

trait Database extends DerivedLogTag {
  protected implicit val dispatcher: ExecutorService
  implicit lazy val executorContext = ExecutionContext.fromExecutorService(dispatcher)
  
  val dbHelper: BaseDaoDB

  def apply[A](f: DB => A)(implicit logTag: LogTag = LogTag("")): CancellableFuture[A] = CancellableFuture {
    implicit val db: DB = dbHelper.getWritableDatabase
    inTransaction(f(db))
  }

  def withTransaction[A](f: DB => A)(implicit logTag: LogTag = LogTag("")): CancellableFuture[A] = apply(f)

  def read[A](f: DB => A, logPrefix: Option[String] = None): Future[A] = {
    verbose(l"$logPrefix Database.read - creating Future")
    Future {
        verbose(l"$logPrefix Database.read - getWritableDatabase")
    	implicit val db: DB = dbHelper.getReadableDatabase
    	verbose(l"$logPrefix Database.read - preInReadTransaction")
	var tr:A = inReadTransaction(f(db), logPrefix)
    	verbose(l"$logPrefix Database.read - final")
	tr
    } (DuleExec)
  }

  def close(): CancellableFuture[Unit] = CancellableFuture {
    dbHelper.close()
  }

  def flushWALToDatabase(): Future[Unit] = CancellableFuture(dbHelper.flushWALFile())
}
