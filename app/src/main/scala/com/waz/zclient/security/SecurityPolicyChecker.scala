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
import android.content.{Context, Intent}
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountManager, ZMessaging}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityChecklist.{Action, Check}
import com.waz.zclient.security.actions._
import com.waz.zclient.security.checks._
import com.waz.zclient.{BuildConfig, Injectable, Injector, R}
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit

import scala.collection.mutable.ListBuffer

class SecurityPolicyChecker(implicit injector: Injector, ec: EventContext) extends Injectable with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Ui

  private lazy val globalPreferences     = inject[GlobalPreferences]
  private lazy val accountManager        = inject[Signal[AccountManager]]

  inject[ActivityLifecycleCallback].appInBackground.onUi {
    case (true, _)               => updateBackgroundEntryTimer()
    case (false, Some(activity)) => run(activity)
    case _ => warn(l"The app is coming to the foreground, but the information about the activity is missing")
  }

  def run(activity: Activity): Unit =
    foregroundSecurityChecklist(activity).run().foreach { allChecksPassed =>
      verbose(l"all checks passed: $allChecksPassed")
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
    verbose(l"authenticate if needed")
    authenticationNeeded.mutate {
      case false if timerExpired => true
      case b => b
    }

    authenticationNeeded.head.foreach {
      case true =>
        val intent = new Intent(parentActivity, classOf[AppLockActivity])
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        parentActivity.startActivity(intent)
      case _ =>
    }
  }

  // TODO: Implement the biometric prompt using this code and add it as a part of the password check

  /*
  val executor = ExecutorWrapper(Threading.Ui)
  val callback = new BiometricPrompt.AuthenticationCallback {
    override def onAuthenticationError(errorCode: Int, errString: CharSequence): Unit = {
      super.onAuthenticationError(errorCode, errString)
      verbose(l"SEC on error, code: $errorCode, str: $errString")

    override def onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult): Unit = {
      super.onAuthenticationSucceeded(result)
      verbose(l"SEC on success, result: $result")

    override def onAuthenticationFailed(): Unit = {
      super.onAuthenticationFailed()
      verbose(l"SEC on failed")
    }

  verbose(l"SEC create prompt")
  val prompt = new BiometricPrompt(parentActivity.asInstanceOf[FragmentActivity], executor, callback)
  verbose(l"SEC authenticating...")
  prompt.authenticate(promptInfo)
  verbose(l"SEC here")

  private lazy val promptInfo = new BiometricPrompt.PromptInfo.Builder()
    .setTitle("Set the title to display.")
    .setSubtitle("Set the subtitle to display.")
    .setDescription("Set the description to display")
    .setNegativeButtonText("Negative Button")
    .build()

  */
}

object SecurityPolicyChecker extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Ui

  val PasswordMinimumLength: Int = 8

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
