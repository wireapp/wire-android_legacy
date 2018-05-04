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

import android.content.Intent
import android.content.Intent._
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.{Color, Paint, PixelFormat}
import android.os.{Build, Bundle}
import android.support.v4.app.{Fragment, FragmentTransaction}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, info, verbose, warn}
import com.waz.api._
import com.waz.content.Preferences.Preference.PrefCodec
import com.waz.content.UserPreferences._
import com.waz.model.{ConvId, UserId}
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, PasswordMissing, Registered, Unregistered}
import com.waz.service.ZMessaging.clock
import com.waz.service.{AccountManager, AccountsService, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.{RichInstant, returning}
import com.waz.zclient.Intents._
import com.waz.zclient.MainActivity._
import com.waz.zclient.SpinnerController.{Hide, Show}
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.controllers.{SharingController, UserAccountsController}
import com.waz.zclient.controllers.navigation.{NavigationControllerObserver, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.fragments.ConnectivityFragment
import com.waz.zclient.pages.main.MainPhoneFragment
import com.waz.zclient.pages.startup.UpdateFragment
import com.waz.zclient.preferences.dialogs.ChangeHandleFragment
import com.waz.zclient.preferences.{PreferencesActivity, PreferencesController}
import com.waz.zclient.tracking.{CrashController, UiTrackingController}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.StringUtils.TextDrawing
import com.waz.zclient.utils.{BuildConfigUtils, Emojis, IntentUtils, ViewUtils}
import com.waz.zclient.views.LoadingIndicatorView
import net.hockeyapp.android.NativeCrashManager

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class MainActivity extends BaseActivity
  with CallingBannerActivity
  with UpdateFragment.Container
  with NavigationControllerObserver
  with OtrDeviceLimitFragment.Container
  with SetHandleFragment.Container {

  implicit val cxt = this

  import Threading.Implicits.Background

  lazy val zms                      = inject[Signal[ZMessaging]]
  lazy val account                  = inject[Signal[Option[AccountManager]]]
  lazy val accountsService          = inject[AccountsService]
  lazy val sharingController        = inject[SharingController]
  lazy val accentColorController    = inject[AccentColorController]
  lazy val callStart                = inject[CallStartController]
  lazy val conversationController   = inject[ConversationController]
  lazy val userAccountsController   = inject[UserAccountsController]
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
      case theme if theme != currentlyDarkTheme =>
        info("restartActivity")
        finish()
        startActivity(getIntent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
      case _ =>
    }

    //TODO - do we need this?
    accountsService.accountManagers.map(_.isEmpty).onUi {
      case true =>
        info("onLogout")
        getControllerFactory.getPickUserController.hideUserProfile()
        getControllerFactory.getNavigationController.resetPagerPositionToDefault()
        finish()
        startActivity(returning(new Intent(this, classOf[AppEntryActivity]))(_.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)))
      case _ =>
    }

    ZMessaging.currentGlobal.blacklist.upToDate.head.map {
      case false =>
        startActivity(new Intent(getApplicationContext, classOf[ForceUpdateActivity]))
        finish()
      case _ => //
    } (Threading.Ui)

    val loadingIndicator = findViewById[LoadingIndicatorView](R.id.progress_spinner)

    spinnerController.spinnerShowing.onUi {
      case Show(animation, forcedTheme)=>
        themeController.darkThemeSet.head.foreach(theme => loadingIndicator.show(animation, forcedTheme.getOrElse(theme), 300))(Threading.Ui)
      case Hide(Some(message))=> loadingIndicator.hideWithMessage(message, 750)
      case Hide(_) => loadingIndicator.hide()
    }

  }

  override protected def onResumeFragments() = {
    info("onResumeFragments")
    super.onResumeFragments()
  }

  override def onStart() = {
    info("onStart")
    getControllerFactory.getNavigationController.addNavigationControllerObserver(this)

    super.onStart()

    if (!getControllerFactory.getUserPreferencesController.hasCheckedForUnsupportedEmojis(Emojis.VERSION))
      Future(checkForUnsupportedEmojis())(Threading.Background)

    startFirstFragment()
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

  def startFirstFragment(): Unit = {
    verbose("startFirstFragment")
    def openSignUpPage(): Unit = {
      startActivity(new Intent(getApplicationContext, classOf[AppEntryActivity]))
      finish()
    }

    account.head.flatMap {
      case Some(am) =>
        (Option(getIntent).flatMap(i => Option(i.getStringExtra(ClientRegStateArg))) match {
          case Some(clientRegState) =>
            getIntent.removeExtra(ClientRegStateArg)
            Future.successful(Right(PrefCodec.SelfClientIdCodec.decode(clientRegState)))
          case _ => am.getOrRegisterClient()
        }).map {
          case Right(Registered(_))   =>
            for {
              z            <- zms.head
              isLogin      <- z.userPrefs(IsLogin).apply()
              isNewClient  <- z.userPrefs(IsNewClient).apply()
              email        <- z.users.selfUser.map(_.email).head
              pendingPw    <- z.userPrefs(PendingPassword).apply()
              pendingEmail <- z.userPrefs(PendingEmail).apply()
              handle       <- z.users.selfUser.map(_.handle).head
            } yield {
              val (f, t) =
                if (email.isDefined && pendingPw)                 (SetOrRequestPasswordFragment(email.get), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined)                  (VerifyEmailFragment(pendingEmail.get),   VerifyEmailFragment.Tag)
                else if (email.isEmpty && isLogin && isNewClient) (AddEmailFragment(),                      AddEmailFragment.Tag)
                else if (handle.isEmpty)                          (SetHandleFragment(),                     SetHandleFragment.Tag)
                else                                              (new MainPhoneFragment,                   MainPhoneFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }

          case Right(LimitReached) =>
            for {
              self         <- am.getSelf
              pendingPw    <- am.storage.userPrefs(PendingPassword).apply()
              pendingEmail <- am.storage.userPrefs(PendingEmail).apply()
            } yield {
              val (f, t) =
                if (self.email.isDefined && pendingPw) (SetOrRequestPasswordFragment(self.email.get), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined)       (VerifyEmailFragment(pendingEmail.get),        VerifyEmailFragment.Tag)
                else if (self.email.isEmpty)           (AddEmailFragment(),                           AddEmailFragment.Tag)
                else                                   (OtrDeviceLimitFragment.newInstance,           OtrDeviceLimitFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }
          case Right(PasswordMissing) =>
            for {
              self         <- am.getSelf
              pendingEmail <- am.storage.userPrefs(PendingEmail).apply()
            } {
              val (f ,t) =
                if (self.email.isDefined)        (SetOrRequestPasswordFragment(self.email.get, hasPassword = true), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined) (VerifyEmailFragment(pendingEmail.get, hasPassword = true),        VerifyEmailFragment.Tag)
                else                             (AddEmailFragment(hasPassword = true),                             AddEmailFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }
          case Right(Unregistered) => warn("This shouldn't happen, going back to sign in..."); Future.successful(openSignUpPage())
          case Left(_) => showGenericErrorDialog()
        } (Threading.Ui)
      case _ =>
        warn("No logged in account, sending to Sign in")
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
    verbose(s"replaceMainFragment: $oldTag -> $newTag")

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
    getControllerFactory.getNavigationController.removeNavigationControllerObserver(this)
  }

  override def onBackPressed(): Unit = {
    Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_content)).foreach {
      case f: OnBackPressedListener if f.onBackPressed() => //
      case _ => super.onBackPressed()
    }
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    info(s"OnActivity requestCode: $requestCode, resultCode: $resultCode")
    super.onActivityResult(requestCode, resultCode, data)
    Option(ZMessaging.currentGlobal).foreach(_.googleApi.onActivityResult(requestCode, resultCode))
    Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_content)).foreach(_.onActivityResult(requestCode, resultCode, data))

    if (requestCode == PreferencesActivity.SwitchAccountCode && data != null) {
      Option(data.getStringExtra(PreferencesActivity.SwitchAccountExtra)).foreach { extraStr =>
        accountsService.setAccount(Some(UserId(extraStr)))
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

  private def initializeControllers() = {
    //Ensure tracking is started
    inject[UiTrackingController]
    inject[KeyboardController]
    // Make sure we have a running OrientationController instance
    getControllerFactory.getOrientationController
    // Here comes code for adding other dependencies to controllers...
    getControllerFactory.getNavigationController.setIsLandscape(isInLandscape(this))
  }

  private def onPasswordWasReset() =
    for {
      Some(am) <- accountsService.activeAccountManager.head
      _        <- am.auth.onPasswordReset(emailCredentials = None)
    } yield {}

  def handleIntent(intent: Intent) = {
    verbose(s"handleIntent: ${intent.log}")

    def switchConversation(convId: ConvId, call: Boolean = false, exp: Option[EphemeralExpiration] = None) =
      CancellableFuture.delay(750.millis).map { _ =>
        verbose(s"setting conversation: $convId")
        conversationController.selectConv(convId, ConversationChangeRequester.INTENT).map { _ =>
          exp.foreach(conversationController.setEphemeralExpiration)
          if (call)
            for {
              Some(acc) <- account.map(_.map(_.userId)).head
              _         <- callStart.startCall(acc, convId)
            } yield {}
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
          accountsService.activeAccount.head.flatMap {
            case Some(acc) if intent.accountId.contains(acc.id) => Future.successful(false)
            case _ => accountsService.setAccount(intent.accountId).map(_ => true)
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

  def onPageVisible(page: Page) =
    getControllerFactory.getGlobalLayoutController.setSoftInputModeForPage(page)

  def onInviteRequestSent(conversation: String) = {
    info(s"onInviteRequestSent($conversation)")
    conversationController.selectConv(Option(new ConvId(conversation)), ConversationChangeRequester.INVITE)
  }

  override def logout() = {
    accountsService.activeAccountId.head.flatMap(_.fold(Future.successful({}))(accountsService.logout)).map { _ =>
      startFirstFragment()
    } (Threading.Ui)
  }

  def manageDevices() = startActivity(ShowDevicesIntent(this))

  def dismissOtrDeviceLimitFragment() = withFragmentOpt(OtrDeviceLimitFragment.Tag)(_.foreach(removeFragment))

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

  private val slideAnimations = Set(
    (SetOrRequestPasswordFragment.Tag, VerifyEmailFragment.Tag),
    (SetOrRequestPasswordFragment.Tag,  AddEmailFragment.Tag),
    (VerifyEmailFragment.Tag, AddEmailFragment.Tag)
  )

  private def isSlideAnimation(oldTag: Option[String], newTag: String) = oldTag.fold(false) { old =>
    slideAnimations.contains((old, newTag)) || slideAnimations.contains((newTag, old))
  }
}

