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
package com.waz.log

import android.util.Log
import com.waz.log.BasicLogging.LogTag

class AndroidLogOutput(override val showSafeOnly: Boolean = false) extends LogOutput {

  override val id: String = AndroidLogOutput.id

  import com.waz.log.InternalLog.LogLevel._
  override def log(str: String, level: InternalLog.LogLevel, tag: LogTag, ex: Option[Throwable] = None): Unit =
    level match {
      case Error   => ex.fold(Log.e(tag.value, str))(e => Log.e(tag.value, str, e))
      case Warn    => ex.fold(Log.w(tag.value, str))(e => Log.w(tag.value, str, e))
      case Info    => Log.i(tag.value, str)
      case Debug   => Log.d(tag.value, str)
      case Verbose => Log.v(tag.value, str)
      case _ =>
    }

  override def clear(): Unit = {
    import scala.sys.process._
    Process(Seq("logcat", "-c")).run()
  }
}

object AndroidLogOutput {
  val id = "android"
}