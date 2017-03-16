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

import java.util

import android.content.pm.PackageManager
import android.os.Bundle
import android.support.annotation.NonNull
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.waz.api.{Permission, PermissionProvider}
import com.waz.zclient.common.controllers.PermissionActivity
import com.waz.zclient.controllers.IControllerFactory
import com.waz.zclient.core.stores.IStoreFactory
import com.waz.zclient.permissions.PermissionRequest
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.{PermissionUtils, TrackingUtils, ViewUtils}

import scala.collection.JavaConverters._

class BaseActivity extends AppCompatActivity
  with ServiceContainer
  with PermissionActivity
  with PermissionProvider {

  lazy val globalTracking = inject[GlobalTrackingController]

  private var started: Boolean = false
  private var permissionRequest = Option.empty[PermissionRequest]

  def injectJava[T](cls: Class[T]) = inject[T](reflect.Manifest.classType(cls), injector)

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setTheme(getBaseTheme)
  }

  override def onStart(): Unit = {
    super.onStart()
    onBaseActivityStart()
  }

  def getBaseTheme: Int = getControllerFactory.getThemeController.getTheme

  def onBaseActivityStart() = {
    getApplication.asInstanceOf[ZApplication].ensureInitialized()
    getControllerFactory.setActivity(this)
    if (!started) {
      started = true
      getStoreFactory.getZMessagingApiStore.getApi.onResume()
    }
    getStoreFactory.getZMessagingApiStore.getApi.setPermissionProvider(this)
    val contentView: View = ViewUtils.getView(getWindow.getDecorView, android.R.id.content)
    if (contentView != null) getControllerFactory.setGlobalLayout(contentView)
  }

  override def onStop() = {
    if (started) {
      getStoreFactory.getZMessagingApiStore.getApi.onPause()
      started = false
    }
    getStoreFactory.getZMessagingApiStore.getApi.removePermissionProvider(this)
    super.onStop()
  }

  override def finish() = {
    if (started) {
      getStoreFactory.getZMessagingApiStore.getApi.onPause()
      started = false
    }
    super.finish()
  }

  def getStoreFactory: IStoreFactory = ZApplication.from(this).getStoreFactory

  def getControllerFactory: IControllerFactory = ZApplication.from(this).getControllerFactory

  override def onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array[String], @NonNull grantResults: Array[Int]): Unit = {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    permissionRequest match {
      case Some(req) if requestCode == PermissionRequest.SERequestId =>
        TrackingUtils.tagChangedContactsPermissionEvent(globalTracking, permissions, grantResults)

        if (grantResults.length < 0) {
          req.cancel()
          permissionRequest = None
        }
        else if (PermissionUtils.verifyPermissions(grantResults:_*)) {
          req.grant()
          permissionRequest = None
        }
        else Option(req.getTargetResponseHandler).foreach { handler =>

          val resultMap = grantResults.zipWithIndex.map { case (result, i) =>
            (Permission.forId(permissions(i)), if (result == PackageManager.PERMISSION_GRANTED) Permission.Status.GRANTED else Permission.Status.DENIED)
          }.toMap.asJava

          handler.handleResponse(resultMap)
          permissionRequest = None
        }

      case _ => getControllerFactory.getRequestPermissionsController.onRequestPermissionsResult(requestCode, grantResults)
    }
  }

  def requestPermissions(ps: util.Set[Permission], callback: ResponseHandler) = {
    val sePermissionRequest = PermissionRequest(this, callback, ps)
    if (PermissionUtils.hasSelfPermissions(this, sePermissionRequest.getPermissionIds:_*)) sePermissionRequest.grant()
    else {
      // TODO: Maybe use {@link PermissionUtils.shouldShowRequestPermissionRationale(this, sePermissionRequest.getPermissionIds())} to explain why we need those permissions
      permissionRequest = Some(sePermissionRequest)
      sePermissionRequest.proceed()
    }
  }
}
