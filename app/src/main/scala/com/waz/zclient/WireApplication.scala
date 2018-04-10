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

import java.util.Calendar

import android.app.{Activity, ActivityManager, NotificationManager}
import android.content.{ClipboardManager, Context, ContextWrapper}
import android.media.AudioManager
import android.os.{Build, PowerManager, Vibrator}
import android.renderscript.RenderScript
import android.support.multidex.MultiDexApplication
import android.support.v4.app.{FragmentActivity, FragmentManager}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api._
import com.waz.content.GlobalPreferences
import com.waz.log.InternalLog
import com.waz.model.ConversationData
import com.waz.permissions.PermissionsService
import com.waz.service.tracking.TrackingService
import com.waz.service.{NetworkModeService, UiLifeCycle, ZMessaging}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.api.scala.ScalaStoreFactory
import com.waz.zclient.appentry.controllers.{AddEmailController, AppEntryController, InvitationsController, SignInController}
import com.waz.zclient.calling.controllers.{CallPermissionsController, CurrentCallController, GlobalCallingController}
import com.waz.zclient.camera.controllers.{AndroidCameraFactory, GlobalCameraController}
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.{AccentColorController, ClientsController, KeyboardController, PasswordController}
import com.waz.zclient.common.controllers.{SoundController, _}
import com.waz.zclient.common.views.ImageController
import com.waz.zclient.controllers._
import com.waz.zclient.controllers.calling.ICallingController
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation.IConfirmationController
import com.waz.zclient.controllers.deviceuser.IDeviceUserController
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.giphy.IGiphyController
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.controllers.navigation.INavigationController
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.IStoreFactory
import com.waz.zclient.cursor.CursorController
import com.waz.zclient.integrations.IntegrationDetailsController
import com.waz.zclient.messages.controllers.{MessageActionsController, NavigationController}
import com.waz.zclient.messages.{LikesController, MessageViewFactory, MessagesController, UsersController}
import com.waz.zclient.notifications.controllers.{CallingNotificationsController, ImageNotificationsController, MessageNotificationsController}
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.preferences.PreferencesController
import com.waz.zclient.tracking.{CrashController, GlobalTrackingController, UiTrackingController}
import com.waz.zclient.utils.{BackStackNavigator, BackendPicker, Callback, UiStorage}
import com.waz.zclient.views.DraftMap
import net.hockeyapp.android.Constants

object WireApplication {
  var APP_INSTANCE: WireApplication = _

  lazy val Global = new Module {

    implicit lazy val ctx:          WireApplication = WireApplication.APP_INSTANCE
    implicit lazy val wContext:     WireContext     = ctx
    implicit lazy val eventContext: EventContext    = EventContext.Global

    //Android services
    bind [ActivityManager]      to ctx.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    bind [PowerManager]         to ctx.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    bind [Vibrator]             to ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
    bind [AudioManager]         to ctx.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    bind [NotificationManager]  to ctx.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    bind [ClipboardManager]     to ctx.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    bind [RenderScript]         to RenderScript.create(ctx)

    def controllerFactory = APP_INSTANCE.asInstanceOf[ZApplication].getControllerFactory
    def storeFactory = APP_INSTANCE.asInstanceOf[ZApplication].getStoreFactory

    // SE services
    bind [Signal[Option[ZMessaging]]]  to ZMessaging.currentUi.currentZms
    bind [Signal[ZMessaging]]          to inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
    bind [GlobalPreferences]           to ZMessaging.currentGlobal.prefs
    bind [NetworkModeService]          to ZMessaging.currentGlobal.network
    bind [UiLifeCycle]                 to ZMessaging.currentGlobal.lifecycle
    bind [TrackingService]             to ZMessaging.currentGlobal.trackingService
    bind [PermissionsService]          to ZMessaging.currentGlobal.permissions

    // old controllers
    // TODO: remove controller factory, reimplement those controllers
    bind [IControllerFactory]            toProvider controllerFactory
    bind [IPickUserController]           toProvider controllerFactory.getPickUserController
    bind [IConversationScreenController] toProvider controllerFactory.getConversationScreenController
    bind [INavigationController]         toProvider controllerFactory.getNavigationController
    bind [IUserPreferencesController]    toProvider controllerFactory.getUserPreferencesController
    bind [ISingleImageController]        toProvider controllerFactory.getSingleImageController
    bind [ISlidingPaneController]        toProvider controllerFactory.getSlidingPaneController
    bind [IDrawingController]            toProvider controllerFactory.getDrawingController
    bind [IDeviceUserController]         toProvider controllerFactory.getDeviceUserController
    bind [IGlobalLayoutController]       toProvider controllerFactory.getGlobalLayoutController
    bind [ILocationController]           toProvider controllerFactory.getLocationController
    bind [IGiphyController]              toProvider controllerFactory.getGiphyController
    bind [ICameraController]             toProvider controllerFactory.getCameraController
    bind [ICallingController]            toProvider controllerFactory.getCallingController
    bind [IConfirmationController]       toProvider controllerFactory.getConfirmationController

    // global controllers
    bind [CrashController]         to new CrashController
    bind [AccentColorController]   to new AccentColorController()
    bind [PasswordController]      to new PasswordController()
    bind [GlobalCallingController] to new GlobalCallingController()
    bind [GlobalCameraController]  to new GlobalCameraController(new AndroidCameraFactory)
    bind [SoundController]         to new SoundController
    bind [ThemeController]         to new ThemeController

    //notifications
    bind [MessageNotificationsController]  to new MessageNotificationsController()
    bind [ImageNotificationsController]    to new ImageNotificationsController()
    bind [CallingNotificationsController]  to new CallingNotificationsController()

    bind [GlobalTrackingController]        to new GlobalTrackingController()
    bind [PreferencesController]           to new PreferencesController()
    bind [ImageController]                 to new ImageController()
    bind [UserAccountsController]          to new UserAccountsController()

    bind [SharingController]               to new SharingController()
    bind [ConversationController]          to new ConversationController()

    bind [NavigationController]            to new NavigationController()
    bind [AppEntryController]              to new AppEntryController()
    bind [SignInController]                to new SignInController()
    bind [InvitationsController]           to new InvitationsController()
    bind [IntegrationDetailsController]    to new IntegrationDetailsController()
    bind [IntegrationsController]          to new IntegrationsController()
    bind [ClientsController]               to new ClientsController()

    // current conversation data
    bind [Signal[ConversationData]] to inject[ConversationController].currentConv

    // accent color
    bind [Signal[AccentColor]] to inject[AccentColorController].accentColor

    // drafts
    bind [DraftMap] to new DraftMap()
  }

