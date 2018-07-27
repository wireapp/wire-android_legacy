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
package com.waz.ui

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.ui.SignalLoader.{LoaderHandle, LoadingReference, ZmsLoaderHandle}
import com.waz.utils.events.Signal

import scala.ref.{ReferenceQueue, WeakReference}

trait SignalLoading {
  // set keeping track of all active loader handles to avoid early GC
  // it relies on LoaderHandle being regular class (equals should compare identity)
  private[ui] var loaderHandles = Set.empty[LoaderHandle[_]]

  def addLoader[A](signal: ZMessaging => Signal[A])(onLoaded: A => Unit)(implicit ui: UiModule): LoaderSubscription = {
    addLoaderOpt({
      case Some(zms) => signal(zms)
      case None => Signal.empty[A]
    })(onLoaded)
  }

  def addLoaderOpt[A, B <: A](signal: Option[ZMessaging] => Signal[B])(onLoaded: A => Unit)(implicit ui: UiModule): LoaderSubscription = {
    val handle = new ZmsLoaderHandle(this, signal, onLoaded)
    loaderHandles += handle
    SignalLoader(handle)
  }
}

trait LoaderSubscription {
  def destroy(): Unit
}

abstract class SignalLoader[A](handle: LoaderHandle[A])(implicit ui: UiModule) extends LoaderSubscription {
  import ui.eventContext

  ui.onStarted { _ => SignalLoader.dropQueue() }

  val ref = new LoadingReference(this, handle)

  def withHandle(body: LoaderHandle[A] => Signal[A]) = ref.get match {
    case Some(h) => body(h.asInstanceOf[LoaderHandle[A]])
    case None =>
      destroy()
      Signal.empty[A]
  }

  protected def signal: Signal[A]

  val observer = signal.onUi { data =>
    ref.get.fold(destroy()) { _.asInstanceOf[LoaderHandle[A]].callback(data) }
  }

  def destroy(): Unit = {
    verbose("destroy()")(s"SignalLoader[${handle.loading.getClass.getName}]")
    observer.destroy()
    ref.get.foreach { handle => handle.loading.loaderHandles -= handle }
    ref.clear()
  }
}

class ZmsSignalLoader[A](handle: ZmsLoaderHandle[A])(implicit ui: UiModule) extends SignalLoader[A](handle)(ui) {
  override def signal = ui.currentZms flatMap { zms => verbose(s"currentZms: $zms"); withHandle { _.asInstanceOf[ZmsLoaderHandle[A]].signal(zms) } }
}

object SignalLoader {

  private val queue = new ReferenceQueue[LoaderHandle[_]]

  private[ui] def dropQueue(): Unit = queue.poll match {
    case Some(ref: LoadingReference[_]) =>
      ref.loader.destroy()
      dropQueue()
    case Some(ref) =>
      error(s"unexpected reference: $ref")
      dropQueue()
    case None => // done
  }

  def apply[A](handle: ZmsLoaderHandle[A])(implicit ui: UiModule) = {
    Threading.assertUiThread()
    dropQueue()
    new ZmsSignalLoader[A](handle)
  }

  sealed trait LoaderHandle[A] {
    val loading: SignalLoading
    val callback: A => Unit
  }

  class ZmsLoaderHandle[A](val loading: SignalLoading, val signal: Option[ZMessaging] => Signal[A], val callback: A => Unit) extends LoaderHandle[A]

  class LoadingReference[A](val loader: SignalLoader[A], handle: LoaderHandle[A]) extends WeakReference(handle, queue)
}
