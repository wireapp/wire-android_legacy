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

import android.app.admin.DevicePolicyManager
import android.content.{ComponentName, Context, Intent}
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.AppLockEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.services.SecurityPolicyService
import com.waz.threading.Threading.Implicits.Ui
import com.waz.zclient.security.SecurityChecklist.{Action, Check}
import com.waz.zclient.security.actions._
import com.waz.zclient.security.checks._
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.{ActivityHelper, BuildConfig, R}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class SecureActivity extends AppCompatActivity with ActivityHelper with DerivedLogTag {

  private implicit val context: Context = this

  private lazy val securityPolicyService = inject[SecurityPolicyService]
  private lazy val globalPreferences = inject[GlobalPreferences]

  override def onStart(): Unit = {
    super.onStart()

    for {
      allChecksPassed <- securityChecklist.run()
      shouldShowAppLock <- shouldShowAppLock if allChecksPassed
    } yield {
      if (shouldShowAppLock) showAppLock()
    }
  }

  override def onStop(): Unit = {
    super.onStop()
    AppLockActivity.updateBackgroundEntryTimer()
  }

  private def securityChecklist: SecurityChecklist = {
    val checksAndActions = new ListBuffer[(Check, List[Action])]()

    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      checksAndActions += RootDetectionCheck(globalPreferences) -> List(
        new WipeDataAction(),
        BlockWithDialogAction(R.string.root_detected_dialog_title, R.string.root_detected_dialog_message)
      )
    }

    checksAndActions += new DeviceAdminCheck(securityPolicyService) -> List(
      ShowDialogAction(
        R.string.security_policy_auth_dialog_title,
        R.string.security_policy_auth_dialog_message,
        android.R.string.ok,
        action = { () =>
          val secPolicy = new ComponentName(this, classOf[SecurityPolicyService])
          val intent = new android.content.Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, secPolicy)
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ContextUtils.getString(R.string.security_policy_desc))

          startActivity(intent)
        }
      )
    )

    checksAndActions += new DevicePasswordComplianceCheck(securityPolicyService) -> List(
      ShowDialogAction(
        R.string.security_policy_pass_dialog_title,
        R.string.security_policy_pass_dialog_message,
        R.string.app_lock_setup_dialog_button,
        action = { () =>
          startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
      )
    )

    new SecurityChecklist(checksAndActions.toList)
  }

  private def shouldShowAppLock: Future[Boolean] = {
    globalPreferences(AppLockEnabled).apply().map { preferenceEnabled =>
      val appLockEnabled = preferenceEnabled || BuildConfig.FORCE_APP_LOCK
      appLockEnabled && AppLockActivity.needsAuthentication
    }
  }

  private def showAppLock(): Unit = {
    val intent = new Intent(this, classOf[AppLockActivity])
    startActivity(intent)
  }
}
