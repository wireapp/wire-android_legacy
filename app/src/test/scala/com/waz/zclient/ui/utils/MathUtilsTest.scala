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
package com.waz.zclient.ui.utils

import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class MathUtilsTest extends JUnitSuite {

  def bin(text: String): Int = {
    return Integer.parseInt(text, 2)
  }

  @Test
  def testRemoveBinaryFlag(): Unit = {
    // flag is present
    assertEquals(MathUtils.removeBinaryFlag(bin("10101"), bin("100")), bin("10001"))
    assertEquals(MathUtils.removeBinaryFlag(bin("11111"), bin("101")), bin("11010"))
    // flag is not present (flag is larger than number)
    assertEquals(MathUtils.removeBinaryFlag(bin("00001"), bin("100")), bin("00001"))
    assertEquals(MathUtils.removeBinaryFlag(bin("00011"), bin("101")), bin("00011"))
    // flag is not present (flag is smaller than number)
    assertEquals(MathUtils.removeBinaryFlag(bin("10001"), bin("100")), bin("10001"))
    // flag is null
    assertEquals(MathUtils.removeBinaryFlag(bin("10101"), bin("000")), bin("10101"))
    // value is null
    assertEquals(MathUtils.removeBinaryFlag(bin("00000"), bin("000")), bin("00000"))
    assertEquals(MathUtils.removeBinaryFlag(bin("00000"), bin("111")), bin("00000"))
  }


}

object MathUtilsTest {
  type Block = () => Unit
}
