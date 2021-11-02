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
import android.content.Context
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountManager, ZMessaging}
import com.wire.signals.Signal
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityChecklist.{Action, Check}
import com.waz.zclient.security.actions._
import com.waz.zclient.security.checks._
import com.waz.zclient.{BuildConfig, Injectable, Injector, R, WireApplication}
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit

import scala.concurrent.Future
import com.waz.threading.Threading._
import com.waz.zclient.legalhold.{LegalHoldApprovalHandler, LegalHoldController}

class SecurityPolicyChecker(implicit injector: Injector) extends Injectable with DerivedLogTag {
  import SecurityPolicyChecker._
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val globalPreferences   = inject[GlobalPreferences]
  private lazy val accountManager      = inject[Signal[AccountManager]]
  private lazy val userPreferences     = inject[Signal[UserPreferences]]
  private lazy val passwordController  = inject[PasswordController]
  private lazy val legalHoldController = inject[LegalHoldController]
  private lazy val legalHoldHandler    = inject[LegalHoldApprovalHandler]

  private val alc = inject[ActivityLifecycleCallback]
  private val appInBackground = alc.appInBackground.map(_._1).onChanged
  private val currentActivity = alc.appInBackground.map(_._2)

  appInBackground.onUi {
    case true =>
      updateBackgroundEntryTimer()
    case false =>
      currentActivity.head.foreach {
        case Some(activity) => run(activity)
        case None =>
          warn(l"The app is coming to the foreground, but the information about the activity is missing")
      }
  }

  def run(activity: Activity): Unit = {
    verbose(l"check run, activity: ${activity.getLocalClassName}")
    for {
      authNeeded      <- isAuthenticationNeeded()
      accManager      <- accountManager.head
      userPrefs       <- userPreferences.head
      allChecksPassed <- runSecurityChecklist(
                           Some(passwordController),
                           globalPreferences,
                           Some(userPrefs),
                           Some(accManager),
                           Some(legalHoldController),
                           Some(legalHoldHandler),
                           isForeground = true,
                           authNeeded = authNeeded
                         )(activity)
    } yield {
      verbose(l"all checks passed: $allChecksPassed")
      if (allChecksPassed && authNeeded) {
        authenticationNeeded ! false
        timerEnabled ! false
      }
    }
  }

  //  This ensures asking for password when the app is first opened / restarted.
  private var timeEnteredBackground: Option[Instant] = None
  private val authenticationNeeded = Signal(false)
  private val timerEnabled = Signal(true)

  private def timerExpired(timeout: Int): Boolean = {
    val secondsSinceEnteredBackground = timeEnteredBackground.fold(Long.MaxValue)(_.until(Instant.now(), ChronoUnit.SECONDS))
    verbose(l"timeEnteredBackground: $timeEnteredBackground, secondsSinceEnteredBackground: $secondsSinceEnteredBackground, timeout is: $timeout")
    secondsSinceEnteredBackground >= timeout
  }

  def updateBackgroundEntryTimer(): Unit = {
    verbose(l"updateBackgroundEntryTimer")
    timerEnabled ! true
    timeEnteredBackground = Some(Instant.now())
  }

  private def isAuthenticationNeeded(): Future[Boolean] =
    timerEnabled.head.flatMap {
      case false => Future.successful(false)
      case true =>
        if (BuildConfig.FORCE_APP_LOCK) Future.successful(true)
        else
          Signal.zip(passwordController.appLockEnabled, passwordController.appLockTimeout).head.flatMap {
          case (false, _) => Future.successful(false)
          case (true, timeout) =>
            authenticationNeeded.mutate {
              case false if timerExpired(timeout) => true
              case b => b
            }
            authenticationNeeded.head
      }
    }
}

