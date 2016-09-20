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

import android.content.Context
import android.support.multidex.MultiDexApplication
import com.waz.api.{NetworkMode, ZMessagingApi, ZMessagingApiFactory}
import com.waz.service.{MediaManagerService, PreferenceService, ZMessaging}
import com.waz.utils.events.{EventContext, Signal, Subscription}
import com.waz.zclient.api.scala.ScalaStoreFactory
import com.waz.zclient.calling.controllers.{CallPermissionsController, CurrentCallController, GlobalCallingController}
import com.waz.zclient.camera.controllers.{AndroidCameraFactory, GlobalCameraController}
import com.waz.zclient.common.controllers.{PermissionActivity, PermissionsController, PermissionsWrapper}
import com.waz.zclient.controllers.{DefaultControllerFactory, IControllerFactory}
import com.waz.zclient.core.stores.IStoreFactory
import com.waz.zclient.notifications.controllers.{CallingNotificationsController, ImageNotificationsController, MessageNotificationsController}
import com.waz.zclient.utils.{BackendPicker, BuildConfigUtils, Callback}

object WireApplication {
  var APP_INSTANCE: WireApplication = _

  lazy val Global = new Module {
    implicit def context = inject[Context]
    implicit def eventContext = EventContext.Global

    bind[Signal[Option[ZMessaging]]] to ZMessaging.currentUi.currentZms
    bind[Signal[ZMessaging]] to ZMessaging.currentUi.currentZms.collect { case Some(zms) => zms }
    bind[PreferenceService] to new PreferenceService(inject[Context])
    bind[GlobalCallingController] to new GlobalCallingController(inject[Context])
    bind[GlobalCameraController] to new GlobalCameraController(inject[Context], new AndroidCameraFactory)(EventContext.Global)
    bind[MediaManagerService] to ZMessaging.currentGlobal.mediaManager

    //notifications
    bind[MessageNotificationsController] to new MessageNotificationsController()
    bind[ImageNotificationsController] to new ImageNotificationsController(inject[Context])
    bind[CallingNotificationsController] to new CallingNotificationsController(inject[Context])
  }

  def services(ctx: WireContext) = new Module {
    bind [ZMessagingApi] to new ZMessagingApiProvider(ctx).api
    bind [Signal[ZMessaging]] to inject[ZMessagingApi].asInstanceOf[com.waz.api.impl.ZMessagingApi].ui.currentZms.collect{case Some(zms)=> zms }
    bind [Signal[NetworkMode]]
  }

  def controllers(implicit ctx: WireContext) = new Module {
    bind[CurrentCallController] to new CurrentCallController()
    bind[CallPermissionsController] to new CallPermissionsController()
    bind[PermissionActivity] to ctx.asInstanceOf[PermissionActivity]
    bind[PermissionsController] to new PermissionsController(new PermissionsWrapper)
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

  def contextModule(ctx: WireContext): Injector = controllers(ctx) :: services(ctx) :: ContextModule(ctx)

  override def onCreate(): Unit = {
    super.onCreate()
    controllerFactory = new DefaultControllerFactory(getApplicationContext)

    new BackendPicker(this).withBackend(new Callback[Void]() {
      def callback(aVoid: Void) = ensureInitialized()
    })
  }

  def ensureInitialized() = {
    if (storeFactory == null) {
      storeFactory = new ScalaStoreFactory(getApplicationContext)
      //TODO initialization of ZMessaging happens here - make this more explicit?
      storeFactory.getZMessagingApiStore.getAvs.setLogLevel(BuildConfigUtils.getLogLevelAVS(this))
    }

    inject[MessageNotificationsController]
    inject[ImageNotificationsController]
    inject[CallingNotificationsController]
  }

  override def onTerminate(): Unit = {
    controllerFactory.tearDown()
    storeFactory.tearDown()
    storeFactory = null
    controllerFactory = null
    super.onTerminate()
  }
}

class ZMessagingApiProvider(ctx: WireContext) {
  val api = ZMessagingApiFactory.getInstance(ctx)

  api.onCreate(ctx)

  ctx.eventContext.register(new Subscription {
    override def subscribe(): Unit = api.onResume()
    override def unsubscribe(): Unit = api.onPause()
    override def enable(): Unit = ()
    override def disable(): Unit = ()
    override def destroy(): Unit = api.onDestroy()
    override def disablePauseWithContext(): Unit = ()
  })
}
