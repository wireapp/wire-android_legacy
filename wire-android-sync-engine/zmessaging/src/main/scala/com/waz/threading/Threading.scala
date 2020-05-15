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
package com.waz.threading

import java.util.Timer
import java.util.concurrent.{Executor, ExecutorService, Executors}

import android.os.{Handler, HandlerThread, Looper}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.utils.returning
import com.waz.zms.BuildConfig

import scala.concurrent.{ExecutionContext, Future, Promise, blocking}
import scala.util.control.NonFatal

object Threading {

  object Implicits {
    implicit lazy val Background: DispatchQueue    = Threading.ThreadPool
    implicit lazy val Ui:         DispatchQueue    = Threading.Ui
    implicit lazy val Image:      DispatchQueue    = Threading.ImageDispatcher
    implicit lazy val IO: ExecutionContext = Threading.IO
  }

  var AssertsEnabled = BuildConfig.DEBUG

  val Cpus = math.max(2, Runtime.getRuntime.availableProcessors())

  def executionContext(service: ExecutorService)(implicit tag: LogTag): ExecutionContext = new ExecutionContext {
    override def reportFailure(cause: Throwable): Unit = {
//      exception(cause, "ExecutionContext failed") TODO make threading mockable and then inject tracking
      error(l"${showString(cause.getMessage)}", cause)
    }
    override def execute(runnable: Runnable): Unit = service.execute(runnable)
  }

  /**
   * Thread pool for non-blocking background tasks.
   */
  val ThreadPool: DispatchQueue = new LimitedDispatchQueue(Cpus, executionContext(Executors.newCachedThreadPool())(LogTag("CpuThreadPool")), "CpuThreadPool")

  /**
   * Thread pool for IO tasks.
   */
  val IOThreadPool: DispatchQueue = new LimitedDispatchQueue(Cpus, executionContext(Executors.newCachedThreadPool())(LogTag("IoThreadPool")), "IoThreadPool")

  val Background = ThreadPool

  val IO = IOThreadPool

  /**
    * Image decoding/encoding dispatch queue. This operations are quite cpu intensive, we don't want them to use all cores (leaving one spare core for other tasks).
    */
  val ImageDispatcher = new LimitedDispatchQueue(Cpus - 1, ThreadPool, "ImageDispatcher")

  // var for tests
  private var _ui: Option[DispatchQueue] = None
  def Ui: DispatchQueue = _ui match {
    case Some(ui) => ui
    case None => returning(new UiDispatchQueue)(setUi)
  }

  def setUi(ui: DispatchQueue) = this._ui = Some(ui)

  val testUiThreadName = "TestUiThread"
  def isUiThread = try {
    Thread.currentThread() == Looper.getMainLooper.getThread
  } catch {
    case NonFatal(e) => Thread.currentThread().getName.contains(testUiThreadName)
  }

  val Timer = new Timer(true)

  Timer.purge()

  lazy val BackgroundHandler: Future[Handler] = {
    val looper = Promise[Looper]
    val looperThread = new HandlerThread("BackgroundHandlerThread") {
      override def onLooperPrepared(): Unit = looper.success(getLooper)
    }
    looperThread.start()
    looper.future.map(new Handler(_))(Background)
  }

  def assertUiThread(): Unit = if (AssertsEnabled && !isUiThread) throw new AssertionError(s"Should be run on Ui thread, but is using: ${Thread.currentThread().getName}")
  def assertNotUiThread(): Unit = if (AssertsEnabled && isUiThread) throw new AssertionError(s"Should be run on background thread, but is using: ${Thread.currentThread().getName}")
}
