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
package com.waz.zclient

import android.content.Intent._
import android.content.res.Configuration
import android.content.{DialogInterface, Intent}
import android.graphics.drawable.ColorDrawable
import android.graphics.{Color, Paint, PixelFormat}
import android.net.Uri
import android.os.{Build, Bundle}
import android.support.v4.app.Fragment
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, info, verbose}
import com.waz.api.{NetworkMode, _}
import com.waz.model.{AccountId, ConvId, ConversationData}
import com.waz.service.ZMessaging
import com.waz.service.ZMessaging.clock
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.{RichInstant, returning}
import com.waz.zclient.Intents._
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.appentry.controllers.AppEntryController.{DeviceLimitStage, EnterAppStage, Unknown}
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallPermissionsController
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.controllers.{SharingController, UserAccountsController}
import com.waz.zclient.controllers.calling.CallingObserver
import com.waz.zclient.controllers.navigation.{NavigationControllerObserver, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.fragments.ConnectivityFragment
import com.waz.zclient.pages.main.MainPhoneFragment
import com.waz.zclient.pages.startup.UpdateFragment
import com.waz.zclient.preferences.{PreferencesActivity, PreferencesController}
import com.waz.zclient.tracking.{CrashController, UiTrackingController}
import com.waz.zclient.utils.PhoneUtils.PhoneState
import com.waz.zclient.utils.StringUtils.TextDrawing
import com.waz.zclient.utils.{BuildConfigUtils, ContextUtils, Emojis, IntentUtils, PhoneUtils, ViewUtils}
import com.waz.zclient.views.LoadingIndicatorView
import net.hockeyapp.android.NativeCrashManager

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class MainActivity extends BaseActivity
  with ActivityHelper
  with MainPhoneFragment.Container
  with UpdateFragment.Container
  with NavigationControllerObserver
  with CallingObserver
  with OtrDeviceLimitFragment.Container {

  import Threading.Implicits.Background

  lazy val zms                      = inject[Signal[ZMessaging]]
  lazy val sharingController        = inject[SharingController]
  lazy val accentColorController    = inject[AccentColorController]
  lazy val callPermissionController = inject[CallPermissionsController]
  lazy val conversationController   = inject[ConversationController]
  lazy val userAccountsController   = inject[UserAccountsController]
  lazy val appEntryController       = inject[AppEntryController]
  lazy val spinnerController        = inject[SpinnerController]

  override def onAttachedToWindow() = {
    super.onAttachedToWindow()
    getWindow.setFormat(PixelFormat.RGBA_8888)
  }

  override def onCreate(savedInstanceState: Bundle) = {
    info("onCreate")

    Option(getActionBar).foreach(_.hide())
    super.onCreate(savedInstanceState)

    //Prevent drawing the default background to reduce overdraw
    getWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT))
    setContentView(R.layout.main)

    ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)

    val fragmentManager = getSupportFragmentManager
    initializeControllers()

    if (savedInstanceState == null) {
      val fragmentTransaction = fragmentManager.beginTransaction
      fragmentTransaction.add(R.id.fl__offline__container, ConnectivityFragment(), ConnectivityFragment.FragmentTag)
      fragmentTransaction.commit
    } else getControllerFactory.getNavigationController.onActivityCreated(savedInstanceState)

    if (BuildConfigUtils.isHockeyUpdateEnabled && !BuildConfigUtils.isLocalBuild(this))
      CrashController.checkForUpdates(this)

    accentColorController.accentColor.map(_.getColor) { color =>
      getControllerFactory.getUserPreferencesController.setLastAccentColor(color)
      getControllerFactory.getAccentColorController.setColor(color)
    }

    handleIntent(getIntent)

    val currentlyDarkTheme = themeController.darkThemeSet.currentValue.contains(true)

    themeController.darkThemeSet.onUi {
      case theme if theme != currentlyDarkTheme => restartActivity()
      case _ =>
    }

    appEntryController.entryStage.onUi {
      case EnterAppStage =>
        if (getSupportFragmentManager.findFragmentByTag(MainPhoneFragment.TAG) == null) replaceMainFragment(new MainPhoneFragment, MainPhoneFragment.TAG)
      case DeviceLimitStage => showUnableToRegisterOtrClientDialog()
      case Unknown =>
        error("Unknown state")
      case _ => openSignUpPage()
    }

    userAccountsController.accounts.map(_.isEmpty).onUi {
      case true =>
        info("onLogout")
        getStoreFactory.reset()
        getControllerFactory.getPickUserController.hideUserProfile()
        getControllerFactory.getNavigationController.resetPagerPositionToDefault()
        finish()
        startActivity(returning(new Intent(this, classOf[MainActivity]))(_.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)))
      case _ =>
    }

    ZMessaging.currentGlobal.blacklist.upToDate.head.map {
      case false =>
        startActivity(new Intent(getApplicationContext, classOf[ForceUpdateActivity]))
        finish()
      case _ => //
    } (Threading.Ui)

    val loadingIndicator = findViewById[LoadingIndicatorView](R.id.progress_spinner)

    (for {
      darkTheme <- themeController.darkThemeSet
      show <- spinnerController.spinnerShowing
    }  yield (show, darkTheme)).onUi{
      case (Some(animation), theme) => loadingIndicator.show(animation, theme, 300)
      case (None, _) => loadingIndicator.hide()
    }
  }

  override protected def onResumeFragments() = {
    info("onResumeFragments")
    super.onResumeFragments()
  }

  override def onStart() = {
    info("onStart")
    getControllerFactory.getNavigationController.addNavigationControllerObserver(this)
    getControllerFactory.getCallingController.addCallingObserver(this)

    super.onStart()
    //This is needed to drag the user back to the calling activity if they open the app again during a call
    CallingActivity.startIfCallIsActive(this)

    if (!getControllerFactory.getUserPreferencesController.hasCheckedForUnsupportedEmojis(Emojis.VERSION))
      Future(checkForUnsupportedEmojis())(Threading.Background)
  }

  override protected def onResume() = {
    info("onResume")
    super.onResume()

    Option(ZMessaging.currentGlobal).foreach(_.googleApi.checkGooglePlayServicesAvailable(this))

    if (inject[PreferencesController].isAnalyticsEnabled)
      CrashController.checkForCrashes(getApplicationContext, getControllerFactory.getUserPreferencesController.getDeviceId)
    else {
      CrashController.deleteCrashReports(getApplicationContext)
      NativeCrashManager.deleteDumpFiles(getApplicationContext)
    }
  }

  override protected def onPause() = {
    info("onPause")
    super.onPause()
  }

  override protected def onSaveInstanceState(outState: Bundle) = {
    info("onSaveInstanceState")
    getControllerFactory.getNavigationController.onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  override def onStop() = {
    super.onStop()
    info("onStop")
    getControllerFactory.getCallingController.removeCallingObserver(this)
    getControllerFactory.getNavigationController.removeNavigationControllerObserver(this)
  }

  override def onBackPressed(): Unit = {
    val backPressedAlready = Seq(R.id.fl__calling__container, R.id.fl_main_content, R.id.fl_main_otr_warning)
      .map(getSupportFragmentManager.findFragmentById)
      .exists {
        case f: OnBackPressedListener => f.onBackPressed()
        case _ => false
      }

    if(!backPressedAlready) super.onBackPressed()
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    info(s"OnActivity requestCode: $requestCode, resultCode: $resultCode")
    super.onActivityResult(requestCode, resultCode, data)
    Option(ZMessaging.currentGlobal).foreach(_.googleApi.onActivityResult(requestCode, resultCode))
    Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_content)).foreach(_.onActivityResult(requestCode, resultCode, data))

    if (requestCode == PreferencesActivity.SwitchAccountCode && data != null) {
      Option(data.getStringExtra(PreferencesActivity.SwitchAccountExtra)).foreach { extraStr =>
        ZMessaging.currentAccounts.switchAccount(AccountId(extraStr))
      }
    }
  }

  override protected def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    verbose(s"onNewIntent: $intent")

    if (IntentUtils.isPasswordResetIntent(intent)) onPasswordWasReset()

    setIntent(intent)
    handleIntent(intent)
  }

  private def restartActivity() = {
    info("restartActivity")
    finish()
    startActivity(getIntent)
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
  }

  private def initializeControllers() = {
    //Ensure tracking is started
    inject[UiTrackingController]
    inject[KeyboardController]
    // Make sure we have a running OrientationController instance
    getControllerFactory.getOrientationController
    // Here comes code for adding other dependencies to controllers...
    getControllerFactory.getNavigationController.setIsLandscape(ContextUtils.isInLandscape(this))
  }

  private def onPasswordWasReset() =
    for {
      Some(am) <- ZMessaging.currentAccounts.getActiveAccountManager
      token    <- ZMessaging.currentAccounts.getActiveAccount.map(_.flatMap(_.accessToken))
      _        <- am.auth.checkLoggedIn(token)
    } yield {}

  def handleIntent(intent: Intent) = {
    verbose(s"handleIntent: ${intent.log}")

    def switchConversation(convId: ConvId, call: Boolean = false, exp: Option[EphemeralExpiration] = None) =
      CancellableFuture.delay(750.millis).map { _ =>
        verbose(s"setting conversation: $convId")
        conversationController.selectConv(convId, ConversationChangeRequester.INTENT).map { _ =>
          exp.foreach(conversationController.setEphemeralExpiration)
          if (call) conversationController.currentConv.head.map { conv => startCall(withVideo = false, conv) }
        }
    } (Threading.Ui).future

    def clearIntent() = {
      intent.clearExtras()
      setIntent(intent)
    }

    intent match {
      case NotificationIntent(accountId, convId, startCall) =>
        verbose(s"notification intent, accountId=$accountId, convId=$convId")
        val switchAccount = {
          val accounts = ZMessaging.currentAccounts
          accounts.activeAccount.head.flatMap {
            case Some(acc) if intent.accountId.contains(acc.id) => Future.successful(false)
            case _ => accounts.switchAccount(intent.accountId.get).map(_ => true)
          }
        }

        switchAccount.flatMap { _ =>
          (intent.convId match {
            case Some(id) => switchConversation(id, startCall)
            case _ =>        Future.successful({})
          }).map(_ => clearIntent())(Threading.Ui)
        }

        try {
          val t = clock.instant()
          if (Await.result(switchAccount, 2.seconds)) verbose(s"Account switched before resuming activity lifecycle. Took ${t.until(clock.instant()).toMillis} ms")
        } catch {
          case NonFatal(e) => error("Failed to switch accounts", e)
        }

      case SharingIntent() =>
        for {
          convs <- sharingController.targetConvs.head
          exp   <- sharingController.ephemeralExpiration.head
          _     <- sharingController.sendContent(this)
          _     <- if (convs.size == 1) switchConversation(convs.head, exp = Some(exp)) else Future.successful({})
        } yield clearIntent()

      case OpenPageIntent(page) => page match {
        case Intents.Page.Settings =>
          startActivityForResult(PreferencesActivity.getDefaultIntent(this), PreferencesActivity.SwitchAccountCode)
          clearIntent()
        case _ => error(s"Unknown page: $page - ignoring intent")
      }

      case _ => setIntent(intent)
    }
  }

  private def openSignUpPage() = {
    startActivity(new Intent(getApplicationContext, classOf[AppEntryActivity]))
    finish()
  }

  private def replaceMainFragment(fragment: Fragment, TAG: String) = {
    getSupportFragmentManager.beginTransaction.replace(R.id.fl_main_content, fragment, TAG).commit
  }

  def onPageVisible(page: Page) =
    getControllerFactory.getGlobalLayoutController.setSoftInputModeForPage(page)

  def onInviteRequestSent(conversation: String) = {
    info(s"onInviteRequestSent($conversation)")
    conversationController.selectConv(Option(new ConvId(conversation)), ConversationChangeRequester.INVITE)
  }

  def onOpenUrl(url: String) = {
    try {
      val normUrl = Uri.parse(if (!url.startsWith("http://") && !url.startsWith("https://")) s"http://$url" else url)
      val browserIntent = returning(new Intent(ACTION_VIEW, normUrl))(_.addFlags(FLAG_ACTIVITY_NEW_TASK))
      startActivity(browserIntent)
    }
    catch {
      case e: Exception => error(s"Failed to open URL: $url")
    }
  }

  private def showUnableToRegisterOtrClientDialog() =
    Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_otr_warning)) match {
      case Some(f) => //do nothing
      case None =>
        getSupportFragmentManager
          .beginTransaction
          .replace(R.id.fl_main_otr_warning, OtrDeviceLimitFragment.newInstance, OtrDeviceLimitFragment.Tag)
          .addToBackStack(OtrDeviceLimitFragment.Tag)
          .commitAllowingStateLoss
  }

  override def logout() = {
    getSupportFragmentManager.popBackStackImmediate
    ZMessaging.currentAccounts.getActiveAccountManager.flatMap {
      case Some(am) => am.logout(flushCredentials = true)
      case _ => Future.successful({})
    }
  }

  def manageDevices() = {
    getSupportFragmentManager.popBackStackImmediate
    startActivity(ShowDevicesIntent(this))
  }

  def dismissOtrDeviceLimitFragment() = getSupportFragmentManager.popBackStackImmediate

  def onStartCall(withVideo: Boolean) = conversationController.currentConv.head.map { conv =>
    handleOnStartCall(withVideo, conv)
  }

  private def handleOnStartCall(withVideo: Boolean, conversation: ConversationData) = {
    if (PhoneUtils.getPhoneState(this) ne PhoneState.IDLE) cannotStartGSM()
    else startCallIfInternet(withVideo, conversation)
  }

  private def startCall(withVideo: Boolean, c: ConversationData): Unit = {
    def call() = callPermissionController.startCall(c.id, withVideo)

    if (c.convType == ConversationData.ConversationType.Group) conversationController.loadMembers(c.id).foreach { members =>
      if (members.size > 5) ViewUtils.showAlertDialog(
        this,
        getString(R.string.group_calling_title),
        getString(R.string.group_calling_message, Integer.valueOf(members.size)),
        getString(R.string.group_calling_confirm),
        getString(R.string.group_calling_cancel),
        new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) = call()
        }, null
      ) else call()
    }(Threading.Ui)
    else call()
  }

  private def cannotStartGSM() =
    ViewUtils.showAlertDialog(
      this,
      R.string.calling__cannot_start__title,
      R.string.calling__cannot_start__message,
      R.string.calling__cannot_start__button,
      null,
      true)

  private def startCallIfInternet(withVideo: Boolean, conversation: ConversationData) = {
    zms.flatMap(_.network.networkMode).head.map {
      case NetworkMode.OFFLINE =>
        ViewUtils.showAlertDialog(
          this,
          R.string.alert_dialog__no_network__header,
          R.string.calling__call_drop__message,
          R.string.alert_dialog__confirmation,
          null, false)
      case NetworkMode._2G =>
        ViewUtils.showAlertDialog(
          this,
          R.string.calling__slow_connection__title,
          R.string.calling__slow_connection__message,
          R.string.calling__slow_connection__button,
          null, true)
      case NetworkMode.EDGE if withVideo =>
        ViewUtils.showAlertDialog(
          this,
          R.string.calling__slow_connection__title,
          R.string.calling__video_call__slow_connection__message,
          R.string.calling__slow_connection__button,
          new DialogInterface.OnClickListener() {
            def onClick(dialogInterface: DialogInterface, i: Int) = startCall(withVideo = true, conversation)
          }, true)
      case _ => startCall(withVideo, conversation)
    }(Threading.Ui)
  }

  private def checkForUnsupportedEmojis() =
    for {
      cf <- Option(getControllerFactory) if !cf.isTornDown
      prefs <- Option(cf.getUserPreferencesController)
    } {
      val paint = new Paint
      val template = returning(new TextDrawing)(_.set("\uFFFF")) // missing char
      val check = new TextDrawing

      val missing = Emojis.getAllEmojisSortedByCategory.asScala.flatten.filter { emoji =>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
          !paint.hasGlyph(emoji)
        else {
          check.set(emoji)
          template == check
        }
      }

      if (missing.nonEmpty) prefs.setUnsupportedEmoji(missing.asJava, Emojis.VERSION)
    }
}

