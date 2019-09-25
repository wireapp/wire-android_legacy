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

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.{ComponentName, Context, Intent}
import android.provider.Settings
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.AppLockEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountManager, ZMessaging}
import com.waz.services.SecurityPolicyService
import com.waz.utils.events.Signal
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityChecklist.{Action, Check}
import com.waz.zclient.security.actions._
import com.waz.zclient.security.checks._
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.{BuildConfig, Injectable, Injector, R}
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class SecurityPolicyChecker(implicit injector: Injector) extends Injectable with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Ui

  private lazy val securityPolicyService = inject[SecurityPolicyService]
  private lazy val globalPreferences     = inject[GlobalPreferences]
  private lazy val accountManager        = inject[Signal[AccountManager]]

  def run(activity: Activity): Unit = {
    for {
      allChecksPassed  <- foregroundSecurityChecklist(activity).run()
      isAppLockEnabled <- if (allChecksPassed) appLockEnabled else Future.successful(false)
      _ = verbose(l"all checks passed: $allChecksPassed, is app lock enabled: $isAppLockEnabled")
    } yield {
      if (isAppLockEnabled) authenticateIfNeeded(activity)
    }
  }

  /**
    * Security checklist for foreground activity
    */
  private def foregroundSecurityChecklist(implicit parentActivity: Activity): SecurityChecklist = {
    verbose(l"securityChecklist")
    val checksAndActions = new ListBuffer[(Check, List[Action])]()

    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      verbose(l"check BLOCK_ON_JAILBREAK_OR_ROOT")
      val rootDetectionCheck = RootDetectionCheck(globalPreferences)
      val rootDetectionActions = List(
        new WipeDataAction(None),
        BlockWithDialogAction(R.string.root_detected_dialog_title, R.string.root_detected_dialog_message)
      )

      checksAndActions += rootDetectionCheck ->  rootDetectionActions
    }

    if (BuildConfig.BLOCK_ON_PASSWORD_POLICY) {
      verbose(l"check BLOCK_ON_PASSWORD_POLICY")
      val deviceAdminCheck = new DeviceAdminCheck(securityPolicyService)
      val deviceAdminActions = List(
        ShowDialogAction(
          R.string.security_policy_setup_dialog_title,
          R.string.security_policy_setup_dialog_message,
          R.string.security_policy_setup_dialog_button,
          action = { () => showDeviceAdminScreen(parentActivity) }
        )
      )

      checksAndActions += deviceAdminCheck -> deviceAdminActions

      val devicePasswordComplianceCheck = new DevicePasswordComplianceCheck(securityPolicyService)
      val devicePasswordComplianceActions =  List(
        new ShowDialogAction(
          ContextUtils.getString(R.string.security_policy_invalid_password_dialog_title),
          ContextUtils.getString(R.string.security_policy_invalid_password_dialog_message, SecurityPolicyService.PasswordMinimumLength.toString),
          ContextUtils.getString(R.string.security_policy_setup_dialog_button),
          action = { () => showSecuritySettings(parentActivity) }
        )
      )

      checksAndActions += devicePasswordComplianceCheck -> devicePasswordComplianceActions
    }

    if (BuildConfig.WIPE_ON_COOKIE_INVALID) {
      verbose(l"check WIPE_ON_COOKIE_INVALID")

      accountManager.head.foreach { am =>
        val cookieCheck = new CookieValidationCheck(am.auth)
        val cookieActions = List(new WipeDataAction(Some(am.userId)))
        checksAndActions += cookieCheck -> cookieActions
      }
    }

    new SecurityChecklist(checksAndActions.toList)
  }

  private def showDeviceAdminScreen(implicit parentActivity: Activity): Unit = {
    val secPolicy = new ComponentName(parentActivity, classOf[SecurityPolicyService])
    val intent = new android.content.Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
      .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, secPolicy)
      .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ContextUtils.getString(R.string.security_policy_description))

    parentActivity.startActivity(intent)
  }

  private def showSecuritySettings(implicit parentActivity: Activity): Unit =
    parentActivity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS))

  def appLockEnabled: Future[Boolean] =
    if (BuildConfig.FORCE_APP_LOCK) Future.successful(true) else globalPreferences(AppLockEnabled).apply()

  // This ensures asking for password when the app is first opened.
  private var timeEnteredBackground: Option[Instant] = Some(Instant.EPOCH)
  val authenticationNeeded = Signal(false)

  private def timerExpired: Boolean = {
    val secondsSinceEnteredBackground = timeEnteredBackground.fold(0L)(_.until(Instant.now(), ChronoUnit.SECONDS))
    verbose(l"timeEnteredBackground: $timeEnteredBackground, secondsSinceEnteredBackground: $secondsSinceEnteredBackground")
    secondsSinceEnteredBackground >= BuildConfig.APP_LOCK_TIMEOUT
  }

  def updateBackgroundEntryTimer(): Unit = timeEnteredBackground = Some(Instant.now())

  def onAuthenticationSuccessful(): Unit = {
    timeEnteredBackground = None
    authenticationNeeded ! false
  }

  def authenticateIfNeeded(parentActivity: Activity): Unit = {
    authenticationNeeded.mutate {
      case false if timerExpired => true
      case b => b
    }

    authenticationNeeded.head.foreach {
      case true => parentActivity.startActivity(new Intent(parentActivity, classOf[AppLockActivity]))
      case _ =>
    }
  }
}

object SecurityPolicyChecker extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Ui

  /**
    * Security checklist for background activities (e.g. receiving notifications). This is
    * static so that it can be accessible from `FCMHandlerService`.
    */
  def backgroundSecurityChecklist(implicit context: Context): SecurityChecklist = {
    val checksAndActions = new ListBuffer[(Check, List[Action])]()

    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      verbose(l"check BLOCK_ON_JAILBREAK_OR_ROOT")

      val rootDetectionCheck = RootDetectionCheck(ZMessaging.currentGlobal.prefs)
      val rootDetectionActions = List(new WipeDataAction(None))

      checksAndActions += rootDetectionCheck ->  rootDetectionActions
    }

    if (BuildConfig.WIPE_ON_COOKIE_INVALID) {
      verbose(l"check WIPE_ON_COOKIE_INVALID")

      ZMessaging.currentAccounts.activeAccountManager.head.foreach {
        case Some(am) =>
          val cookieCheck = new CookieValidationCheck(am.auth)
          val cookieActions = List(new WipeDataAction(Some(am.userId)))
          checksAndActions += cookieCheck -> cookieActions
        case None =>
      }
    }

    new SecurityChecklist(checksAndActions.toList)
  }
}
