/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.services.gps

import java.io.IOException

import android.app.Activity
import android.content.Context
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.PushToken
import com.waz.service.BackendConfig
import com.wire.signals.Signal
import com.waz.utils.wrappers.GoogleApi

class GoogleApiImpl extends GoogleApi with DerivedLogTag {

  override val isGooglePlayServicesAvailable = Signal[Boolean](false)

  override def checkGooglePlayServicesAvailable(activity: Activity): Unit = {}

  override def onActivityResult(requestCode: Int, resultCode: Int): Unit = {}

  @throws(classOf[IOException])
  override def getPushToken: PushToken = PushToken("token")

  @throws(classOf[IOException])
  override def deleteAllPushTokens(): Unit = {}
}

object GoogleApiImpl {
  private val instance = new GoogleApiImpl

  def apply(context: Context, beConfig: BackendConfig, prefs: GlobalPreferences): GoogleApiImpl = instance
}
