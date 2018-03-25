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
import android.content.{DialogInterface, Intent}
import android.graphics.Bitmap
import android.net.Uri
import android.os.{Build, Bundle}
import android.support.v4.app.{Fragment, FragmentTransaction}
import android.widget.Toast
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient._
import com.waz.zclient.appentry.controllers.AppEntryController._
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.appentry.fragments._
import com.waz.zclient.newreg.fragments.SignUpPhotoFragment
import com.waz.zclient.newreg.fragments.SignUpPhotoFragment.UNSPLASH_API_URL
import com.waz.zclient.newreg.fragments.country.CountryController
import com.waz.zclient.preferences.PreferencesController
import com.waz.zclient.preferences.dialogs.ChangeHandleFragment
import com.waz.zclient.tracking.CrashController
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.LoadingIndicatorView
import net.hockeyapp.android.NativeCrashManager

import scala.collection.JavaConverters._

object AppEntryActivity {
  val TAG: String = classOf[AppEntryActivity].getName
  private val HTTPS_PREFIX: String = "https://"
  private val HTTP_PREFIX: String = "http://"
  val PREFETCH_IMAGE_WIDTH: Int = 4
}

class AppEntryActivity extends BaseActivity
  with VerifyPhoneFragment.Container
  with PhoneSetNameFragment.Container
  with SignUpPhotoFragment.Container
  with EmailVerifyEmailFragment.Container
  with CountryDialogFragment.Container
  with FirstLaunchAfterLoginFragment.Container
  with SignInFragment.Container
  with FirstTimeAssignUsernameFragment.Container
  with InsertPasswordFragment.Container
  with CreateTeamFragment.Container
  with AddEmailFragment.Container {

  import Threading.Implicits.Ui

  private lazy val unsplashInitImageAsset = ImageAssetFactory.getImageAsset(AndroidURIUtil.parse(UNSPLASH_API_URL))
  private var unsplashInitLoadHandle: LoadHandle = null
  private lazy val progressView = ViewUtils.getView(this, R.id.liv__progress).asInstanceOf[LoadingIndicatorView]
  private lazy val countryController: CountryController = new CountryController(this)
  private var createdFromSavedInstance: Boolean = false
  private var isPaused: Boolean = false

  private lazy val appEntryController = inject[AppEntryController]
  private lazy val signInController = inject[SignInController]

  ZMessaging.currentGlobal.blacklist.upToDate.onUi {
    case false =>
      startActivity(new Intent(this, classOf[ForceUpdateActivity]))
      finish()
    case _ =>
  }

  override def onBackPressed(): Unit = {

    val topFragment = getSupportFragmentManager.getFragments.asScala.find {
      case f: OnBackPressedListener if f.onBackPressed() => true
      case _ => false
    }

    topFragment match {
      case Some(f: OnBackPressedListener) if f.onBackPressed() => //
      case _ => abortAddAccount()
    }
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    if (getActionBar != null) getActionBar.hide()
    super.onCreate(savedInstanceState)
    ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)
    setContentView(R.layout.activity_signup)
    enableProgress(false)
    createdFromSavedInstance = savedInstanceState != null

    if (!createdFromSavedInstance) {
      appEntryController.clearCredentials()
      signInController.clearCredentials()
    }

    unsplashInitLoadHandle = unsplashInitImageAsset.getSingleBitmap(AppEntryActivity.PREFETCH_IMAGE_WIDTH, new BitmapCallback() {
      def onBitmapLoaded(b: Bitmap): Unit = {
        verbose(s"onBitmapLoaded $b")
      }
    })

    appEntryController.entryStage.onUi {
      case EnterAppStage =>
        onEnterApplication(false)
      case FirstEnterAppStage =>
        onShowFirstLaunchPage()
      case NoAccountState(LoginScreen) =>
        onShowSignInPage()
      case NoAccountState(_) | SetTeamEmail | VerifyTeamEmail | SetUsersNameTeam | SetPasswordTeam | SetUsernameTeam | InviteToTeam =>
        onShowCreateTeamFragment()
      case DeviceLimitStage =>
        onEnterApplication(false)
      case AddNameStage =>
        onShowPhoneNamePage()
      case AddPictureStage =>
        onShowSetPicturePage()
      case VerifyEmailStage =>
        onShowEmailVerifyEmailPage()
      case VerifyPhoneStage =>
        onShowPhoneCodePage()
      case AddHandleStage =>
        onShowSetUsername()
      case InsertPasswordStage =>
        onShowInsertPassword()
      case TeamSetPicture =>
        appEntryController.setPicture(unsplashInitImageAsset, SignUpPhotoFragment.Source.Auto, SignUpPhotoFragment.RegistrationType.Email)
      case AddEmailStage =>
        onShowAddEmail()
      case _ =>
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

  override protected def onPostResume(): Unit = {
    super.onPostResume()
    isPaused = false
  }

  override protected def onPause(): Unit = {
    isPaused = true
    super.onPause()
  }

  override def onStop(): Unit = {
    if (unsplashInitLoadHandle != null) {
      unsplashInitLoadHandle.cancel()
      unsplashInitLoadHandle = null
    }
    super.onStop()
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    info(s"OnActivity result: $requestCode, $resultCode")
    super.onActivityResult(requestCode, resultCode, data)
    getSupportFragmentManager.findFragmentById(R.id.fl_main_content).onActivityResult(requestCode, resultCode, data)
  }

  def enableProgress(enabled: Boolean): Unit = {
    if (enabled)
      progressView.show(LoadingIndicatorView.SpinnerWithDimmedBackground, darkTheme = true)
    else
      progressView.hide()
  }

  def abortAddAccount(): Unit = {
    enableProgress(true)
    ZMessaging.currentAccounts.loggedInAccounts.head.map { accounts =>
      accounts.headOption.fold {
        if (appEntryController.entryStage.currentValue.exists(_ != NoAccountState(FirstScreen))) {
          appEntryController.gotToFirstPage()
        } else {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            finish()
          else
            finishAfterTransition()
        }
      } { acc =>
        ZMessaging.currentAccounts.switchAccount(acc.id).map { _ =>
          onEnterApplication(openSettings = true)
        }
      }
    }
  }

  def onOpenUrl(url: String): Unit =
    try {
      val prefixedUrl =
        if (!url.startsWith(AppEntryActivity.HTTP_PREFIX) && !url.startsWith(AppEntryActivity.HTTPS_PREFIX))
          AppEntryActivity.HTTP_PREFIX + url
        else
          url
      startActivity(returning(new Intent(Intent.ACTION_VIEW, Uri.parse(prefixedUrl)))(_.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
    }
    catch {
      case _: Exception => error(s"Failed to open URL: $url")
    }

  def onShowCreateTeamFragment(): Unit =
    withFragmentOpt(CreateTeamFragment.TAG) {
      case Some(_) =>
      case None => showFragment(CreateTeamFragment.newInstance, CreateTeamFragment.TAG)
    }


  def onShowPhoneCodePage(): Unit =
    if (!isPaused) {
      showFragment(VerifyPhoneFragment.newInstance(false), VerifyPhoneFragment.TAG)
    }

  def onShowPhoneVerifyEmailPage(): Unit =
    showFragment(VerifyPhoneFragment.newInstance(false), VerifyPhoneFragment.TAG)

  def onShowEmailVerifyEmailPage(): Unit =
    showFragment(EmailVerifyEmailFragment.newInstance, EmailVerifyEmailFragment.TAG)

  def onShowSignInPage(): Unit =
    withFragmentOpt(SignInFragment.Tag) {
      case Some(_) =>
      case None =>
        showFragment(new SignInFragment, SignInFragment.Tag)
    }

  def onShowSetPicturePage(): Unit =
    withFragmentOpt(SignUpPhotoFragment.TAG) {
      case Some(_) => //
      case None =>
        ZMessaging.currentAccounts.getActiveAccount.map { accountData =>
          import SignUpPhotoFragment._
          val tpe = accountData match {
            case Some(acc) if acc.phone.isDefined && acc.email.isEmpty => RegistrationType.Phone
            case _ => RegistrationType.Email
          }
          showFragment(newInstance(tpe), TAG)
        }
    }

  def onShowEmailPhoneCodePage(): Unit =
    showFragment(VerifyPhoneFragment.newInstance(true), VerifyPhoneFragment.TAG)

  def onShowFirstLaunchPage(): Unit =
    showFragment(FirstLaunchAfterLoginFragment.newInstance, FirstLaunchAfterLoginFragment.TAG)

  def onShowPhoneNamePage(): Unit = {
    showFragment(PhoneSetNameFragment.newInstance, PhoneSetNameFragment.TAG)
  }

  def onEnterApplication(openSettings: Boolean): Unit = {
    getControllerFactory.getVerificationController.finishVerification()
    startActivity(Intents.EnterAppIntent(openSettings)(this))
    finish()
  }

  private def setDefaultAnimation(transaction: FragmentTransaction): FragmentTransaction = {
    transaction.setCustomAnimations(R.anim.new_reg_in, R.anim.new_reg_out)
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

  def getUnsplashImageAsset: ImageAsset = unsplashInitImageAsset

  def showError(entryError: EntryError, okCallback: => Unit = {}): Unit =
    ViewUtils.showAlertDialog(this,
      entryError.headerResource,
      entryError.bodyResource,
      R.string.reg__phone_alert__button,
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          dialog.dismiss()
          okCallback
        }
      },
      false)

  def onShowSetUsername(): Unit =
    showFragment(FirstTimeAssignUsernameFragment.newInstance("", ""), FirstTimeAssignUsernameFragment.TAG)

  def onShowInsertPassword(): Unit =
    showFragment(InsertPasswordFragment.newInstance(), InsertPasswordFragment.Tag)

  def onShowAddEmail(): Unit =
    showFragment(AddEmailFragment(), AddEmailFragment.Tag)

  def showFragment(f: => Fragment, tag: String): Unit = {
    setDefaultAnimation(getSupportFragmentManager.beginTransaction)
      .replace(R.id.fl_main_content, f, tag)
      .commit
    enableProgress(false)
  }

  override def onChooseUsernameChosen() = {
    ZMessaging.currentAccounts.getActiveAccount.map(_.flatMap(_.handle.map(_.string)).getOrElse("")).map { userName =>
      getSupportFragmentManager.beginTransaction
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        .add(ChangeHandleFragment.newInstance(userName, cancellable = false), ChangeHandleFragment.FragmentTag)
        .addToBackStack(ChangeHandleFragment.FragmentTag)
        .commit
    }
  }

  override def onKeepUsernameChosen(username: String) =
    appEntryController.setUsername(username).map {
      case Left(_) =>
        Toast.makeText(AppEntryActivity.this, getString(R.string.username__set__toast_error), Toast.LENGTH_SHORT).show()
      case Right(_) =>
    }
}
