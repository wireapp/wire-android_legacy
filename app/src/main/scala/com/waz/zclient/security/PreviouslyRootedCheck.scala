/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.security

import android.content.Context
import android.preference.PreferenceManager
import com.waz.zclient.security.RootDetectionCheck.RootDetectedFlag

import scala.concurrent.Future

class PreviouslyRootedCheck(implicit context: Context) extends SecurityCheckList.Check {

  override def isSatisfied: Future[Boolean] = {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val rootedDetected = sharedPreferences.getBoolean(RootDetectedFlag, false)
    Future.successful(!rootedDetected)
  }
}

object PreviouslyRootedCheck {

  def apply()(implicit context: Context): PreviouslyRootedCheck = new PreviouslyRootedCheck()
}
