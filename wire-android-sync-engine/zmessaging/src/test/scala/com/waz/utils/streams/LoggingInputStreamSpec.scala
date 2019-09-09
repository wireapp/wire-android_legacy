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

class LoggingInputStreamSpec extends ZSpec {

  feature("LoggingInputStream") {

    scenario("properly log all content") {
      val testContent = TestData.bytes(1024)
      val logStream = new ByteArrayOutputStream()
      val targetStream = new ByteArrayOutputStream()
      val loggedStream = new LoggingInputStream(new ByteArrayInputStream(testContent), logStream)

      IoUtils.copy(loggedStream, targetStream)

      logStream.toByteArray shouldBe targetStream.toByteArray
    }

  }

}
