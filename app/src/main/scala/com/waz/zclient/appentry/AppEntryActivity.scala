/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.appentry

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.FragmentManager.OnBackStackChangedListener
import android.support.v4.app.{Fragment, FragmentTransaction}
import android.view.View
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.content.Preferences.Preference.PrefCodec
import com.waz.service.AccountManager.ClientRegistrationState
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.SpinnerController.{Hide, Show}
import com.waz.zclient._
import com.waz.zclient.appentry.AppEntryActivity._
import com.waz.zclient.appentry.controllers.InvitationsController
import com.waz.zclient.appentry.fragments.{TeamNameFragment, _}
import com.waz.zclient.newreg.fragments.country.CountryController
import com.waz.zclient.preferences.PreferencesController
import com.waz.zclient.tracking.CrashController
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.views.LoadingIndicatorView
import net.hockeyapp.android.NativeCrashManager

import scala.collection.JavaConverters._

object AppEntryActivity {
  val TAG: String = classOf[AppEntryActivity].getName
  private val HTTPS_PREFIX: String = "https://"
  private val HTTP_PREFIX: String = "http://"
  val PREFETCH_IMAGE_WIDTH: Int = 4

  val MethodArg: String = "method_arg"
  val LoginArgVal: Int = 0
  val CreateTeamArgVal: Int = 1

  def getLoginArgs: Bundle =
    returning(new Bundle()) { b =>
      b.putInt(MethodArg, LoginArgVal)
    }

  def getCreateTeamArgs: Bundle =
    returning(new Bundle()) { b =>
      b.putInt(MethodArg, CreateTeamArgVal)
    }
}

class AppEntryActivity extends BaseActivity {

  private lazy val progressView = ViewUtils.getView(this, R.id.liv__progress).asInstanceOf[LoadingIndicatorView]
  private lazy val countryController: CountryController = new CountryController(this)
  private lazy val invitesController = inject[InvitationsController]
  private lazy val spinnerController  = inject[SpinnerController]
  private var createdFromSavedInstance: Boolean = false
  private var isPaused: Boolean = false

  private lazy val accountsService = inject[AccountsService]
  private lazy val attachedFragment = Signal[String]()

  private lazy val closeButton = returning(ViewUtils.getView(this, R.id.close_button).asInstanceOf[GlyphTextView]) { v =>
    Signal(accountsService.zmsInstances.map(_.nonEmpty), attachedFragment).map {
      case (false, _) => View.GONE
      case (true, fragment) if Set(SignInFragment.Tag, FirstLaunchAfterLoginFragment.Tag, VerifyEmailWithCodeFragment.Tag,
          VerifyPhoneFragment.Tag, CountryDialogFragment.TAG, PhoneSetNameFragment.Tag, InviteToTeamFragment.Tag).contains(fragment) => View.GONE
      case _ => View.VISIBLE
    }.onUi(v.setVisibility(_))
  }

  private lazy val skipButton = returning(findById[TypefaceTextView](R.id.skip_button)) { v =>
    invitesController.invitations.map(_.isEmpty).map {
      case true => R.string.teams_invitations_skip
      case false => R.string.teams_invitations_done
    }.onUi(t => v.setText(t))
  }

  ZMessaging.currentGlobal.blacklist.upToDate.onUi {
    case false =>
      startActivity(new Intent(this, classOf[ForceUpdateActivity]))
      finish()
    case _ =>
  }

  override def onBackPressed(): Unit = {
    getSupportFragmentManager.getFragments.asScala.find {
      case f: OnBackPressedListener if f.onBackPressed() => true
      case _ => false
    }.fold(finish())(_ => ())
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    if (getActionBar != null) getActionBar.hide()
    super.onCreate(savedInstanceState)
    ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)
    setContentView(R.layout.activity_signup)
    enableProgress(false)
    createdFromSavedInstance = savedInstanceState != null

    closeButton.onClick(abortAddAccount())

    withFragmentOpt(AppLaunchFragment.Tag) {
      case Some(_) =>
      case None =>
        Option(getIntent.getExtras).map(_.getInt(MethodArg)) match {
          case Some(LoginArgVal) => showFragment(SignInFragment(), SignInFragment.Tag, animated = false)
          case Some(CreateTeamArgVal) => showFragment(TeamNameFragment(), TeamNameFragment.Tag, animated = false)
          case _ => showFragment(AppLaunchFragment(), AppLaunchFragment.Tag, animated = false)
      }
    }

