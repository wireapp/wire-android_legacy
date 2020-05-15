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
package com.waz.utils.crypto

import java.security.SecureRandom

object ZSecureRandom {
  lazy val random = new SecureRandom()

  def nextInt(): Int = random.nextInt
  def nextInt(bounds: Int): Int = random.nextInt(bounds)
  def nextInt(min: Int, max: Int): Int = random.nextInt((max - min) + 1) + min

  def nextLong(): Long = random.nextLong()
  def nextDouble(): Double = random.nextDouble()
  def nextFloat(): Double = random.nextFloat()
  def nextBytes(bytes: Array[Byte]): Unit = random.nextBytes(bytes)
}
