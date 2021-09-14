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

import ly.count.android.sdk.{Countly, CountlyConfig, DeviceId}

import scala.collection.JavaConverters._

object CountlyApi {
  def changeDeviceIdWithMerge(id: String): Unit =
    Countly.sharedInstance().changeDeviceIdWithMerge(id)

  def init(cxt: WireContext, countlyAppKey: String, serverURL: String,
           logsEnabled: Boolean, id: String): Unit = {
    val config = new CountlyConfig(cxt, countlyAppKey, serverURL)
      .setLoggingEnabled(logsEnabled)
      .setIdMode(DeviceId.Type.DEVELOPER_SUPPLIED)
      .setDeviceId(id)
      .setRecordAppStartTime(true)
    Countly.sharedInstance().init(config)
  }

  def onStart(ctx: WireContext): Unit =
    Countly.sharedInstance().onStart(cxt)

  def onStop(): Unit =
    Countly.sharedInstance().onStop()

  def setUserData(predefinedFields: util.HashMap[String, String],
                  customFields: util.HashMap[String, String]): Unit = {
    Countly.userData.setUserData(predefinedFields, customFields)
    Countly.userData.save()
  }

  def recordEvent(eventArg: TrackingEvent): Unit =
    Countly.sharedInstance().events().recordEvent(eventArg.name, eventArg.segments.asJava)

  def applicationOnCreate(): Unit =
    Countly.applicationOnCreate()
}
