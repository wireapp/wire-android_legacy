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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service._
import com.waz.threading.Threading
import com.wire.signals._

import scala.concurrent.Future

trait UiEventContext {
  implicit lazy val eventContext: EventContext = EventContext()

  private[ui] var createCount = 0
  private[ui] var startCount = 0

  val onStarted = new SourceSignal[Boolean]() with ForcedEventSource[Boolean]
  val onReset = new SourceStream[Boolean] with ForcedEventSource[Boolean]

  def onStart(): Unit = {
    Threading.assertUiThread()
    startCount += 1

    if (startCount == 1) {
      eventContext.start()
      onStarted ! true
    }
  }

  def onPause(): Unit = {
    Threading.assertUiThread()
    assert(startCount > 0, "onPause should be called exactly once for each onResume")
    startCount -= 1

    if (startCount == 0) {
      onStarted ! false
      eventContext.stop()
    }
  }

  def onDestroy(): Unit = {
    Threading.assertUiThread()
    eventContext.destroy()
  }
}

class UiModule(val global: GlobalModule) extends UiEventContext with DerivedLogTag {
  private implicit val ui: UiModule = this
  private lazy val accounts: AccountsService = global.accountsService

  val currentZms: Signal[Option[ZMessaging]] = accounts.activeZms

  currentZms.onChanged { _ => onReset ! true }

  def getCurrent: Future[Option[ZMessaging]] = accounts.activeZms.head
}

