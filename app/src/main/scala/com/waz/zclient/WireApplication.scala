/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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

import android.os.Build
import android.renderscript.RenderScript
import android.support.multidex.MultiDexApplication
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api._
import com.waz.content.GlobalPreferences
import com.waz.log.InternalLog
import com.waz.model.ConversationData
import com.waz.service.{NetworkModeService, UiLifeCycle, ZMessaging}
import com.waz.utils.events.{EventContext, Signal, Subscription}
import com.waz.zclient.api.scala.ScalaStoreFactory
import com.waz.zclient.calling.controllers.{CallPermissionsController, CurrentCallController, GlobalCallingController}
import com.waz.zclient.camera.controllers.{AndroidCameraFactory, GlobalCameraController}
import com.waz.zclient.common.controllers.{PermissionActivity, PermissionsController, PermissionsWrapper}
import com.waz.zclient.controllers._
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.deviceuser.IDeviceUserController
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.giphy.IGiphyController
import com.waz.zclient.controllers.global.{AccentColorController, KeyboardController, PasswordController, SelectionController}
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.controllers.navigation.INavigationController
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.CollectionController
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.IStoreFactory
import com.waz.zclient.core.stores.network.INetworkStore
import com.waz.zclient.cursor.CursorController
import com.waz.zclient.media.SoundController
import com.waz.zclient.messages.controllers.{MessageActionsController, NavigationController}
import com.waz.zclient.messages.{LikesController, MessageViewFactory, MessagesController, UsersController}
import com.waz.zclient.notifications.controllers.{CallingNotificationsController, ImageNotificationsController, MessageNotificationsController}
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.preferences.PreferencesController
import com.waz.zclient.tracking.{CallingTrackingController, CrashController, GlobalTrackingController, UiTrackingController}
import com.waz.zclient.utils.{BackStackNavigator, BackendPicker, Callback, UiStorage}
import com.waz.zclient.views.ImageController

object WireApplication {
  var APP_INSTANCE: WireApplication = _

  lazy val Global = new Module {
    implicit lazy val wContext     = inject[WireContext]
    implicit lazy val eventContext = inject[EventContext]

    def controllerFactory = APP_INSTANCE.asInstanceOf[ZApplication].getControllerFactory
    def storeFactory = APP_INSTANCE.asInstanceOf[ZApplication].getStoreFactory

    // SE services
    bind [Signal[Option[ZMessaging]]]  to ZMessaging.currentUi.currentZms
    bind [Signal[ZMessaging]]          to inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
    bind [GlobalPreferences]           to ZMessaging.currentGlobal.prefs
    bind [NetworkModeService]          to ZMessaging.currentGlobal.network
    bind [UiLifeCycle]                 to ZMessaging.currentGlobal.lifecycle

    // old controllers
    // TODO: remove controller factory, reimplement those controllers
    bind [IControllerFactory]            toProvider controllerFactory
    bind [IPickUserController]           toProvider controllerFactory.getPickUserController
    bind [IConversationScreenController] toProvider controllerFactory.getConversationScreenController
    bind [INavigationController]         toProvider controllerFactory.getNavigationController
    bind [IUserPreferencesController]    toProvider controllerFactory.getUserPreferencesController
    bind [IConversationScreenController] toProvider controllerFactory.getConversationScreenController
    bind [ISingleImageController]        toProvider controllerFactory.getSingleImageController
    bind [ISlidingPaneController]        toProvider controllerFactory.getSlidingPaneController
    bind [IDrawingController]            toProvider controllerFactory.getDrawingController
    bind [IDeviceUserController]         toProvider controllerFactory.getDeviceUserController
    bind [IGlobalLayoutController]       toProvider controllerFactory.getGlobalLayoutController
    bind [ILocationController]           toProvider controllerFactory.getLocationController
    bind [IGiphyController]              toProvider controllerFactory.getGiphyController
    bind [ICameraController]             toProvider controllerFactory.getCameraController

    bind [IStoreFactory]                 toProvider storeFactory
    bind [INetworkStore]                 toProvider storeFactory.networkStore

    // global controllers
    bind [CrashController]         to new CrashController
    bind [AccentColorController]   to new AccentColorController()
    bind [PasswordController]      to new PasswordController()
    bind [GlobalCallingController] to new GlobalCallingController()
    bind [GlobalCameraController]  to new GlobalCameraController(new AndroidCameraFactory)
    bind [SelectionController]     to new SelectionController()
    bind [SoundController]         to new SoundController
    bind [ThemeController]         to new ThemeController

    //notifications
    bind [MessageNotificationsController]  to new MessageNotificationsController()
    bind [ImageNotificationsController]    to new ImageNotificationsController()
    bind [CallingNotificationsController]  to new CallingNotificationsController()

    bind [GlobalTrackingController]        to new GlobalTrackingController()
    bind [CallingTrackingController]       to new CallingTrackingController()
    bind [PreferencesController]           to new PreferencesController()
    bind [ImageController]                 to new ImageController()
    bind [UserAccountsController]          to new UserAccountsController()
    bind [SharingController]         to new SharingController()
    bind [NavigationController]      to new NavigationController()


    // current conversation data
    bind [Signal[ConversationData]] to {
      for {
        zs <- inject[Signal[ZMessaging]]
        convId <- inject[SelectionController].selectedConv
        conv <- zs.convsStorage.signal(convId)
      } yield conv
    }

    // accent color
    bind [Signal[AccentColor]] to inject[AccentColorController].accentColor
  }

