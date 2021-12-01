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
package com.waz.model

import java.util.Date

import com.waz.service.ZMessaging.clock
import org.threeten.bp
import org.threeten.bp.temporal.ChronoUnit
import org.threeten.bp.{Clock, Duration, Instant}

import scala.concurrent.duration.FiniteDuration

trait WireInstant {

  val instant: Instant

  def javaDate: Date = new Date(instant.toEpochMilli)
  def toEpochMilli: Long = instant.toEpochMilli
  def toEpochSec: Long = instant.getEpochSecond

  def isEpoch: Boolean = instant == Instant.EPOCH
}

final case class RemoteInstant(instant: Instant) extends WireInstant with Comparable[RemoteInstant]  {
  override def compareTo(o: RemoteInstant): Int = instant.compareTo(o.instant)

  def -(d: FiniteDuration): RemoteInstant = copy(instant = instant.minusNanos(d.toNanos))
  def -(d: bp.Duration): RemoteInstant = copy(instant = instant.minusNanos(d.toNanos))
  def +(d: FiniteDuration): RemoteInstant = copy(instant = instant.plusNanos(d.toNanos))
  def +(d: bp.Duration): RemoteInstant = copy(instant = instant.plusNanos(d.toNanos))

  def toLocal(drift: Duration): LocalInstant = LocalInstant(this.instant.minus(drift))
}

object RemoteInstant {
  def Epoch = RemoteInstant(Instant.EPOCH)
  def Max = RemoteInstant(Instant.MAX)
  def ofEpochMilli(epochMilli: Long) = RemoteInstant(Instant.ofEpochMilli(epochMilli))
  def ofEpochSec(epochSecond: Long) = RemoteInstant(Instant.ofEpochSecond(epochSecond))
}

case class LocalInstant(instant: Instant) extends WireInstant with Comparable[LocalInstant] {
  def isToday: Boolean = instant.truncatedTo(ChronoUnit.DAYS) == Instant.now.truncatedTo(ChronoUnit.DAYS)
  override def compareTo(o: LocalInstant): Int = instant.compareTo(o.instant)

  def -(d: FiniteDuration): LocalInstant = copy(instant = instant.minusNanos(d.toNanos))
  def -(d: bp.Duration): LocalInstant = copy(instant = instant.minusNanos(d.toNanos))
  def +(d: FiniteDuration): LocalInstant = copy(instant = instant.plusNanos(d.toNanos))
  def +(d: bp.Duration): LocalInstant = copy(instant = instant.plusNanos(d.toNanos))

  def toRemote(drift: Duration): RemoteInstant = RemoteInstant(this.instant.plus(drift))
  def toRemote(drift: FiniteDuration): RemoteInstant = RemoteInstant(this.instant) + drift
}

object LocalInstant {
  val Epoch = LocalInstant(Instant.EPOCH)
  val Max = LocalInstant(Instant.MAX)
  def Now = LocalInstant(Instant.now(clock))
  def Now(clock: Clock) = LocalInstant(Instant.now(clock))
  def ofEpochMilli(epochMilli: Long) = LocalInstant(Instant.ofEpochMilli(epochMilli))
  def ofEpochSecond(epochSecond: Long) = LocalInstant(Instant.ofEpochSecond(epochSecond))
}
