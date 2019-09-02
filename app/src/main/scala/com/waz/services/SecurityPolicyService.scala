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
package com.waz.services

import android.app.admin.{DeviceAdminReceiver, DevicePolicyManager}
import android.content.{ComponentName, Context, Intent}
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
class SecurityPolicyService
  extends DeviceAdminReceiver with DerivedLogTag with Injectable {

  override def onEnabled(context: Context, intent: Intent): Unit = {
    verbose(l"admin rights enabled, setting policy")
    setPasswordPolicy(context)
  }

  private def setPasswordPolicy(context: Context): Unit = {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE).asInstanceOf[DevicePolicyManager]
    val secPolicy = new ComponentName(context, classOf[SecurityPolicyService])
    /**
    We must set the password quality to some minimum quality, PASSWORD_QUALITY_SOMETHING and
     PASSWORD_QUALITY_UNSPECIFIED both result in our minimum length requirement not being enforced.
     PASSWORD_QUALITY_ALPHABETIC is used here as an example, as it's permissive.
     From the Android Device Admin API docs:

     "This constraint is only imposed if the administrator has also requested either
     PASSWORD_QUALITY_NUMERIC , PASSWORD_QUALITY_NUMERIC_COMPLEX, PASSWORD_QUALITY_ALPHABETIC,
     PASSWORD_QUALITY_ALPHANUMERIC, or PASSWORD_QUALITY_COMPLEX with setPasswordQuality(ComponentName, int)"
      **/
    dpm.setPasswordQuality(secPolicy, DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)
    dpm.setPasswordMinimumLength(secPolicy, SecurityPolicyService.PasswordMinimumLength)
    dpm.setPasswordMinimumLetters(secPolicy, 2)
    dpm.setPasswordMinimumUpperCase(secPolicy, 1)
    dpm.setPasswordMinimumLowerCase(secPolicy, 1)
  }

  def isSecurityPolicyEnabled(implicit context: Context): Boolean =
    getManager(context).isAdminActive(new ComponentName(context, classOf[SecurityPolicyService]))

  def isPasswordCompliant(implicit context: Context): Boolean = getManager(context).isActivePasswordSufficient
}

object SecurityPolicyService {
  val PasswordMinimumLength: Int = 8
}
