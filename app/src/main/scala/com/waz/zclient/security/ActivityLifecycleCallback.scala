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
import com.waz.utils.events._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{Injectable, Injector, LaunchActivity}

class ActivityLifecycleCallback(implicit injector: Injector)
  extends Application.ActivityLifecycleCallbacks with Injectable with DerivedLogTag {

  private val activitiesRunning = Signal[(Int, Option[Activity])]((0, None))

  val appInBackground: Signal[(Boolean, Option[Activity])] = activitiesRunning.map { case (running, lastAct) => (running == 0, lastAct) }

  override def onActivityPaused(activity: Activity): Unit = synchronized {
    activity match {
      case _: LaunchActivity =>
      case _: AppLockActivity =>
      case _ =>
        verbose(l"onActivityPaused, activities still active: ${activitiesRunning.currentValue}, ${activity.getClass.getName}")
        activitiesRunning.mutate { case (running, _) => (running - 1, Option(activity))}
    }
  }

  override def onActivityResumed(activity: Activity): Unit = synchronized {
    activity match {
      case _: LaunchActivity =>
      case _: AppLockActivity =>
      case _ =>
        verbose(l"onActivityResumed, activities active now: ${activitiesRunning.currentValue}, ${activity.getClass.getName}")
        activitiesRunning.mutate { case (running, _) => (running + 1, Option(activity))}

    }
  }

  override def onActivityCreated(activity: Activity, bundle: Bundle): Unit = {}
  override def onActivityStarted(activity: Activity): Unit = {}
  override def onActivityStopped(activity: Activity): Unit = {}
  override def onActivityDestroyed(activity: Activity): Unit = {}
  override def onActivitySaveInstanceState(activity: Activity, bundle: Bundle): Unit = {}
}
