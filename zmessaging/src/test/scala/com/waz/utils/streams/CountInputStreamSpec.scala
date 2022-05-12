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
package com.waz.utils.streams

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.waz.TestData
import com.waz.specs.ZSpec
import com.waz.utils.IoUtils
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CountInputStreamSpec extends ZSpec {

  feature("CountInputStream") {

    scenario("properly calculate content size") {
      val testContent = TestData.bytes(1024)
      val targetStream = new ByteArrayOutputStream()
      val countStream = new CountInputStream(new ByteArrayInputStream(testContent))

      IoUtils.copy(countStream, targetStream)

      countStream.getBytesRead shouldBe testContent.length
    }

  }

}
