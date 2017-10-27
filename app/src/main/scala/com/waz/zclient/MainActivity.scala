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

import android.content.Intent._
import android.content.res.Configuration
import android.content.{DialogInterface, Intent}
import android.graphics.drawable.ColorDrawable
import android.graphics.{Color, Paint, PixelFormat}
import android.net.Uri
import android.os.{Build, Bundle}
import android.support.v4.app.Fragment
import android.text.TextUtils
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, info, verbose}
import com.waz.api.{NetworkMode, _}
import com.waz.model.{AccountId, ConvId}
import com.waz.service.ZMessaging
import com.waz.service.ZMessaging.clock
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.{RichInstant, returning}
import com.waz.zclient.AppEntryController.{DeviceLimitStage, EnterAppStage, Unknown}
import com.waz.zclient.Intents._
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallPermissionsController
import com.waz.zclient.common.controllers.PermissionsController
import com.waz.zclient.controllers.accentcolor.AccentColorChangeRequester
import com.waz.zclient.controllers.calling.CallingObserver
import com.waz.zclient.controllers.global.{AccentColorController, SelectionController}
import com.waz.zclient.controllers.navigation.{NavigationControllerObserver, Page}
import com.waz.zclient.controllers.{SharingController, UserAccountsController}
import com.waz.zclient.core.stores.api.ZMessagingApiStoreObserver
import com.waz.zclient.core.stores.connect.{ConnectStoreObserver, IConnectStore}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.core.stores.profile.ProfileStoreObserver
import com.waz.zclient.fragments.ConnectivityFragment
import com.waz.zclient.pages.main.{MainPhoneFragment, MainTabletFragment}
import com.waz.zclient.pages.startup.UpdateFragment
import com.waz.zclient.preferences.{PreferencesActivity, PreferencesController}
import com.waz.zclient.tracking.{CrashController, GlobalTrackingController, LoggedOutEvent, UiTrackingController}
import com.waz.zclient.utils.PhoneUtils.PhoneState
import com.waz.zclient.utils.StringUtils.TextDrawing
import com.waz.zclient.utils.{BuildConfigUtils, Emojis, IntentUtils, LayoutSpec, PhoneUtils, ViewUtils}
import net.hockeyapp.android.{ExceptionHandler, NativeCrashManager}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class MainActivity extends BaseActivity
  with ActivityHelper
  with MainPhoneFragment.Container
  with MainTabletFragment.Container
  with UpdateFragment.Container
  with ProfileStoreObserver
  with ConnectStoreObserver
  with NavigationControllerObserver
  with CallingObserver
  with OtrDeviceLimitFragment.Container
  with ZMessagingApiStoreObserver {

  import Threading.Implicits.Background

  lazy val zms                      = inject[Signal[ZMessaging]]
  lazy val sharingController        = inject[SharingController]
  lazy val accentColorController    = inject[AccentColorController]
  lazy val callPermissionController = inject[CallPermissionsController]
  lazy val permissions              = inject[PermissionsController]
  lazy val selectionController      = inject[SelectionController]
  lazy val userAccountsController   = inject[UserAccountsController]
  lazy val appEntryController       = inject[AppEntryController]
  lazy val tracking                 = inject[GlobalTrackingController]

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

    if (LayoutSpec.isPhone(this)) ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)

    val fragmentManager = getSupportFragmentManager
    initializeControllers()

    if (savedInstanceState == null) {
      val fragmentTransaction = fragmentManager.beginTransaction
      fragmentTransaction.add(R.id.fl__offline__container, ConnectivityFragment(), ConnectivityFragment.FragmentTag)
      fragmentTransaction.commit
    } else getControllerFactory.getNavigationController.onActivityCreated(savedInstanceState)

    if (BuildConfigUtils.isHockeyUpdateEnabled && !BuildConfigUtils.isLocalBuild(this))
      CrashController.checkForUpdates(this)

    accentColorController.accentColor.map(_.getColor)(getControllerFactory.getUserPreferencesController.setLastAccentColor)

    handleIntent(getIntent)

    val currentlyDarkTheme = themeController.darkThemeSet.currentValue.contains(true)

    themeController.darkThemeSet.onUi {
      case theme if theme != currentlyDarkTheme => restartActivity()
      case _ =>
    }

    appEntryController.entryStage.onUi {
      case EnterAppStage => onUserLoggedInAndVerified(getStoreFactory.zMessagingApiStore.getApi.getSelf)
      case DeviceLimitStage => showUnableToRegisterOtrClientDialog()
      case Unknown =>
        error("Unknown state")
      case _ => openSignUpPage()
    }

    appEntryController.autoConnectInvite.onUi { token =>
      getControllerFactory.getUserPreferencesController.setGenericInvitationToken(null)
      getControllerFactory.getUserPreferencesController.setReferralToken(null)

      if (!TextUtils.isEmpty(token) && TextUtils.equals(token, AppEntryController.GenericInviteToken)){
        getStoreFactory.connectStore.requestConnection(token)
      }

      appEntryController.invitationToken ! None
    }
  }

  override protected def onResumeFragments() = {
    info("onResumeFragments")
    super.onResumeFragments()
    getStoreFactory.zMessagingApiStore.addApiObserver(this)
  }

  override def onStart() = {
    info("onStart")

    getStoreFactory.profileStore.addProfileStoreObserver(this)
    getStoreFactory.connectStore.addConnectRequestObserver(this)
    getControllerFactory.getNavigationController.addNavigationControllerObserver(this)
    getControllerFactory.getCallingController.addCallingObserver(this)

    super.onStart()
    //This is needed to drag the user back to the calling activity if they open the app again during a call
    CallingActivity.startIfCallIsActive(this)

    if (!getControllerFactory.getUserPreferencesController.hasCheckedForUnsupportedEmojis(Emojis.VERSION))
      Future(checkForUnsupportedEmojis())(Threading.Background)

    try
        if ("com.wire" == getApplicationContext.getPackageName) {
          Option(getStoreFactory.profileStore.getMyEmail).filter(e => e.endsWith("@wire.com") || e.endsWith("@wearezeta.com")).foreach { email =>
            ExceptionHandler.saveException(new RuntimeException(email), null, null)
            ViewUtils.showAlertDialog(this, "Yo dude!", "Please use Wire Internal", "I promise", null, false)
          }
        }
    catch {
      case t: Throwable => /*noop*/
    }
  }

  override protected def onResume() = {
    info("onResume")
    super.onResume()

    Option(ZMessaging.currentGlobal).foreach(_.googleApi.checkGooglePlayServicesAvailable(this))

    if (inject[PreferencesController].isAnalyticsEnabled)
      CrashController.checkForCrashes(getApplicationContext, getControllerFactory.getUserPreferencesController.getDeviceId, inject[GlobalTrackingController])
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
    getStoreFactory.zMessagingApiStore.removeApiObserver(this)
    getStoreFactory.connectStore.removeConnectRequestObserver(this)
    getStoreFactory.profileStore.removeProfileStoreObserver(this)
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
    // Make sure we have a running OrientationController instance
    getControllerFactory.getOrientationController
    // Here comes code for adding other dependencies to controllers...
    getControllerFactory.getNavigationController.setIsLandscape(ViewUtils.isInLandscape(this))
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////
  //
  //  Navigation
  //
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////
  private def enterApplication(self: Self): Unit = {
    error("Entering application")
    // step 1 - check if app was started via password reset intent
    if (IntentUtils.isPasswordResetIntent(getIntent)) {
      error("Password was reset")
      onPasswordWasReset()
      return
    }
  }

  private def onPasswordWasReset() = {
    tracking.onLoggedOut(LoggedOutEvent.ResetPassword)
    getStoreFactory.zMessagingApiStore.getApi.logout()
    openSignUpPage()
  }

  private def onUserLoggedInAndVerified(self: Self) = {
    verbose("onUserLoggedInAndVerified")
    getStoreFactory.profileStore.setUser(self)
    getControllerFactory.getAccentColorController.setColor(AccentColorChangeRequester.LOGIN, self.getAccent.getColor)
    getControllerFactory.getUsernameController.setUser(self)
    openMainPage()
  }

  def handleIntent(intent: Intent) = {
    verbose(s"handleIntent: ${intent.log}")

    def switchConversation(convId: ConvId, call: Boolean = false, exp: EphemeralExpiration = EphemeralExpiration.NONE) =
      CancellableFuture.delay(750.millis).map { _ =>
        verbose(s"setting conversation: $convId")
        val conv = getStoreFactory.conversationStore.getConversation(convId.str)
        conv.setEphemeralExpiration(exp)
        getStoreFactory.conversationStore.setCurrentConversation(conv, ConversationChangeRequester.INTENT)
        if (call) startCall(withVideo = false, Option(conv))
    } (Threading.Ui).future

    def clearIntent() = {
      intent.clearExtras()
      setIntent(intent)
    }

    intent match {
      case NotificationIntent(accountId, convId, startCall) =>
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
          _     <- if (convs.size == 1) switchConversation(convs.head, exp = exp) else Future.successful({})
        } yield clearIntent()

      case OpenPageIntent(page) => page match {
        case Intents.Page.Settings => startActivityForResult(PreferencesActivity.getDefaultIntent(this), PreferencesActivity.SwitchAccountCode)
        case _ => error(s"Unknown page: $page - ignoring intent")
      }

      case _ => setIntent(intent)
    }
  }

  /**
    * Depending on the orientation it opens either
    * MainPhoneFragment or MainTabletFragment. At the
    * beginning it checks if it is already setup properly.
    */
  private def openMainPage() = {
    if (LayoutSpec.isPhone(this)) {
      if (getSupportFragmentManager.findFragmentByTag(MainPhoneFragment.TAG) == null) replaceMainFragment(new MainPhoneFragment, MainPhoneFragment.TAG)
      info("No need to open main fragment")
    } else {
      if (getSupportFragmentManager.findFragmentByTag(MainTabletFragment.TAG) == null) replaceMainFragment(new MainTabletFragment, MainTabletFragment.TAG)
      info("No need to open main fragment")
    }
  }

  private def openSignUpPage() = {
    startActivity(new Intent(getApplicationContext, classOf[AppEntryActivity]))
    finish()
  }

  private def openForceUpdatePage() = {
    startActivity(new Intent(getApplicationContext, classOf[ForceUpdateActivity]))
    finish()
  }

  private def replaceMainFragment(fragment: Fragment, TAG: String) = {
    getSupportFragmentManager.beginTransaction.replace(R.id.fl_main_content, fragment, TAG).commit
  }

  def onLogout() = {
    userAccountsController.accounts.head.map{ accounts =>
      if (accounts.isEmpty) {
        info("onLogout")
        getStoreFactory.reset()
        getControllerFactory.getPickUserController.hideUserProfile()
        getStoreFactory.conversationStore.onLogout()
        getControllerFactory.getNavigationController.resetPagerPositionToDefault()
        val intent: Intent = new Intent(this, classOf[MainActivity])
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)
        finish()
        startActivity(intent)
      }
    } (Threading.Ui)
  }

  def onForceClientUpdate() = openForceUpdatePage()

  def onAccentColorChangedRemotely(sender: Any, color: Int) = getControllerFactory.getAccentColorController.setColor(AccentColorChangeRequester.REMOTE, color)

  def onPageVisible(page: Page) = {
    getControllerFactory.getGlobalLayoutController.setSoftInputModeForPage(page)
    getControllerFactory.getNavigationController.setPagerSettingForPage(page)
  }

  def onConnectUserUpdated(user: User, usertype: IConnectStore.UserRequester) = ()

  def onInviteRequestSent(conversation: IConversation) = getStoreFactory.conversationStore.setCurrentConversation(Some(conversation), ConversationChangeRequester.INVITE)

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
          .replace(R.id.fl_main_otr_warning, OtrDeviceLimitFragment.newInstance, OtrDeviceLimitFragment.TAG)
          .addToBackStack(OtrDeviceLimitFragment.TAG)
          .commitAllowingStateLoss
  }

  def logout() = {
    getSupportFragmentManager.popBackStackImmediate
    getStoreFactory.zMessagingApiStore.logout()
    getControllerFactory.getUsernameController.logout()
  }

  def manageDevices() = {
    getSupportFragmentManager.popBackStackImmediate
    startActivity(ShowDevicesIntent(this))
  }

  def dismissOtrDeviceLimitFragment() = getSupportFragmentManager.popBackStackImmediate

  def onInitialized(self: Self) = enterApplication(self)

  def onStartCall(withVideo: Boolean) = {
    val conversation = getStoreFactory.conversationStore.currentConversation
    handleOnStartCall(withVideo, conversation)
  }

  private def handleOnStartCall(withVideo: Boolean, conversation: Option[IConversation]) = {
    if (PhoneUtils.getPhoneState(this) ne PhoneState.IDLE) cannotStartGSM()
    else startCallIfInternet(withVideo, conversation)
  }

  private def startCall(withVideo: Boolean, conversation: Option[IConversation]): Unit = conversation.foreach {
    case c if c.getType == IConversation.Type.GROUP && c.getUsers.size() >= 5 =>
      ViewUtils.showAlertDialog(
        this,
        getString(R.string.group_calling_title),
        getString(R.string.group_calling_message, Integer.valueOf(c.getUsers.size())),
        getString(R.string.group_calling_confirm),
        getString(R.string.group_calling_cancel),
        new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) = {
            callPermissionController.startCall(new ConvId(c.getId), withVideo)
          }
      }, null)

    case c =>
      callPermissionController.startCall(new ConvId(c.getId), withVideo)
  }

  private def cannotStartGSM() =
    ViewUtils.showAlertDialog(
      this,
      R.string.calling__cannot_start__title,
      R.string.calling__cannot_start__message,
      R.string.calling__cannot_start__button,
      null,
      true)

  private def startCallIfInternet(withVideo: Boolean, conversation: Option[IConversation]) = {
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