    skipButton.setVisibility(View.GONE)
    getSupportFragmentManager.addOnBackStackChangedListener(new OnBackStackChangedListener {
      override def onBackStackChanged(): Unit = {
        withFragmentOpt(InviteToTeamFragment.Tag) {
          case Some(_) => skipButton.setVisibility(View.VISIBLE)
          case _ => skipButton.setVisibility(View.GONE)
        }
      }
    })
    skipButton.onClick(onEnterApplication(false))

    spinnerController.spinnerShowing.onUi {
      case Show(animation, forcedTheme)=> progressView.show(animation, darkTheme = forcedTheme.getOrElse(true), 300)
      case Hide(Some(message)) => progressView.hideWithMessage(message, 750)
      case Hide(_) => progressView.hide()
    }
  }

  override protected def onResume(): Unit = {
    super.onResume()

    val trackingEnabled: Boolean = injectJava(classOf[PreferencesController]).isAnalyticsEnabled
    if (trackingEnabled) {
      //TODO move to new preferences
      CrashController.checkForCrashes(getApplicationContext, getControllerFactory.getUserPreferencesController.getDeviceId)
    }
    else {
      CrashController.deleteCrashReports(getApplicationContext)
      NativeCrashManager.deleteDumpFiles(getApplicationContext)
    }
  }

  override def onAttachFragment(fragment: Fragment): Unit = {
    super.onAttachFragment(fragment)
    attachedFragment ! fragment.getTag
  }

  override protected def onPostResume(): Unit = {
    super.onPostResume()
    isPaused = false
  }

  override protected def onPause(): Unit = {
    isPaused = true
    super.onPause()
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    info(s"OnActivity result: $requestCode, $resultCode")
    super.onActivityResult(requestCode, resultCode, data)
    getSupportFragmentManager.findFragmentById(R.id.fl_main_content).onActivityResult(requestCode, resultCode, data)
  }

  def enableProgress(enabled: Boolean): Unit = {
    if (enabled)
      progressView.show(LoadingIndicatorView.SpinnerWithDimmedBackground(), darkTheme = true)
    else
      progressView.hide()
  }

  def abortAddAccount(): Unit =
    Option(getIntent.getExtras).map(_.getInt(MethodArg, -1)) match {
      case Some(LoginArgVal | CreateTeamArgVal) =>
        startActivity(Intents.OpenSettingsIntent(this))
      case _ =>
        onEnterApplication(false)
    }

  def onEnterApplication(openSettings: Boolean, clientRegState: Option[ClientRegistrationState] = None): Unit = {
    getControllerFactory.getVerificationController.finishVerification()
    val intent = Intents.EnterAppIntent(openSettings)(this)
    clientRegState.foreach(state => intent.putExtra(MainActivity.ClientRegStateArg, PrefCodec.SelfClientIdCodec.encode(state)))
    startActivity(intent)
    finish()
  }

  private def setDefaultAnimation(transaction: FragmentTransaction): FragmentTransaction = {
    transaction.setCustomAnimations(
      R.anim.fragment_animation_second_page_slide_in_from_right,
      R.anim.fragment_animation_second_page_slide_out_to_left,
      R.anim.fragment_animation_second_page_slide_in_from_left,
      R.anim.fragment_animation_second_page_slide_out_to_right)
    transaction
  }

  def openCountryBox(): Unit = {
    getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(new CountryDialogFragment, CountryDialogFragment.TAG)
      .addToBackStack(CountryDialogFragment.TAG)
      .commit
    KeyboardUtils.hideKeyboard(this)
  }

  def getCountryController: CountryController = countryController

  def dismissCountryBox(): Unit = {
    getSupportFragmentManager.popBackStackImmediate
    KeyboardUtils.showKeyboard(this)
  }

  def showFragment(f: => Fragment, tag: String, animated: Boolean = true): Unit = {
    val transaction = getSupportFragmentManager.beginTransaction()
    if (animated) setDefaultAnimation(transaction)
      transaction
      .replace(R.id.fl_main_content, f, tag)
      .addToBackStack(tag)
      .commit
    enableProgress(false)
  }
}
