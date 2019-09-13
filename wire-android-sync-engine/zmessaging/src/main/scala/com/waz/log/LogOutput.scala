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

import com.waz.log.BasicLogging.LogTag
import com.waz.log.InternalLog.LogLevel

import scala.concurrent.Future

trait LogOutput {
  def id: String
  def showSafeOnly: Boolean
  def level: LogLevel = LogLevel.Verbose

  def log(str: String, level: InternalLog.LogLevel, tag: LogTag, ex: Option[Throwable] = None): Unit
  def log(str: String, cause: Throwable, level: InternalLog.LogLevel, tag: LogTag): Unit =
    log(str, level, tag, Some(cause))

  def close(): Unit = ()
  def flush(): Unit = ()
  def clear(): Unit = ()
}
