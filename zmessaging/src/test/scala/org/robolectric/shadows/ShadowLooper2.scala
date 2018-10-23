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

import java.util.concurrent.Executors

import android.os.Looper
import org.robolectric.annotation.Implements
import org.robolectric.util.Scheduler

import scala.concurrent.{ExecutionContext, Future}

@Implements(classOf[Looper])
class ShadowLooper2 extends ShadowLooper {

  val scheduler = new Scheduler() {
    private implicit val dispatcher = new ExecutionContext {
      val executor = Executors.newFixedThreadPool(1)
      override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
      override def execute(runnable: Runnable): Unit = executor.execute(runnable)
    }

    override def postDelayed(runnable: Runnable, delayMillis: Long): Unit = Future { super.postDelayed(runnable, delayMillis) }

    override def post(runnable: Runnable): Unit = Future { super.post(runnable) }
  }

  override def post(runnable: Runnable, delayMillis: Long): Boolean =
    if (quit) false
    else {
      scheduler.postDelayed(runnable, delayMillis)
      true
    }

  override def getScheduler: Scheduler = scheduler
}
