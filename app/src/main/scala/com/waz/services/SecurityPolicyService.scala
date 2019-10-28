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

import android.app.Activity
import android.app.admin.{DeviceAdminReceiver, DevicePolicyManager}
import android.content.{ComponentName, Context, Intent}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.BaseActivity.RequestPoliciesEnable
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

object SecurityPolicyService extends DerivedLogTag {
  def checkAdminEnabled(dpm: DevicePolicyManager, secPolicy: ComponentName, secPolicyDescription: String)(implicit activity: Activity): Unit = {
    verbose(l"checkAdminEnabled(${activity.getClass.getName})")
    if (!dpm.isAdminActive(secPolicy)) {
      verbose(l"admin not active, sending request")
      val intent = new android.content.Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, secPolicy)
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, secPolicyDescription)

      activity.startActivityForResult(intent, RequestPoliciesEnable)
    } else {
      verbose(l"admin active")
      checkPassword(dpm, secPolicy)
    }
  }

  def checkPassword(dpm: DevicePolicyManager, secPolicy: ComponentName)(implicit activity: Activity) = {
    dpm.setPasswordQuality(secPolicy, DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)
    dpm.setPasswordMinimumLength(secPolicy, 8)
    dpm.setPasswordMinimumLetters(secPolicy, 2)
    dpm.setPasswordMinimumUpperCase(secPolicy, 1)
    dpm.setPasswordMinimumLowerCase(secPolicy, 1)
    if (!dpm.isActivePasswordSufficient) {
      verbose(l"current password is insufficient")
      activity.startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
    }
  }
}