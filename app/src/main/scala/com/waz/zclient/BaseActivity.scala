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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.InternalLog
import com.waz.permissions.PermissionsService
import com.waz.permissions.PermissionsService.{Permission, PermissionProvider}
import com.waz.service.{UiLifeCycle, ZMessaging}
import com.waz.services.websocket.WebSocketService
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.returning
import com.waz.zclient.Intents.RichIntent
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.controllers.IControllerFactory
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.log.LogUI._

import scala.collection.breakOut
import scala.collection.immutable.ListSet
import scala.concurrent.duration._


class BaseActivity extends AppCompatActivity
  with ServiceContainer
  with ActivityHelper
  with PermissionProvider
  with DerivedLogTag {

  import BaseActivity._

  lazy val themeController          = inject[ThemeController]
  lazy val globalTrackingController = inject[GlobalTrackingController]
  lazy val permissions              = inject[PermissionsService]

  def injectJava[T](cls: Class[T]) = inject[T](reflect.Manifest.classType(cls), injector)

  override protected def onCreate(savedInstanceState: Bundle): Unit = {
    verbose(l"onCreate")
    super.onCreate(savedInstanceState)
    setTheme(getBaseTheme)
  }

  override def onStart(): Unit = {
    verbose(l"onStart")
    super.onStart()
    onBaseActivityStart()
  }

  def onBaseActivityStart(): Unit = {
    getControllerFactory.setActivity(this)
    ZMessaging.currentUi.onStart()
    inject[UiLifeCycle].acquireUi()
    permissions.registerProvider(this)
    Option(ViewUtils.getContentView(getWindow)).foreach(getControllerFactory.setGlobalLayout)
  }

  override protected def onResume(): Unit = {
    verbose(l"onResume")
    super.onResume()
    onBaseActivityResume()
  }

  def onBaseActivityResume(): Unit =
    CancellableFuture.delay(150.millis).foreach { _ =>
      WebSocketService(this)
    } (Threading.Ui)

  override protected def onResumeFragments(): Unit = {
    verbose(l"onResumeFragments")
    super.onResumeFragments()
  }

  override def onWindowFocusChanged(hasFocus: Boolean): Unit = {
    verbose(l"onWindowFocusChanged: $hasFocus")
  }

  def getBaseTheme: Int = themeController.forceLoadDarkTheme

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    verbose(l"onActivityResult: requestCode: $requestCode, resultCode: $resultCode, data: ${RichIntent(data)}")
    super.onActivityResult(requestCode, resultCode, data)
    permissions.registerProvider(this)
  }

  override protected def onPause(): Unit = {
    verbose(l"onPause")
    super.onPause()
  }

  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    verbose(l"onSaveInstanceState")
    super.onSaveInstanceState(outState)
  }

  override def onStop() = {
    verbose(l"onStop")
    ZMessaging.currentUi.onPause()
    inject[UiLifeCycle].releaseUi()
    InternalLog.flush()
    super.onStop()
  }

  override def onDestroy() = {
    verbose(l"onDestroy")
    globalTrackingController.flushEvents()
    permissions.unregisterProvider(this)
    super.onDestroy()
  }

  def getControllerFactory: IControllerFactory = ZApplication.from(this).getControllerFactory

  override def requestPermissions(ps: ListSet[Permission]) = {
    verbose(l"requestPermissions: $ps")
    ActivityCompat.requestPermissions(this, ps.map(_.key).toArray, PermissionsRequestId)
  }

  override def hasPermissions(ps: ListSet[Permission]) = ps.map { p =>
    returning(p.copy(granted = ContextCompat.checkSelfPermission(this, p.key) == PackageManager.PERMISSION_GRANTED)) { p =>
      verbose(l"hasPermission: $p")
    }
  }

  override def onRequestPermissionsResult(requestCode: Int, keys: Array[String], grantResults: Array[Int]): Unit = {
    verbose(l"onRequestPermissionsResult: $requestCode, ${keys.toSet.map(redactedString)}, ${grantResults.toSet.map((r: Int) => r == PackageManager.PERMISSION_GRANTED)}")
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
