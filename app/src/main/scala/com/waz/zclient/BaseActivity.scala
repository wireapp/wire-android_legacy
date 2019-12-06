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

import android.app.admin.DevicePolicyManager
import android.app.{Activity, ActivityManager}
import android.content.pm.PackageManager
import android.content.{ComponentName, Context, Intent}
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.InternalLog
import com.waz.permissions.PermissionsService
import com.waz.permissions.PermissionsService.{Permission, PermissionProvider}
import com.waz.service.{UiLifeCycle, ZMessaging}
import com.waz.services.SecurityPolicyService
import com.waz.services.websocket.WebSocketService
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.Intents.RichIntent
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.controllers.IControllerFactory
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.{ContextUtils, ViewUtils}

import scala.collection.JavaConverters._
import scala.collection.immutable.ListSet
import scala.collection.{breakOut, mutable}
import scala.concurrent.duration._

class BaseActivity extends AppCompatActivity
  with ServiceContainer
  with ActivityHelper
  with PermissionProvider
  with DerivedLogTag {

  import BaseActivity._

  protected lazy val themeController = inject[ThemeController]
  protected lazy val userPreferences = inject[Signal[UserPreferences]]
  private lazy val permissions       = inject[PermissionsService]
  private lazy val activityLifecycle = inject[ActivityLifecycleCallback]
  private lazy val uiLifeCycle       = inject[UiLifeCycle]
  private lazy val secPolicy         = new ComponentName(this, classOf[SecurityPolicyService])
  private lazy val dpm               = getSystemService(Context.DEVICE_POLICY_SERVICE).asInstanceOf[DevicePolicyManager]

  def injectJava[T](cls: Class[T]) = inject[T](reflect.Manifest.classType(cls), injector)

  private val subs = mutable.HashSet[Subscription]()

  // there should be only one task but since we have access only to tasks
  // associated with our app we can safely exclude them all
  private def excludeFromRecents(exclude: Boolean): Unit =
    inject[ActivityManager].getAppTasks.asScala.toList.foreach(_.setExcludeFromRecents(exclude))

  override protected def onCreate(savedInstanceState: Bundle): Unit = {
    verbose(l"onCreate")
    super.onCreate(savedInstanceState)
    setTheme(getBaseTheme)

    if (BuildConfig.BLOCK_ON_PASSWORD_POLICY)
      SecurityPolicyService.checkAdminEnabled(dpm, secPolicy, ContextUtils.getString(R.string.security_policy_description)(this))(this)
  }

  override def onStart(): Unit = {
    verbose(l"onStart")
    super.onStart()
    onBaseActivityStart()
  }

  def onBaseActivityStart(): Unit = {
    getControllerFactory.setActivity(this)
    ZMessaging.currentUi.onStart()
    uiLifeCycle.acquireUi()
    permissions.registerProvider(this)
    Option(ViewUtils.getContentView(getWindow)).foreach(getControllerFactory.setGlobalLayout)
  }

  override protected def onResume(): Unit = {
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

    if (requestCode == RequestPoliciesEnable && resultCode == Activity.RESULT_OK) {
      verbose(l"enabling policies now")
      SecurityPolicyService.checkPassword(dpm, secPolicy)(this)
    }
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
    uiLifeCycle.releaseUi()
    InternalLog.flush()
    super.onStop()
  }

  override def onDestroy() = {
    verbose(l"onDestroy")
    subs.foreach(_.unsubscribe())
    subs.clear()
    inject[GlobalTrackingController].flushEvents()
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
  val RequestPoliciesEnable = 163
}
