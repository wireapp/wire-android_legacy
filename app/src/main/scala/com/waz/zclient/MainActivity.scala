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
import android.os.{Build, Bundle, Handler}
import android.support.v4.app.Fragment
import android.text.TextUtils
import com.localytics.android.Localytics
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, info, warn}
import com.waz.api.{NetworkMode, _}
import com.waz.model.ConvId
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallPermissionsController
import com.waz.zclient.controllers.accentcolor.AccentColorChangeRequester
import com.waz.zclient.controllers.calling.CallingObserver
import com.waz.zclient.controllers.global.{AccentColorController, SelectionController}
import com.waz.zclient.controllers.navigation.{NavigationControllerObserver, Page}
import com.waz.zclient.controllers.sharing.SharedContentType
import com.waz.zclient.controllers.tracking.events.connect.AcceptedGenericInviteEvent
import com.waz.zclient.controllers.tracking.events.exception.ExceptionEvent
import com.waz.zclient.controllers.tracking.events.profile.SignOut
import com.waz.zclient.controllers.tracking.screens.ApplicationScreen
import com.waz.zclient.controllers.{SharingController, UserAccountsController}
import com.waz.zclient.core.api.scala.AppEntryStore
import com.waz.zclient.core.controllers.tracking.attributes.OpenedMediaAction
import com.waz.zclient.core.controllers.tracking.events.media.OpenedMediaActionEvent
import com.waz.zclient.core.controllers.tracking.events.session.LoggedOutEvent
import com.waz.zclient.core.stores.api.ZMessagingApiStoreObserver
import com.waz.zclient.core.stores.connect.{ConnectStoreObserver, IConnectStore}
import com.waz.zclient.core.stores.conversation.{ConversationChangeRequester, ConversationStoreObserver}
import com.waz.zclient.core.stores.profile.ProfileStoreObserver
import com.waz.zclient.fragments.ConnectivityFragment
import com.waz.zclient.pages.main.grid.GridFragment
import com.waz.zclient.pages.main.{MainPhoneFragment, MainTabletFragment}
import com.waz.zclient.pages.startup.UpdateFragment
import com.waz.zclient.preferences.{PreferencesActivity, PreferencesController}
import com.waz.zclient.tracking.{GlobalTrackingController, UiTrackingController}
import com.waz.zclient.utils.PhoneUtils.PhoneState
import com.waz.zclient.utils.StringUtils.TextDrawing
import com.waz.zclient.utils.{BuildConfigUtils, Emojis, HockeyCrashReporting, IntentUtils, LayoutSpec, PhoneUtils, ViewUtils}
import net.hockeyapp.android.{ExceptionHandler, NativeCrashManager}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class MainActivity extends BaseActivity
  with ActivityHelper
  with MainPhoneFragment.Container
  with MainTabletFragment.Container
  with GridFragment.Container
  with UpdateFragment.Container
  with ProfileStoreObserver
  with ConnectStoreObserver
  with NavigationControllerObserver
  with CallingObserver
  with OtrDeviceLimitFragment.Container
  with ZMessagingApiStoreObserver
  with ConversationStoreObserver {

  import Threading.Implicits.Background

  lazy val zms                      = inject[Signal[ZMessaging]]
  lazy val sharingController        = inject[SharingController]
  lazy val accentColorController    = inject[AccentColorController]
  lazy val callPermissionController = inject[CallPermissionsController]
  lazy val selectionController      = inject[SelectionController]
  lazy val userAccountsController   = inject[UserAccountsController]

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
      if (BuildConfig.SHOW_GRIDOVERLAY) fragmentTransaction.add(R.id.fl_main_grid, GridFragment.newInstance, GridFragment.TAG)
      fragmentTransaction.commit
    } else getControllerFactory.getNavigationController.onActivityCreated(savedInstanceState)

    if (BuildConfigUtils.isHockeyUpdateEnabled && !BuildConfigUtils.isLocalBuild(this))
      HockeyCrashReporting.checkForUpdates(this)

    onLaunch()

    getControllerFactory.getLoadTimeLoggerController.appStart()

    accentColorController.accentColor.map(_.getColor)(getControllerFactory.getUserPreferencesController.setLastAccentColor)

    Option(getIntent).flatMap(i => Option(i.getExtras)).map(_.getBoolean(MainActivity.OpenSettingsArg, false)) match {
      case Some(true) =>
        startActivity(PreferencesActivity.getDefaultIntent(this))
      case _ =>
    }

    val currentlyDarkTheme = themeController.darkThemeSet.currentValue.contains(true)
    val currentAccount = ZMessaging.currentAccounts.activeAccountPref.signal.currentValue.flatten

    Signal(themeController.darkThemeSet, ZMessaging.currentAccounts.activeAccountPref.signal).onUi {
      case (theme, acc) if acc != currentAccount || theme != currentlyDarkTheme => restartActivity()
      case _ =>
    }

  }

  override protected def onResumeFragments() = {
    info("onResumeFragments")
    super.onResumeFragments()
    getStoreFactory.zMessagingApiStore.addApiObserver(this)
  }

  override def onStart() = {
    info("onStart")

    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    getStoreFactory.profileStore.addProfileStoreObserver(this)
    getStoreFactory.connectStore.addConnectRequestObserver(this)
    getControllerFactory.getNavigationController.addNavigationControllerObserver(this)
    getControllerFactory.getCallingController.addCallingObserver(this)
    getStoreFactory.conversationStore.addConversationStoreObserver(this)
    handleInvite()
    handleReferral()

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
      HockeyCrashReporting.checkForCrashes(getApplicationContext, getControllerFactory.getUserPreferencesController.getDeviceId, globalTracking)
    else {
      HockeyCrashReporting.deleteCrashReports(getApplicationContext)
      NativeCrashManager.deleteDumpFiles(getApplicationContext)
    }

    Localytics.setInAppMessageDisplayActivity(this)
    Localytics.handleTestMode(getIntent)
  }

  override protected def onPause() = {
    info("onPause")
    Localytics.dismissCurrentInAppMessage()
    Localytics.clearInAppMessageDisplayActivity()
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
    getStoreFactory.conversationStore.removeConversationStoreObserver(this)
    getControllerFactory.getCallingController.removeCallingObserver(this)
    getStoreFactory.zMessagingApiStore.removeApiObserver(this)
    getStoreFactory.connectStore.removeConnectRequestObserver(this)
    getStoreFactory.profileStore.removeProfileStoreObserver(this)
    getControllerFactory.getNavigationController.removeNavigationControllerObserver(this)
    getControllerFactory.getUserPreferencesController.setLastAccentColor(getStoreFactory.profileStore.getAccentColor)
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
    getSupportFragmentManager.findFragmentById(R.id.fl_main_content).onActivityResult(requestCode, resultCode, data)
  }

  override protected def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    if (IntentUtils.isPasswordResetIntent(intent)) onPasswordWasReset()

    onLaunch()

    setIntent(intent)

    Option(intent.getStringExtra(LaunchActivity.APP_PAGE)).filter(_.nonEmpty).foreach { page =>
      setIntent(IntentUtils.resetAppPage(intent))
      import IntentUtils._
      page match {
        case LOCALYTICS_DEEPLINK_SEARCH |
             LOCALYTICS_DEEPLINK_PROFILE => restartAppWithPage(page)
        case LOCALYTICS_DEEPLINK_SETTINGS => startActivity(PreferencesActivity.getDefaultIntent(this))
      }
    }
  }

  private def restartActivity() = {
    info("restartActivity")
    finish()
    startActivity(IntentUtils.getAppLaunchIntent(this))
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
  }

  private def onLaunch() = {
    globalTracking.appLaunched(getIntent)
    Option(getControllerFactory.getUserPreferencesController.getCrashException).foreach { crash =>
      globalTracking.tagEvent(ExceptionEvent.exception(crash, getControllerFactory.getUserPreferencesController.getCrashDetails))
    }
  }

  private def restartAppWithPage(page: String) = {
    startActivity(returning(new Intent(this, classOf[MainActivity]))(_.putExtra(LaunchActivity.APP_PAGE, page)))
    finish()
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
    // step 2 - no one is logged in
    ZMessaging.currentAccounts.activeAccountPref().map {
      case Some(account) =>
      case None => {
        // finally - no one is logged in
        error("No user is logged in")
        openSignUpPage()
      }
    }(Threading.Ui)

    import com.waz.api.ClientRegistrationState._
    self.getClientRegistrationState match {
      case PASSWORD_MISSING =>
        if (!TextUtils.isEmpty(self.getEmail)) {
          startActivity(new Intent(this, classOf[OTRSignInActivity]))
          finish()
          return
        }
        else getStoreFactory.zMessagingApiStore.getApi.logout()
      case LIMIT_REACHED =>
        showUnableToRegisterOtrClientDialog()
      case _ =>
    }
    onUserLoggedInAndVerified(self)
  }

  private def onPasswordWasReset() = {
    getStoreFactory.zMessagingApiStore.getApi.logout()
    openSignUpPage()
  }

  private def onUserLoggedInAndVerified(self: Self) = {
    getStoreFactory.profileStore.setUser(self)
    getControllerFactory.getAccentColorController.setColor(AccentColorChangeRequester.LOGIN, self.getAccent.getColor)
    getControllerFactory.getUsernameController.setUser(self)

    getIntent match {
      case intent if IntentUtils.isLaunchFromNotificationIntent(intent) =>
        val startCallNotificationIntent = IntentUtils.isStartCallNotificationIntent(intent)
        info(s"Start from notification with call=$startCallNotificationIntent")

        Option(getStoreFactory.conversationStore.getConversation(IntentUtils.getLaunchConversationId(intent))).foreach { conversation =>
          // Only want to swipe over when app has loaded
          new Handler().postDelayed(new Runnable() {
            def run() = {
              getStoreFactory.conversationStore.setCurrentConversation(Option(conversation), ConversationChangeRequester.NOTIFICATION)
              if (startCallNotificationIntent) startCall(false)
            }
          }, MainActivity.LAUNCH_CONVERSATION_CHANGE_DELAY)
        }
        IntentUtils.clearLaunchIntentExtra(intent)
        setIntent(intent)

      case intent if IntentUtils.isLaunchFromSharingIntent(intent) =>
        val sharedText = IntentUtils.getLaunchConversationSharedText(intent)
        val sharedFileUris = IntentUtils.getLaunchConversationSharedFiles(intent)
        val expiration = IntentUtils.getEphemeralExpiration(intent)

        Option(IntentUtils.getLaunchConversationIds(intent)).map(_.asScala.filter(_ != null).toSeq).foreach {
          case convId +: Nil =>
            val conv = getStoreFactory.conversationStore.getConversation(convId)

            if (!TextUtils.isEmpty(sharedText)) getControllerFactory.getSharingController.setSharedContentType(SharedContentType.TEXT)
            else getControllerFactory.getSharingController.setSharedContentType(SharedContentType.FILE)

            getControllerFactory.getSharingController.setSharedText(sharedText)
            getControllerFactory.getSharingController.setSharedUris(sharedFileUris)
            getControllerFactory.getSharingController.setSharingConversationId(convId)

            Option(conv).foreach(_.setEphemeralExpiration(expiration))

            sharingController.sendContent(sharedText, sharedFileUris, Seq(convId).asJava, expiration, this)

            // Only want to swipe over when app has loaded
            new Handler().postDelayed(new Runnable() {
              def run() = {
                getStoreFactory.conversationStore.setCurrentConversation(Option(conv), ConversationChangeRequester.SHARING)
              }
            }, MainActivity.LAUNCH_CONVERSATION_CHANGE_DELAY)

          case convs => sharingController.sendContent(sharedText, sharedFileUris, convs.asJava, expiration, this)
        }

        IntentUtils.clearLaunchIntentExtra(intent)
        setIntent(intent)

      case intent => setIntent(intent)
    }
    openMainPage()
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

  private def handleInvite(): Unit = {
    val token = getControllerFactory.getUserPreferencesController.getGenericInvitationToken
    getControllerFactory.getUserPreferencesController.setGenericInvitationToken(null)
    if (TextUtils.isEmpty(token) || TextUtils.equals(token, AppEntryStore.GENERAL_GENERIC_INVITE_TOKEN)) return
    getStoreFactory.connectStore.requestConnection(token)
    globalTracking.tagEvent(new AcceptedGenericInviteEvent)
  }

  private def handleReferral(): Unit = {
    val referralToken = getControllerFactory.getUserPreferencesController.getReferralToken
    getControllerFactory.getUserPreferencesController.setReferralToken(null)
    if (TextUtils.isEmpty(referralToken) || TextUtils.equals(referralToken, AppEntryStore.GENERAL_GENERIC_INVITE_TOKEN)) return
    getStoreFactory.connectStore.requestConnection(referralToken)
    globalTracking.tagEvent(new AcceptedGenericInviteEvent)
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

  def onMyNameHasChanged(sender: Any, myName: String) = ()

  def onMyEmailHasChanged(myEmail: String, isVerified: Boolean) = ()

  def onMyPhoneHasChanged(myPhone: String, isVerified: Boolean) = ()

  def onPhoneUpdateFailed(myPhone: String, errorCode: Int, message: String, label: String) = ()

  def onMyEmailAndPasswordHasChanged(myEmail: String) = ()

  //TODO this is all tracking - make a page controller and set a signal the global tracking controller can listen to
  def onPageVisible(page: Page) = {
    getControllerFactory.getGlobalLayoutController.setSoftInputModeForPage(page)
    getControllerFactory.getNavigationController.setPagerSettingForPage(page)
    import Page._

    page match {
      case CONVERSATION_LIST =>
        globalTracking.onApplicationScreen(ApplicationScreen.CONVERSATION_LIST)
      case MESSAGE_STREAM =>
        (for {
          zms <- zms.head
          convId <- selectionController.selectedConv.head
          Some(conv) <- zms.convsStorage.get(convId)
          withOtto <- GlobalTrackingController.isOtto(conv, zms.usersStorage)
        } yield if (withOtto) ApplicationScreen.CONVERSATION__BOT else ApplicationScreen.CONVERSATION ).map(globalTracking.onApplicationScreen(_))

      case CAMERA =>
        globalTracking.onApplicationScreen(ApplicationScreen.CAMERA)
      case DRAWING =>
        globalTracking.onApplicationScreen(ApplicationScreen.DRAW_SKETCH)
      case CONNECT_REQUEST_INBOX =>
        globalTracking.onApplicationScreen(ApplicationScreen.CONVERSATION__INBOX)
      case PENDING_CONNECT_REQUEST_AS_CONVERSATION =>
        globalTracking.onApplicationScreen(ApplicationScreen.CONVERSATION__PENDING)
      case PARTICIPANT =>
        globalTracking.onApplicationScreen(ApplicationScreen.CONVERSATION__PARTICIPANTS)
      case PICK_USER_ADD_TO_CONVERSATION =>
        globalTracking.onApplicationScreen(ApplicationScreen.START_UI__ADD_TO_CONVERSATION)
      case PICK_USER =>
        globalTracking.onApplicationScreen(ApplicationScreen.START_UI)
      case _ => warn(s"Unknown page: $page")
    }
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
    // TODO: Remove old SignOut event AN-4232
    globalTracking.tagEvent(new SignOut)
    globalTracking.tagEvent(new LoggedOutEvent)
    getStoreFactory.zMessagingApiStore.logout()
    getControllerFactory.getUsernameController.logout()
  }

  def manageDevices() = {
    getSupportFragmentManager.popBackStackImmediate
    startActivity(PreferencesActivity.getOtrDevicesPreferencesIntent(this))
  }

  def dismissOtrDeviceLimitFragment() = getSupportFragmentManager.popBackStackImmediate

  def onInitialized(self: Self) = enterApplication(self)

  def onStartCall(withVideo: Boolean) = {
    handleOnStartCall(withVideo)
    getStoreFactory.conversationStore.currentConversation.foreach { conv =>
      globalTracking.tagEvent(OpenedMediaActionEvent.cursorAction(if (withVideo) OpenedMediaAction.VIDEO_CALL else OpenedMediaAction.AUDIO_CALL, conv))
    }
  }

  private def handleOnStartCall(withVideo: Boolean) = {
    if (PhoneUtils.getPhoneState(this) ne PhoneState.IDLE) cannotStartGSM()
    else startCallIfInternet(withVideo)
  }

  private def startCall(withVideo: Boolean) = getStoreFactory.conversationStore.currentConversation.foreach {
    case c if c.getType == IConversation.Type.GROUP && c.getUsers.size() >= 5 =>
      ViewUtils.showAlertDialog(
        this,
        getString(R.string.group_calling_title),
        getString(R.string.group_calling_message, Integer.valueOf(c.getUsers.size())),
        getString(R.string.group_calling_confirm),
        getString(R.string.group_calling_cancel),
        new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) = {
            callPermissionController.startCall(new ConvId(c.getId), withVideo, getControllerFactory.getUserPreferencesController.isVariableBitRateEnabled)
          }
      }, null)

    case c =>
      callPermissionController.startCall(new ConvId(c.getId), withVideo, getControllerFactory.getUserPreferencesController.isVariableBitRateEnabled)
  }

  private def cannotStartGSM() =
    ViewUtils.showAlertDialog(
      this,
      R.string.calling__cannot_start__title,
      R.string.calling__cannot_start__message,
      R.string.calling__cannot_start__button,
      null,
      true)

  private def startCallIfInternet(withVideo: Boolean) = {
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
            def onClick(dialogInterface: DialogInterface, i: Int) = startCall(true)
          }, true)
      case _ => startCall(withVideo)
    }(Threading.Ui)
  }

  def onConversationListUpdated(conversationsList: ConversationsList) = ()

  def onConversationListStateHasChanged(state: ConversationsList.ConversationsListState) = ()

  def onCurrentConversationHasChanged(fromConversation: IConversation, toConversation: IConversation, conversationChangerSender: ConversationChangeRequester) = ()

  def onConversationSyncingStateHasChanged(syncState: SyncState) = ()

  def onMenuConversationHasChanged(fromConversation: IConversation) = ()

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

  def onMyUsernameHasChanged(myUsername: String) = ()
}

object MainActivity {
  private val LAUNCH_CONVERSATION_CHANGE_DELAY: Int = 123
  val OpenSettingsArg = "OpenSettingsArg"
}
