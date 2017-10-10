/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient

import android.content.res.Configuration
import android.content.{DialogInterface, Intent}
import android.graphics.Bitmap
import android.net.Uri
import android.os.{Build, Bundle}
import android.support.v4.app.FragmentTransaction
import android.widget.Toast
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.api._
import com.waz.model.Handle
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.AppEntryController._
import com.waz.zclient.appentry._
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.fragments.CountryDialogFragment
import com.waz.zclient.newreg.fragments.SignUpPhotoFragment
import com.waz.zclient.newreg.fragments.SignUpPhotoFragment.UNSPLASH_API_URL
import com.waz.zclient.newreg.fragments.country.CountryController
import com.waz.zclient.preferences.PreferencesController
import com.waz.zclient.preferences.dialogs.ChangeHandleFragment
import com.waz.zclient.tracking.{CrashController, GlobalTrackingController}
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
  with InAppWebViewFragment.Container
  with CountryDialogFragment.Container
  with FirstLaunchAfterLoginFragment.Container
  with SignInFragment.Container
  with FirstTimeAssignUsernameFragment.Container
  with InsertPasswordFragment.Container {

  private lazy val unsplashInitImageAsset = ImageAssetFactory.getImageAsset(AndroidURIUtil.parse(UNSPLASH_API_URL))
  private var unsplashInitLoadHandle: LoadHandle = null
  private lazy val progressView = ViewUtils.getView(this, R.id.liv__progress).asInstanceOf[LoadingIndicatorView]
  private lazy val countryController: CountryController = new CountryController(this)
  private var createdFromSavedInstance: Boolean = false
  private var isPaused: Boolean = false

  private lazy val appEntryController = inject[AppEntryController]

  ZMessaging.currentGlobal.blacklist.upToDate.onUi {
    case false =>
      startActivity(new Intent(this, classOf[ForceUpdateActivity]))
      finish()
    case _ =>
  }

  override def onBackPressed(): Unit = {
    getSupportFragmentManager.getFragments.asScala.foreach {
      case fragment: InAppWebViewFragment =>
        getSupportFragmentManager.popBackStackImmediate
        return
      case fragment: OnBackPressedListener if fragment.onBackPressed() =>
        return
      case _ =>
    }
    abortAddAccount()
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    if (getActionBar != null) getActionBar.hide()
    super.onCreate(savedInstanceState)
    ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)
    setContentView(R.layout.activity_signup)
    enableProgress(false)
    createdFromSavedInstance = savedInstanceState != null

    unsplashInitLoadHandle = unsplashInitImageAsset.getSingleBitmap(AppEntryActivity.PREFETCH_IMAGE_WIDTH, new BitmapCallback() {
      def onBitmapLoaded(b: Bitmap): Unit = {}
    })

    appEntryController.entryStage.onUi {
      case EnterAppStage =>
        onEnterApplication(false)
      case FirstEnterAppStage =>
        onShowFirstLaunchPage()
      case LoginStage =>
        onShowSignInPage()
      case DeviceLimitStage =>
        onEnterApplication(false)
      case AddNameStage =>
        onShowPhoneNamePage()
      case AddPictureStage =>
        onShowEmailSetPicturePage()
      case VerifyEmailStage =>
        onShowEmailVerifyEmailPage()
      case VerifyPhoneStage =>
        onShowPhoneCodePage()
      case AddHandleStage =>
        onShowSetUsername()
      case InsertPasswordStage =>
        onShowInsertPassword()
      case _ =>
    }
  }

  override protected def onResume(): Unit = {
    super.onResume()
    //TODO move hockey crash reporting to globaltracking controller
    val trackingEnabled: Boolean = injectJava(classOf[PreferencesController]).isAnalyticsEnabled
    if (trackingEnabled) {
      CrashController.checkForCrashes(getApplicationContext, getControllerFactory.getUserPreferencesController.getDeviceId, inject[GlobalTrackingController])
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
    ZLog.info(s"OnActivity result: $requestCode, $resultCode")
    super.onActivityResult(requestCode, resultCode, data)
    getSupportFragmentManager.findFragmentById(R.id.fl_main_content).onActivityResult(requestCode, resultCode, data)
  }

  def enableProgress(enabled: Boolean): Unit = {
    if (enabled)
      progressView.show(LoadingIndicatorView.SPINNER_WITH_DIMMED_BACKGROUND, true)
    else
      progressView.hide()
  }

  def abortAddAccount(): Unit = {
    implicit val ec = Threading.Ui
    enableProgress(true)
    ZMessaging.currentAccounts.loggedInAccounts.head.map { accounts =>
      accounts.headOption.fold {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
          finish()
        else
          finishAfterTransition()
      } { acc =>
        ZMessaging.currentAccounts.switchAccount(acc.id).map { _ =>
          onEnterApplication(true)
        }
      }
    }
  }

  def onOpenUrl(url: String): Unit = {
    try {
      val prefixedUrl =
        if (!url.startsWith(AppEntryActivity.HTTP_PREFIX) && !url.startsWith(AppEntryActivity.HTTPS_PREFIX))
          AppEntryActivity.HTTP_PREFIX + url
        else
          url
      val browserIntent: Intent = new Intent(Intent.ACTION_VIEW, Uri.parse(prefixedUrl))
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(browserIntent)
    }
    catch {
      case e: Exception => {
        ZLog.error(s"Failed to open URL: $url")
      }
    }
  }

  def onShowPhoneCodePage(): Unit = {
    if (isPaused) {
      return
    }
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, VerifyPhoneFragment.newInstance(false), VerifyPhoneFragment.TAG).commit
    enableProgress(false)
    getControllerFactory.getNavigationController.setLeftPage(Page.PHONE_VERIFY_CODE, AppEntryActivity.TAG)
  }

  def onShowPhoneVerifyEmailPage(): Unit = {
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, VerifyPhoneFragment.newInstance(false), VerifyPhoneFragment.TAG).commit
    enableProgress(false)
  }

  def onShowPhoneSetPicturePage(): Unit = {
    if (getSupportFragmentManager.findFragmentByTag(SignUpPhotoFragment.TAG) != null) {
      return
    }
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, SignUpPhotoFragment.newInstance(SignUpPhotoFragment.RegistrationType.Phone), SignUpPhotoFragment.TAG).commit
    enableProgress(false)
    getControllerFactory.getNavigationController.setLeftPage(Page.REGISTRATION_ADD_PHOTO, AppEntryActivity.TAG)
  }

  def onShowEmailVerifyEmailPage(): Unit = {
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, EmailVerifyEmailFragment.newInstance, EmailVerifyEmailFragment.TAG).commit
    enableProgress(false)
  }

  def onShowSignInPage(): Unit = {
    if (getSupportFragmentManager.findFragmentByTag(SignInFragment.Tag) != null) {
      return
    }

    enableProgress(false)

    if (fromGenericInvite) {
      val referralToken = getControllerFactory.getUserPreferencesController.getReferralToken
      val token = getControllerFactory.getUserPreferencesController.getGenericInvitationToken
      appEntryController.invitationToken ! Option(token)
    }

    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, new SignInFragment, SignInFragment.Tag).commit
    enableProgress(false)
    getControllerFactory.getNavigationController.setLeftPage(Page.LOGIN_REGISTRATION, AppEntryActivity.TAG)
  }

  def onShowEmailSetPicturePage(): Unit = {
    if (getSupportFragmentManager.findFragmentByTag(SignUpPhotoFragment.TAG) != null) {
      return
    }
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, SignUpPhotoFragment.newInstance(SignUpPhotoFragment.RegistrationType.Email), SignUpPhotoFragment.TAG).commit
    enableProgress(false)
  }

  def onShowEmailPhoneCodePage(): Unit = {
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, VerifyPhoneFragment.newInstance(true), VerifyPhoneFragment.TAG).commit
    enableProgress(false)
  }

  def onShowFirstLaunchPage(): Unit = {
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, FirstLaunchAfterLoginFragment.newInstance, FirstLaunchAfterLoginFragment.TAG).commit
    enableProgress(false)
  }

  def onShowPhoneNamePage(): Unit = {
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, PhoneSetNameFragment.newInstance, PhoneSetNameFragment.TAG).commit
    enableProgress(false)
    getControllerFactory.getNavigationController.setLeftPage(Page.PHONE_REGISTRATION_ADD_NAME, AppEntryActivity.TAG)
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

  def onOpenUrlInApp(url: String, withCloseButton: Boolean): Unit = {
    val prefixedUrl =
      if (!url.startsWith(AppEntryActivity.HTTP_PREFIX) && !url.startsWith(AppEntryActivity.HTTPS_PREFIX))
        AppEntryActivity.HTTP_PREFIX + url
      else
        url
    val transaction = getSupportFragmentManager.beginTransaction
    transaction.setCustomAnimations(R.anim.new_reg_in, R.anim.new_reg_out)
    transaction.add(R.id.fl_main_web_view, InAppWebViewFragment.newInstance(prefixedUrl, withCloseButton), InAppWebViewFragment.TAG)
    transaction.addToBackStack(InAppWebViewFragment.TAG)
    transaction.commit
    KeyboardUtils.hideKeyboard(this)
  }

  def dismissInAppWebView(): Unit = getSupportFragmentManager.popBackStackImmediate

  def getCountryController: CountryController = countryController

  def dismissCountryBox(): Unit = {
    getSupportFragmentManager.popBackStackImmediate
    KeyboardUtils.showKeyboard(this)
  }

  private def fromGenericInvite: Boolean = {
    val referralToken: String = getControllerFactory.getUserPreferencesController.getReferralToken
    val token: String = getControllerFactory.getUserPreferencesController.getGenericInvitationToken
    token != null || GenericInviteToken == referralToken
  }

  def getUnsplashImageAsset: ImageAsset = unsplashInitImageAsset

  def onPageVisible(page: Page): Unit = {
  }

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

  def onShowSetUsername(): Unit = {
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, FirstTimeAssignUsernameFragment.newInstance("", ""), FirstTimeAssignUsernameFragment.TAG).commit
    enableProgress(false)
  }

  def onShowInsertPassword(): Unit = {
    val transaction: FragmentTransaction = getSupportFragmentManager.beginTransaction
    setDefaultAnimation(transaction).replace(R.id.fl_main_content, InsertPasswordFragment.newInstance(), InsertPasswordFragment.Tag).commit
    enableProgress(false)
  }

  override def onChooseUsernameChosen() = {
    getSupportFragmentManager.beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(ChangeHandleFragment.newInstance(getStoreFactory.profileStore.getSelfUser.getUsername, cancellable = false), ChangeHandleFragment.FragmentTag)
      .addToBackStack(ChangeHandleFragment.FragmentTag)
      .commit
  }

  override def onKeepUsernameChosen(username: String) = {
    ZMessaging.currentUi.users.setSelfHandle(Handle(username), None).map {
      case Left(_) =>
        Toast.makeText(AppEntryActivity.this, getString(R.string.username__set__toast_error), Toast.LENGTH_SHORT).show()
        getControllerFactory.getUsernameController.logout()
        getControllerFactory.getUsernameController.setUser(getStoreFactory.zMessagingApiStore.getApi.getSelf)
      case Right(_) =>
    } (Threading.Ui)
  }
}
