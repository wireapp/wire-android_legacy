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

import android.content.Context
import android.graphics.Color
import com.waz.log.LogShow.SafeToLog
import com.waz.model.AccentColor._
import com.waz.utils.Identifiable

case class AccentColor(override val id: Int, r: Int, g: Int, b: Int, a: Int)
  extends SafeToLog with Identifiable[Int] {
  def this(id: Int, r: Double, g: Double, b: Double, a: Double) = this(id, int(r), int(g), int(b), int(a))

  lazy val color = Color.argb(a, r, g, b)
}

object AccentColor {

  def apply(id: Int): AccentColor = colorsMap.getOrElse(id, defaultColor)

  def apply(r: Double, g: Double, b: Double, a: Double): AccentColor = apply(int(r), int(g), int(b), int(a))

  /**
    * Finds closest matching accent color.
   */
  def apply(r: Int, g: Int, b: Int, a: Int): AccentColor = {
    def sq(x: Int) = x * x
    colors.minBy(c => sq(c.r - r) + sq(c.g - g) + sq(c.b - b) + sq(c.a - a))
  }

  private def int(c: Double) = (c * 255).toInt

  private val Default = new AccentColor(1, 0.141, 0.552, 0.827, 1)

  private[AccentColor] var colors = Vector(
    new AccentColor(1, 0.141, 0.552, 0.827, 1),
    new AccentColor(2, 0, 0.784, 0, 1),
    new AccentColor(3, 1, 0.823, 0, 1),
    new AccentColor(4, 1, 0.152, 0, 1),
    new AccentColor(5, 1, 0.588, 0, 1),
    new AccentColor(6, 0.996, 0.368, 0.741, 1),
    new AccentColor(7, 0.615, 0, 1, 1)
  )

  private[AccentColor] var colorsMap = Map(0 -> Default) ++ colors.toIdMap

  def defaultColor = colorsMap.getOrElse(0, Default)

  def getColors: Vector[AccentColor] = colors

  def setColors(newColors: Vector[AccentColor]): Unit = {
    colors = newColors
    colorsMap = Map(0 -> colors.headOption.getOrElse(Default)) ++ colors.map(c => c.id -> c).toMap
  }

  def loadArray(context: Context, colorsArrayId: Int): Vector[AccentColor] =
    context.getResources.getIntArray(colorsArrayId).zipWithIndex.map { case (color, id) => create(id + 1, color) }.toVector

  def create(id: Int, color: Int): AccentColor =
    new AccentColor(id, Color.red(color), Color.green(color), Color.blue(color), Color.alpha(color))

}
