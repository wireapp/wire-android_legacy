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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.services

import android.app.admin.DeviceAdminReceiver
import android.content.{Context, Intent}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.Injectable
import com.waz.zclient.log.LogUI._

/**
  * This class performs two functions, firstly it serves as the required DeviceAdminReceived instance
  * defined in the AndroidManifest. It sets our policy once we have been granted admin rights.
  *
  * Secondly, we use it to implement some static helper methods. This means we can use the
  * `getManager` method to get the policy manager service, rather than pass it in as a parameter
  * which we would have to do if we used a companion object.
  */
class SecurityPolicyService extends DeviceAdminReceiver with DerivedLogTag with Injectable {
  override def onEnabled(context: Context, intent: Intent): Unit = {
    verbose(l"admin rights enabled, setting policy")
  }
}
