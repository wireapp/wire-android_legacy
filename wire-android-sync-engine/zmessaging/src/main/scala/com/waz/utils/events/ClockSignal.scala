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
package com.waz.utils.events

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.CancellableFuture
import com.waz.threading.CancellableFuture.delayed
import com.waz.threading.Threading.Background
import com.waz.utils._
import org.threeten.bp.Instant.now
import org.threeten.bp.{Clock, Instant}
import com.waz.log.LogSE._

import scala.concurrent.duration.{FiniteDuration, _}

case class ClockSignal(interval: FiniteDuration, clock: Clock = Clock.systemUTC())
  extends SourceSignal[Instant](Some(now(clock))) with DerivedLogTag {

  private var delay = CancellableFuture.successful({})

  def refresh: Unit = if (wired) {
    publish(now(clock))
    delay.cancel()
    delay = delayed(interval)(refresh)(Background)
  } else {
    info(l"Cannot publish ClockSignal value: not wired")
  }

  //To force a refresh in tests when clock is advanced
  def check() = {
    val lastRefresh = value.getOrElse(Instant.EPOCH)
    if (interval <= lastRefresh.until(now(clock)).toMillis.millis) refresh
  }

  override def onWire: Unit = refresh
}
