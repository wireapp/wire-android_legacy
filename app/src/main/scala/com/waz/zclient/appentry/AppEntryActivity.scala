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

import android.content.res.Configuration
import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import androidx.fragment.app.{Fragment, FragmentManager, FragmentTransaction}
import androidx.fragment.app.FragmentManager.OnBackStackChangedListener
import com.waz.content.Preferences.Preference.PrefCodec
import com.waz.service.AccountManager.ClientRegistrationState
import com.waz.service.{AccountsService, GlobalModule}
import com.waz.sync.client.CustomBackendClient
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.SpinnerController.{Hide, Show}
import com.waz.zclient._
import com.waz.zclient.appentry.AppEntryActivity._
import com.waz.zclient.appentry.controllers.InvitationsController
import com.waz.zclient.appentry.fragments.{TeamNameFragment, _}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.deeplinks.DeepLink.{Access, ConversationToken, CustomBackendToken, UserToken}
import com.waz.zclient.deeplinks.DeepLinkService.Error.{InvalidToken, UserLoggedIn}
import com.waz.zclient.deeplinks.DeepLinkService.{DoNotOpenDeepLink, OpenDeepLink}
import com.waz.zclient.deeplinks.{DeepLink, DeepLinkService}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.newreg.fragments.country.CountryController
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils.{showConfirmationDialog, showErrorDialog, showLogoutWarningIfNeeded}
import com.waz.zclient.utils.{BackendController, ContextUtils, RichView, ViewUtils}
import com.waz.zclient.views.LoadingIndicatorView

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

  import Threading.Implicits.Ui

  implicit def ctx: Context = this

  private lazy val progressView = ViewUtils.getView(this, R.id.liv__progress).asInstanceOf[LoadingIndicatorView]
  private lazy val countryController: CountryController = new CountryController(this)
  private lazy val invitesController = inject[InvitationsController]
  private lazy val spinnerController  = inject[SpinnerController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val deepLinkService: DeepLinkService = inject[DeepLinkService]
  private var createdFromSavedInstance: Boolean = false
  private var isPaused: Boolean = false

  private lazy val accountsService = inject[AccountsService]
  private lazy val attachedFragment = Signal[String]()

  private lazy val closeButton = returning(ViewUtils.getView(this, R.id.close_button).asInstanceOf[GlyphTextView]) { v =>
    val fragmentTags = Set(
      SignInFragment.Tag,
      FirstLaunchAfterLoginFragment.Tag,
      VerifyEmailWithCodeFragment.Tag,
      VerifyPhoneFragment.Tag,
      CountryDialogFragment.TAG,
      PhoneSetNameFragment.Tag,
      InviteToTeamFragment.Tag
    )

    Signal(accountsService.zmsInstances.map(_.nonEmpty), attachedFragment).map {
      case (false, _)                                          => View.GONE
      case (true, fragment) if fragmentTags.contains(fragment) => View.GONE
      case _                                                   => View.VISIBLE
    }.onUi(v.setVisibility)
  }

  private lazy val skipButton = returning(findById[TypefaceTextView](R.id.skip_button)) { v =>
    invitesController.invitations.map(_.isEmpty).map {
      case true => R.string.teams_invitations_skip
      case false => R.string.teams_invitations_done
    }.onUi(t => v.setText(t))
  }

  ForceUpdateActivity.checkBlacklist(this)

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

    showFragment()

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

    deepLinkService.deepLink.collect { case Some(result) => result } onUi {
      case OpenDeepLink(UserToken(_), _) | DoNotOpenDeepLink(DeepLink.User, _) =>
        showErrorDialog(R.string.deep_link_user_error_title, R.string.deep_link_user_error_message)
        deepLinkService.deepLink ! None

      case OpenDeepLink(ConversationToken(_), _) | DoNotOpenDeepLink(DeepLink.Conversation, _) =>
        showErrorDialog(R.string.deep_link_conversation_error_title, R.string.deep_link_conversation_error_message)
        deepLinkService.deepLink ! None

      case OpenDeepLink(CustomBackendToken(configUrl), _) =>
        verbose(l"got custom backend url: $configUrl")
        deepLinkService.deepLink ! None

        inject[AccentColorController].accentColor.head.flatMap { color =>
          showConfirmationDialog(
            title       = ContextUtils.getString(R.string.custom_backend_dialog_confirmation_title),
            msg         = ContextUtils.getString(R.string.custom_backend_dialog_confirmation_message, configUrl.toString),
            positiveRes = R.string.custom_backend_dialog_connect,
            negativeRes = android.R.string.cancel,
            color       = color
          )
        }.foreach {
        case false =>
          verbose(l"cancelling backend switch")
        case true =>
          enableProgress(true)
          inject[CustomBackendClient].loadBackendConfig(configUrl).foreach {
            case Left(errorResponse) =>
              error(l"error trying to download backend config.", errorResponse)
              enableProgress(false)

              showErrorDialog(
                  R.string.custom_backend_dialog_network_error_title,
                  R.string.custom_backend_dialog_network_error_message)

            case Right(config) =>
              verbose(l"got config response: $config")
              enableProgress(false)

              inject[BackendController].switchBackend(inject[GlobalModule], config, configUrl)
              verbose(l"switched backend")

              // re-present fragment for updated ui.
              getSupportFragmentManager.popBackStackImmediate(AppLaunchFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
              showFragment(AppLaunchFragment(), AppLaunchFragment.Tag, animated = false)
          }
        }

      case DoNotOpenDeepLink(Access, UserLoggedIn) =>
        verbose(l"do not open, Access, user logged in")
        showErrorDialog(
          R.string.custom_backend_dialog_logged_in_error_title,
          R.string.custom_backend_dialog_logged_in_error_message)
        deepLinkService.deepLink ! None

      case DoNotOpenDeepLink(Access, InvalidToken) =>
        verbose(l"do not open, Access, invalid token")
        showErrorDialog(
          R.string.custom_backend_dialog_network_error_title,
          R.string.custom_backend_dialog_network_error_message)
        deepLinkService.deepLink ! None

      case _ =>
    }

    userAccountsController.mostRecentLoggedOutAccount.onUi {
      case Some((_, reason)) =>
        showLogoutWarningIfNeeded(reason).foreach(_ => userAccountsController.mostRecentLoggedOutAccount ! None)
      case None =>
    }
  }

  // It is possible to open the app through intents with deep links. If that happens, we can't just
  // show the fragment that was opened previously - we have to take the user to the fragment specified
  // by the intent (at this point the information about it should be already stored somewhere).
  // If this is the case, in `onResume` we can pop back the stack and show the new fragment.
  override def onResume(): Unit = {
    super.onResume()
    // if the SSO token is present we use it to log in the user
    userAccountsController.ssoToken.head.foreach {
      case Some(_) =>
        getSupportFragmentManager.popBackStackImmediate(AppLaunchFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        showFragment(AppLaunchFragment(), AppLaunchFragment.Tag, animated = false)
      case _ =>
    }(Threading.Ui)
  }

  private def showFragment(): Unit = withFragmentOpt(AppLaunchFragment.Tag) {
    case Some(_) =>
    case None =>
      userAccountsController.ssoToken.head.foreach {
        case Some(_) =>
        // if the SSO token is present we will handle it in onResume
        case _ =>
          Option(getIntent.getExtras).map(_.getInt(MethodArg)) match {
            case Some(LoginArgVal) =>      showFragment(SignInFragment(), SignInFragment.Tag, animated = false)
            case Some(CreateTeamArgVal) => showFragment(TeamNameFragment(), TeamNameFragment.Tag, animated = false)
            case _ if !BuildConfig.ACCOUNT_CREATION_ENABLED =>
              showFragment(SignInFragment(SignInFragment.SignInOnlyLogin), SignInFragment.Tag, animated = false)
            case _ =>                      showFragment(AppLaunchFragment(), AppLaunchFragment.Tag, animated = false)
          }
      }(Threading.Ui)
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
    info(l"OnActivity result: $requestCode, $resultCode")
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
