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

import android.os.{Handler, Looper}
import com.waz.ZLog.ImplicitTag._
import com.waz.service._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events._

import scala.concurrent.{ExecutionContext, Future}

trait ZMessagingResolverComponent {
  val zms: ZMessagingResolver
}

class ZMessagingResolver(ui: UiModule) {
  def apply[A](f: ZMessaging => A)(implicit ec: ExecutionContext = Threading.Background): CancellableFuture[A] =
    CancellableFuture.lift(ui.getCurrent.collect { case Some(zms) => f(zms) })

  def flatMapFuture[A](f: ZMessaging => Future[A])(implicit ec: ExecutionContext = Threading.Background) = apply(identity).future flatMap f
}

trait UiEventContext {
  implicit val eventContext = new EventContext() {}

  private[ui] var createCount = 0
  private[ui] var startCount = 0

  val onStarted = new SourceSignal[Boolean]() with ForcedEventSource[Boolean]
  val onReset = new Publisher[Boolean] with ForcedEventSource[Boolean]

  def onStart(): Unit = {
    Threading.assertUiThread()
    startCount += 1

    if (startCount == 1) {
      eventContext.onContextStart()
      onStarted ! true
    }
  }

  def onPause(): Unit = {
    Threading.assertUiThread()
    assert(startCount > 0, "onPause should be called exactly once for each onResume")
    startCount -= 1

    if (startCount == 0) {
      onStarted ! false
      eventContext.onContextStop()
    }
  }

  def onDestroy(): Unit = {
    Threading.assertUiThread()
    eventContext.onContextDestroy()
  }
}

class UiModule(val global: GlobalModule) extends UiEventContext with ZMessagingResolverComponent {

  private implicit val ui: UiModule = this

  val zms = new ZMessagingResolver(this)

  lazy val accounts = global.accountsService

  val currentAccount = accounts.activeAccountManager
  val currentZms = accounts.activeZms

  currentZms.onChanged { _ => onReset ! true }

  def getCurrent = accounts.activeZms.head
  

}

object UiModule {
  val UiHandler = new Handler(Looper.getMainLooper)
}
