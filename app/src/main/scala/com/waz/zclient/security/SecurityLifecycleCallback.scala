/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.security

import android.app.{Activity, Application}
import android.os.Bundle
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{Injectable, Injector, LaunchActivity}
import com.waz.zclient.log.LogUI._

class SecurityLifecycleCallback(implicit injector: Injector) extends Application.ActivityLifecycleCallbacks with Injectable with DerivedLogTag {
  private var activitiesStarted = 0

  override def onActivityStarted(activity: Activity): Unit = synchronized {
    activity match {
      case _: LaunchActivity =>
      case _ =>
        activitiesStarted += 1
        verbose(l"onActivityStarted, activities active now: $activitiesStarted, ${activity.getClass.getName}")
        if (activitiesStarted == 1) inject[SecurityPolicyChecker].run(activity)
    }
  }

  override def onActivityStopped(activity: Activity): Unit = synchronized {
    activity match {
      case _: LaunchActivity =>
      case _ =>
        activitiesStarted -= 1
        verbose(l"onActivityStopped, activities still active: $activitiesStarted, ${activity.getClass.getName}")
        if (activitiesStarted == 0) inject[SecurityPolicyChecker].updateBackgroundEntryTimer()
    }
  }

  override def onActivityCreated(activity: Activity, bundle: Bundle): Unit = {}
  override def onActivityDestroyed(activity: Activity): Unit = {}
  override def onActivityPaused(activity: Activity): Unit = {}
  override def onActivityResumed(activity: Activity): Unit = {}
  override def onActivitySaveInstanceState(activity: Activity, bundle: Bundle): Unit = {}
}
