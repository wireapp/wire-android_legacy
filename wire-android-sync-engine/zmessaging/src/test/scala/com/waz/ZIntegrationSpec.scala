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
package com.waz

import com.waz.log.{InternalLog, LogOutput}
import com.waz.specs.DummyLogsService
import com.waz.utils.isTest
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest._

trait ZIntegrationSpec extends AsyncFeatureSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with Matchers
  with OneInstancePerTest {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    isTest = true

    InternalLog.reset()
    InternalLog.add(new SystemLogOutput)
    InternalLog.setLogsService(new DummyLogsService)
  }
}

trait ZIntegrationMockSpec extends ZIntegrationSpec with AsyncMockFactory


import com.waz.log.BasicLogging.LogTag
import com.waz.log.InternalLog.dateTag

class SystemLogOutput extends LogOutput {
  override val id: String = SystemLogOutput.id

  override val showSafeOnly: Boolean = false

  override def log(str: String, level: InternalLog.LogLevel, tag: LogTag, ex: Option[Throwable] = None): Unit = {
    println(s"$dateTag/$level/${tag.value}: $str")
    ex.foreach(e => println(InternalLog.stackTrace(e)))
  }
}

object SystemLogOutput {
  val id = "system"
}
