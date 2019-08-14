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

import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.Signal

import scala.concurrent.Future

trait LogsService {
  def logsEnabledGlobally: Signal[Boolean]
  def logsEnabled: Future[Boolean]
  def setLogsEnabled(enabled: Boolean): Future[Unit]
}

//TODO Think about cycle dependency between LogsService and InternalLog
class LogsServiceImpl(globalPreferences: GlobalPreferences)
  extends LogsService with DerivedLogTag {
  
  import com.waz.utils.events.EventContext.Implicits._

  override lazy val logsEnabledGlobally: Signal[Boolean] =
    globalPreferences(GlobalPreferences.LogsEnabled).signal

  logsEnabledGlobally.ifFalse.apply { _ =>
    InternalLog.clearAll()
  }

  override def logsEnabled: Future[Boolean] =
    globalPreferences(GlobalPreferences.LogsEnabled).apply()

  override def setLogsEnabled(enabled: Boolean): Future[Unit] =
    globalPreferences(GlobalPreferences.LogsEnabled) := enabled
}
