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

import java.util.concurrent.{ExecutorService, Executors}

import android.os.{Handler, HandlerThread, Looper}
import com.waz.utils.returning
import com.waz.zms.BuildConfig
import com.wire.signals.Threading.Cpus
import com.wire.signals.{DispatchQueue, EventContext, EventStream, Events, Signal, Subscription}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

object Threading {

  implicit class RichSignal[E](val signal: Signal[E]) extends AnyVal {
    def onUi(subscriber: Events.Subscriber[E])(implicit context: EventContext): Subscription =
      signal.on(Threading.Ui)(subscriber)(context)
  }

  implicit class RichEventStream[E](val stream: EventStream[E]) extends AnyVal {
    def onUi(subscriber: Events.Subscriber[E])(implicit context: EventContext): Subscription =
      stream.on(Threading.Ui)(subscriber)(context)
  }

  final class UiDispatchQueue() extends DispatchQueue {
    private val handler = new Handler(Looper.getMainLooper)

    override def execute(runnable: Runnable): Unit = handler.post(runnable)
  }

  object Implicits {
    implicit lazy val Background: DispatchQueue = Threading.Background
    implicit lazy val Ui:         DispatchQueue = Threading.Ui
    implicit lazy val Image:      DispatchQueue = Threading.ImageDispatcher
    implicit lazy val IO:         DispatchQueue = Threading.IO
  }

  var AssertsEnabled: Boolean = BuildConfig.DEBUG

  /**
   * Thread pool for IO tasks.
   */
  val IOThreadPool: DispatchQueue = DispatchQueue(Cpus, Executors.newCachedThreadPool(), Option("IoThreadPool"))

  /**
   * Thread pool for non-blocking background tasks.
   */
  val Background: DispatchQueue = DispatchQueue(Cpus, Executors.newCachedThreadPool(), Option("CpuThreadPool"))

  com.wire.signals.Threading.setAsDefault(Background)

  val IO: DispatchQueue = IOThreadPool

  /**
    * Image decoding/encoding dispatch queue. This operations are quite cpu intensive, we don't want them to use all cores (leaving one spare core for other tasks).
    */
  val ImageDispatcher: DispatchQueue = DispatchQueue(Cpus - 1, Background.asInstanceOf[ExecutionContext], Option("ImageDispatcher"))

  // var for tests
  private var _ui: Option[DispatchQueue] = None
  lazy val Ui: DispatchQueue = _ui match {
    case Some(ui) => ui
    case None => returning(new UiDispatchQueue)(setUi)
  }

  def setUi(ui: DispatchQueue): Unit = this._ui = Some(ui)

  val testUiThreadName = "TestUiThread"
  def isUiThread: Boolean = try {
    Thread.currentThread() == Looper.getMainLooper.getThread
  } catch {
    case NonFatal(_) => Thread.currentThread().getName.contains(testUiThreadName)
  }

  lazy val BackgroundHandler: Future[Handler] = {
    val looper = Promise[Looper]
    val looperThread = new HandlerThread("BackgroundHandlerThread") {
      override def onLooperPrepared(): Unit = looper.success(getLooper)
    }
    looperThread.start()
    looper.future.map(new Handler(_))(Background)
  }

  def assertUiThread(): Unit =
    if (AssertsEnabled && !isUiThread)
      throw new AssertionError(s"Should be run on Ui thread, but is using: ${Thread.currentThread().getName}")
}
