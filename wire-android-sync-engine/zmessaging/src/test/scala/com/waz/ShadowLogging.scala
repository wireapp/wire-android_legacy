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

import java.io.{File, FileOutputStream, OutputStream, PrintStream}
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

import org.robolectric.shadows.ShadowLog
import org.scalatest._
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

trait ShadowLogging extends RobolectricSuite { this: Suite =>
  import ShadowLogging._

  @volatile private var currentStream: Option[PrintStream] = None
  private lazy val counter = new AtomicInteger(0)
  private def idx = f"${counter.getAndIncrement()}%02d"

  protected def logfileBaseDir: File

  private def baseName = suiteName.replaceAll("[^\\w]+", "_")

  abstract override def runShadow(testName: Option[String], args: Args): Status = {
    logfileBaseDir.mkdirs()
    logfileBaseDir.listFiles().filter(_.getName.matches(s"${baseName}_\\d+_\\w+\\.alog")).foreach(_.delete)
    currentStream = Some(setupShadowLog(new File(logfileBaseDir, s"${baseName}_${idx}_init.alog")))
    super.runShadow(testName, args)
  }

  abstract override protected def runTest(testName: String, args: Args): Status = {
    currentStream.foreach { stream =>
      stream.close()
      ShadowLog.stream = null
      currentStream = None
    }
    withShadowLog(new File(logfileBaseDir, s"${baseName}_${idx}_${testName.replaceAll("[^\\w]+", "_")}.alog")) {
      super.runTest(testName, args)
    }
  }
}

object ShadowLogging {
  def setupShadowLog(logFile: File): PrintStream = {
    logFile.getParentFile.mkdirs()
    logFile.delete()
    setupShadowLogAround(new FileOutputStream(logFile))
  }

  private lazy val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)

  def setupShadowLogAround(stream: OutputStream): PrintStream = {
    ShadowLog.setupLogging()
    ShadowLog.stream = new PrintStream(stream) {
      override def println(str: String): Unit = print(s"${formatter.format(LocalDateTime.now)}    $str${System.lineSeparator}")
    }
    ShadowLog.stream
  }

  def withShadowLog[A](logFile: File)(body: => A) = {
    val stream = setupShadowLog(logFile)
    try {
      body
    } finally {
      stream.close()
      ShadowLog.stream = null
    }
  }
}
