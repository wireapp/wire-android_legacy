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
package com.waz.utils

import java.io.File.createTempFile
import java.io.{File, FileOutputStream, OutputStream}

import org.scalatest.{FeatureSpec, Ignore, Matchers}

class IoUtilsSpec extends FeatureSpec with Matchers {
  scenario("Copy from stream to file") {
    val target = returning(createTempFile("meep", ".gif"))(_.deleteOnExit())
    val size = IoUtils.copy(getClass.getResourceAsStream("/gifs/artifacts1.gif"), target)
    target should have length inputSize
    size shouldEqual inputSize
  }

  scenario("Counting written bytes") {
    val target = returning(createTempFile("meep", ".gif"))(_.deleteOnExit())
    val (counted, returned) = IoUtils.counting(new FileOutputStream(target))(o => IoUtils.copy(getClass.getResourceAsStream("/gifs/artifacts1.gif"), o))
    target should have length inputSize
    counted shouldEqual inputSize
    returned shouldEqual inputSize
  }

  scenario("Counting written bytes to a custom output stream") {
    case class Tallying() extends OutputStream { // the point here is that the write methods are not implemented by using each other
      private val builder = new StringBuilder()
      override def write(buffer: Array[Byte]): Unit = (0 until buffer.length).foreach(builder append '|')
      override def write(buffer: Array[Byte], offset: Int, count: Int): Unit = (offset until math.min(offset + count, buffer.length)) foreach tally
      override def write(oneByte: Int): Unit = tally(oneByte)
      private def tally(b: Int) = builder append '|'
      def result = builder.toString()
    }

    val tallying = Tallying()
    val (counted, returned) = IoUtils.counting(tallying)(o => IoUtils.copy(getClass.getResourceAsStream("/gifs/artifacts1.gif"), o))
    tallying.result.size shouldEqual inputSize
    counted shouldEqual inputSize
    returned shouldEqual inputSize
  }

  lazy val inputSize = new File(getClass.getResource("/gifs/artifacts1.gif").getFile).length
}
