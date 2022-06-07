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

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.{Paint, PixelFormat}
import android.os.Bundle
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.waz.content.UserPreferences._
import com.waz.content.{TeamsStorage, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData.ConnectionStatus.{apply => _}
import com.waz.model._
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, PasswordMissing, Registered, Unregistered}
import com.waz.service.AccountsService.UserInitiated
import com.waz.service.ZMessaging.clock
import com.waz.service.{AccountManager, AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.{RichInstant, returning}
import com.waz.zclient.Intents.{RichIntent, _}
import com.waz.zclient.SpinnerController.{Hide, Show}
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController, PasswordController}
import com.waz.zclient.common.controllers.{BrowserController, FeatureConfigsController, SharingController, UserAccountsController}
import com.waz.zclient.common.fragments.ConnectivityFragment
import com.waz.zclient.controllers.navigation.{NavigationControllerObserver, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.deeplinks.DeepLink.{logTag => _, _}
import com.waz.zclient.deeplinks.DeepLinkService
import com.waz.zclient.deeplinks.DeepLinkService.Error.{InvalidToken, SSOLoginTooManyAccounts}
import com.waz.zclient.deeplinks.DeepLinkService._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.notifications.controllers.MessageNotificationsController
import com.waz.zclient.pages.main.MainPhoneFragment
import com.waz.zclient.pages.startup.UpdateFragment
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.preferences.dialogs.ChangeHandleFragment
import com.waz.zclient.tracking.{GlobalTrackingController, UiTrackingController}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.StringUtils.TextDrawing
import com.waz.zclient.utils.{Emojis, IntentUtils, ResString, ViewUtils}
import com.waz.zclient.views.LoadingIndicatorView
import com.wire.signals.Signal

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class MainActivity extends BaseActivity
  with CallingBannerActivity
  with UpdateFragment.Container
  with NavigationControllerObserver
  with OtrDeviceLimitFragment.Container
  with SetHandleFragment.Container
  with DerivedLogTag {

  private implicit val cxt: MainActivity = this

  import Threading.Implicits.Ui

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val account                = inject[Signal[Option[AccountManager]]]
  private lazy val accountsService        = inject[AccountsService]
  private lazy val sharingController      = inject[SharingController]
  private lazy val accentColorController  = inject[AccentColorController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val spinnerController      = inject[SpinnerController]
  private lazy val passwordController     = inject[PasswordController]
  private lazy val deepLinkService        = inject[DeepLinkService]
  private lazy val usersController        = inject[UsersController]
  private lazy val featureConfigsController = inject[FeatureConfigsController]

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    getWindow.setFormat(PixelFormat.RGBA_8888)
  }

  override def onCreate(savedInstanceState: Bundle) = {
    Option(getActionBar).foreach(_.hide())
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)

    val fragmentManager = getSupportFragmentManager
    initializeControllers()

    if (savedInstanceState == null) {
      val fragmentTransaction = fragmentManager.beginTransaction
      fragmentTransaction.add(R.id.fl__offline__container, ConnectivityFragment(), ConnectivityFragment.FragmentTag)
      fragmentTransaction.commit
    } else getControllerFactory.getNavigationController.onActivityCreated(savedInstanceState)

    handleIntent(getIntent)

    val currentlyDarkTheme = themeController.darkThemeSet.currentValue.contains(true)

    themeController.darkThemeSet.onUi {
      case theme if theme != currentlyDarkTheme =>
        info(l"restartActivity")
        recreate()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
      case _ =>
    }

    userAccountsController.mostRecentLoggedOutAccount.onUi {
      case Some((_, reason)) =>
        showLogoutWarningIfNeeded(reason).foreach(_ => userAccountsController.mostRecentLoggedOutAccount ! None)
      case None =>
    }

    userAccountsController.allAccountsLoggedOut.onUi {
      case true =>
        getControllerFactory.getPickUserController.hideUserProfile()
        getControllerFactory.getNavigationController.resetPagerPositionToDefault()
        startActivity(AppEntryActivity.newIntent(this))
        finishAffinity()
      case false =>
    }

    for {
      Some(self) <- userAccountsController.currentUser.head
      teamName <- self.teamId.fold(
        Future.successful(Option.empty[Name])
      )(teamId =>
        inject[TeamsStorage].get(teamId).map(_.map(_.name))
      )
      prefs <- userPreferences.head
      shouldWarn <- prefs(UserPreferences.ShouldWarnStatusNotifications).apply()
      avVisible <- usersController.availabilityVisible.head
      color <- accentColorController.accentColor.head
    } yield {
      (shouldWarn && avVisible, self.availability, teamName) match {
        case (true, Availability.Away, Some(name)) =>
          inject[MessageNotificationsController].showAppNotification(
            ResString(R.string.availability_notification_blocked_title, name.str),
            ResString(R.string.availability_notification_blocked)
          )
          showStatusNotificationWarning(self.availability, color).foreach { dontShowAgain =>
            if (dontShowAgain) prefs(UserPreferences.StatusNotificationsBitmask).mutate(_ | self.availability.bitmask)
          }
        case (true, Availability.Busy, Some(name)) =>
          inject[MessageNotificationsController].showAppNotification(
            ResString(R.string.availability_notification_changed_title, name.str),
            ResString(R.string.availability_notification_changed)
          )
          showStatusNotificationWarning(self.availability, color).foreach { dontShowAgain =>
            if (dontShowAgain) prefs(UserPreferences.StatusNotificationsBitmask).mutate(_ | self.availability.bitmask)
          }
        case _ =>
      }
      if (shouldWarn) prefs(UserPreferences.ShouldWarnStatusNotifications) := false
    }

    ForceUpdateActivity.checkBlacklist(this)
    ZMessaging.currentGlobal.backendConfiguration.foreach { _ =>
      ForceUpdateActivity.checkBlacklist(this)
    }

    val loadingIndicator = findViewById[LoadingIndicatorView](R.id.progress_spinner)

    spinnerController.spinnerShowing.onUi {
      case Show(animation, forcedIsDarkTheme) =>
        themeController.darkThemeSet.head.foreach(theme => loadingIndicator.show(animation, forcedIsDarkTheme.getOrElse(theme), 300))(Threading.Ui)
      case Hide(Some(message)) => loadingIndicator.hideWithMessage(message, 750)
      case Hide(_) => loadingIndicator.hide()
    }

    deepLinkService.deepLink.onUi {
      case None =>

      case Some(OpenDeepLink(SSOLoginToken(token), _)) =>
        verbose(l"open SSO token ${showString(token)}")
        openSignUpPage(Some(token))
        deepLinkService.deepLink ! None

      case Some(DoNotOpenDeepLink(SSOLogin, InvalidToken)) =>
        verbose(l"do not open, SSO token invalid")
        showErrorDialog(
          R.string.sso_signin_wrong_code_title,
          R.string.sso_signin_wrong_code_message)
          .map { _ => startFirstFragment() }
        deepLinkService.deepLink ! None

      case Some(DoNotOpenDeepLink(SSOLogin, SSOLoginTooManyAccounts)) =>
        verbose(l"do not open, SSO token, too many accounts")
        showErrorDialog(
          R.string.sso_signin_max_accounts_title,
          R.string.sso_signin_max_accounts_message)
          .map { _ => startFirstFragment() }
        deepLinkService.deepLink ! None

      case Some(DeepLinkUnknown) =>
        verbose(l"received unrecognized deep link")
        showErrorDialog(
          R.string.deep_link_generic_error_title,
          R.string.deep_link_generic_error_message)
          .map { _ => startFirstFragment() }
        deepLinkService.deepLink ! None

      case Some(_) =>
        verbose(l"the default path (no deep link, or a link handled later)")
        startFirstFragment()
    }

    userPreferences.flatMap(_.preference[Boolean](UserPreferences.ShouldWarnAVSUpgrade).signal).onUi { shouldWarn =>
      if (shouldWarn) {
        accentColorController.accentColor.head.foreach { accentColor =>
          showAVSUpgradeWarning(accentColor) { didConfirm =>
            if (didConfirm) inject[BrowserController].openPlayStoreListing()
            userPreferences.head.foreach { prefs =>
              prefs(UserPreferences.ShouldWarnAVSUpgrade) := false
            }
          }
        }
      }
    }

    userPreferences.flatMap(_.preference(UserPreferences.ShouldInformFileSharingRestriction).signal).onUi { shouldInform =>
      if (shouldInform) {
        userPreferences.head.flatMap(_(UserPreferences.FileSharingFeatureEnabled).apply()).foreach { isEnabled =>
          showFileSharingRestrictionInfoDialog(isEnabled, { _ =>
            userPreferences.head.foreach { prefs =>
              prefs(UserPreferences.ShouldInformFileSharingRestriction) := false
            }
          })
        }
      }
    }
    userPreferences.flatMap(_.preference(UserPreferences.ShouldInformSelfDeletingMessagesChanged).signal).onUi { shouldInform =>
      if (!shouldInform) {}
      else for {
        prefs                     <- userPreferences.head
        isFeatureEnabled          <- prefs.preference(AreSelfDeletingMessagesEnabled).apply()
        enforcedTimeoutInSeconds  <- prefs.preference(SelfDeletingMessagesEnforcedTimeout).apply()
      } yield {
        showSelfDeletingMessagesConfigsChangeInfoDialog(isFeatureEnabled, enforcedTimeoutInSeconds) { _ =>
          userPreferences.head.foreach { prefs =>
            prefs(UserPreferences.ShouldInformSelfDeletingMessagesChanged) := false
          }
        }
      }
    }

    if(BuildConfig.CONFERENCE_CALLING_RESTRICTION)
      observeTeamUpgrade()

    featureConfigsController.startUpdatingFlagsWhenEnteringForeground()
  }

  private def observeTeamUpgrade(): Unit = {
    userPreferences.flatMap(_.preference(UserPreferences.ShouldInformPlanUpgradedToEnterprise).signal).onUi { shouldInform =>
      if (shouldInform) {
        accentColorController.accentColor.head.foreach { accentColor =>
          showPlanUpgradedInfoDialog(accentColor) { _ =>
            userPreferences.head.foreach { prefs =>
              prefs(UserPreferences.ShouldInformPlanUpgradedToEnterprise) := false
            }
          }
        }
      }
    }
  }

  private def initTracking: Future[Unit] =
    for {
      prefs            <- userPreferences.head
      id               <- prefs.preference(CurrentTrackingId).apply()
      shouldShare      <- prefs.preference(ShouldShareTrackingId).apply()
      trackingCtrl     =  inject[GlobalTrackingController]
      _                =  verbose(l"trackingId: $id, shouldShare: $shouldShare")
      _                <- if (id.isEmpty || shouldShare) trackingCtrl.setAndSendNewTrackingId() else Future.successful(())
      _                <- if (shouldShare) prefs.preference(ShouldShareTrackingId) := false else Future.successful(())
      check            <- prefs.preference[Boolean](TrackingEnabledOneTimeCheckPerformed).apply()
      analyticsEnabled <- prefs.preference[Boolean](TrackingEnabled).apply()
      isProUser        <- userAccountsController.isProUser.head
      _                <- if (!check)
                            (prefs(TrackingEnabled) := isProUser).flatMap(_ => prefs(TrackingEnabledOneTimeCheckPerformed) := true)
                          else Future.successful(())
      _                <- if (analyticsEnabled && isProUser) trackingCtrl.init() else Future.successful(())
    } yield ()

  override def onStart(): Unit = {
    getControllerFactory.getNavigationController.addNavigationControllerObserver(this)
    inject[NavigationController].mainActivityActive.mutate(_ + 1)

    super.onStart()

    if (!getControllerFactory.getUserPreferencesController.hasCheckedForUnsupportedEmojis(Emojis.VERSION))
      Future(checkForUnsupportedEmojis())(Threading.Background)

    for {
      _      <- initTracking
      _      <- inject[GlobalTrackingController].start(this)
    } yield ()

    val intent = getIntent
    deepLinkService.checkDeepLink(intent)
    intent.setData(null)
    setIntent(intent)
  }

  override protected def onResume(): Unit = {
    super.onResume()
    Option(ZMessaging.currentGlobal).foreach(_.googleApi.checkGooglePlayServicesAvailable(this))
  }

  override def onDestroy(): Unit = {
    verbose(l"[BE]: onDestroy")
    super.onDestroy()
  }

  private def openSignUpPage(ssoToken: Option[String] = None): Unit = {
    verbose(l"openSignUpPage(${ssoToken.map(showString)})")
    userAccountsController.ssoToken ! ssoToken
    startActivity(new Intent(getApplicationContext, classOf[AppEntryActivity]))
    finish()
  }

  def startFirstFragment(): Unit = {
    verbose(l"startFirstFragment, intent: ${RichIntent(getIntent)}")
    account.head.foreach {
      case Some(am) =>
        am.getOrRegisterClient().map {
          case Right(Registered(_)) =>
            for {
              z <- zms.head
              _ <- z.users.clearAccountPassword()
              self <- z.users.selfUser.head
              isLogin <- z.userPrefs(IsLogin).apply()
              isNewClient <- z.userPrefs(IsNewClient).apply()
              pendingPw <- z.userPrefs(PendingPassword).apply()
              pendingEmail <- z.userPrefs(PendingEmail).apply()
              passwordManagedBySSO <- accountsService.activeAccountHasCompanyManagedPassword.head
            } yield {
              val (f, t) =
                if (passwordManagedBySSO) {
                  if (self.handle.isEmpty) (SetHandleFragment(), SetHandleFragment.Tag)
                  else (new MainPhoneFragment, MainPhoneFragment.Tag)
                }
                else if (self.email.isDefined && pendingPw) (SetOrRequestPasswordFragment(self.email.get), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined) (VerifyEmailFragment(pendingEmail.get), VerifyEmailFragment.Tag)
                else if (self.email.isEmpty && isLogin && isNewClient && self.phone.isDefined)
                  (AddEmailFragment(), AddEmailFragment.Tag)
                else if (self.handle.isEmpty) (SetHandleFragment(), SetHandleFragment.Tag)
                else (new MainPhoneFragment, MainPhoneFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }

          case Right(LimitReached) =>
            for {
              self <- am.getSelf
              pendingPw <- am.storage.userPrefs(PendingPassword).apply()
              pendingEmail <- am.storage.userPrefs(PendingEmail).apply()
              passwordManagedBySSO <- accountsService.activeAccountHasCompanyManagedPassword.head
            } yield {
              val (f, t) =
                if (passwordManagedBySSO) (OtrDeviceLimitFragment.newInstance, OtrDeviceLimitFragment.Tag)
                else if (self.email.isDefined && pendingPw) (SetOrRequestPasswordFragment(self.email.get), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined) (VerifyEmailFragment(pendingEmail.get), VerifyEmailFragment.Tag)
                else if (self.email.isEmpty) (AddEmailFragment(), AddEmailFragment.Tag)
                else (OtrDeviceLimitFragment.newInstance, OtrDeviceLimitFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }

          case Right(PasswordMissing) =>
            for {
              self <- am.getSelf
              pendingEmail <- am.storage.userPrefs(PendingEmail).apply()
              passwordManagedBySSO <- accountsService.activeAccountHasCompanyManagedPassword.head
            } {
              val (f, t) =
                if (passwordManagedBySSO) {
                  if (self.handle.isEmpty) (SetHandleFragment(), SetHandleFragment.Tag)
                  else (new MainPhoneFragment, MainPhoneFragment.Tag)
                }
                else if (self.email.isDefined) (SetOrRequestPasswordFragment(self.email.get, hasPassword = true), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined) (VerifyEmailFragment(pendingEmail.get, hasPassword = true), VerifyEmailFragment.Tag)
                else (AddEmailFragment(hasPassword = true), AddEmailFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }
          case Right(Unregistered) => warn(l"This shouldn't happen, going back to sign in..."); Future.successful(openSignUpPage())
          case Left(_) => showGenericErrorDialog()
        }
      case _ =>
        warn(l"No logged in account, sending to Sign in")
        Future.successful(openSignUpPage())
    }
  }

  def replaceMainFragment(fragment: Fragment, newTag: String, reverse: Boolean = false, addToBackStack: Boolean = true): Unit = {

    import scala.collection.JavaConverters._
    val oldTag = getSupportFragmentManager.getFragments.asScala.toList.flatMap(Option(_)).lastOption.flatMap {
      case _: SetOrRequestPasswordFragment => Some(SetOrRequestPasswordFragment.Tag)
      case _: VerifyEmailFragment          => Some(VerifyEmailFragment.Tag)
      case _: AddEmailFragment             => Some(AddEmailFragment.Tag)
      case _ => None
    }
    verbose(l"replaceMainFragment: ${oldTag.map(redactedString)} -> ${redactedString(newTag)}")

    val (in, out) = (MainActivity.isSlideAnimation(oldTag, newTag), reverse) match {
      case (true, true)  => (R.anim.fragment_animation_second_page_slide_in_from_left_no_alpha, R.anim.fragment_animation_second_page_slide_out_to_right_no_alpha)
      case (true, false) => (R.anim.fragment_animation_second_page_slide_in_from_right_no_alpha, R.anim.fragment_animation_second_page_slide_out_to_left_no_alpha)
      case _             => (R.anim.fade_in, R.anim.fade_out)
    }

    val frag = Option(getSupportFragmentManager.findFragmentByTag(newTag)) match {
      case Some(f) => returning(f)(_.setArguments(fragment.getArguments))
      case _       => fragment
    }

    val transaction = getSupportFragmentManager
      .beginTransaction
      .setCustomAnimations(in, out)
      .replace(R.id.fl_main_content, frag, newTag)
    if (addToBackStack) transaction.addToBackStack(newTag)
    transaction.commit
    spinnerController.hideSpinner()
  }

  def removeFragment(fragment: Fragment): Unit = {
    val transaction = getSupportFragmentManager
      .beginTransaction
      .remove(fragment)
    transaction.commit
  }

  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    getControllerFactory.getNavigationController.onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  override def onStop(): Unit = {
    super.onStop()
    getControllerFactory.getNavigationController.removeNavigationControllerObserver(this)
    inject[NavigationController].mainActivityActive.mutate(_ - 1)
    inject[GlobalTrackingController].stop()
  }

  override def onBackPressed(): Unit =
    Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_content)).foreach {
      case f: OnBackPressedListener if f.onBackPressed() => //
      case _ => super.onBackPressed()
    }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    Option(ZMessaging.currentGlobal).foreach(_.googleApi.onActivityResult(requestCode, resultCode))
    Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_content)).foreach(_.onActivityResult(requestCode, resultCode, data))

    if (requestCode == PreferencesActivity.SwitchAccountCode && data != null) {
      Option(data.getStringExtra(PreferencesActivity.SwitchAccountExtra)).foreach { extraStr =>
        accountsService.setAccount(Some(UserId(extraStr)))
      }
    }
  }

  override protected def onNewIntent(intent: Intent): Unit = {
    super.onNewIntent(intent)
    verbose(l"onNewIntent: ${RichIntent(intent)}")

    if (IntentUtils.isPasswordResetIntent(intent)) onPasswordWasReset()

    setIntent(intent)
    handleIntent(intent).foreach {
      case false => deepLinkService.checkDeepLink(intent)
      case _ =>
    }
  }

  private def initializeControllers(): Unit = {
    //Ensure tracking is started
    inject[UiTrackingController]
    inject[KeyboardController]
    // Here comes code for adding other dependencies to controllers...
    getControllerFactory.getNavigationController.setIsLandscape(isInLandscape(this))
  }

  private def onPasswordWasReset() =
    for {
      Some(am) <- accountsService.activeAccountManager.head
      _        <- am.auth.onPasswordReset(emailCredentials = None)
    } yield {}

  private def handleIntent(intent: Intent): Future[Boolean] = {
    verbose(l"handleIntent: ${RichIntent(intent)}")

    if (intent.initSync)
      userPreferences.head.foreach { prefs => prefs(shouldSyncAllOnUpdate) := true }

    def clearIntent(): Unit = {
      intent.clearExtras()
      setIntent(intent)
    }

    intent match {
      case NotificationIntent(accountId, convId, startCall) =>
        verbose(l"notification intent, accountId: $accountId, convId: $convId")
        val switchAccount = {
          accountsService.activeAccount.head.flatMap {
            case Some(acc) if intent.accountId.contains(acc.id) => Future.successful(false)
            case _ => accountsService.setAccount(intent.accountId).map(_ => true)
          }
        }

        val res = switchAccount.flatMap { _ =>
          (intent.convId match {
            case Some(id) => conversationController.switchConversation(id, startCall)
            case _ =>        Future.successful({})
          }).map(_ => clearIntent())(Threading.Ui)
        }

        try {
          val t = clock.instant()
          if (Await.result(switchAccount, 2.seconds)) verbose(l"Account switched before resuming activity lifecycle. Took ${t.until(clock.instant()).toMillis} ms")
        } catch {
          case NonFatal(e) => error(l"Failed to switch accounts", e)
        }

        res.map(_ => true)

      case SharingIntent() =>
        for {
          convs <- sharingController.sendContent(intent, this)
          _     <- if (convs.size == 1) conversationController.switchConversation(convs.head) else Future.successful({})
          _     =  clearIntent()
        } yield true

      case OpenPageIntent(page) => page match {
        case Intents.Page.Settings =>
          startActivityForResult(PreferencesActivity.getDefaultIntent(this), PreferencesActivity.SwitchAccountCode)
          clearIntent()
          Future.successful(true)
        case _ =>
          error(l"Unknown page: ${redactedString(page)} - ignoring intent")
          Future.successful(false)
      }

      case _ =>
        verbose(l"unknown intent $intent")
        setIntent(intent)
        Future.successful(false)
    }
  }

  def onPageVisible(page: Page): Unit =
    getControllerFactory.getGlobalLayoutController.setSoftInputModeForPage(page)

  def onInviteRequestSent(conversation: String): Future[Unit] = {
    info(l"onInviteRequestSent(${redactedString(conversation)})")
    conversationController.selectConv(Option(new ConvId(conversation)), ConversationChangeRequester.INVITE)
  }

  override def logout(): Unit = {
    accountsService.activeAccountId.head.flatMap(_.fold(Future.successful({})){ id => accountsService.logout(id, reason = UserInitiated) }).map { _ =>
      startFirstFragment()
    } (Threading.Ui)
  }

  def manageDevices(): Unit = startActivity(ShowDeviceRemovalIntent(this))

  def dismissOtrDeviceLimitFragment(): Unit = withFragmentOpt(OtrDeviceLimitFragment.Tag)(_.foreach(removeFragment))

  private def checkForUnsupportedEmojis() =
    for {
      cf <- Option(getControllerFactory) if !cf.isTornDown
      prefs <- Option(cf.getUserPreferencesController)
    } {
      val paint = new Paint
      val template = returning(new TextDrawing)(_.set("\uFFFF")) // missing char
      val check = new TextDrawing

      val missing = Emojis.getAllEmojisSortedByCategory.asScala.flatten.filter { emoji =>
          !paint.hasGlyph(emoji)
      }

      if (missing.nonEmpty) prefs.setUnsupportedEmoji(missing.asJava, Emojis.VERSION)
    }

  override def onChooseUsernameChosen(): Unit =
    getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(ChangeHandleFragment.newInstance("", cancellable = false), ChangeHandleFragment.Tag)
      .addToBackStack(ChangeHandleFragment.Tag)
      .commit

  override def onUsernameSet(): Unit = replaceMainFragment(new MainPhoneFragment, MainPhoneFragment.Tag, addToBackStack = false)
}

object MainActivity {
  val ClientRegStateArg: String = "ClientRegStateArg"

  def newIntent(activity: Activity) = new Intent(activity, classOf[MainActivity]).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

  private val slideAnimations = Set(
    (SetOrRequestPasswordFragment.Tag, VerifyEmailFragment.Tag),
    (SetOrRequestPasswordFragment.Tag,  AddEmailFragment.Tag),
    (VerifyEmailFragment.Tag, AddEmailFragment.Tag)
  )

  private def isSlideAnimation(oldTag: Option[String], newTag: String) = oldTag.fold(false) { old =>
    slideAnimations.contains((old, newTag)) || slideAnimations.contains((newTag, old))
  }
}

