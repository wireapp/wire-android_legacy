/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.utils

import com.waz.utils.MathUtilsTest.binary
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class MathUtilsTest extends JUnitSuite {

  @Test
  def testRemoveBinaryFlag_flagIsPresent(): Unit = {
    assertEquals(MathUtils.removeBinaryFlag(binary("10101"), binary("100")), binary("10001"))
    assertEquals(MathUtils.removeBinaryFlag(binary("11111"), binary("101")), binary("11010"))
  }

  @Test
  def testRemoveBinaryFlag_flagIsNotPresent(): Unit = {
    // flag is not present (flag is larger than number)
    assertEquals(MathUtils.removeBinaryFlag(binary("00001"), binary("100")), binary("00001"))
    assertEquals(MathUtils.removeBinaryFlag(binary("00011"), binary("101")), binary("00011"))
    // flag is not present (flag is smaller than number)
    assertEquals(MathUtils.removeBinaryFlag(binary("10001"), binary("100")), binary("10001"))
  }

  @Test
  def testRemoveBinaryFlag_NullFlag(): Unit = {
    // flag is null
    assertEquals(MathUtils.removeBinaryFlag(binary("10101"), binary("000")), binary("10101"))
  }

  @Test
  def testRemoveBinaryFlag_NullValue(): Unit = {
    // value is null
    assertEquals(MathUtils.removeBinaryFlag(binary("00000"), binary("000")), binary("00000"))
    assertEquals(MathUtils.removeBinaryFlag(binary("00000"), binary("111")), binary("00000"))
  }

  @Test
  def testRoundingTo5(): Unit = {
    assertEquals(5, MathUtils.roundToNearest5(4))
    assertEquals(10, MathUtils.roundToNearest5(9))
    assertEquals(0, MathUtils.roundToNearest5(1))
  }

  @Test
  def testLogRounding(): Unit = {
    assertEquals(256, MathUtils.logRoundFactor6(300))
    assertEquals(64, MathUtils.logRoundFactor6(66))
    assertEquals(32, MathUtils.logRoundFactor6(63))
    assertEquals(32, MathUtils.logRoundFactor6(33))
    assertEquals(4, MathUtils.logRoundFactor6(5))
    assertEquals(2, MathUtils.logRoundFactor6(2))
  }

}

object MathUtilsTest {
  def binary(text: String): Int = {
    return Integer.parseInt(text, 2)
  }
  type Block = () => Unit
}