  def controllers(implicit ctx: WireContext) = new Module {

    private implicit val eventContext = ctx.eventContext

    bind [ZMessagingApi]      to new ZMessagingApiProvider(ctx, inject[UiLifeCycle]).api
    bind [Signal[ZMessaging]] to inject[ZMessagingApi].asInstanceOf[com.waz.api.impl.ZMessagingApi].ui.currentZms.collect{case Some(zms)=> zms }

    bind [KeyboardController]        to new KeyboardController()
    bind [CurrentCallController]     to new CurrentCallController()
    bind [CallPermissionsController] to new CallPermissionsController()
    bind [AssetsController]          to new AssetsController()
    bind [BrowserController]         to new BrowserController()
    bind [MessageViewFactory]        to new MessageViewFactory()
    bind [PermissionActivity]        to ctx.asInstanceOf[PermissionActivity]
    bind [PermissionsController]     to new PermissionsController(new PermissionsWrapper)
    bind [UsersController]           to new UsersController()
    bind [ScreenController]          to new ScreenController()
    bind [MessageActionsController]  to new MessageActionsController()
    bind [MessagesController]        to new MessagesController()
    bind [LikesController]           to new LikesController()
    bind [CollectionController]      to new CollectionController()
    bind [UiStorage]                 to new UiStorage()
    bind [BackStackNavigator]        to new BackStackNavigator()
    bind [AppEntryController]        to new AppEntryController()
    bind [SignInController]          to new SignInController()

    bind [CursorController]           to new CursorController()
    bind [ConversationListController] to new ConversationListController()

    /**
      * Since tracking controllers will immediately instantiate other necessary controllers, we keep them separated
      * based on the activity responsible for generating their events (we don't want to instantiate an uneccessary
      * MessageActionsController in the CallingActivity, for example
      */
    bind [UiTrackingController]    to new UiTrackingController()
  }
}

class WireApplication extends MultiDexApplication with WireContext with Injectable {
  type NetworkSignal = Signal[NetworkMode]
  import WireApplication._
  WireApplication.APP_INSTANCE = this

  override def eventContext: EventContext = EventContext.Global

  lazy val module: Injector = Global :: AppModule

  protected var controllerFactory: IControllerFactory = _
  protected var storeFactory: IStoreFactory = _

  def contextModule(ctx: WireContext): Injector = controllers(ctx) :: ContextModule(ctx)

  override def onCreate(): Unit = {
    super.onCreate()

    if (ZmsVersion.DEBUG) {
      InternalLog.init(getApplicationContext.getApplicationInfo.dataDir)
    }

    verbose("onCreate")
    controllerFactory = new ControllerFactory(getApplicationContext)

    new BackendPicker(this).withBackend(new Callback[Void]() {
      def callback(aVoid: Void) = ensureInitialized()
    })
  }

  def ensureInitialized() = {
    if (storeFactory == null) {
      //TODO initialization of ZMessaging happens here - make this more explicit?
      storeFactory = new ScalaStoreFactory(getApplicationContext, inject[SelectionController])
      storeFactory.zMessagingApiStore.getApi
    }

    inject[MessageNotificationsController]
    inject[ImageNotificationsController]
    inject[CallingNotificationsController]

    //TODO [AN-4942] - is this early enough for app launch events?
    inject[GlobalTrackingController]
    inject[CrashController] //needs to register crash handler
    inject[CallingTrackingController]
    inject[ThemeController]
    inject[PreferencesController]
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

class ZMessagingApiProvider(ctx: WireContext, uiLifeCycle: UiLifeCycle) {
  val api = ZMessagingApiFactory.getInstance(ctx)

  api.onCreate(ctx)

  ctx.eventContext.register(new Subscription {
    override def subscribe(): Unit = {
      api.onResume()
      uiLifeCycle.acquireUi()
    }
    override def unsubscribe(): Unit = {
      api.onPause()
      uiLifeCycle.releaseUi()
    }
    override def enable(): Unit = ()
    override def disable(): Unit = ()
    override def destroy(): Unit = api.onDestroy()
    override def disablePauseWithContext(): Unit = ()
  })
}
