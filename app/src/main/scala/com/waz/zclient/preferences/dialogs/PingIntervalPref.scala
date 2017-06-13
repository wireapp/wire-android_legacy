/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.preferences.dialogs

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.util.AttributeSet
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.PreferenceHelper

import scala.concurrent.duration._
import scala.util.Try

/**
  * Custom preference to view and modify ping interval in PingIntervalService.
  *
  * This interval is no longer stored in SharedPreferences, so we need to modify loading and persisting logic.
  */
class PingIntervalPref(context: Context, attrs: AttributeSet) extends EditTextPreference(context, attrs) with PreferenceHelper {

  setDialogLayoutResource(android.support.v7.preference.R.layout.preference_dialog_edittext)

  val pingIntervalService = inject[Signal[ZMessaging]].map(_.pingInterval)
  val interval = returning(pingIntervalService.flatMap(_.interval))(_.disableAutowiring())

  override def getPersistedString(default: String) = interval.currentValue.fold("0")(_.toMillis.toString)

  override def persistString(value: String): Boolean = {
    if (shouldPersist()) {
      Try(value.toLong.millis) foreach { interval =>
        pingIntervalService.currentValue foreach { _.setPingInterval(interval) }
      }
      return true
    }
    false
  }
}
