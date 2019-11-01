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
import com.waz.content.GlobalPreferences.AppLockEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountManager, ZMessaging}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityChecklist.{Action, Check}
import com.waz.zclient.security.actions._
import com.waz.zclient.security.checks._
import com.waz.zclient.{BuildConfig, Injectable, Injector, R}
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit

import scala.concurrent.Future

class SecurityPolicyChecker(implicit injector: Injector, ec: EventContext) extends Injectable with DerivedLogTag {
  import SecurityPolicyChecker._
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val globalPreferences  = inject[GlobalPreferences]
  private lazy val accountManager     = inject[Signal[AccountManager]]
  private lazy val userPreferences    = inject[Signal[UserPreferences]]
  private lazy val passwordController = inject[PasswordController]

  inject[ActivityLifecycleCallback].appInBackground.onUi {
    case (true, _)               => updateBackgroundEntryTimer()
    case (false, Some(activity)) => run()(activity)
    case _                       =>
      warn(l"The app is coming to the foreground, but the information about the activity is missing")
  }

  def run()(implicit activity: Activity): Unit =
    for {
      authNeeded      <- isAuthenticationNeeded()
      accManager      <- accountManager.head
      userPrefs       <- userPreferences.head
      allChecksPassed <- runSecurityChecklist(Some(passwordController), globalPreferences, Some(userPrefs), Some(accManager), isForeground = true, authNeeded = authNeeded)
    } yield {
      verbose(l"all checks passed: $allChecksPassed")
      if (allChecksPassed && authNeeded) {
        timeEnteredBackground = None
        authenticationNeeded ! false
      }
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

  private def isAuthenticationNeeded(): Future[Boolean] =
    if (BuildConfig.FORCE_APP_LOCK) Future.successful(true)
    else globalPreferences.preference(AppLockEnabled).apply().flatMap {
      case false => Future.successful(false)
      case true =>
        authenticationNeeded.mutate {
          case false if timerExpired => true
          case b => b
        }
        authenticationNeeded.head
    }
}

object SecurityPolicyChecker extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Ui

  private val EmptyCheck = Future.successful(Option.empty[(Check, List[Action])])

  private def blockOnJailbreak(globalPreferences: GlobalPreferences, isForeground: Boolean)(implicit context: Context) =
    if (BuildConfig.BLOCK_ON_JAILBREAK_OR_ROOT) {
      verbose(l"check BLOCK_ON_JAILBREAK_OR_ROOT")
      val check = RootDetectionCheck(globalPreferences)
      val actions: List[Action] = List(new WipeDataAction(None)) ++
        (if (isForeground) List(BlockWithDialogAction(R.string.root_detected_dialog_title, R.string.root_detected_dialog_message)) else Nil)

      Future.successful(Some((check, actions)))
    } else EmptyCheck

  private def wipeOnCookieInvalid(accountManager: AccountManager)(implicit context: Context) =
    if (BuildConfig.WIPE_ON_COOKIE_INVALID) {
      verbose(l"check WIPE_ON_COOKIE_INVALID")
      val check = new CookieValidationCheck(accountManager.auth)
      val actions = List(new WipeDataAction(Some(accountManager.userId)))
      Future.successful(Some(check, actions))
    } else EmptyCheck

  private def requestPassword(passwordController: PasswordController,
                              userPreferences: UserPreferences,
                              accountManager: AccountManager,
                              authNeeded: Boolean)(implicit context: Context, eventContext: EventContext) =
    if (authNeeded) {
      verbose(l"check request password")
      val check = RequestPasswordCheck(passwordController, userPreferences)
      val actions = if (BuildConfig.FORCE_APP_LOCK) List(new WipeDataAction(Some(accountManager.userId))) else Nil
      Future.successful(Some(check, actions))
    } else EmptyCheck

  /**
    * Security checklist for foreground activity
    */
  private def runSecurityChecklist(passwordController: Option[PasswordController],
                                   globalPreferences : GlobalPreferences,
                                   userPreferences   : Option[UserPreferences],
                                   accountManager    : Option[AccountManager],
                                   isForeground      : Boolean,
                                   authNeeded        : Boolean
                                  )(implicit context: Context, eventContext: EventContext): Future[Boolean] = {
    def unpack[A, B, C](a: Option[A], b: Option[B], c: Option[C]): Option[(A, B, C)] = (a, b, c) match {
      case (Some(aa), Some(bb), Some(cc)) => Some((aa, bb, cc))
      case _ => None
    }

    for {
      blockOnJailbreak    <- blockOnJailbreak(globalPreferences, isForeground)
      wipeOnCookieInvalid <- accountManager.fold(EmptyCheck)(wipeOnCookieInvalid)
      requestPassword     <- unpack(passwordController, userPreferences, accountManager).fold(EmptyCheck) {
                               case (ctrl, prefs, am) => requestPassword(ctrl, prefs, am, authNeeded)
                             }
      list                =  new SecurityChecklist(List(blockOnJailbreak, wipeOnCookieInvalid, requestPassword).flatten)
      allChecksPassed     <- list.run()
    } yield allChecksPassed
  }

  /**
    * Security checklist for background activities (e.g. receiving notifications). This is
    * static so that it can be accessible from `FCMHandlerService`.
    */
  def runBackgroundSecurityChecklist()(implicit context: Context, eventContext: EventContext): Future[Boolean] =
    ZMessaging.currentAccounts.activeAccountManager.head.flatMap(am =>
      runSecurityChecklist(
        passwordController = None,
        globalPreferences  = ZMessaging.currentGlobal.prefs,
        userPreferences    = None,
        accountManager     = am,
        isForeground       = false,
        authNeeded         = false
      )
    )

}