object SecurityPolicyChecker extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Ui

  private val EmptyCheck = Future.successful(Option.empty[(Check, List[Action])])

  private def blockOnJailbreak(globalPreferences: GlobalPreferences, isForeground: Boolean)(implicit context: Context) =
    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      verbose(l"check BLOCK_ON_JAILBREAK_OR_ROOT")
      val check = RootDetectionCheck(globalPreferences)
      val actions: List[Action] = List(new WipeDataAction()) ++
        (if (isForeground) List(BlockWithDialogAction(R.string.root_detected_dialog_title, R.string.root_detected_dialog_message)) else Nil)

      Future.successful(Some((check, actions)))
    } else EmptyCheck

  private def wipeOnCookieInvalid(accountManager: AccountManager)(implicit context: Context) =
    if (BuildConfig.WIPE_ON_COOKIE_INVALID) {
      verbose(l"check WIPE_ON_COOKIE_INVALID")
      val check = new CookieValidationCheck(accountManager.auth)
      val actions = List(new WipeDataAction())
      Future.successful(Some(check, actions))
    } else EmptyCheck

  private def requestPassword(passwordController: PasswordController,
                              userPreferences: UserPreferences,
                              authNeeded: Boolean)(implicit context: Context) =
    if (authNeeded) {
          verbose(l"check request password, force app lock from the build: ${BuildConfig.FORCE_APP_LOCK}")
          val check = RequestPasswordCheck(passwordController, userPreferences)
          val actions = if (BuildConfig.FORCE_APP_LOCK) List(new WipeDataAction()) else Nil
          Future.successful(Some(check, actions))
    } else EmptyCheck

  private def requestLegalHoldAcceptance(legalHoldController: LegalHoldController,
                                         legalHoldApprovalHandler: LegalHoldApprovalHandler)(implicit context: Context) = {
      verbose(l"check request legal hold acceptance")
      val check = RequestLegalHoldCheck(legalHoldController)
      val actions = List(new ShowLegalHoldApprovalAction(legalHoldApprovalHandler))
      Future.successful(Some(check, actions))
  }

  /**
    * Security checklist for foreground activity
    */
  private def runSecurityChecklist(passwordController : Option[PasswordController],
                                   globalPreferences  : GlobalPreferences,
                                   userPreferences    : Option[UserPreferences],
                                   accountManager     : Option[AccountManager],
                                   legalHoldController: Option[LegalHoldController],
                                   legalHoldHandler   : Option[LegalHoldApprovalHandler],
                                   isForeground       : Boolean,
                                   authNeeded         : Boolean
                                  )(implicit context: Context): Future[Boolean] = {

    for {
      blockOnJailbreak    <- blockOnJailbreak(globalPreferences, isForeground)
      wipeOnCookieInvalid <- accountManager.fold(EmptyCheck)(wipeOnCookieInvalid)
      requestPassword     <- (passwordController, userPreferences) match {
                               case (Some(ctrl), Some(prefs)) =>
                                 Signal.zip(ctrl.appLockEnabled, ctrl.customPasswordEmpty).head.flatMap {
                                   case (true, true) => EmptyCheck // the user must set the password first
                                   case _            => requestPassword(ctrl, prefs, authNeeded)
                                 }
                               case _ => EmptyCheck
                             }
      requestLegalHold    <- (legalHoldController, legalHoldHandler) match {
                               case (Some(controller), Some(handler)) => requestLegalHoldAcceptance(controller, handler)
                               case  _ => EmptyCheck
                             }
      list                =  new SecurityChecklist(List(blockOnJailbreak, wipeOnCookieInvalid, requestPassword, requestLegalHold).flatten)
      allChecksPassed     <- list.run()
    } yield allChecksPassed
  }

  /**
   * Security checklist for background activities (e.g. receiving notifications). This is
   * static so that it can be accessible from `FCMHandlerService`.
   *
   * This checklist might be called from FCMHandlerService when the device is in the doze mode and
   * the app has only a few seconds to handle and incoming message. Every millisecond counts, so
   * we use a quick if/else at start to check if more complicated checks are necessary at all.
   */
  def runBackgroundSecurityChecklist()(implicit context: Context): Future[Boolean] =
    if (!needsBackgroundSecurityChecklist) {
      Future.successful(true)
    } else {
      WireApplication.ensureInitialized()
      ZMessaging.currentAccounts.activeAccountManager.head.flatMap(am =>
        runSecurityChecklist(
          passwordController  = None,
          globalPreferences   = ZMessaging.currentGlobal.prefs,
          userPreferences     = None,
          accountManager      = am,
          legalHoldController = None,
          legalHoldHandler    = None,
          isForeground        = false,
          authNeeded          = false
        )
    )
  }

  def needsBackgroundSecurityChecklist: Boolean =
    BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT || BuildConfig.WIPE_ON_COOKIE_INVALID
}
