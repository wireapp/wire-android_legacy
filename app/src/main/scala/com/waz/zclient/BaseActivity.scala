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
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.log.InternalLog
import com.waz.permissions.PermissionsService
import com.waz.permissions.PermissionsService.{Permission, PermissionProvider}
import com.waz.service.{UiLifeCycle, ZMessaging}
import com.waz.services.websocket.WebSocketService
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.controllers.IControllerFactory
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.ViewUtils

import scala.collection.breakOut
import scala.collection.immutable.ListSet


class BaseActivity extends AppCompatActivity
  with ServiceContainer
  with ActivityHelper
  with PermissionProvider {

  import BaseActivity._

  lazy val themeController          = inject[ThemeController]
  lazy val globalTrackingController = inject[GlobalTrackingController]
  lazy val permissions              = inject[PermissionsService]

  def injectJava[T](cls: Class[T]) = inject[T](reflect.Manifest.classType(cls), injector)

  override protected def onCreate(savedInstanceState: Bundle) = {
    verbose(s"onCreate")
    super.onCreate(savedInstanceState)
    setTheme(getBaseTheme)
  }

  override def onStart(): Unit = {
    verbose(s"onStart")
    super.onStart()
    onBaseActivityStart()
  }

  def onBaseActivityStart(): Unit = {
    getControllerFactory.setActivity(this)
    ZMessaging.currentUi.onStart()
    inject[UiLifeCycle].acquireUi()
    if (!this.isInstanceOf[LaunchActivity]) permissions.registerProvider(this)
    Option(ViewUtils.getContentView(getWindow)).foreach(getControllerFactory.setGlobalLayout)
    WebSocketService(this)
  }

  override protected def onResume(): Unit = {
    verbose(s"onResume")
    super.onResume()
  }


  override protected def onResumeFragments(): Unit = {
    verbose("onResumeFragments")
    super.onResumeFragments()
  }

  override def onWindowFocusChanged(hasFocus: Boolean): Unit = {
    verbose(s"onWindowFocusChanged: $hasFocus")
  }

  def getBaseTheme: Int = themeController.forceLoadDarkTheme

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    verbose(s"onActivityResult: requestCode: $requestCode, resultCode: $resultCode, data: $data")
    super.onActivityResult(requestCode, resultCode, data)
    permissions.registerProvider(this)
  }

  override protected def onPause(): Unit = {
    verbose(s"onPause")
    super.onPause()
  }

  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    verbose("onSaveInstanceState")
    super.onSaveInstanceState(outState)
  }

  override def onStop() = {
    verbose(s"onStop")
    ZMessaging.currentUi.onPause()
    inject[UiLifeCycle].releaseUi()
    InternalLog.flush()
    super.onStop()
  }

  override def onDestroy() = {
    verbose(s"onDestroy")
    globalTrackingController.flushEvents()
    permissions.unregisterProvider(this)
    super.onDestroy()
  }

  def getControllerFactory: IControllerFactory = ZApplication.from(this).getControllerFactory

  override def requestPermissions(ps: ListSet[Permission]) = {
    verbose(s"requestPermissions: $ps")
    ActivityCompat.requestPermissions(this, ps.map(_.key).toArray, PermissionsRequestId)
  }

  override def hasPermissions(ps: ListSet[Permission]) = ps.map { p =>
    returning(p.copy(granted = ContextCompat.checkSelfPermission(this, p.key) == PackageManager.PERMISSION_GRANTED)) { p =>
      verbose(s"hasPermission: $p")
    }
  }

  override def onRequestPermissionsResult(requestCode: Int, keys: Array[String], grantResults: Array[Int]): Unit = {
    verbose(s"onRequestPermissionsResult: $requestCode, ${keys.toSet}, ${grantResults.toSet.map((r: Int) => r == PackageManager.PERMISSION_GRANTED)}")
    if (requestCode == PermissionsRequestId) {
      val ps = hasPermissions(keys.map(Permission(_))(breakOut))
      //if we somehow call requestPermissions twice, ps will be empty - so don't send results back to PermissionsService, as it will probably be for the wrong request.
      if (ps.nonEmpty) permissions.onPermissionsResult(ps)
    }
  }
}

object BaseActivity {
  val PermissionsRequestId = 162
}