  def controllers(implicit ctx: WireContext) = new Module {

    private implicit val eventContext = ctx.eventContext

    bind [Activity] to {
      def getActivity(ctx: Context): Activity = ctx match {
        case a: Activity => a
        case w: ContextWrapper => getActivity(w.getBaseContext)
      }
      getActivity(ctx)
    }
    bind [FragmentManager] to inject[Activity].asInstanceOf[FragmentActivity].getSupportFragmentManager

    bind [KeyboardController]        to new KeyboardController()
    bind [CurrentCallController]     to new CurrentCallController()
    bind [CallPermissionsController] to new CallPermissionsController()
    bind [AssetsController]          to new AssetsController()
    bind [BrowserController]         to new BrowserController()
    bind [MessageViewFactory]        to new MessageViewFactory()

    bind [ScreenController]          to new ScreenController()
    bind [MessageActionsController]  to new MessageActionsController()
    bind [MessagesController]        to new MessagesController()
    bind [LikesController]           to new LikesController()
    bind [CollectionController]      to new CollectionController()
    bind [UiStorage]                 to new UiStorage()
    bind [BackStackNavigator]        to new BackStackNavigator()

    bind [CursorController]             to new CursorController()
    bind [ConversationListController]   to new ConversationListController()
    bind [IntegrationDetailsController] to new IntegrationDetailsController()
    bind [CreateConversationController]    to new CreateConversationController()
    bind [ParticipantsController]       to new ParticipantsController()
    bind [UsersController]              to new UsersController()
    bind [SpinnerController]            to new SpinnerController()
    bind [AddEmailController]           to new AddEmailController()

    bind [ErrorsController]             to new ErrorsController()

    /**
      * Since tracking controllers will immediately instantiate other necessary controllers, we keep them separated
      * based on the activity responsible for generating their events (we don't want to instantiate an uneccessary
      * MessageActionsController in the CallingActivity, for example
      */
    bind [UiTrackingController]    to new UiTrackingController()
  }

  protected def clearOldVideoFiles(context: Context): Unit = {
    val oneWeekAgo = Calendar.getInstance
    oneWeekAgo.add(Calendar.DAY_OF_YEAR, -7)
    Option(context.getExternalCacheDir).foreach { _.listFiles().foreach { file =>
      val fileName = file.getName
      val fileModified = Calendar.getInstance()
      fileModified.setTimeInMillis(file.lastModified)
      if (fileName.startsWith("VID_") && fileName.endsWith(".mp4") && fileModified.before(oneWeekAgo)) {
        file.delete()
      }
    }}
  }
}

class WireApplication extends MultiDexApplication with WireContext with Injectable {
  type NetworkSignal = Signal[NetworkMode]
  import WireApplication._
  WireApplication.APP_INSTANCE = this

  override def eventContext: EventContext = EventContext.Global

  lazy val module: Injector = Global

  protected var controllerFactory: IControllerFactory = _
  protected var storeFactory: IStoreFactory = _

  def contextModule(ctx: WireContext): Injector = controllers(ctx)

  override def onCreate(): Unit = {
    super.onCreate()
    InternalLog.init(getApplicationContext.getApplicationInfo.dataDir)

    verbose("onCreate")
    controllerFactory = new ControllerFactory(getApplicationContext)

    new BackendPicker(this).withBackend(new Callback[Void]() {
      def callback(aVoid: Void) = ensureInitialized()
    })

    Constants.loadFromContext(getApplicationContext)
  }

  def ensureInitialized() = {
    if (storeFactory == null) {
      ZMessaging.onCreate(this)
      storeFactory = new ScalaStoreFactory(getApplicationContext)
    }

    inject[MessageNotificationsController]
    inject[ImageNotificationsController]
    inject[CallingNotificationsController]

    //TODO [AN-4942] - is this early enough for app launch events?
    inject[GlobalTrackingController]
    inject[CrashController] //needs to register crash handler
    inject[ThemeController]
    inject[PreferencesController]
    clearOldVideoFiles(getApplicationContext)
  }

  override def onTerminate(): Unit = {
    controllerFactory.tearDown()
    storeFactory.tearDown()
    storeFactory = null
    controllerFactory = null
    if (Build.VERSION.SDK_INT > 22){
      RenderScript.releaseAllContexts()
    } else {
      inject[RenderScript].destroy()
    }

    InternalLog.flush()

    super.onTerminate()
  }
}
