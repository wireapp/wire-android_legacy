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

import android.app.{Activity, AlertDialog, KeyguardManager}
import android.content.{Context, DialogInterface, Intent}
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{BuildConfig, R}
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit

class AppLockActivity extends AppCompatActivity with DerivedLogTag {
  import AppLockActivity._

  private lazy val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE).asInstanceOf[KeyguardManager]

  override def onStart(): Unit = {
    super.onStart()

    if (isDeviceSecure) showAuthenticationScreen() else showSetupDialog()
  }

  private def isDeviceSecure: Boolean = keyguardManager.isKeyguardSecure

  private def showAuthenticationScreen(): Unit = {
    info(l"presenting authentication screen")
    val (title, message) = (getString(R.string.app_lock_locked_title), getString(R.string.app_lock_locked_message))
    val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, message)
    startActivityForResult(intent, ConfirmDeviceCredentialsRequestCode)
  }

  private def showSetupDialog(): Unit = {
    val openSettingsAction = new DialogInterface.OnClickListener {
      override def onClick(dialog: DialogInterface, which: Int): Unit = {
        startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS))
        finish()
      }
    }

    new AlertDialog.Builder(this)
      .setMessage(R.string.app_lock_setup_dialog_messsage)
      .setPositiveButton(R.string.app_lock_setup_dialog_button, openSettingsAction)
      .setCancelable(false)
      .create()
      .show()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == ConfirmDeviceCredentialsRequestCode && resultCode == Activity.RESULT_OK) {
      info(l"authentication successful")
      timeEnteredBackground = None
      finish()
    }
  }
}

object AppLockActivity extends DerivedLogTag {

  final val ConfirmDeviceCredentialsRequestCode = 1

  private var timeEnteredBackground: Option[Instant] = Some(Instant.EPOCH)

  def updateBackgroundEntryTimer(): Unit = {
    timeEnteredBackground = Some(Instant.now())
  }

  def isAppLockExpired: Boolean = {
    val now = Instant.now()
    val secondsSinceEnteredBackground = timeEnteredBackground.getOrElse(now).until(now, ChronoUnit.SECONDS)
    secondsSinceEnteredBackground >= BuildConfig.APP_LOCK_TIMEOUT
  }
}
