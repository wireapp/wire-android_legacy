/**
 * Wire
 * Copyright (C) 2021 Wire Swiss GmbH
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

package com.waz.zclient.tracking

import java.util

import com.waz.service.tracking.TrackingEvent
import com.waz.zclient.WireContext

object CountlyApi {
  def changeDeviceIdWithMerge(id: String): Unit = {}

  def init(cxt: WireContext, countlyAppKey: String, serverURL: String,
           logsEnabled: Boolean, id: String): Unit = {}

  def onStart(ctx: WireContext): Unit = {}

  def onStop(): Unit = {}

  def setUserData(predefinedFields: util.HashMap[String, String],
                  customFields: util.HashMap[String, String]): Unit = {}

  def recordEvent(eventArg: TrackingEvent): Unit = {}

  def applicationOnCreate(): Unit = {}
}
