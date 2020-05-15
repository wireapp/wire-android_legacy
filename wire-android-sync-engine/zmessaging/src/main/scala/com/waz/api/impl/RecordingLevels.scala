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
package com.waz.api.impl

import com.waz.api
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.{AggregatingSignal, EventStream, Signal}
import com.waz.utils.returning
import com.waz.log.LogSE._

import scala.concurrent.Future
import scala.math.max

class RecordingLevels(liveLevels: EventStream[Float]) extends DerivedLogTag {
  private val allLevels = returning(new AggregatingSignal[Float, Vector[Float]](liveLevels,
    Future.successful(Vector.empty), _ :+ _))(_.disableAutowiring())

  def windowed(windowSize: Int): Signal[Array[Float]] = allLevels map { levels =>
    val startIndex = max(levels.size - windowSize, 0)
    Array.tabulate(windowSize) { n =>
      val index = startIndex + n
      if (index < levels.size) levels(index) else 0f
    }
  }

  def overview: api.AudioOverview = AudioOverview(allLevels.currentValue)
}

case class AudioOverview(allLevels: Option[Vector[Float]]) extends api.AudioOverview with DerivedLogTag {
  override def isEmpty: Boolean = allLevels.forall(_.isEmpty)

  override def getLevels(numberOfLevels: Int): Array[Float] = {
    verbose(l"getLevels($numberOfLevels)")
    allLevels.filterNot(_.isEmpty).fold(Array.fill(numberOfLevels)(0f)) { levels =>
      if (numberOfLevels == 0) Array.empty[Float]
      else if (levels.length == 1) Array.fill(numberOfLevels)(levels(0))
      else {
        if (levels.length < numberOfLevels) {
          val dx = (levels.size - 1).toFloat / (numberOfLevels - 1).toFloat
          val interpolate = new LinearInterpolation(levels)
          Array.tabulate(numberOfLevels)(n => interpolate(n * dx))
        } else {
          val dx = levels.size.toFloat / numberOfLevels.toFloat
          Array.tabulate(numberOfLevels)(n => Iterator.range((n * dx).toInt, ((n + 1f) * dx).toInt).map(levels).max)
        }
      }
    }
  }
}

class LinearInterpolation(controlPoints: Vector[Float]) extends PartialFunction[Float, Float] {
  override def apply(x: Float): Float =
    if (isDefinedAt(x)) {
      val x0 = x.floor
      val y0 = controlPoints(x0.toInt)
      val x1 = x.ceil
      val y1 = controlPoints(x1.toInt)
      val dx = x - x0
      val dy = (y1 - y0) * (x - x0)
      y0 + dy
    } else throw new IndexOutOfBoundsException(s"x is outside the interpolation range: $x")

  override def isDefinedAt(x: Float): Boolean = x >= 0 && x <= controlPoints.size - 1
}

